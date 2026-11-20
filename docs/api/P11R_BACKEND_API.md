# P11-R 后端接口文档（FastAPI + Worker）

Base URL：`http://127.0.0.1:8000/api/v1`

管理端接口严格对齐 [P11_TRAINING_CONTRACT.md](./P11_TRAINING_CONTRACT.md) 3.1–3.11；
内部 Worker 接口为本轮新增，仅用于独立训练执行器，不对浏览器开放。

## 1. 通用约定

| 项 | 约定 |
|---|---|
| 成功响应 | `{ "data": ... }` |
| 错误响应 | `{ "error": { "code": "...", "message": "...", "traceId": "..." } }` |
| traceId | 请求头 `X-Trace-Id` 可选；响应头回写；日志只记录方法与路径 |
| 鉴权（管理端） | `Authorization: Bearer <P11R_ADMIN_TOKEN>`；本地未配置时放行 |
| 鉴权（Worker） | `X-Worker-Token: <P11R_WORKER_TOKEN>` |
| 时间 | 数据库存 UTC ISO；接口输出东八区 `YYYY-MM-DD HH:mm` |
| 分页 | `page` 从 1 起，`pageSize` 上限 100 |

### 错误码

| HTTP | code | 说明 |
|---|---|---|
| 400 | `PARAMETER_INVALID` | 参数越界 / 请求体校验失败 |
| 400 | `TASK_NAME_REQUIRED` / `TASK_NAME_TOO_LONG` | 任务名称缺失或超过 50 字 |
| 400 | `MODEL_TYPE_INVALID` / `TASK_TYPE_INVALID` | 模型或任务类型不受支持 |
| 400 | `EVENT_TYPE_INVALID` / `EVENT_PAYLOAD_INVALID` | Worker 事件类型或字段不合法 |
| 401 | `WORKER_UNAUTHORIZED` / `ADMIN_UNAUTHORIZED` | 鉴权失败 |
| 404 | `TRAINING_TASK_NOT_FOUND` | 任务不存在 |
| 404 | `DATASET_VERSION_NOT_FOUND` / `DATASET_NOT_FOUND` | 服务端没有对应快照 |
| 409 | `TASK_STATE_INVALID` | 非法状态流转 |
| 409 | `LEASE_LOST` | 租约失效或 Worker 不匹配 |
| 409 | `CANCEL_NOT_REQUESTED` | 未收到取消请求却上报取消 |
| 422 | `DATASET_NOT_TRAINABLE` | 数据集资格校验未通过（message 附阻止原因） |

## 2. 管理端接口

### 2.1 `GET /admin/training-tasks/stats`

```json
{ "data": { "total": 1, "pending": 1, "running": 0, "succeeded": 0, "failed": 0, "canceled": 0 } }
```

### 2.2 `GET /admin/training-tasks/options`

返回 `TrainingOptions`：`modelTypes`、`optimizers`、`devices`、`statuses`、
`executionModes`、`taskTypes`、`datasets`（来自服务端快照，含版本列表）。

### 2.3 `GET /admin/training-tasks`

查询参数：`keyword`、`modelType`、`datasetId`、`taskStatus`、`executionMode`、
`createdFrom`、`createdTo`、`page`、`pageSize`。返回 `PageResult<TrainingTask>`，按创建时间倒序。

### 2.4 `GET /admin/training-tasks/{taskId}`

返回 `TrainingTask`，扩展字段 `trainingProfile`、`workerId` 便于排查来源。

### 2.5 数据集资格

- `GET /admin/datasets/{datasetId}/training-checks` → `TrainingDatasetCheck[]`
- `GET /admin/dataset-versions/{versionId}/training-check?datasetId=...` → `TrainingDatasetCheck`
- `POST /admin/dataset-snapshots` → 登记/覆盖服务端版本快照（见下）
- `POST /admin/dataset-versions/{versionId}/revoke` → 撤销快照，后续创建 / 重试 / Worker 开训都会被阻断

登记快照请求体（演示数据必须标记 `synthetic_demo`）：

```json
{
  "datasetId": "DS-2026-004",
  "datasetName": "演示·屏幕多模态反诈数据集（合成）",
  "datasetType": "multimodal",
  "datasetVersionId": "VER-DS-2026-004-02",
  "datasetVersion": "v2.0.0",
  "sampleCount": 40,
  "trainCount": 28,
  "validationCount": 6,
  "testCount": 6,
  "sourceStatus": "clear",
  "licenseStatus": "allowed",
  "qualityStatus": "passed",
  "duplicateStatus": "resolved",
  "leakageRisk": "grouped",
  "manualReviewStatus": "not_required",
  "versionStatus": "published",
  "snapshotSource": "synthetic_demo",
  "note": "合成演示快照"
}
```

### 2.6 `POST /admin/training-tasks`

请求体 `CreateTrainingTaskInput`（camelCase）。服务端依次校验名称、模型/任务类型、
参数范围，并重新执行七项数据集资格检查；通过后创建 `PENDING` 任务与初始日志。

### 2.7 日志 / 指标 / 结果

- `GET /admin/training-tasks/{taskId}/logs?level=&keyword=&page=&pageSize=`
  → `TrainingLogPage`（含 `executionMode`、`notice`）
- `GET /admin/training-tasks/{taskId}/metrics` → `TrainingMetricSeries`（`source=worker`）
- `GET /admin/training-tasks/{taskId}/result` → `TrainingResult | null`（未成功任务返回 `null`）

### 2.8 `POST /admin/training-tasks/{taskId}/retry`

仅 `FAILED` 可重试；重新执行资格校验；创建 `attempt + 1` 的新任务并记录 `retryOfTaskId`。

### 2.9 `POST /admin/training-tasks/{taskId}/cancel`

| 当前状态 | 行为 |
|---|---|
| `PENDING` | 直接 `CANCELED` |
| `RUNNING` | 记录 `cancelRequestedAt`，仍为 `RUNNING`，等待 Worker 确认 |
| 其他 | 409 `TASK_STATE_INVALID` |

## 3. 内部 Worker 接口

全部要求 `X-Worker-Token`。

### 3.1 `POST /internal/worker/tasks/claim`

```json
{ "workerId": "worker-cpu-01", "leaseSeconds": 120 }
```

返回任务上下文（无可领取任务时 `data=null`）：

```json
{
  "data": {
    "taskId": "T-2026-001",
    "taskName": "文本反诈分类-CPU Smoke Test",
    "datasetId": "DS-2026-004",
    "datasetVersionId": "VER-DS-2026-004-02",
    "datasetVersion": "v2.0.0",
    "model": { "taskType": "risk_binary", "framework": "pytorch", "implementationStatus": "not_implemented" },
    "parameters": { "epochs": 5, "batchSize": 8, "learningRate": 0.001, "randomSeed": 42, "checkpointInterval": 2, "optimizer": "adamw", "device": "cpu", "validationSplit": 15 },
    "datasetSnapshot": { "trainCount": 28, "validationCount": 6, "testCount": 6, "licenseStatus": "allowed", "versionStatus": "published" },
    "requestedAt": "2026-11-20T02:00:00Z",
    "attempt": 1,
    "trainingProfile": "smoke_test",
    "artifactDir": "ml/training/artifacts/T-2026-001",
    "cancelRequested": false,
    "eligibility": { "eligible": true, "checks": [], "blockedReasons": [] }
  }
}
```

### 3.2 `GET /internal/worker/tasks/{taskId}/context`

领取后重新获取上下文与最新资格状态。

### 3.3 `POST /internal/worker/tasks/{taskId}/heartbeat`

```json
{ "workerId": "worker-cpu-01", "leaseSeconds": 120 }
```

```json
{ "data": { "taskId": "T-2026-001", "taskStatus": "running", "cancelRequested": false, "heartbeatAt": "...", "leaseExpiresAt": "..." } }
```

Worker 不匹配或租约失效返回 409 `LEASE_LOST`。

### 3.4 `POST /internal/worker/tasks/{taskId}/events`

```json
{
  "eventId": "T-2026-001-task.metric-1a2b3c4d5e6f",
  "eventType": "task.metric",
  "timestamp": "2026-11-20T02:01:00Z",
  "workerId": "worker-cpu-01",
  "payload": {
    "epoch": 1,
    "trainLoss": 0.62,
    "validationLoss": 0.58,
    "trainAccuracy": 0.71,
    "validationAccuracy": 0.75,
    "validationPrecision": 0.74,
    "validationRecall": 0.78,
    "validationF1": 0.76
  }
}
```

事件类型与行为：

| eventType | 要求状态 | 行为 |
|---|---|---|
| `task.started` | `RUNNING` | 写入开始时间与日志 |
| `task.log` | `RUNNING` | 追加日志（自动脱敏） |
| `task.metric` | `RUNNING` | 按 `(taskId, epoch)` 幂等写入 |
| `task.checkpoint` | `RUNNING` | 记录检查点路径与验证损失 |
| `task.succeeded` | `RUNNING` | 置 `SUCCEEDED`，写 `TrainingResult` |
| `task.failed` | `RUNNING` | 置 `FAILED`，写脱敏错误信息 |
| `task.canceled` | `RUNNING` 且有取消请求 | 置 `CANCELED` |

同一 `eventId` 重复上报返回 `duplicate=true`，不重复写库。

## 4. 状态机

```
PENDING ──claim/started──▶ RUNNING ──succeeded──▶ SUCCEEDED
   │                         │
   └──cancel──▶ CANCELED ◀──┴──canceled（需先有取消请求）
                             └──failed──▶ FAILED ──retry──▶ 新 PENDING 任务
```

租约超时的 `RUNNING` 任务由服务端标记 `FAILED` 并写明原因，不自动重跑。
