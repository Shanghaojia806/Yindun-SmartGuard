# P11-R 后端 + Worker 验收报告

日期：2026-11-20
范围：FastAPI 业务后端、SQLite 队列、独立 Python Worker、PyTorch CPU Smoke Test、
模型产物清单与 SHA-256 / Ed25519 校验、前端真实 API 接入。

> 重要边界：本轮只验证训练基础设施。所有训练使用固定种子的合成二分类数据，
> `trainingProfile=smoke_test`、`datasetType=synthetic`、`artifactType=smoke_test`。
> 它不是反诈模型，不代表任何诈骗识别能力，产物禁止进入正式发布流程。

## 一、交付物

| 类型 | 路径 |
|---|---|
| 业务后端 | `services/business-api/` |
| 训练执行器 | `ml/training/` |
| 接口文档 | `docs/api/P11R_BACKEND_API.md` |
| 后端 README | `services/business-api/README.md` |
| Worker README | `ml/training/README.md` |
| 前端接入 | `apps/admin-web/src/views/training/*`、`apps/admin-web/.env.example` |

## 二、目录结构

```
services/business-api/
├── app/
│   ├── api/           admin_training.py / admin_datasets.py / internal_worker.py / deps.py
│   ├── core/          config.py / errors.py（traceId）/ logging.py / time.py
│   ├── models/        6 张表：快照、任务、日志、指标、检查点、结果、Worker 事件
│   ├── repositories/  queue.py（SQLite 原子领取 / 租约 / 超时）
│   ├── schemas/       training.py / worker.py / common.py
│   ├── services/      training_service.py / dataset_eligibility.py / worker_events.py / serializers.py
│   └── main.py
├── scripts/seed_demo_snapshots.py
└── tests/             12 个后端测试
```

```
ml/training/
├── worker/            main.py / client.py / runner.py / artifacts.py
├── datasets/          synthetic.py
├── models/            smoke_model.py
├── callbacks/         reporter.py
├── artifacts/         运行产物（.gitignore 排除）
├── verify_manifest.py 独立校验脚本
└── tests/             5 个 Worker 测试
```

## 三、数据库结构

| 表 | 关键字段 |
|---|---|
| `dataset_version_snapshots` | dataset_id / dataset_version_id（唯一）/ 样本数 / 拆分数量 / source / license / quality / duplicate / leakage / manual_review / version_status / revoked / snapshot_source / snapshot_created_at |
| `training_tasks` | task_id / 名称 / 模型与任务类型 / 数据集与版本 / 拆分快照 / 参数 / status / execution_mode / training_profile / 时间 / cancel_requested_at / retry_of / retried_by / attempt / worker_id / heartbeat_at / lease_expires_at / last_event_at |
| `training_logs` | log_id / task_id / timestamp / level / message / source（api、worker） |
| `training_metrics` | (task_id, epoch) 唯一 / loss / accuracy / precision / recall / f1 / recorded_at |
| `training_checkpoints` | task_id / epoch / checkpoint_path / validation_loss |
| `training_results` | task_id / result_id / final_metrics / best_checkpoint / model_artifact_path / manifest_path / evaluation_status / simulated / training_profile |
| `worker_events` | event_id（主键，幂等）/ task_id / event_type / received_at / payload |

SQLite 启用 WAL、busy_timeout 与外键；应用重启后历史任务、日志、指标、结果均可读取。

## 四、队列、租约与状态机

- Worker 通过 `POST /api/v1/internal/worker/tasks/claim` 领取任务；
- SQLite 无 `SKIP LOCKED`，采用「条件更新抢占」：`UPDATE ... WHERE task_id=? AND status='PENDING'`，
  rowcount=1 才算领取成功；
- 领取即写入 `workerId / heartbeatAt / leaseExpiresAt`，状态 `PENDING → RUNNING`；
- 每个 Epoch 发送心跳续约，响应携带 `cancelRequested`；
- 租约超时的 RUNNING 任务标记 `FAILED` 并写 ERROR 日志，不自动重跑（避免重复产物）；
- 状态机：`PENDING → RUNNING → SUCCEEDED / FAILED`，`PENDING/RUNNING → CANCELED`，
  非法流转统一返回 409。

## 五、数据集资格校验

- 服务端快照是唯一可信来源；创建任务、失败重试、Worker 开训前共三次复核；
- 七项阻断检查：来源明确、许可允许训练且未撤销、质检通过、重复已处理、
  版本已发布、同源泄漏人工复核、训练/验证/测试拆分有效；
- 前端传入的 `licenseApproved / qualityPassed / manualReviewStatus` 等结论不参与判定；
- 演示快照通过 `scripts/seed_demo_snapshots.py` 登记，全部标记 `synthetic_demo`。

## 六、真实执行记录（Windows 本地）

环境：Python 3.14.6、PyTorch 2.15.0+cpu、SQLite、Uvicorn 0.54。

| 场景 | 任务 | 结果 |
|---|---|---|
| 正常训练 | T-2026-001（5 epochs） | `succeeded`，5 条指标、13 条日志、3 个检查点 + model.pt |
| 签名产物 | T-2026-002（4 epochs） | `succeeded`，`signatureStatus=signed`，`keyLabel=development` |
| 资格撤销 | T-2026-003 | 创建后被撤销版本，Worker 开训前阻断 → `failed`，无产物 |
| 失败重试 | T-2026-004（attempt=2） | `succeeded`，`retryOfTaskId=T-2026-003` |
| 运行中取消 | T-2026-008（200 epochs） | 取消请求后仍为 `running`，Worker 确认后 `canceled`，无 model.pt |
| 前端创建 + 取消 | T-2026-009 | 通过真实表单创建，列表显示「等待执行 / 真实 Worker」；取消后为「已取消」 |
| 前端重复重试 | T-2026-003 | 已重试过，再次点击返回 `TASK_ALREADY_RETRIED`，未生成重复任务 |

产物校验（T-2026-001，未配置密钥时）：

```
PASS：文件大小一致（4957 字节）
PASS：SHA-256 一致（7627ff557506a26ea1e9a35335cc3d20845110e23d0e442de56af6efc90e0ce7）
INFO：signatureStatus=missing，未签名产物不得进入正式发布流程
INFO：该产物为 CPU Smoke Test 结果，不是反诈模型，禁止正式发布。
```

T-2026-002 使用本地开发密钥签名后：

```
PASS：Ed25519 签名有效（keyLabel=development）
WARN：使用开发密钥签名，不能作为生产发布依据
```

## 七、测试结果

| 组 | 命令 | 结果 |
|---|---|---|
| 后端 | `cd services/business-api; python -m pytest -q` | 12 passed |
| Worker | `python -m pytest ml/training/tests -q` | 5 passed |
| 产物 | `python -m ml.training.verify_manifest ...` | 哈希一致；篡改文件后校验失败（测试覆盖） |
| 前端类型 | `npm run typecheck` | 通过 |
| 前端构建 | `npm run build` | 通过 |
| 前端联调 | Vite + 真实后端（VITE_USE_MOCK=false） | 列表、详情（5 条指标 / 日志 / model.pt）、创建页 7 项资格、UI 创建、UI 取消、重复重试拦截均通过 |

后端测试覆盖：启动与建表、创建/查询任务、参数拦截、资格校验（含 7 项）、
非法状态流转、Worker Token 鉴权、事件幂等、日志与指标持久化、取消、重试、
租约超时、心跳的 `LEASE_LOST`。

Worker 测试覆盖：真实 CPU 训练（指标 / 检查点 / 产物 / 清单）、取消、资格阻断、
训练异常上报、合成数据可复现与 SHA-256 校验。

## 八、前端 Mock / Worker 切换

- `VITE_USE_MOCK=false`：任务来自 FastAPI，页面显示「真实 Worker」；
- `VITE_USE_MOCK=true`：Mock 适配器继续可用，页面显示「模拟任务」；
- 页面文案与标签按 `IS_MOCK` 与任务自身 `executionMode` 条件渲染，不再无条件宣称模拟任务；
- `VITE_API_BASE_URL=/api/v1`，Vite 将 `/api` 代理到 `http://127.0.0.1:8000`；
- `.env.local` 与密钥文件均已被 `.gitignore` 排除。

## 九、尚未实现的生产能力

1. PostgreSQL 多 Worker 调度（`FOR UPDATE SKIP LOCKED`）与 Redis + Celery 迁移；
2. 真实反诈模型、真实数据、GPU 训练与分布式训练；
3. 逐样本级数据集复核（本轮为服务端聚合快照）；
4. 生产签名密钥托管与密钥轮换（本轮仅本地开发密钥）；
5. 管理端完整登录鉴权（当前可用 `P11R_ADMIN_TOKEN` 简单 Bearer 校验）；
6. 产物上传对象存储、训练资源配额与计费；
7. 与 P12 正式发布流程的自动衔接（Smoke Test 产物始终保持阻断）。

## 十、已知限制

- 未安装 NumPy，PyTorch 会输出一次 `Failed to initialize NumPy` 警告；本链路不依赖 NumPy，训练与产物均正常；
- 演示快照仅用于本地联调，不能视为 P10 真实数据集；
- 单 Worker 为第一阶段明确限制，多 Worker 并发领取未做压力验证。
