# 银盾智鉴 · 业务接口契约草案

> 文档编号：P00-CONTRACT-DRAFT
> 版本：v0.1（**草案，非最终契约**）
> 状态：**待与 V1.3 原文核对 / 待与 FastAPI 实现核对**
> 最后更新：2026-11-16

---

## 0. 效力声明

本文档是**契约草案**，用于统一前后端对资源与字段的命名预期。

- 不具备最终约束力；最终以 **OpenAPI 契约**为准（V1.3 第 4.4 节）。
- 凡 V1.3 未列出的路径、字段、错误码，均为 P00 阶段推导，标 `【待核对】`。
- 本轮（P00）不实现任何接口，不产生可运行代码。

V1.3 第 4.4 节给出的接口分组为：

```
/auth  /family/relations  /family/consents  /risk-events/share
/family/risk-events  /admin/datasets  /admin/training-tasks  /admin/models
```

---

## 1. 通用约定

| 项 | 草案约定 | 核对状态 |
|---|---|---|
| Base Path | `/api/v1` | 【待核对】V1.3 未指定 |
| 传输 | HTTPS + JSON（UTF-8） | 推导 |
| 认证 | `Authorization: Bearer <access_token>` | 【待核对】 |
| 时间格式 | ISO 8601，UTC（如 `2026-11-16T03:20:00Z`） | 推导 |
| ID 类型 | 字符串（不暴露自增主键） | 推导 |
| 分页 | `page`（从 1 起）/ `pageSize`（默认 20，上限 100） | 推导 |
| 排序 | `sort=field:asc,field2:desc` | 推导 |
| 幂等 | 写操作支持 `Idempotency-Key` 请求头 | 【待核对】 |
| 追踪 | 响应头 `X-Trace-Id` | 推导 |

### 1.1 成功响应

```json
{
  "data": { },
  "meta": { "traceId": "..." }
}
```

### 1.2 分页响应

```json
{
  "data": {
    "items": [],
    "page": 1,
    "pageSize": 20,
    "total": 0
  },
  "meta": { "traceId": "..." }
}
```

### 1.3 错误响应

```json
{
  "error": {
    "code": "CONSENT_REVOKED",
    "message": "老人已撤销对该事件的共享授权",
    "details": [],
    "traceId": "..."
  }
}
```

### 1.4 错误码草案

| HTTP | code | 含义 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | 参数校验失败 |
| 401 | `UNAUTHENTICATED` | 未登录或 token 失效 |
| 403 | `FORBIDDEN` | 角色无此能力 |
| 403 | `CONSENT_REQUIRED` | 缺少有效授权 |
| 403 | `CONSENT_REVOKED` | 授权已撤销 |
| 404 | `NOT_FOUND` | 资源不存在（越权时也可返回 404 以避免探测） |
| 409 | `STATE_CONFLICT` | 状态机冲突（如重复接受邀请） |
| 410 | `INVITATION_EXPIRED` | 邀请已过期 |
| 422 | `UNPROCESSABLE` | 业务规则拒绝（如无许可样本标记可训练） |
| 429 | `RATE_LIMITED` | 频率限制 |
| 503 | `TRAINING_EXECUTOR_UNAVAILABLE` | 训练执行器不可用 |

> 【待与 V1.3 原文核对】V1.3 未定义错误码体系，以上为草案。

### 1.5 Mock 标识约定

Mock 必须可被机器与用户同时识别：

- 响应体 `meta.dataSource`: `"mock" | "real"`（默认 `"real"`）。
- 响应头 `X-Data-Source: mock`。
- 前端在页面显著位置渲染"演示数据"标识。

---

## 2. 鉴权 `/auth`

| 方法 | 路径 | 说明 | 角色 |
|---|---|---|---|
| POST | `/auth/login` | 账号登录 | 全部 |
| POST | `/auth/refresh` | 刷新令牌 | 全部 |
| POST | `/auth/logout` | 登出 | 全部 |
| GET | `/auth/me` | 当前身份与角色 | 全部 |

### 2.1 `POST /auth/login` 请求

```json
{
  "account": "string",
  "credential": "string",
  "role": "elder | family | admin",
  "deviceId": "string"
}
```

### 2.2 `GET /auth/me` 响应

```json
{
  "data": {
    "userId": "u_xxx",
    "displayName": "string",
    "roles": ["elder"],
    "activeRole": "elder",
    "familyIds": [],
    "elderIds": []
  }
}
```

> 说明：P03 仅实现**本地模拟登录**，不得据此后端契约宣称真实登录已完成。

---

## 3. 家庭关系 `/family/relations`

| 方法 | 路径 | 说明 | 角色 |
|---|---|---|---|
| GET | `/family/relations` | 查询绑定列表 | 老人 / 家属 |
| POST | `/family/relations/invitations` | 创建绑定邀请 | 【待核对】老人 / 家属 |
| POST | `/family/relations/invitations/{invitationId}/accept` | 接受邀请 | 老人 / 家属 |
| POST | `/family/relations/invitations/{invitationId}/reject` | 拒绝邀请 | 老人 / 家属 |
| DELETE | `/family/relations/{relationId}` | 解除绑定 | 老人 / 家属 |

### 3.1 Relation 对象

| 字段 | 类型 | 说明 |
|---|---|---|
| relationId | string | 绑定关系 ID |
| elderId | string | 老人 ID |
| familyUserId | string | 家属用户 ID |
| relationStatus | enum | `pending / active / rejected / expired / unbound` |
| createdAt | datetime | 创建时间 |
| confirmedAt | datetime? | 双方确认时间 |
| unboundAt | datetime? | 解绑时间 |

### 3.2 约束

- **双向确认**：绑定必须经双方确认后进入 `active`（V1.3 全局验收）。
- 不允许单方强制绑定。
- 解绑后关联的 `ConsentRecord` 一律置为失效。

---

## 4. 授权共享 `/family/consents`

| 方法 | 路径 | 说明 | 角色 |
|---|---|---|---|
| GET | `/family/consents` | 查询授权记录 | 老人（本人）/ 家属（仅被授权项，只读） |
| POST | `/family/consents` | 创建授权 | **仅老人** |
| PATCH | `/family/consents/{consentId}` | 修改范围 / 有效期 | **仅老人** |
| POST | `/family/consents/{consentId}/revoke` | 撤销授权 | **仅老人** |

### 4.1 ConsentRecord 对象

| 字段 | 类型 | 说明 |
|---|---|---|
| consentId | string | 授权 ID |
| consenterId | string | 授权人（必须为老人本人） |
| granteeFamilyId | string | 被授权家属 |
| consentType | enum | `event_share`（按事件）/ `scope_grant`（事先范围） |
| eventScope | object | 范围定义（风险类型、时间窗、单事件） |
| validFrom | datetime | 生效时间 |
| validUntil | datetime? | 失效时间，null 表示长期（仍可撤销） |
| revoked | boolean | 是否已撤销 |
| revokedAt | datetime? | 撤销时间 |
| auditTrail | array | 审计记录列表 |

### 4.2 硬性规则

| # | 规则 |
|---|---|
| C1 | 授权**默认关闭**，不提供"默认开启"选项 |
| C2 | 家属、管理员均不能创建或修改授权 |
| C3 | 撤销立即生效，家属对该事件范围内所有访问返回 `CONSENT_REVOKED` |
| C4 | 撤销动作写入审计日志 |
| C5 | 授权范围不得包含"原始屏幕录像""实时屏幕" |

---

## 5. 事件分享 `/risk-events/share`

| 方法 | 路径 | 说明 | 角色 |
|---|---|---|---|
| POST | `/risk-events/share` | 按事件分享给指定家属 | **仅老人** |
| DELETE | `/risk-events/share/{shareId}` | 撤销单次分享 | **仅老人** |
| GET | `/risk-events/share` | 查询本人已发起的分享 | 老人 |

### 5.1 分享请求

```json
{
  "eventId": "evt_xxx",
  "granteeFamilyIds": ["f_xxx"],
  "validUntil": "2026-12-31T00:00:00Z",
  "note": "string"
}
```

### 5.2 约束

- 分享内容仅为**事件摘要**，永不包含原始录像。
- `eventId` 必须属于请求者本机产生的本地事件。

---

## 6. 家属侧风险事件 `/family/risk-events`

| 方法 | 路径 | 说明 | 角色 |
|---|---|---|---|
| GET | `/family/risk-events` | 授权共享事件列表 | 家属 |
| GET | `/family/risk-events/{eventId}` | 事件详情 | 家属（需有效授权） |
| GET | `/family/risk-events/{eventId}/handling` | 处理记录 | 家属 / 老人 |
| POST | `/family/risk-events/{eventId}/handling` | 更新协同状态 | 家属 |
| PATCH | `/family/risk-events/{eventId}/handling/{handlingId}` | 补充备注 | 家属 |

### 6.1 列表响应项（脱敏视图）

| 字段 | 说明 |
|---|---|
| eventId | 事件 ID |
| occurredAt | 发生时间 |
| riskType | 风险类型 |
| riskLevel | 风险级别（只读，源端模型结论） |
| evidenceSummary | 证据摘要（脱敏） |
| handlingStatus | 协同状态 |
| sharedAt | 共享时间 |
| consentId | 依据的授权 ID |

**明确不下发**：原始录像、实时屏幕、完整浏览历史、模型原始分数。

### 6.2 协同状态枚举

`pending_view / viewed / contacted / verifying / assisted / unverifiable`

对应文案：待查看 / 已查看 / 已联系 / 核实中 / 已协助处理 / 无法核实。

### 6.3 硬性规则

| # | 规则 |
|---|---|
| H1 | 每次读请求都重新校验 `ConsentRecord` 有效性，不依赖缓存 |
| H2 | 家属不能修改 `riskLevel` / `riskType` / `evidenceSummary` |
| H3 | 处理状态与 AI 原始风险等级分表存储、分别返回 |
| H4 | 无授权时返回空列表 + 明确原因，不返回伪造数据 |

---

## 7. 管理端：数据集 `/admin/datasets`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin/datasets` | 数据集列表 |
| POST | `/admin/datasets` | 创建数据集 |
| GET | `/admin/datasets/{datasetId}` | 详情 |
| POST | `/admin/datasets/{datasetId}/samples` | 导入样本 |
| PATCH | `/admin/datasets/{datasetId}/samples/{sampleId}` | 更新标注 |
| POST | `/admin/datasets/{datasetId}/quality-checks` | 触发质检 |
| POST | `/admin/datasets/{datasetId}/split` | 数据拆分 |
| GET | `/admin/datasets/{datasetId}/versions` | 版本列表 |
| POST | `/admin/datasets/{datasetId}/versions` | 冻结版本 |

### 7.1 Sample 必备字段

| 字段 | 说明 |
|---|---|
| sampleId | 样本 ID |
| source | 来源（自制 / 明确许可素材） |
| license | 许可信息 |
| riskCategory | 风险类别（支持多标签） |
| evidenceLocation | 证据位置 |
| reviewStatus | 审核状态 |
| trainable | 是否可正式训练 |

> 规则：`license` 缺失时 `trainable` 恒为 `false`，服务端拒绝将其标记为可训练。

---

## 8. 管理端：训练任务 `/admin/training-tasks`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin/training-tasks` | 任务列表 |
| POST | `/admin/training-tasks` | 创建任务 |
| GET | `/admin/training-tasks/{taskId}` | 任务详情 |
| GET | `/admin/training-tasks/{taskId}/logs` | 日志（支持分页/增量） |
| GET | `/admin/training-tasks/{taskId}/metrics` | 指标序列 |
| POST | `/admin/training-tasks/{taskId}/retry` | 失败重试 |
| POST | `/admin/training-tasks/{taskId}/cancel` | 取消任务 |

### 8.1 状态枚举

`pending / running / succeeded / failed / canceled`

### 8.2 约束

- 训练由独立 Python Worker 执行，浏览器不参与训练。
- 未接真实 Worker 时，响应必须带 `dataSource: "mock"`，不得声称训练成功。

---

## 9. 管理端：模型版本 `/admin/models`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin/models` | 模型仓库列表 |
| GET | `/admin/models/{modelVersion}` | 版本详情 |
| POST | `/admin/models/{modelVersion}/review` | 提交/记录审核 |
| POST | `/admin/models/{modelVersion}/release` | 发布 |
| POST | `/admin/models/{modelVersion}/withdraw` | 撤回 |
| POST | `/admin/models/{modelVersion}/rollback` | 回滚 |

### 9.1 ModelVersion 对象

| 字段 | 说明 |
|---|---|
| modelVersion | 版本号 |
| datasetVersion | 训练数据版本 |
| fileFormat | 文件格式 |
| fileHash | 校验值 |
| signature | 签名 |
| runtimeCompatibility | 运行时兼容信息 |
| evaluationSummary | 评估摘要 |
| releaseStatus | `draft / reviewing / approved / released / withdrawn` 【待核对】 |

### 9.2 约束

- 发布按钮仅在有权限且满足审核条件时可用；**必须服务端二次校验**。
- 发布、撤回、回滚均记录操作人与时间，UI 展示审核记录。
- 端侧检查更新前需校验 `fileHash` 与 `signature`。

---

## 10. 端侧本地接口草案（非 HTTP）

> 以下为 Android 内部接口草案，用于统一 ViewModel 与引擎之间的边界（V1.3 第 4.3 节）。**P00 不实现**，仅声明契约。

```kotlin
// 草案：待与 guard-engine 实现核对
interface GuardEngine {
    val state: Flow<GuardState>
    fun requestStart(): Result<Unit>
    fun stop()
}

enum class GuardState { OFF, REQUESTING, RUNNING, DEGRADED, STOPPED, ERROR }

interface RiskEventRepository {
    fun observeEvents(filter: RiskEventFilter): Flow<List<RiskEvent>>
    suspend fun getEvent(eventId: String): RiskEvent?
    suspend fun deleteEvent(eventId: String)
    suspend fun clearAll()
}

interface FamilyConsentRepository {
    suspend fun listConsents(): List<ConsentRecord>
    suspend fun revoke(consentId: String): Result<Unit>
}

interface TrainingTaskRepository {
    suspend fun listTasks(query: TaskQuery): PagedResult<TrainingTask>
    suspend fun createTask(request: CreateTaskRequest): Result<TrainingTask>
}
```

**Mock 约束**：以上接口的 Fake 实现必须可在 UI 上被识别为"模拟"，不得伪装真实采集或真实识别。

---

## 11. 待确认事项

| # | 事项 |
|---|---|
| Q1 | Base Path、认证方式（Bearer / Cookie）、令牌有效期 |
| Q2 | 老人端**本地事件**是否需要上传业务后端；V1.3 仅在 4.4 列出共享相关接口 |
| Q3 | `ConsentRecord` 是否支持"长期有效"（`validUntil = null`） |
| Q4 | 绑定邀请的发起方与邀请码有效期 |
| Q5 | `riskLevel` 的枚举值与中文文案 |
| Q6 | `releaseStatus` 的完整状态机 |
| Q7 | 管理端是否复用现有后端服务，及其现有 OpenAPI 是否存在 |
| Q8 | 越权访问返回 403 还是 404（防枚举策略） |
| Q9 | 是否引入 `Idempotency-Key` |
| Q10 | 设备与 App 版本上报接口（A03 需要）尚未定义 |

---

## 12. 相关文档

- [PRODUCT_BASELINE.md](../requirements/PRODUCT_BASELINE.md)
- [ROLE_PERMISSION_MATRIX.md](../requirements/ROLE_PERMISSION_MATRIX.md)
- [PAGE_INDEX.md](../requirements/PAGE_INDEX.md)
- [IMPLEMENTATION_PLAN.md](../requirements/IMPLEMENTATION_PLAN.md)
