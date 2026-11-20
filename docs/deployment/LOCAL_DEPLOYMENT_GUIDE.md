# 银盾智鉴 · 本地部署指南

> 文档编号：P13-LOCAL-DEPLOYMENT-GUIDE
> 日期：2026-11-20
> 适用：开发、联调、验收环境（Windows PowerShell）。
> **本指南不覆盖生产部署；当前系统不具备生产上线条件。**

## 1. 环境要求

| 组件 | 版本 / 说明 |
|---|---|
| Windows | PowerShell 5.1 及以上 |
| Python | 3.11+，仓库根目录使用 `.venv` |
| Node.js | 18+，npm 9+ |
| JDK | 17 或 21（Android Gradle 构建） |
| Android SDK | compileSdk 35，`local.properties` 指向本机 SDK |
| 端口 | FastAPI 8000，Vite 5173 / 5175，Worker 轮询 2s |

## 2. 一次性初始化

```powershell
cd C:\Users\32546\Desktop\银盾智鉴

# Python 依赖（后端 + Worker 共用仓库根 .venv）
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r services\business-api\requirements.txt
pip install -r ml\training\requirements.txt

# Web 依赖
cd apps\admin-web
npm install
cd ..\..

# 后端环境变量
copy services\business-api\.env.example services\business-api\.env

# Web 环境变量
copy apps\admin-web\.env.example apps\admin-web\.env.local
```

数据库无需手工建表：FastAPI 启动时执行 `init_db()`，默认写入
`services/business-api/data/p11r.db`（目录与文件均被 `.gitignore` 排除）。

登记演示数据集快照（可选，用于本地训练联调）：

```powershell
.\.venv\Scripts\Activate.ps1
cd services\business-api
..\..\.venv\Scripts\python.exe scripts\seed_demo_snapshots.py
```

## 3. 启动顺序

### 3.1 FastAPI（终端 A）

```powershell
cd services\business-api
..\..\.venv\Scripts\python.exe -m uvicorn app.main:app --reload --port 8000
```

验证：

```powershell
curl.exe http://127.0.0.1:8000/health
# {"data":{"status":"ok","service":"p11r-business-api"}}
```

### 3.2 Python Worker（终端 B）

```powershell
cd C:\Users\32546\Desktop\银盾智鉴
$env:P11R_API_BASE_URL = "http://127.0.0.1:8000/api/v1"
$env:P11R_WORKER_TOKEN  = "dev-worker-token-change-me"
$env:P11R_WORKER_ID     = "worker-cpu-01"
.\.venv\Scripts\python.exe -m ml.training.worker.main --loop --interval 2
```

只执行一个任务：

```powershell
.\.venv\Scripts\python.exe -m ml.training.worker.main --once
```

### 3.3 管理端（终端 C）

```powershell
cd apps\admin-web
npm run dev
```

访问 `http://127.0.0.1:5173`（若被占用则使用启动日志中的端口）。
`.env.local` 中 `VITE_USE_MOCK=false` 时，Vite 将 `/api` 代理到 `127.0.0.1:8000`。

### 3.4 Android

```powershell
cd apps\android
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
```

APK：`apps\android\app\build\outputs\apk\debug\app-debug.apk`。
连接设备后：

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## 4. 验证清单

| 验证项 | 命令 / 操作 |
|---|---|
| 后端单测 | `cd services\business-api; ..\..\.venv\Scripts\python.exe -m pytest -q` → 19 passed |
| Worker 单测 | `python -m pytest ml\training\tests -q` → 5 passed |
| Web 类型检查 | `cd apps\admin-web; npm run typecheck` |
| Web 构建 | `npm run build` |
| Android 构建 | `gradlew.bat assembleDebug testDebugUnitTest` → 97 tests |
| 清单校验 | `python -m ml.training.verify_manifest ml\training\artifacts\<taskId>\artifact-manifest.json` |
| 训练闭环 | 管理端 A05 创建任务 → Worker 日志 → 任务成功 → A06 导入 |
| 发布拦截 | A06 版本详情 → 审核清单 → 灰度 / 正式应显示阻断 |

## 5. 常见问题

1. **页面显示演示数据**：确认 `apps/admin-web/.env.local` 中 `VITE_USE_MOCK=false`，并重启 Vite。
2. **导入模型提示 409 MODEL_IMPORT_INVALID**：训练任务必须 `succeeded` 且 `executionMode=worker`，并存在 `artifact-manifest.json`。
3. **模型哈希失败**：产物文件被修改或清单与文件不匹配；重新运行 Worker，不要手改清单。
4. **签名 not_verified**：未配置公钥或清单未签名；开发环境使用 `ml/training/keys/dev_ed25519_public.pem`。
5. **数据库锁**：SQLite 单 Worker 设计；不要同时运行多个 Worker。
6. **Android 构建找不到 SDK**：检查 `apps/android/local.properties` 的 `sdk.dir`。

## 6. 停止与清理

- 各终端 `Ctrl+C` 停止服务。
- 不要删除 `services/business-api/data/` 中的数据库，除非确认不再需要现有任务与模型记录。
- 训练产物位于 `ml/training/artifacts/`，可按任务清理，但会使已导入模型的哈希校验失败。
- 本轮不提供生产部署、Docker、反向代理或 HTTPS 配置。
