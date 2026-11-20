# P11-R 部署说明（本地开发）

当前阶段只支持单机开发部署：FastAPI + SQLite + 单 Worker。

## 启动顺序

```powershell
# 1. 业务后端
cd services/business-api
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
copy .env.example .env
python scripts\seed_demo_snapshots.py
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000

# 2. 训练执行器（仓库根目录，另开终端）
$env:P11R_API_BASE_URL = "http://127.0.0.1:8000/api/v1"
$env:P11R_WORKER_TOKEN  = "<与后端一致>"
python -m ml.training.worker.main --loop --interval 2

# 3. 前端
cd apps/admin-web
npm install
copy .env.example .env.local
npm run dev
```

## 端口

| 服务 | 端口 |
|---|---|
| FastAPI | 8000 |
| Vite | 5173（被占用时自动递增） |

Vite 将 `/api` 代理到 `http://127.0.0.1:8000`，`VITE_API_BASE_URL=/api/v1`。

## 生产部署尚未实现

- PostgreSQL 多 Worker 调度、Redis + Celery；
- Worker 容器化与 GPU 节点调度；
- HTTPS、网关鉴权、审计日志归档；
- 密钥托管（KMS / Vault）与生产 Ed25519 密钥轮换；
- 产物对象存储与保留策略。
