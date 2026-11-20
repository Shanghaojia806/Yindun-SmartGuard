# 银盾智鉴 · P13 系统验收报告

> 文档编号：P13-SYSTEM-ACCEPTANCE-REPORT
> 日期：2026-11-20
> 范围：Android 用户端、Vue 管理端、FastAPI 业务后端、Python Worker、模型仓库与发布流程
> 结论：**工程基础设施与“训练 → 产物 → 模型仓库 → 发布拦截”链路真实可运行；老人端/家属端业务数据、管理端多数运营模块仍为 Mock 或占位；不具备生产上线条件。**

---

## 1. 项目整体架构

```text
apps/android（Kotlin + Compose）
  ├─ 老人端：引导 / 守护 / 记录 / 我的 + P05 屏幕采集 PoC
  ├─ 家属端：家庭 / 风险 / 协同 / 我的（本地 Fake 仓库）
  └─ 本机 Room / DataStore，无 INTERNET 权限

apps/admin-web（Vue 3 + TS + Element Plus + ECharts）
  └─ IS_MOCK 切换：Mock Adapter 或 Vite /api 代理 → FastAPI

services/business-api（FastAPI + SQLAlchemy 2 + SQLite/WAL）
  ├─ /admin/training-tasks/*   训练任务与日志/指标/结果
  ├─ /admin/dataset-snapshots  数据集快照登记与资格接口
  ├─ /admin/models/*           模型仓库、审核、发布、撤回、回滚
  └─ /internal/worker/*        Worker Token 隔离的内部接口

ml/training（Python Worker + PyTorch CPU）
  ├─ 领取任务 → 资格复核 → 训练 → checkpoint → model.pt
  └─ SHA-256 + artifact-manifest.json + Ed25519（开发密钥）签名
```

## 2. 三端功能覆盖矩阵

完整逐项矩阵见 `docs/testing/P13_ACCEPTANCE_MATRIX.md`。汇总：

| 端口 | REAL | PARTIAL | MOCK | PENDING |
|---|---|---|---|---|
| 老人端 E01–E15 | 7 | 4 | 1 | 3（E10/E11/E15） |
| 家属端 F01–F12 | 0（本地实现均按 Mock 计） | 2 | 9 | 1（F10） |
| 管理端 A01–A10 | 2（A05/A06） | 2（A04/A10） | 3（A01–A03） | 3（A07–A09） |

## 3. Android 构建和测试结果

| 命令 | 结果 |
|---|---|
| `gradlew.bat assembleDebug` | BUILD SUCCESSFUL（45 tasks，1m38s，UP-TO-DATE） |
| `gradlew.bat testDebugUnitTest` | BUILD SUCCESSFUL；10 个测试套件，**97 tests / 0 failures / 0 errors** |
| APK 产物 | `apps/android/app/build/outputs/apk/debug/app-debug.apk`（约 11.6 MB） |

Android 侧未执行 instrumented test；本轮未连接真机或模拟器做点击验收。

## 4. Web 构建和测试结果

| 命令 | 结果 |
|---|---|
| `npm run typecheck` | 通过，0 错误 |
| `npm run build` | 通过，built in 17.64s |
| 产物体积 | `index` 1,092.34 kB（gzip 360.43 kB）；`TrainingDetailView` 547.07 kB（gzip 184.43 kB） |
| Mock / Real 切换 | `.env.local` 中 `VITE_USE_MOCK=false`，真实模式已用于 A05/A06 |

P12 已对 A06 做浏览器实测；P13 未重复全量点击，仅完成构建与代码级检查。本轮修复了模型版本数据集卡片读取 Mock 的问题：真实模式改调 `GET /admin/model-versions/{id}/dataset-snapshot`。

## 5. FastAPI 测试结果

```text
python -m pytest -q  →  19 passed, 1 warning
```

覆盖：训练任务创建 / 列表 / 日志 / 指标 / 取消 / 重试、Worker Token 鉴权、数据集资格拦截、模型导入、哈希校验、签名校验、发布权限、撤回与回滚审计、重复导入幂等、数据集快照接口。

## 6. Worker 测试结果

```text
python -m pytest ml/training/tests -q  →  5 passed
```

覆盖：训练参数、产物写出、SHA-256、清单结构、签名与验签辅助逻辑。

## 7. 真实 CPU Smoke Test 记录

使用独立临时 SQLite（不污染 `data/p11r.db`）启动 FastAPI，真实 Worker 以 `--once` 领取任务：

```text
[server] health: ok
创建任务 T-2026-001 → 201 pending
[worker] 领取任务 T-2026-001（attempt=1）
[worker] 任务 T-2026-001 结束：succeeded

日志（11 条，原文）：
- 训练任务已创建，已通过服务端数据集资格校验，等待独立训练执行器领取。
- 任务已被训练执行器 worker-p13 领取，开始准备训练环境
- 训练执行器已开始执行 CPU Smoke Test
- 训练环境：PyTorch 2.15.0+cpu / 设备 CPU / trainingProfile=smoke_test / datasetType=synthetic
- 合成数据拆分：训练 84 / 验证 18 / 测试 18，固定随机种子 42
- Epoch 1/3：train_loss=0.6938 val_loss=0.7114 val_f1=0.4444
- Epoch 2/3：train_loss=0.6771 val_loss=0.6984 val_f1=0.4444
- Epoch 3/3：train_loss=0.6644 val_loss=0.6845 val_f1=0.5263
- 模型产物已写出：ml/training/artifacts/T-2026-001/model.pt（sha256=fb5405be49278d04…）
- 产物清单：ml/training/artifacts/T-2026-001/artifact-manifest.json，signatureStatus=signed
- 训练执行器上报任务成功，模型产物与清单已写入产物目录

结果行：
best_checkpoint_json = {"epoch":3,"checkpointPath":"ml/training/artifacts/T-2026-001/checkpoint-epoch-003.pt","validationLoss":0.684493}
model_artifact_path = ml/training/artifacts/T-2026-001/model.pt
manifest_path = ml/training/artifacts/T-2026-001/artifact-manifest.json
final_metrics_json = {"epoch":3,"trainLoss":0.664366,"validationLoss":0.684493,"trainAccuracy":0.583333,"validationAccuracy":0.5,"validationPrecision":0.625,"validationRecall":0.454545,"validationF1":0.526316}
evaluation_status = not_evaluated
simulated = 0
training_profile = smoke_test
```

闭环 11 项全部满足：创建 → 领取 → RUNNING → PyTorch 训练 → 日志 → 指标 → checkpoint → model.pt → SHA-256 → manifest → SUCCEEDED。

任务取消：创建后调用 `/cancel` 返回 `200 canceled`。
资格拦截：使用 `versionStatus=draft`、`licenseStatus=unknown` 的快照创建任务返回 `422 DATASET_NOT_TRAINABLE`。
Worker 异常退出 / 租约超时：由后端租约回收逻辑与测试覆盖，本轮未单独注入进程崩溃。

**边界：Smoke Test 使用合成数据和 3 个 epoch，只证明训练基础设施可运行，不代表任何反诈识别能力。**

## 8. 模型产物校验记录

Smoke Test 模型导入后：

| 检查 | 结果 |
|---|---|
| 关联训练任务 | `T-2026-001`，Worker 执行 |
| 文件 SHA-256 | 服务端重算 `fb5405be49278d04…`，`verified` |
| Ed25519 验签 | `verified`，`keyLabel=development` |
| 模型版本 | `v0.1.0-smoke`，`trainingProfile=smoke_test` |
| 数据集快照接口 | `200`，`dataSource=dataset_version_snapshot`，`eligible=true` |

P12 真实产物（T-2026-001/002）复核仍有效：`7627ff…` 未签名、`da09c4…` 开发密钥签名，均只允许受限内部测试。

## 9. 发布权限验证结果

| 渠道 | Smoke Test 模型 | 说明 |
|---|---|---|
| internal_test | eligible = true | 明确标记受限内部测试 |
| canary | eligible = false | smoke_test、合成数据、开发密钥、无真实测试报告 |
| production | eligible = false | 同上，另缺真实设备兼容性记录 |

服务端在 `approve / publish / rollback` 时重新计算，前端 `eligible` 不参与放行。P12 已实测 `422 MODEL_REVIEW_BLOCKED`，本轮未降低门槛。

## 10. 家庭关系和授权边界

- 家庭绑定、共享授权、处理记录、消息均为 Android 本地 Fake 仓库，**不存在两台手机之间的远程同步**。
- App 未声明 `INTERNET` 权限，当前不存在屏幕内容上传路径。
- 家属端只能读取本地 `FakeSharedRiskEventRepository` 中的共享事件，不能访问实时屏幕或原始录像。
- 撤销授权在本地仓库生效；没有真实服务端 ConsentRecord 可供跨设备验证。
- 真实跨设备链路（RiskEvent → ConsentRecord → SharedRiskEvent → HandlingRecord）仍属未实现。

## 11. 数据库迁移结果

- 使用 SQLAlchemy `Base.metadata.create_all()` 在启动时建表，SQLite 开启 WAL、`foreign_keys=ON`、`busy_timeout=5000`。
- **没有 Alembic 或任何版本化迁移脚本**；表结构变更无法平滑升级。
- 本轮使用临时库副本完成验收，未清空、未重置 `services/business-api/data/p11r.db`。
- 数据库文件位于 `services/business-api/data/`，已被 `.gitignore` 排除。

## 12. 安全审计结果

摘要（详见 `P13_SECURITY_AUDIT.md`）：

- 私钥、`.pem`、`.env.local`、SQLite、模型产物均未被 Git 跟踪。
- 请求日志只记录方法、路径、状态、耗时、traceId，不记录请求体或 Token。
- Worker 接口与前端管理接口分离，需 `X-Worker-Token`。
- **风险**：默认 `worker_token=dev-worker-token-change-me`；`admin_token` 为空时管理接口不鉴权；当前 Git 仓库尚无任何提交（0 tracked files）。
- 端侧无 INTERNET 权限，未发现未经授权的屏幕上传路径。

## 13. 性能与稳定性

| 项 | 结论 |
|---|---|
| Android 处理帧率 | 代码固定 5 FPS（可配 10 FPS），原始帧仍由系统刷新率产生 |
| ImageReader 缓冲 | `maxImages=2`，每帧 `acquireLatestImage()` 后 `close()`，节流跳过的帧同样释放 |
| Android CPU / 内存 | **本轮未测量**（无设备性能采样） |
| Web 加载 | 构建通过；`index` 1,092 kB，`TrainingDetail` 547 kB，需继续分包优化 |
| Worker 心跳 / 租约 | 心跳与 120s 租约已实现，异常退出依赖租约回收 |
| 数据库异常恢复 | WAL + busy_timeout；无迁移与备份演练 |
| 重复任务 / 事件 | 模型导入按 `trainingTaskId` 幂等；训练任务重复提交未做业务级去重 |

## 14. 尚未实现的功能

- 老人端 E10 反诈知识、E11 情景测试、E15 反馈帮助。
- 家属端 F10 反诈知识；F01 真实登录、F02–F09/F11 真实远程数据。
- 管理端 A07 规则管理、A08 知识库、A09 风险反馈（占位页）。
- A04 数据集列表 / 详情 / 标注 / 质检的真实后端 API。
- 真实 RBAC、真实管理员认证、操作审计后端、备份恢复。
- 真实端侧 AI 推理、模型下发、设备端模型部署与灰度。

## 15. 需要真实设备验证的内容

- MediaProjection 系统授权、前台服务通知、系统撤销授权后的资源释放。
- 5 FPS 采集下的 CPU、内存、发热与耗电。
- 字体缩放、触控区域、风险提醒在老人实际使用中的可理解性。
- 已知测试环境为华为 Mate 60 Pro / HarmonyOS 7.0.0.111，通过卓易通运行 APK，**属于 HarmonyOS 兼容环境，不等于原生 Android 系统测试**；原生 Android 真机测试仍待补。

## 16. 项目整体验收结论

| 维度 | 结论 |
|---|---|
| A. 已实现且真实测试通过 | Worker 训练闭环、任务取消、资格拦截、模型产物哈希/验签、发布资格拦截、撤回/回滚审计、管理端构建、Android 构建与 97 项单测 |
| B. 已实现但仅 Mock | 老人端风险事件与守护、家属端全部业务数据、A01–A03 运营数据 |
| C. 部分实现 | 屏幕采集 PoC、老人端语音/隐私、A04 数据集、A10 系统管理 |
| D. 尚未实现 | E10/E11/E15、F10、A07–A09、真实 RBAC、真实端侧 AI、跨设备家庭同步 |
| E. 存在阻断问题 | **无 P0 阻断**；但生产化前必须解决空 `admin_token`、默认 Worker Token、无数据库迁移、无真实身份认证 |

维度完成度：

- 工程基础设施：**高**（三端可构建，后端 + Worker 可运行）
- 前端功能完成度：老人端约 70%，家属端界面约 90% / 数据 0%，管理端约 60%
- 真实后端完成度：训练与模型域高，用户 / 设备 / 数据集业务域低
- 真实 AI 模型完成度：**0**（仅有 Smoke Test 基础设施验证）
- 端侧模型部署完成度：**0**
- 生产环境准备程度：**低**（本地开发级）

**最终判定：P13 验收通过“开发阶段交付”，不通过“生产上线”。**
