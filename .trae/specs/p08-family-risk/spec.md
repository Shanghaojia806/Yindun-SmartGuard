# P08 规格说明｜家属风险事件管理与家庭协同处理

> 目标：在 P00–P07 既有 Android 工程上增量实现家属端 F03 家庭首页、F05 风险列表、F06 风险详情、F07 协同处理、F08 处理详情、F11 消息中心。
> 核心原则：**只有「有效家庭关系 + 有效共享授权 + 有效分享记录」三层同时成立，家属才能看到事件**；AI 原始风险等级与协同处理状态分开保存；处理历史只追加不覆盖；全部为本地模拟，不宣称真实推送或生产级对象级权限。
> 本轮只执行 P08，不进入 P09。

---

## 1. 已核对现状与约束

| 项 | 现状（已读取确认） |
|---|---|
| 数据库 | `guard/risk/RiskEventStore.kt`：`SilverShieldDatabase` version=2，已有 `risk_events`、`family_relations`、`family_invites`、`consent_records`，已有 `MIGRATION_1_2` |
| P06 风险模型 | `guard/risk/RiskEvent.kt`：`RiskEvent`、`RiskType`、`RiskLevel`、`RiskCouldality`、`RiskSourceType`、`RiskSyncStatus`、`RiskHandleStatus`；`MockRiskEvents` 三条示例 |
| P07 关系/授权 | `family/FamilyModels.kt`：`FamilyRelation`、`FamilyInvite`、`ConsentRecord`、`ConsentStatus`、`AuthorizationType`、`ConsentScopeType`、`LocalFamilyAccounts` |
| P07 仓库 | `family/FamilyRepository.kt`、`family/ConsentRepository.kt`、`family/FamilyStore.kt`（`FamilyLocalStore` / `RoomFamilyLocalStore` / `InMemoryFamilyLocalStore`） |
| 分享入口 | `ui/elder/RiskDetailViewModel.kt` 调用 `ConsentRepository.grantEventShare(eventId, 7 天)`；`revokeEventShare` 撤销单条分享 |
| 家属导航 | `ui/family/FamilyAppScreen.kt` 独立 NavHost，`FamilyTab` 四个一级入口；`FamilySubPage` 仅支持精确路由匹配 |
| 家属页面 | `FamilyHomeScreen` / `FamilyRisksScreen` / `FamilyCooperationScreen` 为占位；`FamilyBindScreen` / `FamilyRelationScreen` / `FamilyConsentScreen` / `FamilyProfileScreen` 已实现 |
| 设计系统 | `StatusCard`、`PrimaryButton`、`EmptyState`、`ErrorState`、`LoadingState`、`FamilySection`、`InfoRow`、`PrivacyNote`、`SampleTag`、`RiskBadge`、`formatRiskTime` |
| 测试 | JUnit4 + `runBlocking`；无 coroutines-test；上一轮 77 个单测通过 |

**硬约束**
1. 复用 P06 `RiskEvent`，不复制第二套风险事件模型；不保存/展示原始屏幕录像、实时屏幕或完整浏览历史。
2. 不删除 P06 本地风险记录；不重建工程；不改动 P07 已验收的绑定/授权规则（除数据库注册新表）。
3. 新增 Room 表使用 `Migration(2, 3)`，保留 version=1/2 的已有表与数据；禁止 destructive migration、pm clear、卸载应用。
4. 权限校验集中在 Repository 层，UI 不能通过隐藏菜单或改状态绕过；详情路由直连同样被拦截。
5. 所有新增记录 `isMock = true`，UI 显示「模拟数据」；消息为本地 Mock，不宣称接入真实推送。
6. 具备 Loading / Success / Empty / Error；弱网/离线不得把未同步消息标为「已送达」。

---

## 2. 数据模型（新增 `family/FamilyRiskModels.kt`）

### 2.1 枚举

```kotlin
enum class ShareStatus(val label: String) { ACTIVE("共享中"), REVOKED("已撤销"), EXPIRED("已失效") }

enum class HandlingStatus(val label: String) {
    PendingView("待查看"), Viewed("已查看"), Contacted("已联系"),
    Verifying("核实中"), Assisted("已协助处理"), Unverifiable("无法核实");
    val isOpen: Boolean // PendingView/Viewed/Contacted/Verifying
}

enum class FamilyMessageType(val label: String) {
    RiskShared("新风险事件分享"), RiskRevoked("风险事件撤销"),
    RelationChanged("家庭关系变更"), ConsentChanged("授权状态变化"),
    HandlingChanged("协同处理状态变化")
}

enum class MessageDeliveryState(val label: String) {
    Delivered("已送达"), Delayed("延迟中"), Undelivered("离线未送达")
}

enum class FamilyNetworkMode(val label: String) {
    Online("正常同步"), Delayed("消息延迟"), Offline("离线")
}
```

### 2.2 `SharedRiskEvent`

字段：`shareId`、`eventId`、`elderUserId`、`familyUserId`、`consentId`、`sharedAt`、`shareStatus: ShareStatus`、`readAt: Long?`、`revokedAt: Long?`、`isMock`（默认 true）。
计算属性：`isRead = readAt != null`。
不含任何原始画面/录像/完整浏览历史字段。

**生成规则（可追溯）**：`shareId = "share-${consentId}-${eventId}"`，由 `sync()` 依据 P07 `ConsentRecord` 物化；同一授权同一事件重复同步幂等，保留 `readAt`。

### 2.3 `HandlingRecord`（追加式历史）

字段：`handlingId`、`shareId`、`eventId`、`handlerUserId`、`handlingStatus`、`note`、`createdAt`、`updatedAt`、`previousStatus: HandlingStatus?`、`isMock`。
每次状态/备注更新插入**新行**，不覆盖旧行；`handlerUserId` 固定为 `LocalFamilyAccounts.FAMILY_USER_ID`。
当前协同状态 = 该 `shareId` 下 `createdAt` 最大的记录，若无记录则为 `PendingView`。

### 2.4 `FamilyMessage`

字段：`messageId`、`messageType`、`title`、`content`、`relatedEventId: String?`、`relatedShareId: String?`、`createdAt`、`readAt: Long?`、`isRead`、`deliveryState: MessageDeliveryState`、`isMock`。

### 2.5 访问控制结果

```kotlin
enum class ShareDenyReason(val message: String) {
    NO_RELATION, RELATION_INACTIVE, SHARE_NOT_FOUND, SHARE_REVOKED,
    CONSENT_REVOKED, CONSENT_EXPIRED, CONSENT_MISSING, OUT_OF_SCOPE, EVENT_MISSING
}
sealed interface ShareAccess { data class Granted(val view: SharedEventView); data class Denied(val reason: ShareDenyReason) }
data class SharedEventView(val share: SharedRiskEvent, val event: RiskEvent)
data class FamilyEventItem(val share, val event, val handlingStatus, val latestHandling: HandlingRecord?)
```

---

## 3. 持久化与迁移

### 3.1 新增 `family/FamilyRiskStore.kt`

三张表（枚举存 `name`，`isMock` 存 INTEGER）：

| 表 | 主键 | 关键列 | 索引 |
|---|---|---|---|
| `shared_risk_events` | `shareId` | eventId, elderUserId, familyUserId, consentId, sharedAt, shareStatusKey, readAt, revokedAt, isMock | eventId、consentId |
| `handling_records` | `handlingId` | shareId, eventId, handlerUserId, handlingStatusKey, previousStatusKey, note, createdAt, updatedAt, isMock | shareId |
| `family_messages` | `messageId` | messageTypeKey, title, content, relatedEventId, relatedShareId, createdAt, readAt, isRead, deliveryStateKey, isMock | relatedShareId |

提供 `FamilyRiskLocalStore` 接口、`RoomFamilyRiskLocalStore(context)`、`InMemoryFamilyRiskLocalStore`（JVM 测试用）。

### 3.2 修改 `guard/risk/RiskEventStore.kt`

- `@Database` 增加三个 Entity，`version = 3`。
- 新增 `sharedRiskEventDao()`、`handlingRecordDao()`、`familyMessageDao()`。
- 新增 `MIGRATION_2_3`：仅 `CREATE TABLE IF NOT EXISTS` + `CREATE INDEX IF NOT EXISTS`，不动已有表；`addMigrations(MIGRATION_1_2, MIGRATION_2_3)`。
- 不修改 `RiskEventDao` 的读写语义，P06 本地记录保持可查。

---

## 4. Repository（Fake 实现，权限集中校验）

### 4.1 `family/SharedRiskEventRepository.kt`

```kotlin
interface SharedRiskEventRepository {
    val shares: Flow<List<SharedRiskEvent>>
    val relation: Flow<FamilyRelation?>
    val activeConsents: Flow<List<ConsentRecord>>
    suspend fun sync()
    fun observeVisible(): Flow<List<SharedEventView>>
    fun observeAccess(shareId: String): Flow<ShareAccess>
    suspend fun access(shareId: String): ShareAccess
    suspend fun markViewed(shareId: String): Result<Unit>
}
```

`FakeSharedRiskEventRepository(riskStore, familyStore, riskEvents: Flow<List<RiskEvent>>, now)`：

- `sync()`：取最新 ACTIVE 关系与该关系下 ACTIVE 授权；对 `riskEvents` 中每条被授权覆盖的事件，按确定性 `shareId` 物化 `ACTIVE` 分享；已存在则保留 `readAt`。对已有分享：授权撤销 → `REVOKED` + `revokedAt`；授权过期 → `EXPIRED`；无 ACTIVE 关系 → 全部 `REVOKED`。
- `observeVisible()`：实时合并 shares/relations/consents/riskEvents，仅返回 `isVisible()` 为真的事件，按 `sharedAt` 倒序。
- `isVisible()` 三层校验：
  1. 存在 ACTIVE `FamilyRelation`，且 elder/family userId 与分享一致；
  2. `shareStatus == ACTIVE && revokedAt == null`；
  3. 对应 `consentId` 的授权存在、归属同一 `relationId`、`effectiveStatus == ACTIVE`、`covers(eventId, riskType)`。
- `observeAccess(shareId)`：逐层返回具体 `DenyReason`（未绑定 / 关系无效 / 分享不存在 / 分享已撤销 / 授权已撤销 / 授权过期 / 授权记录缺失 / 范围外 / 原始事件缺失），供 F06/F08 显示授权失效说明。
- `markViewed(shareId)`：先 `access()`；仅 Granted 且 `readAt == null` 时写入 `readAt = now`；Denied 返回失败。

### 4.2 `family/HandlingRecordRepository.kt`

```kotlin
interface HandlingRecordRepository {
    val records: Flow<List<HandlingRecord>>
    suspend fun updateStatus(shareId: String, status: HandlingStatus, note: String): Result<HandlingRecord>
}
```

`FakeHandlingRecordRepository(riskStore, sharedRiskRepository, now)`：
- 必须 `sharedRiskRepository.access(shareId)` 为 Granted，否则 `FamilyOperationException(FORBIDDEN, "共享已失效，不能继续处理")`；
- 读取上一条记录得到 `previousStatus`（无则 `PendingView`）；状态与备注均未变化时拒绝；
- 追加新 `HandlingRecord`，`handlerUserId = FAMILY_USER_ID`，不修改/删除任何旧记录；
- 不提供删除事件、修改 AI 风险等级的 API。

### 4.3 `family/FamilyMessageRepository.kt`

```kotlin
interface FamilyMessageRepository {
    val messages: Flow<List<FamilyMessage>>
    val networkMode: StateFlow<FamilyNetworkMode>
    suspend fun setNetworkMode(mode: FamilyNetworkMode)
    suspend fun refresh()
    suspend fun markRead(messageId: String)
    suspend fun markAllRead()
}
```

`FakeFamilyMessageRepository(riskStore, familyStore, riskEvents, now)`：
- `refresh()` 根据当前 shares / latest relation / consents / handling records 生成确定性 ID 的消息，已存在则跳过（保留已读状态）：
  - `msg-share-{shareId}`：新分享（含事件标题）；
  - `msg-revoked-{shareId}`：分享撤销/失效；
  - `msg-relation-active-{relationId}` / `msg-relation-revoked-{relationId}`：关系建立/解除（仅 confirmedAt 非空）；
  - `msg-consent-granted|revoked|expired-{consentId}`：仅 `ScopeGrant`，避免与按事件分享重复；
  - `msg-handling-{handlingId}`：每次协同处理记录。
- 新消息 `deliveryState` 按当前 `networkMode`：Online→Delivered，Delayed→Delayed，Offline→Undelivered。
- `setNetworkMode(Online)` 时把未送达消息升级为 Delivered；切到 Delayed/Offline 不回退已送达消息。
- `markRead` / `markAllRead` 持久化 `readAt` 与 `isRead`。

---

## 5. ViewModel

### 5.1 `family/FamilyRiskViewModel.kt`（F03/F05/F07 Tab/F11 共用）

`FamilyRiskUiState`：`loading`、`loadFailed`、`relation`、`activeConsents`、`items: List<FamilyEventItem>`、`allShares`、`messages`、`networkMode`、`message`。

派生：`isBound`、`hasConsent`、`sharedCount`、`unreadEventCount`、`pendingHandlingCount`、`assistedCount`、`unreadMessageCount`、`newSharedCount`、`viewedPendingCount`、`verifyingCount`、`contactedCount`、`revokedShareIds`。

方法：`refresh()`（sync + message refresh）、`retry()`、`markMessageRead(id)`、`markAllMessagesRead()`、`setNetworkMode(mode)`、`consumeMessage()`。
`refresh()` 失败置 `loadFailed`，供 ErrorState 使用。

### 5.2 `family/FamilyEventViewModel.kt`（F06/F07 动作/F08 共用，按 shareId 创建）

构造：`shareId`、`SharedRiskEventRepository`、`HandlingRecordRepository`、`FamilyMessageRepository`。
`FamilyEventUiState`：`loading`、`accessDenied: ShareDenyReason?`、`share`、`event`、`handlingStatus`、`history`、`saving`、`message`。

方法：`refresh()`、`updateStatus(status, note)`、`consumeMessage()`。
- `init`：`sync()` → `markViewed(shareId)` → `messageRepository.refresh()`。
- 状态流合并 `observeAccess(shareId)` 与 `handlingRepository.records`；授权撤销后 State 立即变为 `accessDenied`，页面停止展示受限详情。
- `updateStatus` 通过 Repository 执行，成功后再 `messageRepository.refresh()`。

---

## 6. 页面与导航

### 6.1 路由（修改 `navigation/AppRoutes.kt`、`ui/family/FamilySubPage.kt`）

```kotlin
const val ARG_SHARE_ID = "shareId"
const val FAMILY_RISK_DETAIL = "family/risk/{shareId}"     // F06
const val FAMILY_COOPERATE   = "family/cooperate/{shareId}" // F07 动作页
const val FAMILY_HANDLING    = "family/handling/{shareId}"  // F08
const val FAMILY_MESSAGES    = "family/messages"            // F11
fun familyRiskDetailRoute(shareId: String) = "family/risk/$shareId"
fun familyCooperateRoute(shareId: String) = "family/cooperate/$shareId"
fun familyHandlingRoute(shareId: String) = "family/handling/$shareId"
```

`FamilySubPage` 增加 Detail/Cooperate/Handling/Messages，`fromRoute` 支持 `{arg}` 前缀匹配；二级页隐藏底部导航并显示返回。

### 6.2 修改 `ui/family/FamilyAppScreen.kt`

- 创建 `RoomRiskEventRepository`、`RoomFamilyRiskLocalStore`、`FakeSharedRiskEventRepository`、`FakeHandlingRecordRepository`、`FakeFamilyMessageRepository`、`FamilyRiskViewModel`。
- 注册 F06/F07/F08/F11 路由，按 `entry.arguments` 的 `shareId` 创建 `FamilyEventViewModel`。
- F03/F05/F07Tab/F11 共用 `FamilyRiskViewModel`；所有回调经 ViewModel/Repository，不在 Composable 直接操作数据库。

### 6.3 页面

| 页面 | 文件 | 内容 |
|---|---|---|
| F03 家庭首页 | `FamilyHomeScreen.kt`（重写） | 家庭成员卡（绑定关系/授权状态/关系管理入口）、风险概况 4 项统计（仅授权可见数据）、待处理事项 4 类、消息入口与未读数、未绑定/无授权/无分享空态、离线提示、演示数据声明 |
| F05 风险列表 | `FamilyRisksScreen.kt`（重写） | 事件卡（标题/类型/等级/发生时间/分享时间/查看状态/协同状态）；风险等级、类型、处理状态、已读未读四组筛选；五种空态（未绑定/无授权/无分享/全部处理/授权撤销） |
| F06 风险详情 | 新建 `FamilyRiskDetailScreen.kt` | 标题/类型/AI 原始等级/发生时间/分享时间/线索/证据摘要/模态/信息充分性/建议/协同状态；辅助判断免责声明；信息不足文案；禁止录像与实时屏幕说明；进入即标记已查看；授权失效说明 |
| F07 协同处理 | `FamilyCooperationScreen.kt`（重写为待协同清单）+ 新建 `FamilyCooperateScreen.kt`（动作页） | 联系老人（模拟联系入口 + 紧急 110 系统拨号）、6 状态更新、备注、状态历史摘要、权限边界 |
| F08 处理详情 | 新建 `FamilyHandlingDetailScreen.kt` | 原始事件摘要、AI 等级、当前协同状态、操作时间线（操作人/时间/变更前后状态/备注）、当前处理人、最近处理时间、继续更新入口；授权撤销即拦截 |
| F11 消息中心 | 新建 `FamilyMessagesScreen.kt` | 消息列表、未读数、单条/全部已读、跳转相关事件、撤销状态、网络模式切换与延迟/离线标签、演示数据声明 |

---

## 7. 授权校验与安全验收（Repository 层）

1. 无 ACTIVE 关系 → `Denied(NO_RELATION/RELATION_INACTIVE)`，列表为空。
2. 无 ACTIVE 覆盖授权 → 不物化 share，详情 `Denied(CONSENT_*)`。
3. 授权范围外事件不显示（`covers()` 校验）。
4. 撤销后 `observeAccess` 立即变为 Denied，详情页停止展示。
5. 解除关系后 `sync()` 将分享置 REVOKED，访问被拒。
6. 直接访问 `family/risk/{shareId}` 仍走 `observeAccess`，不能绕过。
7. 家属端无删除事件、无修改 AI 判断、无授权写入 API（反射测试断言）。
8. 所有处理记录追加保存，重启后保留。

---

## 8. 测试与验证

### 8.1 新增单测

| 文件 | 覆盖 |
|---|---|
| `family/SharedRiskEventRepositoryTest.kt` | 未分享不可见；分享后可见；未绑定/未授权不可见；授权撤销；解绑；详情直连拦截；同步幂等；重启（重建议仓库）保留；P06 事件数据不被修改 |
| `family/HandlingRecordRepositoryTest.kt` | 更新状态；备注；历史保留；previousStatus 正确；AI 等级不变；无删除 API；授权撤销后拒绝更新；重启保留 |
| `family/FamilyMessageRepositoryTest.kt` | 新分享生成消息；未读数；单条/全部已读；撤销消息；处理状态消息；离线/延迟状态；切回在线才变已送达；重启已读保留 |

测试使用 `InMemoryFamilyLocalStore`、`InMemoryFamilyRiskLocalStore` 与测试内 `FakeRiskEvents`（实现 `MutableStateFlow<List<RiskEvent>>`）。

### 8.2 构建与运行

- `cd apps/android && .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest`（需关闭沙箱以避免 Kotlin daemon 在中文路径下的 AccessDenied）。
- 模拟器 `emulator-5554` 实测：
  1. 老人端绑定 → E07 分享 → 家属端 F03/F05 出现事件；
  2. 打开 F06 后查看状态变为已查看；
  3. F07 更新状态 + 备注 → F08 时间线追加；
  4. 老人端撤销分享 → 家属端 F06/F08 显示授权失效；
  5. F11 未读/已读、离线/延迟状态；
  6. 重启应用校验分享、历史、已读、撤销状态保留，P06 记录仍在。

### 8.3 交付

`docs/testing/P08_FAMILY_RISK_REPORT.md` 输出指令要求的 15 项，并注明未接入真实推送/跨设备后端、本地 Mock 不构成生产级安全能力。

---

## 9. 文件变更清单

**新增**
- `family/FamilyRiskModels.kt`
- `family/FamilyRiskStore.kt`
- `family/SharedRiskEventRepository.kt`
- `family/HandlingRecordRepository.kt`
- `family/FamilyMessageRepository.kt`
- `family/FamilyRiskViewModel.kt`
- `family/FamilyEventViewModel.kt`
- `ui/family/FamilyRiskDetailScreen.kt`
- `ui/family/FamilyCooperateScreen.kt`
- `ui/family/FamilyHandlingDetailScreen.kt`
- `ui/family/FamilyMessagesScreen.kt`
- `ui/family/FamilyRiskComponents.kt`
- `test/.../family/SharedRiskEventRepositoryTest.kt`
- `test/.../family/HandlingRecordRepositoryTest.kt`
- `test/.../family/FamilyMessageRepositoryTest.kt`
- `docs/testing/P08_FAMILY_RISK_REPORT.md`

**修改**
- `guard/risk/RiskEventStore.kt`（version=3 + 三张表 + MIGRATION_2_3）
- `navigation/AppRoutes.kt`
- `ui/family/FamilySubPage.kt`
- `ui/family/FamilyAppScreen.kt`
- `ui/family/FamilyHomeScreen.kt`
- `ui/family/FamilyRisksScreen.kt`
- `ui/family/FamilyCooperationScreen.kt`

**不修改**
- P06 `RiskEvent` 模型与 `RiskEventDao` 语义
- P07 `FamilyRepository` / `ConsentRepository` 业务规则
- 老人端导航与页面（分享入口已在 P07 完成）

---

## 10. 验收标准

- [ ] 六个页面全部可进入，Loading/Success/Empty/Error 状态齐全。
- [ ] 未绑定、未授权、范围外、已撤销、关系解除五种情况下均无法看到事件，详情路由直连也被拦截。
- [ ] F05 三种状态（AI 判断 / 查看 / 协同处理）分列展示，不混用。
- [ ] F07/F08 状态历史追加保留，AI 原始等级不被覆盖。
- [ ] F11 未读、已读、撤销消息、延迟与离线状态正确。
- [ ] Room v2→v3 迁移成功，P06/P07 数据保留，重启后新状态保留。
- [ ] `assembleDebug` 与 `testDebugUnitTest` 实际执行并通过（或如实说明失败原因）。
- [ ] 交付报告 15 项齐全，明确未接入真实推送与生产级服务端鉴权。
