# P11-R 业务后端（FastAPI）

训练管理业务后端。负责训练任务的持久化、队列领取、数据集版本资格校验、
Worker 事件接收与状态流转。训练执行由独立的 `ml/training` Worker 完成，本服务不执行 PyTorch。

## 目录结构

```
services/business-api/
├── app/
│   ├── api/            # 管理端 / 内部 Worker 路由
│   ├── core/           # 配置、traceId、异常、日志、时间工具
│   ├── models/         # SQLAlchemy 2 数据表
│   ├── schemas/        # Pydantic 请求模型
│   ├── repositories/   # 队列适配器（SQLite 原子领取 / 租约）
│   ├── services/       # 任务服务、资格校验、Worker 事件、序列化
│   └── main.py         # 应用入口
├── scripts/            # 演示快照登记脚本
├── tests/              # pytest（真实 HTTP 栈 + 临时 SQLite）
├── requirements.txt
└── .env.example
```

## 本地运行（Windows）

```powershell
cd services/business-api
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
copy .env.example .env
python scripts\seed_demo_snapshots.py
python -m uvicorn app.main:app --reload --port 8000
```

- 健康检查：`GET http://127.0.0.1:8000/health`
- 交互式文档：`http://127.0.0.1:8000/docs`
- 数据库默认写入 `services/business-api/data/p11r.db`（已加入 .gitignore）

## 环境变量

| 变量 | 说明 |
|---|---|
| `P11R_DATABASE_URL` | 默认 SQLite；生产改 PostgreSQL 连接串 |
| `P11R_WORKER_TOKEN` | Worker 回调 Token，必须与 Worker 一致，不进入前端 |
| `P11R_ADMIN_TOKEN` | 管理端 Bearer Token，留空表示本地不校验 |
| `P11R_WORKER_LEASE_SECONDS` | 任务租约秒数，默认 120 |
| `P11R_CORS_ORIGINS` | 允许的前端来源 |
| `P11R_LOG_LEVEL` | 日志级别 |

## 任务队列（SQLite 单 Worker）

1. Worker 调用 `POST /api/v1/internal/worker/tasks/claim`；
2. 服务端在事务内选取最早的 `PENDING` 任务，用条件更新 `WHERE status='PENDING'` 原子抢占；
3. 领取成功立即写入 `workerId / heartbeatAt / leaseExpiresAt`；
4. Worker 每个 Epoch 调用心跳续约，并读取 `cancelRequested`；
5. 租约超时（`leaseExpiresAt < now`）的 `RUNNING` 任务在下次查询时被标记 `FAILED`，
   不会自动重跑，避免重复写入模型产物；
6. 多 Worker 调度留待迁移 PostgreSQL `FOR UPDATE SKIP LOCKED` 或 Redis + Celery，
   上层接口不需要修改。

## 数据集资格校验

- 唯一可信来源是 `dataset_version_snapshots` 表；
- 创建任务、失败重试、Worker 开训前都会重新执行七项阻断检查；
- 前端提交的 `licenseApproved / qualityPassed / manualReviewStatus` 等结论一律不采信；
- 快照可通过 `POST /api/v1/admin/dataset-snapshots` 登记，
  演示数据必须显式标记 `snapshotSource=synthetic_demo`。

## 测试

```powershell
cd services/business-api
.\.venv\Scripts\Activate.ps1
python -m pytest -q
```

测试使用独立临时 SQLite 库，不会修改本地开发数据。
