# 银盾智鉴 · P11 训练任务与结果管理报告

> 文档编号：P11-TRAINING-REPORT
> 版本：v1.0
> 日期：2026-11-19
> 范围：A05 训练管理（训练任务创建、列表、详情、日志、指标、重试、取消）
> 结论：**P11 前端与 Mock 业务链路完成；真实 FastAPI 与 Python Worker 尚未实现**

---

## 0. 一句话结论

本轮在 `apps/admin-web` 内完成 A05 训练管理的全部页面与 Mock 业务适配层，
严格区分「模拟任务」与真实 Worker 任务；浏览器不执行训练、不伪造训练进度与成功结果。
真实 `services/business-api` 与 `ml/training` 在当前仓库中不存在，本轮未创建 Python Worker
代码，仅预留接口契约（`docs/api/P11_TRAINING_CONTRACT.md`）。

## 1. 执行前检查

| 检查项 | 结果 |
|---|---|
| Git 状态 | 仓库尚无提交，工作区包含 `apps/`、`docs/` 等目录（全部未跟踪） |
| `apps/admin-web` 工程 | 存在，Vue 3 + TS + Vite 6.4.6 + Element Plus + ECharts 5 |
| P09 API Service / Mock Adapter | 存在，`IS_MOCK` 开关与 `mockCall()` 复用 |
| P10 数据集模块 | 存在：`datasetStore`、`datasetVersions`、`datasetSamples`、`qualityReports`、去重与拆分 |
| P10 训练资格/泄漏复核 | 存在版本字段 `versionStatus`、`manualReviewStatus`、拆分数量 |
| Vue Router | `src/router/index.ts` 由 `ADMIN_MODULES` 生成，已挂载 A05 子页面 |
| Pinia | 会话 Store 存在；训练模块使用页面本地状态 + API Service |
| ECharts | `src/plugins/echarts.ts` 已注册 Line / Grid / Tooltip / Legend |
| `services/business-api` | **不存在** |
| `ml/training` | **不存在** |
| 已有 Python 训练代码 / Worker / 模型文件 | **不存在** |

必读文档已核对：`PRODUCT_BASELINE.md`、`ROLE_PERMISSION_MATRIX.md`、
`IMPLEMENTATION_PLAN.md`、`CONTRACT_DRAFT.md`、`P10_DATASET_REPORT.md`。

## 2. 文件清单

### 新增（10）

| 文件 | 说明 |
|---|---|
| `apps/admin-web/src/types/training.ts` | 训练领域类型、参数范围、参数校验 |
| `apps/admin-web/src/api/training.ts` | A05 Service，Mock / 真实接口切换 |
| `apps/admin-web/src/api/mock/trainingData.ts` | 12 条演示任务、日志、指标、结果种子 |
| `apps/admin-web/src/api/mock/trainingAdapter.ts` | Mock 业务实现、资格校验、状态机 |
| `apps/admin-web/src/views/training/TrainingListView.vue` | 任务列表、统计卡、筛选、操作 |
| `apps/admin-web/src/views/training/TrainingCreateView.vue` | 四步创建向导 |
| `apps/admin-web/src/views/training/TrainingDetailView.vue` | 任务详情、进度、结果 |
| `apps/admin-web/src/components/training/TrainingLogPanel.vue` | 日志面板 |
| `apps/admin-web/src/components/training/MetricChart.vue` | 指标折线图（含空状态） |
| `apps/admin-web/src/components/training/TrainingStatusTag.vue` | 状态标签（含「正在取消」） |
| `docs/api/P11_TRAINING_CONTRACT.md` | 预留 API / Worker 契约 |
| `docs/testing/P11_TRAINING_REPORT.md` | 本报告 |

### 修改（3）

| 文件 | 改动 |
|---|---|
| `apps/admin-web/src/router/index.ts` | A05 指向训练列表；新增 `/admin/training/new` 与 `/admin/training/:taskId` |
| `apps/admin-web/src/components/StatusTag.vue` | 新增 `succeeded / canceled / canceling` 预设 |
| `apps/admin-web/src/layouts/AdminLayout.vue` | 侧边栏版本文案更新为 P11 |

### 删除（1）

| 文件 | 说明 |
|---|---|
| `apps/admin-web/src/views/admin/TrainingManagementView.vue` | A05 旧占位页，已由训练列表取代 |

未修改 Android 工程，未删除 P09 / P10 代码。

## 3. 数据模型

### TrainingTask

`taskId、taskName、taskDescription、modelType、modelConfig（taskType/framework=pytorch/
modelFamily/inputModalities/implementationStatus/note）、datasetId、datasetName、
datasetVersionId、datasetVersion、datasetSplit、trainingParameters、taskStatus、
executionMode、createdAt、startedAt、completedAt、createdBy、errorMessage、progress、
cancelRequestedAt?、retryOfTaskId?、retriedByTaskId?、attempt`。

### TrainingParameters

`epochs（1–200）、batchSize（1–512）、learningRate（0.00001–0.1）、optimizer（adam/adamw/sgd）、
randomSeed（0–4294967295）、device（cpu/cuda/mps）、validationSplit（5–50%）、
checkpointInterval（1–50 且 ≤ epochs）`。

默认值：20 / 32 / 0.001 / adamw / 42 / cpu / 15 / 5。
`validateTrainingParameters()` 由表单与 Mock 服务端共用，真实 FastAPI 仍需独立校验。

### TrainingMetric / TrainingLog / TrainingResult

- 指标：`epoch、trainLoss、validationLoss、trainAccuracy、validationAccuracy、
  validationPrecision、validationRecall、validationF1、recordedAt`，
  训练集与验证集分列，不提供单一 Accuracy 结论。
- 日志：`logId、taskId、timestamp、level（INFO/WARNING/ERROR/DEBUG）、message、
  source（worker/mock_demo/api）`。
- 结果：`resultId、taskId、finalMetrics、bestCheckpoint、modelArtifactPath、
  datasetVersion、completedAt、evaluationStatus、evaluationSummary、simulated`。

## 4. 任务列表（A05 首页）

- 6 张统计卡：总数 / 等待执行 / 正在运行 / 成功 / 失败 / 已取消，由 `getTrainingStats()`
  对任务数组实时聚合。
- 列表列：任务（名称 + ID）、模型类型、数据集 / 版本、训练设备、创建时间、状态、执行模式、操作。
- 筛选：关键词（ID/名称）、模型类型、数据集、任务状态、执行模式、创建时间范围。
- 操作：详情、日志、指标、失败重试（仅 FAILED）、取消（仅 PENDING/RUNNING）；
  RUNNING 且已请求取消时展示「模拟确认停止」。
- 顶部 `el-alert` 固定提示「当前未连接真实 Python 训练执行器」。

## 5. 创建任务向导（四步）

1. **基本信息**：名称（必填、≤50）、描述（≤200）、模型类型、任务类型；
   模型类型携带 `implementationStatus`，页面明确显示「尚未实现（仅配置项）」。
2. **选择数据集**：读取 P10 数据集与版本，逐版本执行 7 项资格检查；
   不通过版本禁用选择并直接列出阻止原因；提供「执行 A04 演示质检与去重准备」，
   仅修改本地 Mock 数据。
3. **训练参数**：范围输入、默认值、表单错误提示。
4. **确认任务**：基本信息、数据拆分、参数、执行模式、资格检查结果；
   提交后状态为 `PENDING`，不会标记为成功。

Mock 服务端在创建时再次执行 `assertVersionTrainable`，前端结论不被信任。

## 6. 训练任务详情

- 基本信息、参数配置、执行进度（当前 / 总 Epoch、阶段、开始时间、更新时间、进度条）；
- 任务结果区：成功后展示最终指标、最佳检查点、产物路径、训练耗时、评估状态；
  `simulated = true` 显示「演示结果，未执行真实 PyTorch 训练」；
  未成功任务显示「系统不会生成模拟成功结果」；
- 标签页：任务概况 / 训练日志 / 训练指标，URL `?tab=` 同步。

## 7. 训练日志

- 等级筛选（全部 / INFO / WARNING / ERROR / DEBUG）、关键词搜索、分页（20/50/100）、刷新；
- ERROR 行整行标红，等级标签区分颜色；
- 来源列区分「真实 Worker / 演示日志 / 业务接口」；
- Mock 任务顶部固定提示「当前为演示日志，尚未连接真实训练执行器。」

## 8. 训练指标

- Epoch 进度展示当前 / 总轮数；
- 三个 ECharts 折线图：Loss（训练实线、验证虚线）、Accuracy（训练/验证）、
  Precision / Recall / F1 对比；
- 横轴 Epoch，Tooltip 显示数值，空数据展示 `el-empty`；
- 页面标注「演示指标：由 Mock 生成，未执行真实 PyTorch 训练，不能作为模型能力结论」。

## 9. 失败重试与任务取消

- 重试仅允许 `FAILED`：创建新任务（attempt+1、`retryOfTaskId`），原任务、失败原因、
  历史日志完整保留，原任务写 `retriedByTaskId`；重试前重新执行资格校验。
- 取消：PENDING 直接 `CANCELED`；RUNNING 先记录 `cancelRequestedAt` 并保持 RUNNING，
  页面显示「正在取消」，Mock 模式下需点击「模拟确认停止」后才进入 CANCELED。
- 已成功 / 已失败 / 已取消任务取消返回 409；非 FAILED 任务重试返回 409。
- 所有重试与取消操作均有二次确认。

## 10. P10 数据集资格校验衔接

七项检查（全部为阻断项）：

| 检查 | 数据来源 |
|---|---|
| 来源明确 | 样本 `sourceName` 非空比例 ≥ 90% |
| 许可允许训练 | `licenseStatus = verified && allowTraining` 比例 ≥ 75%（Mock 近似口径） |
| 必要质检 | A04 质检报告 `passedCount ≥ max(3, 30% 版本样本)` |
| 无未处理重复 | A04 去重分组中 `pending = 0` |
| 版本状态 | `versionStatus = published` |
| 同源泄漏复核 | `manualReviewStatus ≠ pending` |
| 拆分有效 | 样本量达标且训练/验证/测试均非空 |

真实后端必须按版本快照逐条复核，不能沿用数据集级近似值。

## 11. Python Worker 对接情况

- 未创建 Worker、未编写 PyTorch 训练代码、未创建模型文件；
- 已在 `docs/api/P11_TRAINING_CONTRACT.md` 定义：任务投递消息、Worker 上报事件
  （started / log / metric / checkpoint / succeeded / failed / canceled）、
  产物路径与安全约束；
- Service 层已按真实路径预留 `/admin/training-tasks/*`、`/admin/datasets/{id}/training-checks`、
  `/admin/dataset-versions/{id}/training-check`；
- `confirmSimulatedExecutorStop()` 在真实模式下直接抛错，禁止前端确认停止。

## 12. Mock 与真实任务的区分方式

| 位置 | 区分手段 |
|---|---|
| 数据 | `executionMode = mock/worker`、`TrainingLog.source`、`MetricSource`、`TrainingResult.simulated` |
| 列表 | 执行模式列显示「模拟任务」标签 + 顶部全局提示 |
| 创建 | 确认页与提交提示均说明为模拟任务 |
| 详情 | 顶部 Mock 提示、结果区 `simulated` 提示 |
| 日志 | `notice` 字段 + 顶部警告条 |
| 指标 | `notice` + 「演示指标」标识 |

## 13. 类型检查与构建

| 命令 | 结果 |
|---|---|
| `npm run typecheck` | 通过（0 error） |
| `npm run build` | 通过，2306 modules，21.39s |

产物体积：`TrainingDetailView` 546.87 kB（gzip 184.36 kB，含 ECharts），
`index` 1090.84 kB（gzip 360.05 kB，含 Element Plus）。属当前可接受范围，
后续可按需对 ECharts 做进一步拆分。

## 14. 实际测试记录

### 14.1 服务端 Mock 业务验证（Node + esbuild 打包真实源码，15/15 PASS）

| 用例 | 结果 |
|---|---|
| epochs = 0 / 201 被拒绝 | PASS |
| learningRate = 0.2 被拒绝 | PASS |
| checkpointInterval > epochs 被拒绝 | PASS |
| validationSplit = 4 被拒绝 | PASS |
| 默认参数合法 | PASS |
| 无许可样本 → 资格不合格 + 创建抛 `DATASET_NOT_TRAINABLE` | PASS |
| 拆分空集合 → 阻止原因包含「空集合」 | PASS |
| 同源泄漏 `manualReviewStatus = pending` → 阻止原因包含「人工复核」 | PASS |
| 草稿版本 → 版本状态阻止 | PASS |
| 完成质检 + 去重后 DS-2026-004 v1.1.0 可创建 PENDING 模拟任务 | PASS |
| FAILED 重试生成新任务且原任务/原因保留 | PASS |
| 非 FAILED 重试被拒绝 | PASS |
| RUNNING 取消先进「正在取消」，确认后 CANCELED | PASS |
| 已成功任务取消被拒绝 | PASS |

说明：许可 / 拆分 / 同源复核三类拦截通过运行时构造数据验证，验证脚本运行于临时目录，
执行后已删除，不进入仓库、不参与构建。

### 14.2 浏览器交互测试（http://localhost:5175）

| 场景 | 结果 |
|---|---|
| 列表加载 12 条、6 张统计卡、模拟任务标识、无控制台错误 | PASS |
| 搜索 `T-2026-003` → 1 条 | PASS |
| 状态筛选「训练失败」→ 2 条 | PASS |
| 翻页第 2 页 → 2 条，页码高亮正确 | PASS |
| 非失败任务无「重试」按钮 | PASS |
| 创建向导：空任务名 → 「请填写任务名称」，停留第 1 步 | PASS |
| 创建向导：DS-2026-004 执行演示质检去重后版本可选 | PASS |
| 创建向导：清空 Epochs → 「训练轮数必须是 1–200 的整数」，停留第 3 步 | PASS |
| 完整创建 → T-2026-013，等待执行、模拟任务 | PASS |
| 详情 T-2026-004：等待执行、0/20 Epoch、无成功结果说明 | PASS |
| 详情指标页：空状态，无伪造图表 | PASS |
| 详情日志页：演示日志提示 + 日志列表 | PASS |
| 失败任务 T-2026-009 重试 → T-2026-014，重试来源正确 | PASS |
| RUNNING T-2026-002 取消 → 正在取消 → 模拟确认停止 → 已取消 | PASS |

已知限制：Mock 数据仅保存在页面内存，整页刷新会重置；跨页面验证需通过 SPA 内导航完成。
这是 Mock 阶段的预期行为，不代表真实后端缺陷。

## 15. 尚未实现的能力

1. FastAPI 业务接口（`services/business-api` 不存在）；
2. Python 训练 Worker 与 PyTorch 训练流水线（`ml/training` 不存在）；
3. 任务队列 / 调度器、Worker 心跳与取消信号通道；
4. 真实日志、指标、Checkpoint、模型产物存储；
5. 模型评估流水线与评估报告（A06/P12 范围）；
6. 后端按版本快照的许可逐条复核（当前为 Mock 近似口径）；
7. Mock 数据持久化（刷新即重置）。

## 16. 验收结论

- A05 训练管理前端功能完整：列表、统计、筛选、分页、四步创建、详情、日志、
  指标、失败重试、任务取消、状态约束均已实现并通过实测；
- 训练任务与 P10 数据集版本建立可追溯关系，资格校验在创建与重试时二次执行；
- 浏览器未执行任何训练，未使用定时器伪造进度，未生成虚假成功结果；
- `typecheck` 与 `build` 均通过；
- 真实后端与 Worker 未实现，已在契约文档中明确预留，所有演示任务均有显式标识。

**P11 前端与 Mock 业务层验收通过；真实训练能力待后续轮次落地。**
