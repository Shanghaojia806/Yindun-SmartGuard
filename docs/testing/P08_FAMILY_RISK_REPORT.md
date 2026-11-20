# P08 家属风险事件管理与家庭协同处理 交付报告

> 范围：仅家属端 F03 / F05 / F06 / F07 / F08 / F11。
> 未开发 Web 管理后台，未接入真实跨设备后端，未新增 AI 识别算法，未重建 Android 工程。
> 本轮所有事件与消息均为本地模拟数据，界面均标注「模拟数据 / 本地模拟消息」。

---

## 1. 新增文件清单

数据层与业务层：

1. `apps/android/app/src/main/java/com/silvershield/app/family/FamilyRiskModels.kt`
2. `apps/android/app/src/main/java/com/silvershield/app/family/FamilyRiskStore.kt`
3. `apps/android/app/src/main/java/com/silvershield/app/family/SharedRiskEventRepository.kt`
4. `apps/android/app/src/main/java/com/silvershield/app/family/HandlingRecordRepository.kt`
5. `apps/android/app/src/main/java/com/silvershield/app/family/FamilyMessageRepository.kt`
6. `apps/android/app/src/main/java/com/silvershield/app/family/FamilyRiskViewModel.kt`
7. `apps/android/app/src/main/java/com/silvershield/app/family/FamilyEventViewModel.kt`

界面层：

8. `apps/android/app/src/main/java/com/silvershield/app/ui/family/FamilyRiskComponents.kt`
9. `apps/android/app/src/main/java/com/silvershield/app/ui/family/FamilyRiskDetailScreen.kt`（F06）
10. `apps/android/app/src/main/java/com/silvershield/app/ui/family/FamilyCooperateScreen.kt`（F07 动作页）
11. `apps/android/app/src/main/java/com/silvershield/app/ui/family/FamilyHandlingDetailScreen.kt`（F08）
12. `apps/android/app/src/main/java/com/silvershield/app/ui/family/FamilyMessagesScreen.kt`（F11）

测试：

13. `apps/android/app/src/test/java/com/silvershield/app/family/SharedRiskEventRepositoryTest.kt`（9 项）
14. `apps/android/app/src/test/java/com/silvershield/app/family/HandlingRecordRepositoryTest.kt`（6 项）
15. `apps/android/app/src/test/java/com/silvershield/app/family/FamilyMessageRepositoryTest.kt`（5 项）

## 2. 修改文件清单

1. `guard/risk/RiskEventStore.kt`：数据库 `version = 3`，注册 3 个 Entity / DAO，新增 `MIGRATION_2_3`（仅 `CREATE TABLE/INDEX IF NOT EXISTS`，不改动 P06/P07 表）。
2. `navigation/AppRoutes.kt`：新增 F06/F07/F08/F11 路由与 `shareId` 参数、路由构造器。
3. `ui/family/FamilySubPage.kt`：新增 RiskDetail / Cooperate / HandlingDetail / Messages，支持 `{arg}` 前缀匹配。
4. `ui/family/FamilyAppScreen.kt`：注入 `RoomRiskEventRepository`、`RoomFamilyRiskLocalStore`、三个 Fake Repository、两个 ViewModel；注册四条新路由。
5. `ui/family/FamilyHomeScreen.kt`：重写为 F03。
6. `ui/family/FamilyRisksScreen.kt`：重写为 F05。
7. `ui/family/FamilyCooperationScreen.kt`：重写为 F07 待协同清单。

未删除 P06 风险记录，未修改 P06 `RiskEvent` 模型与 P07 `ConsentRecord` / `FamilyRelation` 模型。

## 3. SharedRiskEvent 数据模型

```kotlin
data class SharedRiskEvent(
    val shareId: String,          // share-{consentId}-{eventId}，确定性生成，幂等
    val eventId: String,          // 可追溯 P06 原始事件
    val elderUserId: String,
    val familyUserId: String,
    val consentId: String,        // 可追溯 P07 授权记录
    val sharedAt: Long,
    val shareStatus: ShareStatus, // ACTIVE / REVOKED / EXPIRED
    val readAt: Long?,            // 家属首次查看时间
    val revokedAt: Long?,
    val isMock: Boolean = true,
)
```

约束落实：

- 分享对象只承载「摘要 + 授权范围标识」，不存储截图、屏幕录像、实时屏幕或浏览历史。
- `Sync()` 由 **P07 ConsentRecord 物化**：仅当「ACTIVE 家庭关系 + ACTIVE 授权 + 授权覆盖该事件」时生成/保持 ACTIVE；授权撤销 → `REVOKED`，授权过期 → `EXPIRED`，关系失效 → 全部 `REVOKED`；`readAt` 不被覆盖。
- 读取路径统一走 `observeAccess(shareId)`，UI 无法绕过。

## 4. HandlingRecord 数据模型

```kotlin
data class HandlingRecord(
    val handlingId: String,
    val shareId: String,
    val eventId: String,
    val handlerUserId: String,        // local-family
    val handlingStatus: HandlingStatus,
    val previousStatus: HandlingStatus, // 变更前状态，显式记录
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isMock: Boolean = true,
)
```

六状态固定：`待查看 / 已查看 / 已联系 / 核实中 / 已协助处理 / 无法核实`。

- AI 等级存放在 P06 `RiskEvent.riskLevel`，协同状态存放在 `HandlingRecord`，两者表与字段完全分离，家属没有写 AI 判断的 API。
- **追加式历史**：每次更新插入新记录，不做更新覆盖；`previousStatus` 取上一条记录状态。
- 相同状态 + 相同备注会被拒绝，避免产生无意义记录。

## 5. FamilyMessage 数据模型

```kotlin
data class FamilyMessage(
    val messageId: String,               // 确定性 ID，重复 refresh 不重复
    val messageType: FamilyMessageType,  // RiskShared / RiskRevoked / RelationChanged / ConsentChanged / HandlingChanged
    val title: String,
    val content: String,
    val relatedEventId: String?,
    val relatedShareId: String?,
    val createdAt: Long,
    val readAt: Long?,
    val isRead: Boolean,
    val deliveryState: MessageDeliveryState, // Delivered / Delayed / Undelivered
    val isMock: Boolean = true,
)
```

消息由本地 `FakeFamilyMessageRepository` 从分享 / 关系 / 授权 / 处理记录派生，未接入任何推送服务；页面底部明确说明「本轮消息通过本地 Mock Repository 生成，未接入真实推送服务」。

## 6. 页面完成情况

| 页面 | 状态 | 要点 |
| --- | --- | --- |
| F03 家庭首页 | 完成 | 家庭成员卡（绑定状态、关系状态、授权状态、关系管理入口）；4 项风险统计（已授权共享 / 待查看 / 待处理 / 已协助处理）；4 类待处理事项可跳转 F05/F07；消息入口与未读数；未绑定 / 待确认 / 无授权 / 无分享 / 授权已撤销 / 全部处理 空态；模拟数据声明 |
| F05 风险列表 | 完成 | 四组筛选（等级 / 类型 / 处理状态 / 已查看）；每条分别显示 AI 等级、家属查看状态、协同处理状态；未绑定 / 关系解除 / 无授权 / 授权撤销 / 无分享 / 全部处理 / 筛选为空 七种空态 |
| F06 风险详情 | 完成 | 标题、类型、AI 等级、发生时间、分享时间、风险线索、证据摘要、可用模态、信息充分性、处理建议、协同状态共 11 项；明确「系统判断，家属不可修改」；信息不足时提示进一步核实；隐私说明明确不展示录像与实时屏幕；进入即写 `readAt` |
| F07 协同处理 | 完成 | 模拟联系老人弹窗（无真实号码时不虚构，提供 110 `ACTION_DIAL`）；六状态更新；备注输入；保存反馈；最近处理记录；AI 边界说明与授权失效拦截 |
| F08 处理详情 | 完成 | 原始事件摘要、AI 等级、当前状态、当前处理人、最近处理时间、完整时间线（操作人 / 时间 / 变更前后 / 备注 / 模拟标签）、继续更新入口；授权撤销时整页替换为授权失效说明 |
| F11 消息中心 | 完成 | 列表、未读数、单条已读、全部已读、点击跳转事件、撤销事件显示「详情不再可见」、模拟网络三态与离线/延迟提示 |

依赖复用：`FamilySection`、`StatusCard`、`EmptyState`、`ErrorState`、`LoadingState`、`PrimaryButton`、`RiskBadge`、`SampleTag`、`formatRiskTime` 等 P02 设计系统组件。

## 7. 老人授权事件到家属接收的业务流程

1. 老人端 P07 建立 ACTIVE `FamilyRelation`，并写入 `ConsentRecord`（如 `EventShare` / `EventIds`）。
2. 家属端 `FakeSharedRiskEventRepository.sync()` 读取关系 + 授权 + P06 事件，按 `shareId = share-{consentId}-{eventId}` 物化 `SharedRiskEvent`。
3. `observeVisible()` 同时校验「关系 ACTIVE + 授权 ACTIVE + 授权覆盖事件 + 分享 ACTIVE」，只有四者全成立的事件进入 UI。
4. F03 统计与待处理分桶、F05 列表均来自 `items`（已校验集合），不在 UI 侧做权限判断。
5. 家属打开 F06：`observeAccess(shareId)` 复核后写入 `readAt`，AI 等级与查看状态分别展示。
6. F07 更新协同状态时再次 `access()` 校验，追加 `HandlingRecord`；`FamilyMessageRepository.refresh()` 派生「协同处理状态已更新」消息。
7. 授权撤销 → `sync()` 将分享置 `REVOKED` 并写 `revokedAt`，列表立即为空态，详情/协同/处理详情全部替换为授权失效提示。

## 8. 授权撤销与关系解除测试

单元测试（真实执行，全部通过）：

- `revokedConsent_takesEffectImmediately`：撤销后 `observeVisible()` 立即为空，`access()` 返回 `CONSENT_REVOKED`。
- `revokedRelation_stopsSharing`：解除关系并同步后为空，`access()` 返回 `RELATION_INACTIVE`，存量分享全部非 ACTIVE。
- `directDetailRoute_cannotBypassCheck`：未知 `shareId` → `SHARE_NOT_FOUND`；撤销后同一直连地址 → 拒绝且 `markViewed` 失败。
- `outOfScopeEvent_isDenied`：授权只覆盖 event-1 时，伪造 event-2 分享 → `OUT_OF_SCOPE`。
- `unboundFamily_cannotAccessShare` / `shareWithoutConsent_isDenied`：无关系 / 无授权均拒绝。

模拟器实测（Android，真实 Room）：

- 授权置为 REVOKED 并重启后，F05 显示「共享授权已撤销 / 老人已经撤销了风险事件共享，已撤销的事件不会继续显示。」
- F03 显示「共享授权已撤销 …历史处理记录仍保留在本机」。
- 从 F11 已撤销消息点击进入详情，页面被替换为「无法查看这条共享事件 / 老人已经撤销了这条风险事件的分享，详情不再可见。」，证明详情路由无法绕过校验。

## 9. 协同处理历史测试

- `updateStatus_appendsFirstRecord`：首条记录 `previousStatus = PendingView`，`handlerUserId = local-family`。
- `repeatedUpdates_keepFullHistory`：三次更新产生三条记录，前后状态链正确，旧备注不被覆盖。
- `noteIsTrimmed_andAttachedToEvent`：备注去空白并与 `eventId` 关联。
- `aiRiskLevel_isNeverOverwritten`：更新后 P06 事件对象完全不变（`riskLevel` 仍为 High）。
- `revokedConsent_blocksFurtherUpdates`：撤销后写入被拒绝，历史条数不变。
- `unchangedStatusAndNote_isRejected`：同状态同备注被拒绝。

模拟器实测：F07 选择「已联系」并填写备注保存，出现「处理记录已保存」；F08 时间线显示 `待查看 → 已联系`、操作人、时间与备注；重启并撤销授权后，`handling_records` 仍保留该记录。

## 10. 消息中心测试

- `newShare_createsUnreadMessage_linkedToEvent`：分享生成未读消息，关联 `eventId` 与 `shareId`，重复 refresh 不重复。
- `markRead_updatesSingleMessage_andSurvivesRestart`：单条已读 + `readAt`，新建仓库实例后仍为已读。
- `markAllRead_clearsUnreadCount`：全部已读后无未读、`readAt` 全部非空。
- `revokedShare_createsRevocationMessage`：撤销生成 `RiskRevoked` 消息并关联原分享。
- `offlineAndDelayed_modes_areReflectedOnNewMessages`：离线 → `Undelivered`，延迟 → `Delayed`，切回在线后升级为 `Delivered`。

模拟器实测：4 条未读 → 全部标记已读；消息类型包含分享、关系建立、协同更新、授权撤销；撤销后新增「风险事件分享已失效」且历史消息显示「相关事件已撤销或已失效，详情不再可见。」

## 11. 离线与延迟测试

- 消息中心提供「正常同步 / 消息延迟 / 离线（演示）」三态切换。
- 离线时显示「当前为离线状态（演示）… 离线期间的新消息会标记为『离线未送达』，切回正常同步后才会变为已送达」，未同步状态不会被表述为已送达。
- 单元测试覆盖三种投递状态与恢复在线后的升级逻辑。
- 说明：这是本地模拟网络状态，不代表真实网络或推送能力。

## 12. Android APK 构建结果

命令：`.\gradlew.bat :app:assembleDebug`（`apps/android`）

结果：`BUILD SUCCESSFUL`，产物 `apps/android/app/build/outputs/apk/debug/app-debug.apk`（约 11.1 MB）。

已在模拟器 `emulator-5554` 上覆盖安装成功，启动无崩溃。

## 13. 单元测试结果

命令：`.\gradlew.bat :app:testDebugUnitTest`

- 总计 **97 个测试，0 失败**（含 P07 及以前全部测试）。
- 本轮新增 20 个：`SharedRiskEventRepositoryTest` 9、`HandlingRecordRepositoryTest` 6、`FamilyMessageRepositoryTest` 5，全部通过。

数据库迁移实测（非单元测试）：

- 升级前：`user_version = 2`，`risk_events = 1`、`family_relations = 3`、`family_invites = 3`、`consent_records = 2`。
- 覆盖安装并启动后：`user_version = 3`，新增 `shared_risk_events`、`handling_records`、`family_messages` 三张表，P06 / P07 既有行数不变。
- 全程未使用 `pm clear`、未卸载应用、未删除数据库。

端到端验证说明：P07 留下的家庭关系当时处于「等待对方确认」，为验证 P08 分享链路，验证期间通过本地 Room 数据库注入了一条 ACTIVE 关系与一条 `EventShare` 授权（`isMock = 1`，备注「P08 端到端验证授权（本地模拟）」）。验证结束后已删除注入的授权、分享、处理与消息记录，并将关系恢复为 PENDING，P06 / P07 数据未被删除。

## 14. 尚未接入的真实服务端与推送能力

- 真实后端对象级权限校验（当前为本地 Fake Repository 模拟，不具备生产级安全能力）。
- 真实跨设备分享与同步（当前分享由本机授权物化）。
- 真实推送 / 长连接（消息为本地派生，网络三态为 UI 模拟）。
- 真实电话或视频联系（无真实号码时不虚构，仅提供模拟入口与 110 拨号）。
- 真实 AI 识别引擎（继续使用 P06 模拟事件）。
- Web 管理后台。

## 15. P08 验收结论

P08 范围内六个家属端页面、三张新表与三类 Repository、两个 ViewModel、授权三层校验、追加式处理历史、本地消息中心均已落地并通过构建与单元测试；模拟器实测验证了「分享 → 可见 → 查看 → 协同 → 撤销 → 拦截 → 重启持久化」的完整闭环，P06 / P07 数据在 v2→v3 迁移后完整保留。

已知边界与限制（均已在 UI 与报告如实标注）：

- 消息、分享、网络状态均为本地模拟，不代表真实推送能力。
- 授权校验依赖本地 Repository，真实跨设备场景仍需后端对象级权限校验。
- 详情页内容完全由授权范围内的摘要构成，不含原始录像、实时屏幕或完整浏览历史。

结论：P08 按 V1.3 范围与权限边界完成，可进入下一轮指令；本轮不自动进入 P09。
