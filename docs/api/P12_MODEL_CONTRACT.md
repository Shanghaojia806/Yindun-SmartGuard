# 银盾智鉴 · A06 模型版本管理与发布接口契约（P12）

> 文档编号：P12-MODEL-CONTRACT
> 版本：v1.0（FastAPI 已落地，接入 P11-R 真实训练产物）
> 状态：**已实现**。`services/business-api` 提供 `/api/v1/admin/models/*` 与
> `/api/v1/admin/model-versions/*`；模型文件位于 `ml/training/artifacts/{taskId}/`。
> 最后更新：2026-11-20

---

## 0. 边界声明

- 浏览器只负责仓库浏览、版本详情、审核清单预览、发布 / 撤回 / 回滚操作与记录查看。
- 浏览器**不读取、不写入、不校验模型文件**，不做量化、推理或哈希计算。
- 真实模式（`IS_MOCK=false`）下数据来自 FastAPI；Mock 模式（`IS_MOCK=true`）继续使用
  `src/api/mock/modelAdapter.ts`，产物 `artifactSource = demo_record`，页面持续展示演示提示。
- 真实模型版本的 `artifactSource = training_worker`，必须关联 P11-R `TrainingTask`，
  文件哈希、Ed25519 签名、数据集资格与兼容性全部由服务端重新校验。
- 真实发布必须由后端重新校验权限、审核状态、文件哈希、签名、数据集合法性与兼容性，
  **前端按钮状态不能作为放行依据**。
- Smoke Test（`trainingProfile=smoke_test`）与合成数据（`datasetType=synthetic` 或
  `snapshotSource=synthetic_demo`）产物只允许受限内部测试，不构成反诈模型发布成果。

## 1. 总体链路

```
Vue 3 Admin
  → API Service（src/api/model.ts）
  → FastAPI Business API（/admin/models/*、/admin/model-versions/*）
  → Model Artifact Storage（真实文件 + sha256 + 签名）
  → Training / Evaluation 结果（P11 Worker 产出）
  → Release / Audit 表（只追加，不可删除）
  → Vue 3 Admin
```

## 2. 通用约定

| 项 | 约定 |
|---|---|
| Base Path | `/api/v1` |
| 认证 | `Authorization: Bearer <access_token>`（沿用 P09 约定） |
| 分页 | `page` 从 1 起，`pageSize` 默认 20，上限 100 |
| 时间 | ISO 8601（UTC），前端按本地时区展示 |
| 状态写入权 | 仅业务服务可写 `releaseStatus`；前端只调用动作接口 |
| 产物来源 | `artifactSource = demo_record / training_worker / manual_upload` |
| 验证状态 | `verified / not_verified / failed / demo_record / not_available` |

错误响应沿用 `CONTRACT_DRAFT.md`：

```json
{ "error": { "code": "MODEL_RELEASE_BLOCKED", "message": "发布前置条件未满足：…", "traceId": "..." } }
```

## 3. 管理端接口

### 3.1 概览统计

`GET /admin/models/stats`

```json
{ "data": { "modelTotal": 4, "versionTotal": 7, "pendingReview": 1, "published": 1, "withdrawn": 1, "compatibilityUntested": 2 } }
```

要求：由模型 / 版本表实时聚合，不得写死；演示数据与真实数据需通过返回字段或环境标识区分。

### 3.2 配置项

`GET /admin/models/options`

返回 `ModelOptions`：模型类型、框架、发布状态、兼容性状态、发布渠道、数据集版本、训练任务。
渠道必须携带 `note`，明确内部测试为演示渠道、不向真实设备下发。

### 3.3 模型仓库列表

`GET /admin/models`

| 参数 | 类型 | 说明 |
|---|---|---|
| keyword | string | 模型名称 / 模型 ID 模糊搜索 |
| modelType | string | 模型类型 |
| framework | string | 训练框架 |
| releaseStatus | string | 按最新版本状态筛选 |
| compatibilityStatus | string | 按最新版本兼容性筛选 |
| publishedFrom / publishedTo | date | 当前发布版本发布时间范围 |
| page / pageSize | int | 分页 |

响应：`PageResult<ModelSummary>`，包含最新版本、当前发布版本、训练来源、兼容性等派生字段。

### 3.4 模型详情

`GET /admin/models/{modelId}` → `ModelSummary`

### 3.5 版本列表

`GET /admin/models/{modelId}/versions`

| 参数 | 类型 | 说明 |
|---|---|---|
| keyword | string | 版本号 / 版本 ID |
| releaseStatus | string | 发布状态 |
| compatibilityStatus | string | 兼容性 |
| page / pageSize | int | 分页 |

响应：`PageResult<ModelVersion>`，按 `createdAt` 倒序。

### 3.6 版本详情

`GET /admin/models/{modelId}/versions/{modelVersionId}` → `ModelVersion`

同时需要：

- `GET /admin/model-artifacts/{artifactId}` → `ModelArtifact`
- `GET /admin/model-artifacts?modelId={modelId}` → `ModelArtifact[]`

### 3.7 审核清单

`GET /admin/model-versions/{modelVersionId}/review-check?channel={channel}` → `ModelReviewResult`

渠道：`internal_test / canary / production`。返回 11 项检查：

| key | 审核项 | 阻断条件 |
|---|---|---|
| training_status | 关联训练任务成功 | 任务缺失或非 `succeeded` |
| dataset_version | 数据集版本有效 | 版本不存在或非 `published` |
| dataset_review | 数据集通过必要审核 | `quality_blocked / archived / unknown` |
| leakage_review | 同源数据泄漏人工复核 | 人工复核为 `pending` 或无法确认 |
| artifact_exists | 模型文件存在 | 产物记录缺失；演示记录在正式渠道阻断 |
| file_integrity | 文件完整性验证 | 哈希失败；无真实哈希在正式渠道阻断 |
| signature | 数字签名验证 | 签名失败；无真实签名在正式渠道阻断 |
| evaluation | 模型评估完成 | `not_evaluated`；演示指标在正式渠道阻断 |
| test_evaluation | 测试集评估有效 | 未记录测试集版本；演示指标在正式渠道阻断 |
| runtime_compatibility | 目标运行时兼容性 | 存在 `untested / test_failed / incompatible`；部分兼容仅允许内部测试 |
| publish_permission | 发布审批权限 | 角色不含「系统管理员 / 发布管理员」 |

返回：

```json
{
  "data": {
    "modelVersionId": "MV-2026-001-03",
    "channel": "production",
    "checks": [{ "key": "file_integrity", "label": "文件完整性验证", "status": "unverified", "blocking": true, "detail": "..." }],
    "eligible": false,
    "blockedReasons": ["文件完整性验证：没有真实模型文件，无法进行哈希校验"],
    "checkedAt": "2026-11-20T01:00:00Z",
    "note": "灰度 / 正式发布要求真实模型文件、签名与完整设备测试"
  }
}
```

### 3.8 提交审核

`POST /admin/model-versions/{modelVersionId}/submit-review`

```json
{ "channel": "internal_test", "notes": "提交内部测试审核", "operator": "演示管理员", "roleLabel": "系统管理员（模拟）" }
```

允许状态：`draft / rejected / pending_review` → `pending_review`，并追加发布记录与审计记录。

### 3.9 审核通过 / 拒绝

- `POST /admin/model-versions/{modelVersionId}/approve` → `pending_review → approved`
  - 服务端必须重新执行审核清单；`eligible = false` 返回 `422 MODEL_REVIEW_BLOCKED`。
- `POST /admin/model-versions/{modelVersionId}/reject` → `pending_review → rejected`
  - 必须填写拒绝原因。

### 3.10 发布

`POST /admin/model-versions/{modelVersionId}/publish`

```json
{ "channel": "internal_test", "deviceScope": "内部测试设备（演示）", "notes": "演示发布", "operator": "演示管理员", "roleLabel": "系统管理员（模拟）" }
```

前置条件（服务端逐项校验，任一不满足返回 422）：

1. 角色具备发布权限；
2. 版本状态为 `approved`；
3. 审核清单 `eligible = true`；
4. 文件哈希、签名、评估、数据集与兼容性满足目标渠道要求；
5. 内部测试渠道必须明确标记为演示发布，不产生真实可用模型。

成功：`approved → published`，追加 `ReleaseRecord`（`releaseId`、渠道、操作人、审核人、发布时间、说明）与审计记录。

### 3.11 撤回

`POST /admin/model-versions/{modelVersionId}/withdraw`

```json
{ "reason": "发现高风险误报", "impact": "已发布版本将停止分发", "operator": "演示管理员", "roleLabel": "系统管理员（模拟）" }
```

- 仅 `published` 可撤回；
- 原因必填；
- 只更新发布记录与版本状态，**不得声称远程卸载设备上的模型**；
- 成功：`published → withdrawn`，保留原发布记录并追加撤回时间与说明。

### 3.12 回滚

`POST /admin/models/{modelId}/rollback`

```json
{ "fromVersionId": "MV-2026-001-02", "toVersionId": "MV-2026-001-01", "notes": "回退到稳定版本", "operator": "演示管理员", "roleLabel": "系统管理员（模拟）" }
```

- `fromVersionId` 必须为当前 `published` 版本；
- `toVersionId` 必须为 `approved / published` 且通过回滚前审核；
- 生成**新的** `ReleaseRecord`，写入 `rollbackFromVersionId / rollbackToVersionId`；
- 原版本置为 `rolled_back`，目标版本置为 `published`，历史记录不得删除或覆盖。

### 3.13 发布记录与审计

- `GET /admin/models/{modelId}/releases`
- `GET /admin/model-versions/{modelVersionId}/releases`
- `GET /admin/models/{modelId}/audits`
- `GET /admin/model-versions/{modelVersionId}/audits`
- `GET /admin/model-versions/compare?baseVersionId=&targetVersionId=`

审计记录只追加，不提供删除接口。覆盖动作：创建版本、提交审核、审核通过、审核拒绝、发布、撤回、回滚、文件校验失败、兼容性失败。

### 3.14 从训练任务导入真实产物

`POST /admin/model-versions/import`

```json
{
  "trainingTaskId": "T-2026-002",
  "modelName": "",
  "description": "",
  "operator": "P12验收员",
  "roleLabel": "系统管理员"
}
```

服务端行为（顺序执行，任一失败返回错误，不创建版本）：

1. 校验训练任务存在、`status = SUCCEEDED`、`executionMode = worker`；
2. 从 `training_results.manifest_path` 读取产物清单，校验清单 `taskId` 与任务一致；
3. 按清单 `artifactPath` 读取真实文件，重新计算 **SHA-256**，与清单 `sha256`、`fileSize` 比对；
   文件缺失或哈希不一致时拒绝导入；
4. 使用 Ed25519 **公钥**验签（公钥路径优先取清单 `signature.publicKeyPath`，
   否则回退 `P11R_ED25519_PUBLIC_KEY_PATH` / `dev_ed25519_public.pem`）；
   开发密钥记录 `signatureKeyLabel = development`、`signatureIsDevelopment = true`；
5. 关联数据集快照，派生 `datasetOrigin = synthetic / real`；
6. 创建模型（必要时）与版本（`v0.{n}.0-smoke`），写入真实 `fileHash`、签名与审计；
7. 返回版本详情，`productionReady = false`，`publishBlockedReason` 列出阻断原因。

同一 `trainingTaskId` 重复导入返回既有版本（幂等），不重复建版本。

### 3.15 兼容性测试记录

`POST /admin/model-versions/{modelVersionId}/compatibility-tests`

```json
{
  "platform": "android",
  "architecture": "arm64-v8a",
  "inferenceRuntime": "pytorch_mobile",
  "runtimeVersion": "1.13.1",
  "supportedDevices": ["Xiaomi 13"],
  "compatibilityStatus": "compatible",
  "isRealDeviceTest": true,
  "testedBy": "测试工程师",
  "notes": "真机冒烟"
}
```

仅 `isRealDeviceTest = true` 的记录可作为正式渠道兼容性依据；
`isMock` 由服务端按该字段派生，前端不可伪造。

### 3.16 验签配置查询

`GET /admin/model-verification-config`

```json
{ "data": { "publicKeyPath": "ml/training/keys/dev_ed25519_public.pem", "roles": ["系统管理员", "发布管理员"] } }
```

只返回公钥路径与允许角色，不返回任何私钥。

## 4. 渠道准入矩阵

| 条件 | internal_test（受限内部测试） | canary / production |
|---|---|---|
| 文件存在 + SHA-256 一致 | 必须通过 | 必须通过 |
| 未签名 | 允许，标记未验证 | **阻断** |
| 开发密钥签名（development） | 允许，标记开发密钥 | **阻断** |
| `trainingProfile = smoke_test` | 允许，明确标记 | **阻断** |
| `datasetType = synthetic` / 合成来源 | 允许，明确标记 | **阻断** |
| 无真实测试集评估报告 | 允许，标记未验证 | **阻断** |
| 无真实设备测试记录 | 允许，标记未验证 | **阻断** |
| 评估状态 `not_evaluated` 且非 smoke | 阻断 | 阻断 |
| 哈希 / 文件校验失败 | 阻断 | 阻断 |
| 角色无发布权限 | 阻断 | 阻断 |

服务端在 `submit / approve / publish / rollback` 时重新执行矩阵校验；
前端展示的 `eligible` 仅用于提示，不参与放行。

## 5. 状态机

```
draft ──submit──▶ pending_review ──approve──▶ approved ──publish──▶ published ──withdraw──▶ withdrawn
  ▲                    │                                              │
  └────reject◀─────────┘                                              └──rollback──▶ rolled_back
```

约束：

- 已发布版本不可修改评估、量化、兼容性或文件信息；
- 撤回与回滚都产生新记录，不覆盖历史；
- 无已审核且可用的目标版本时禁止回滚；
- 所有动作必须写入审计记录，Mock 与真实实现一致。

## 6. 错误码

| HTTP | code | 含义 |
|---|---|---|
| 403 | MODEL_PERMISSION_DENIED | 当前角色无审核 / 发布 / 撤回 / 回滚权限 |
| 404 | MODEL_NOT_FOUND | 模型不存在 |
| 404 | MODEL_VERSION_NOT_FOUND | 模型版本不存在 |
| 404 | ARTIFACT_NOT_FOUND | 模型文件记录不存在 |
| 409 | MODEL_STATE_INVALID | 当前状态不允许该动作 |
| 409 | MODEL_IMPORT_INVALID | 训练任务非成功 / 非 Worker 执行 / 产物清单缺失 |
| 409 | ROLLBACK_SOURCE_INVALID | 当前发布版本不存在或已不是发布状态 |
| 409 | ROLLBACK_TARGET_INVALID | 回滚目标未审核通过或不可用 |
| 422 | MODEL_REVIEW_BLOCKED | 审核清单存在阻断项 |
| 422 | MODEL_RELEASE_BLOCKED | 发布前置条件未满足 |
| 422 | ROLLBACK_TARGET_NOT_READY | 回滚目标未通过回滚前检查 |
| 400 | WITHDRAW_REASON_REQUIRED | 未填写撤回原因 |
| 400 | ROLLBACK_TARGET_INVALID | 回滚目标与当前版本相同 |

## 7. 真实后端落地状态

| 要求 | 实现 |
|---|---|
| 哈希基于真实产物 | `app/services/artifact_verify.py::verify_file` 服务端重算 SHA-256，T-2026-001/002 均 `verified` |
| Ed25519 公钥验签 | `verify_signature` 使用公钥验证清单签名；开发密钥标记 `development` |
| 服务端二次校验 | `approve / publish / rollback` 重新调用 `evaluate_review`，不信任前端 `eligible` |
| 数据集资格与人工复核 | 读取 P10 `dataset_version_snapshots`，合成来源与未通过审核阻断正式渠道 |
| 真实设备兼容性 | `model_compatibility_tests.is_real_device_test`；无真实记录阻断正式渠道 |
| 审计只追加 | `model_audit_logs` 由服务写入，无删除 / 修改接口 |
| 真实 / 演示区分 | `artifactSource = training_worker / demo_record`、`isMock`、`trainingProfile`、`datasetOrigin` |

验证证据见 `docs/testing/P12_REAL_ARTIFACT_REPORT.md`。
