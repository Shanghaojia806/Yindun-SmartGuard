# 银盾智鉴 · 环境配置说明

> 文档编号：P13-ENVIRONMENT-CONFIGURATION
> 日期：2026-11-20
> 说明：所有配置均来自当前代码与 `.env.example`；未列出的变量不被读取。
> 密钥、Token、数据库、模型产物不得提交 Git。

## 1. 后端 `services/business-api`

来源：`services/business-api/.env.example`，加载后覆盖 `app/core/config.py` 默认值。

| 变量 | 默认 / 示例 | 作用 | 风险提示 |
|---|---|---|---|
| `P11R_DATABASE_URL` | `sqlite:///services/business-api/data/p11r.db` | SQLAlchemy 连接串 | 生产需改 PostgreSQL |
| `P11R_WORKER_TOKEN` | `dev-worker-token-change-me` | Worker 回调 Token | 生产必须替换为随机长 Token |
| `P11R_ADMIN_TOKEN` | 空 | 管理端 Bearer Token | **为空时不鉴权** |
| `P11R_WORKER_LEASE_SECONDS` | `120` | 任务租约秒数 | 过短会误判超时 |
| `P11R_ED25519_PUBLIC_KEY_PATH` | `ml/training/keys/dev_ed25519_public.pem` | 模型签名验签公钥 | 只允许公钥 |
| `P11R_MODEL_RELEASE_ROLES` | `系统管理员,发布管理员` | 允许审核 / 发布角色名 | 仅字符串比对，不是真实身份 |
| `P11R_CORS_ORIGINS` | `http://127.0.0.1:5173,...` | 允许的前端来源 | 生产需收敛 |
| `P11R_LOG_LEVEL` | `INFO` | 日志级别 | 不记录请求体与 Token |
| `P11R_APP_NAME` | `银盾智鉴业务服务`（代码默认） | 服务名 | — |

数据库行为：

- 启动时 `init_db()` 创建缺失表，无 Alembic 迁移。
- SQLite 开启 WAL、`foreign_keys=ON`、`busy_timeout=5000`。
- `services/business-api/data/` 整个目录被 `.gitignore` 排除。

## 2. Worker `ml/training`

| 变量 | 示例 | 作用 |
|---|---|---|
| `P11R_API_BASE_URL` | `http://127.0.0.1:8000/api/v1` | FastAPI 地址 |
| `P11R_WORKER_TOKEN` | 与后端一致 | 内部鉴权，仅放请求头 |
| `P11R_WORKER_ID` | `worker-cpu-01` | Worker 标识 |
| `P11R_ED25519_PRIVATE_KEY_PATH` | 可选路径 | 清单签名私钥 |
| `P11R_ED25519_KEY_LABEL` | `development` | 密钥标签，用于区分生产密钥 |
| `P11R_ARTIFACT_ROOT` | 可选，默认 `ml/training/artifacts` | 产物根目录（以代码为准） |

启动参数：

```powershell
python -m ml.training.worker.main --once
python -m ml.training.worker.main --loop --interval 2
```

产物目录：`ml/training/artifacts/{taskId}/{model.pt, checkpoint-epoch-XXX.pt, artifact-manifest.json}`。
签名私钥默认位置 `ml/training/keys/dev_ed25519_private.pem`，已被 `*.pem` 规则排除。

## 3. 管理端 `apps/admin-web`

来源：`apps/admin-web/.env.example` → 复制为 `.env.local`（已忽略）。

| 变量 | 默认 / 示例 | 作用 |
|---|---|---|
| `VITE_API_BASE_URL` | `/api/v1` | 业务接口前缀；开发时由 Vite 代理 |
| `VITE_APP_TITLE` | `银盾智鉴 · 管理后台` | 页面标题 |
| `VITE_USE_MOCK` | `false` | `true` 使用 Mock Adapter，`false` 调用 FastAPI |
| `VITE_MOCK_LATENCY_MS` | `260` | Mock 延迟，仅 Mock 模式生效 |

代理：`vite.config.ts` 将 `/api` 转发到 `http://127.0.0.1:8000`。

## 4. Android `apps/android`

| 项 | 值 |
|---|---|
| namespace / applicationId | `com.silvershield.app` |
| compileSdk / targetSdk | 35 / 35 |
| minSdk | 26 |
| Java / Kotlin | 17 |
| 版本 | `versionCode=1`，`versionName=0.1.0` |
| SDK 路径 | `apps/android/local.properties` 的 `sdk.dir`（已忽略） |

权限（`AndroidManifest.xml`）：

- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_MEDIA_PROJECTION`
- `POST_NOTIFICATIONS`
- **无 `INTERNET` 权限**（当前不存在网络上传能力）

服务：`ScreenCaptureService`（`foregroundServiceType=mediaProjection`，`stopWithTask=true`）。

## 5. 端口与地址

| 服务 | 地址 |
|---|---|
| FastAPI | `http://127.0.0.1:8000` |
| 健康检查 | `http://127.0.0.1:8000/health` |
| OpenAPI | `http://127.0.0.1:8000/docs` |
| 管理端开发服务器 | `http://127.0.0.1:5173`（占用时自动改用 5174/5175） |

## 6. 配置安全要求

1. 任何共享环境必须设置 `P11R_ADMIN_TOKEN`，并接入真实身份认证。
2. `P11R_WORKER_TOKEN` 必须随机生成，只存在于后端与 Worker 环境。
3. `*.pem`、`.env*`、`data/`、`artifacts/` 一律不提交；`.env.example` 只放占位值。
4. 生产签名私钥不得放在仓库目录；建议使用 KMS 或独立密钥管理。
5. 正式渠道发布前必须替换开发公钥 / 私钥并保留密钥轮换与吊销记录。
