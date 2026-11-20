# 银盾智鉴 · A05 训练管理接口契约（P11）

> 文档编号：P11-TRAINING-CONTRACT
> 版本：v0.1（前端已按此契约实现 Mock Adapter，FastAPI / Worker 尚未落地）
> 状态：**预留契约**。`services/business-api` 与 `ml/training` 在当前仓库中不存在，
> 本文档用于约束 P12 及后续真实后端实现，不代表任何接口已完成。
> 最后更新：2026-11-19

---

## 0. 边界声明

- 浏览器只负责任务配置、状态展示、日志与结果查看，**不执行任何模型训练**。
- 训练由独立 Python Worker 执行，框架为 PyTorch。
- 当前 `apps/admin-web` 通过 `IS_MOCK` 开关使用 `src/api/mock/trainingAdapter.ts`；
  所有任务 `executionMode = "mock"`，页面固定展示「模拟任务」标识。
- 本文档中的路径与字段与 `src/types/training.ts`、`src/api/training.ts` 一一对应。

## 1. 总体链路

```
Vue 3 Admin
  → API Service（src/api/training.ts）
  → FastAPI Business API（/admin/training-tasks/*）
  → Training Task Queue / Dispatcher
  → Python Training Worker（PyTorch）
  → 日志 / 指标 / Checkpoint / 结果 落库或对象存储
  → FastAPI
  → Vue 3 Admin
```

## 2. 通用约定

| 项 | 约定 |
|---|---|
| Base Path | `/api/v1` |
| 认证 | `Authorization: Bearer <access_token>`（沿用 P09 约定） |
| 分页 | `page` 从 1 起，`pageSize` 默认 20，上限 100 |
| 时间 | ISO 8601（UTC），前端负责按本地时区展示 |
| 状态更新权 | 仅执行器或经授权的业务服务可写 `taskStatus`；前端只读 |
| 执行模式 | `mock`（演示） / `worker`（真实 Worker） |

错误响应沿用 `CONTRACT_DRAFT.md` 结构：

```json
{ "error": { "code": "DATASET_NOT_TRAINABLE", "message": "数据集版本不满足训练条件：…", "traceId": "..." } }
```

## 3. 管理端接口

### 3.1 概览统计

`GET /admin/training-tasks/stats`

```json
{ "data": { "total": 12, "pending": 3, "running": 2, "succeeded": 3, "failed": 2, "canceled": 2 } }
```

要求：由任务表实时聚合，不得写死；`mock` 与 `worker` 任务是否分开统计由后端决定，
前端固定展示「含演示任务」说明。

### 3.2 配置项

`GET /admin/training-tasks/options`

返回 `TrainingOptions`：模型类型、优化器、设备、任务状态、执行模式、任务类型、数据集及版本。
模型类型必须携带 `implementationStatus`（`not_implemented | planned | worker_ready`），
用于避免把「配置项」宣称为已完成的模型架构。

### 3.3 任务列表

`GET /admin/training-tasks`

| 参数 | 类型 | 说明 |
|---|---|---|
| keyword | string | 任务 ID / 任务名称模糊搜索 |
| modelType | string | 模型类型 |
| datasetId | string | 数据集 |
| taskStatus | string | `pending/running/succeeded/failed/canceled` |
| executionMode | string | `mock` / `worker` |
| createdFrom / createdTo | date | 创建时间范围（含边界） |
| page / pageSize | int | 分页 |

响应：`PageResult<TrainingTask>`，按 `createdAt` 倒序。

### 3.4 任务详情

`GET /admin/training-tasks/{taskId}` → `TrainingTask`

### 3.5 数据集资格校验

- `GET /admin/datasets/{datasetId}/training-checks` → `TrainingDatasetCheck[]`（全部版本）
- `GET /admin/dataset-versions/{versionId}/training-check?datasetId=...` → `TrainingDatasetCheck`

七项检查（全部为阻断项）：

| key | 含义 |
|---|---|
| source | 数据集来源明确 |
| license | 使用许可允许模型训练 |
| quality | 样本通过必要质检 |
| duplicate | 没有未处理的重复数据 |
| version_status | 版本状态为 `published` |
| manual_review | 同源数据泄漏风险已完成人工复核 |
| split | 训练 / 验证 / 测试拆分均非空且样本量达标 |

真实后端必须**基于版本快照逐条校验原始样本**，不得复用数据集级聚合近似值
（Mock 的许可阈值 75%、质检阈值 30% 仅为演示口径）。

### 3.6 创建任务

`POST /admin/training-tasks`

请求体：`CreateTrainingTaskInput`

```json
{
  "taskName": "文本反诈分类-第三轮训练",
  "taskDescription": "可选",
  "modelType": "text_risk_classifier",
  "taskType": "risk_binary",
  "datasetId": "DS-2026-004",
  "datasetVersionId": "VER-DS-2026-004-02",
  "trainingParameters": {
    "epochs": 20, "batchSize": 32, "learningRate": 0.001, "optimizer": "adamw",
    "randomSeed": 42, "device": "cpu", "validationSplit": 15, "checkpointInterval": 5
  }
}
```

服务端必须依次执行：

1. 字段与长度校验（名称 ≤ 50、描述 ≤ 200）；
2. 模型类型与任务类型兼容性校验；
3. 参数范围校验（与 `validateTrainingParameters` 一致）；
4. **重新执行数据集资格校验**（不得信任前端结论）；
5. 创建 `PENDING` 任务，写入 `createdBy`、`modelConfig`、`datasetSplit` 快照与初始日志。

成功返回 201 + `TrainingTask`。失败错误码：

| HTTP | code | 场景 |
|---|---|---|
| 400 | TASK_NAME_REQUIRED / TASK_NAME_TOO_LONG | 名称缺失或超长 |
| 400 | PARAMETER_INVALID | 参数越界 |
| 422 | DATASET_NOT_TRAINABLE | 资格校验未通过（message 附阻止原因） |
| 404 | DATASET_VERSION_NOT_FOUND | 版本不存在 |

### 3.7 日志

`GET /admin/training-tasks/{taskId}/logs?level=&keyword=&page=&pageSize=`

响应 `TrainingLogPage`：`items`、`page`、`pageSize`、`total`、`executionMode`、`notice`。

- `notice` 在 `mock` 任务下必须为「当前为演示日志，尚未连接真实训练执行器。」
- 日志不得包含密钥、令牌、个人隐私原文；敏感信息必须脱敏。

### 3.8 指标

`GET /admin/training-tasks/{taskId}/metrics` → `TrainingMetricSeries`

```json
{ "taskId": "T-2026-001", "source": "worker", "metrics": [], "notice": "指标来自真实训练执行器上报。" }
```

字段：`epoch、trainLoss、validationLoss、trainAccuracy、validationAccuracy、
validationPrecision、validationRecall、validationF1、recordedAt`。
`source = mock` 时前端必须展示演示标识，且不得用于模型能力结论。

### 3.9 结果

`GET /admin/training-tasks/{taskId}/result` → `TrainingResult | null`

未成功任务必须返回 `null`，禁止返回伪造的成功结果。`simulated = true` 表示演示结果。

### 3.10 重试

`POST /admin/training-tasks/{taskId}/retry`

- 仅 `FAILED` 可重试，否则 409 `TASK_STATE_INVALID`；
- 保留原任务、`errorMessage` 与历史日志，写入 `retriedByTaskId`；
- 创建新任务记录（`attempt + 1`，`retryOfTaskId = 原任务`）；
- **重新执行数据集资格校验**，失败则 422。

### 3.11 取消

`POST /admin/training-tasks/{taskId}/cancel`

| 当前状态 | 行为 |
|---|---|
| PENDING | 直接置 `CANCELED`（执行器确认无进程可停） |
| RUNNING | 记录 `cancelRequestedAt`，状态保持 `RUNNING`，向 Worker 发送取消信号；Worker 确认后才置 `CANCELED` |
| SUCCEEDED / FAILED / CANCELED | 409 `TASK_STATE_INVALID` |

前端在 `RUNNING + cancelRequestedAt` 时展示「正在取消」，禁止提前显示已取消。

## 4. Worker 契约（预留）

### 4.1 任务投递（FastAPI → Worker）

队列消息建议字段：

```json
{
  "taskId": "T-2026-013",
  "datasetId": "DS-2026-004",
  "datasetVersionId": "VER-DS-2026-004-02",
  "model": { "type": "text_risk_classifier", "taskType": "risk_binary", "framework": "pytorch" },
  "parameters": { "epochs": 20, "batchSize": 32, "learningRate": 0.001, "optimizer": "adamw",
                  "randomSeed": 42, "device": "cpu", "validationSplit": 15, "checkpointInterval": 5 },
  "datasetSnapshot": { "trainCount": 28, "validationCount": 6, "testCount": 6 },
  "requestedAt": "2026-11-19T08:00:00Z"
}
```

Worker 必须再次校验数据集版本快照与许可，失败时上报 `FAILED` 而非开始训练。

### 4.2 Worker 上报

| 事件 | 说明 |
|---|---|
| task.started | 置 `RUNNING`，写 `startedAt` 与当前 Epoch |
| task.log | 追加 INFO / WARNING / ERROR / DEBUG 日志 |
| task.metric | 每个 Epoch 上报一行训练集 / 验证集指标 |
| task.checkpoint | 按 `checkpointInterval` 上报检查点路径 |
| task.succeeded | 置 `SUCCEEDED`，写 `completedAt`、`TrainingResult` |
| task.failed | 置 `FAILED`，写 `errorMessage`（脱敏） |
| task.canceled | 收到取消信号并停止后置 `CANCELED` |

状态机只允许：`PENDING → RUNNING → SUCCEEDED`、`PENDING/RUNNING → CANCELED`、
`RUNNING → FAILED`、`FAILED → 新任务（重试）`。任何已完成任务不得被改回 `RUNNING`。

### 4.3 产物管理

- Checkpoint 与模型文件写入独立产物目录 / 对象存储，路径记录在 `TrainingCheckpoint` 与
  `TrainingResult.modelArtifactPath`；
- 训练缓存与模型产物不得混入数据集目录；
- 模型发布（A06）留待 P12，本轮只记录产物路径与评估状态。

## 5. 安全约束（与需求第十二节对应）

1. 训练数据必须来自 A04 已发布且通过资格校验的版本；
2. 后端必须重新验证许可，禁止使用无许可样本；
3. 禁止将用户私人屏幕内容自动导入训练集；
4. 日志不得泄露密钥与隐私信息；
5. 训练任务与数据集版本必须建立可追溯关系（`datasetId + datasetVersionId` 快照）；
6. Mock 与真实任务必须通过 `executionMode / source / simulated` 明确区分。
