# P07 规格说明｜家属绑定、家庭关系与隐私授权管理

> 目标：在现有 P00–P06 Android 工程上增量实现 F02 绑定老人、F04 关联关系、F09 共享授权，以及老人端 E12「我的」中的家庭关系管理与风险共享授权管理入口。
> 核心原则：**家庭绑定 ≠ 数据共享授权**。绑定后默认全部关闭；老人独占授权与撤销；家属只读；未授权/已撤销不得显示共享成功；全部为本地模拟，不宣称跨设备传输。

---

## 1. 现状与约束（已核对）

| 项 | 现状 |
|---|---|
| 数据库 | `guard/risk/RiskEventStore.kt` 中 `SilverShieldDatabase` version=1，仅 `risk_events` 表，DB 名 `silver_shield.db` |
| 风险模型 | `guard/risk/RiskEvent.kt`：RiskEvent / RiskType / RiskLevel / RiskHandleStatus / MockRiskEvents |
| 详情页 | `ui/elder/ElderRiskDetailScreen.kt` + `RiskDetailViewModel.kt`，分享按钮仅做 `familySharingEnabled` 前置检查，不落库 |
| 家属端导航 | `ui/family/FamilyAppScreen.kt` 仅 4 个一级 Tab，无二级页支持；`FamilyProfileScreen.kt`、`FamilyHomeScreen.kt` 为占位 |
| 老人端导航 | `ui/elder/ElderAppScreen.kt` 已有二级页机制（`ElderSubPage`）与返回按钮 |
| 设计系统 | `StatusCard` / `PrimaryButton` / `EmptyState` / `ErrorState` / `LoadingState` / `SampleTag` / `StatusTone` / Tokens |
| 单测 | 仅 JUnit，无 coroutines-test；使用 `runBlocking` |

**硬约束**
1. 新增 Room 表必须通过 `Migration(1, 2)` 升级，保留 `risk_events` 与 P06 本地记录，禁止 `fallbackToDestructiveMigration` / pm clear。
2. 复用 P06 `RiskEvent`，按事件分享只记录事件摘要与授权，不复制/上传原始屏幕内容。
3. 所有家庭关系、邀请、授权数据 `isMock = true`，UI 显示「模拟数据」。
4. 不开发 P08 家属风险列表/协同处理，不进入 P08。

---

## 2. 数据模型（新增 `com.silvershield.app.family`）

### 2.1 枚举

```kotlin
enum class FamilyRelationStatus { PENDING, ACTIVE, REJECTED, EXPIRED, REVOKED }
enum class InviteStatus { PENDING, ACCEPTED, REJECTED, EXPIRED, CANCELLED }
enum class InviteParty(val label: String) { Elder("老人"), Family("家属") }
enum class AuthorizationType(val label: String) { EventShare("按事件分享"), ScopeGrant("事先授权范围") }
enum class ConsentStatus { NOT_GRANTED, ACTIVE, EXPIRED, REVOKED }
enum class ConsentScopeType { EventIds, RiskTypes, AllTypes }
```

### 2.2 `FamilyRelation`

字段：`relationId`、`elderUserId`、`familyUserId`、`elderDisplayName`、`familyDisplayName`、`relationStatus`、`initiatedBy: InviteParty`、`createdAt`、`confirmedAt: Long?`、`expiresAt`、`revokedAt: Long?`、`isMock`。
计算属性：`effectiveStatus(now)`：PENDING 且 `now > expiresAt` → EXPIRED；REVOKED 优先。

### 2.3 `FamilyInvite`

字段：`inviteId`、`inviteCode`（6 位，去歧义字符集）、`inviterRole: InviteParty`、`inviterUserId`、`inviterDisplayName`、`inviteeRole: InviteParty`、`createdAt`、`expiresAt`、`status`、`usedAt: Long?`、`relationId: String?`、`isMock`。
计算属性：`effectiveStatus(now)`：PENDING 且过期 → EXPIRED。

### 2.4 `ConsentRecord`

字段：`consentId`、`elderUserId`、`familyUserId`、`relationId`、`authorizationType`、`scopeType: ConsentScopeType`、`scopeValues: List<String>`（RiskType.name 或 eventId）、`sourceEventId: String?`、`grantedAt`、`expiresAt: Long?`、`revokedAt: Long?`、`consentStatus`、`updatedAt`、`isMock`。
计算属性：`effectiveStatus(now)`：REVOKED > EXPIRED > ACTIVE。
`ConsentRecord.covers(eventId, riskType)`：EventIds 含 eventId；RiskTypes 含 riskType.name；AllTypes 恒真。

### 2.5 本地身份（模拟）

`LocalFamilyAccounts`：`ELDER_USER_ID = "local-elder"`、`FAMILY_USER_ID = "local-family"`、显示名「本机老人（模拟）」「本机家属（模拟）」。角色中立邀请：发起方为当前角色，对方为另一本地角色。

---

## 3. 持久化与迁移

### 3.1 新增 Entity / DAO（`family/FamilyStore.kt`）

- `family_relations`、`family_invites`（`inviteCode` 唯一索引）、`consent_records` 三张表；枚举存 `name`，列表换行分隔。
- `FamilyRelationDao`：`observeAll(): Flow<List<...>>`、`getById`、`upsert`、`updateStatus`、`deleteById`。
- `FamilyInviteDao`：`observeAll`、`getByCode`、`getById`、`upsert`、`updateStatus`。
- `ConsentRecordDao`：`observeAll`、`getById`、`upsert`、`updateStatusByRelation`。
- `FamilyLocalStore` 接口 + `RoomFamilyLocalStore`（应用实现）+ `InMemoryFamilyLocalStore`（JVM 单测实现，`MutableStateFlow`）。

### 3.2 修改 `guard/risk/RiskEventStore.kt`

- `@Database(entities = [RiskEventEntity, FamilyRelationEntity, FamilyInviteEntity, ConsentRecordEntity], version = 2, exportSchema = false)`。
- 新增抽象方法 `familyRelationDao()`、`familyInviteDao()`、`consentRecordDao()`。
- 新增 `MIGRATION_1_2`（`ALTER` 不需要；`CREATE TABLE IF NOT EXISTS` 三张新表 + `inviteCode` 唯一索引），`Room.databaseBuilder(...).addMigrations(MIGRATION_1_2)`。
- `risk_events` 表结构与数据完全不变。

---

## 4. 仓库与业务规则

### 4.1 `FamilyRepository` / `FakeFamilyRepository`

接口：

```kotlin
val relations: Flow<List<FamilyRelation>>
val invites: Flow<List<FamilyInvite>>
suspend fun generateInvite(role: InviteParty): Result<FamilyInvite>
suspend fun lookupInvite(code: String, role: InviteParty): InviteLookup
suspend fun acceptInvite(code: String, role: InviteParty): Result<FamilyRelation>
suspend fun rejectInvite(code: String, role: InviteParty): Result<Unit>
suspend fun cancelInvite(inviteId: String): Result<Unit>
suspend fun revokeRelation(relationId: String, role: InviteParty): Result<Unit>
suspend fun refreshExpiry()
```

规则（`FakeFamilyRepository(store: FamilyLocalStore, now: () -> Long, codeGenerator: () -> String)`）：

| # | 规则 |
|---|---|
| R1 | 已有 PENDING/ACTIVE 关系时禁止再次生成邀请 |
| R2 | 邀请码 6 位，字符集 `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`，在同库内唯一，重复则重试 |
| R3 | 有效期 24h；过期邀请 `lookup/accept/reject` 均返回 `Expired` |
| R4 | 仅另一角色可接受/拒绝；同角色返回 `SelfInvite` |
| R5 | 已使用（ACCEPTED/REJECTED/CANCELLED）邀请不可复用 |
| R6 | 接受 → 关系 PENDING→ACTIVE，写 `confirmedAt`，邀请 PENDING→ACCEPTED + `usedAt`；必须双方确认后才 ACTIVE |
| R7 | 拒绝 → 关系 REJECTED，邀请 REJECTED |
| R8 | 取消 → 删除 PENDING 关系，邀请 CANCELLED |
| R9 | 解除 → 关系 REVOKED + `revokedAt`，并调用 `ConsentRepository.revokeForRelation` 使全部授权 REVOKED |
| R10 | 错误码：`invalid_format / not_found / expired / used / self / no_relation`，UI 显示通俗中文 |

`InviteLookup` 密封类：`Found(invite)`、`NotFound`、`Expired`、`Used(status)`、`SelfInvite`、`InvalidFormat`。

### 4.2 `ConsentRepository` / `FakeConsentRepository`

```kotlin
val consents: Flow<List<ConsentRecord>>
suspend fun grantEventShare(eventId: String, validityMillis: Long?): Result<ConsentRecord>
suspend fun grantScope(riskTypes: Set<RiskType>, validityMillis: Long?): Result<ConsentRecord>
suspend fun revoke(consentId: String): Result<Unit>
suspend fun revokeForRelation(relationId: String): Result<Unit>
suspend fun refreshExpiry()
fun activeForFamily(consents: List<ConsentRecord>, now: Long): List<ConsentRecord>
fun canShare(eventId: String, riskType: RiskType): Boolean
```

规则：

| # | 规则 |
|---|---|
| C1 | 无 ACTIVE 关系时禁止任何授权（`NoRelation`） |
| C2 | 默认无记录 = NOT_GRANTED；不自动创建任何授权 |
| C3 | 授权范围禁止原始录像 / 实时屏幕 / 完整浏览历史（模型中不存在这些字段，UI 明示） |
| C4 | 撤销立即写入 `revokedAt` + REVOKED，重启后不复活 |
| C5 | 过期按 `expiresAt <= now` 计算为 EXPIRED 并持久化 |
| C6 | 按事件授权仅记录 eventId + 摘要授权，不复制 RiskEvent，不上传内容 |
| C7 | 同一事件重复授权复用已有 ACTIVE 记录 |

---

## 5. ViewModel

- `family/FamilyViewModel.kt`：`FamilyViewModel(role: UserRole, familyRepository, consentRepository)`；`uiState` 含 `loading / relation / invites / generatedInvite / preview / message / error`；动作 `generateInvite / lookupInvite / acceptInvite / rejectInvite / cancelInvite / revokeRelation / retry`；`companion factory(...)`。
- `family/ConsentViewModel.kt`：`ConsentViewModel(role, consentRepository, familyRepository)`；老人可 `grantEventShare / grantScope / revoke`；家属只暴露读取与 `retry`，无任何写入方法（编译期即不可代授权）。

页面只通过 ViewModel 调用仓库，禁止在 Composable 内直接访问 DAO 或硬编码状态。

---

## 6. 导航与页面

### 6.1 路由（修改 `navigation/AppRoutes.kt`）

```
FAMILY_BIND      = "family/bind"        // F02
FAMILY_RELATION  = "family/relation"    // F04
FAMILY_CONSENT   = "family/consent"     // F09
ELDER_FAMILY     = "elder/family"       // E12 家庭关系管理
ELDER_FAMILY_CONSENT = "elder/family/consent" // E12 风险共享授权
```

`ElderSubPage` 增加 `FamilyManagement`、`FamilyConsentManagement`（标题「家庭关系」「风险共享授权」）。

### 6.2 家属端（修改 `FamilyAppScreen.kt`）

- 新增 `FamilySubPage` 枚举（`ui/family/FamilySubPage.kt`），二级页隐藏底部导航、顶部显示返回按钮；`BackHandler` 优先 `popBackStack`。
- 注册三条二级路由，复用同一 `RoomFamilyLocalStore` / `FakeFamilyRepository` / `FakeConsentRepository` 实例（`remember`）。
- `FamilyProfileScreen` 增加入口：「绑定老人 F02」「关联关系 F04」「共享授权 F09」，并显示当前绑定状态摘要。

### 6.3 F02 `ui/family/FamilyBindScreen.kt`

展示：当前绑定状态卡、生成邀请码、邀请码大字展示 + 有效期 + 模拟数据标签、输入邀请码（6 位、自动大写、长度限制）、邀请预览（对方身份 / 发起方 / 有效期）、接受 / 拒绝、等待确认卡（取消邀请）、绑定成功卡、解除入口（跳 F04）、通俗说明。支持 Loading / Empty / Error / Success，状态均有文字与图标。

### 6.4 F04 `ui/family/FamilyRelationScreen.kt`

未绑定：说明 + 去绑定；PENDING：发起方、等待对象、有效期、取消；ACTIVE：家庭成员、状态、绑定时间、解除；REJECTED/EXPIRED：状态 + 重新邀请。解除必须 `AlertDialog` 二次确认，说明「解除后将停止共享、已有授权失效，但不会删除老人本机风险记录」；解除后立即刷新为未绑定，授权历史显示已撤销。

### 6.5 F09 `ui/family/FamilyConsentScreen.kt`

只读：有效授权卡片列表（授权类型、范围、生效/失效时间、状态图标+文字）；「家庭绑定不代表自动授权」「家属不能修改授权」「不含原始录像/实时屏幕/完整浏览历史」说明；未绑定 → EmptyState；已撤销记录以「已撤销」只读展示；提供刷新。

### 6.6 老人端（修改 `ElderProfileScreen.kt` / `ElderAppScreen.kt`）

- E12 增加「家庭关系管理」卡片入口（与风险共享授权入口分开，不合并为单一开关）。
- `ui/elder/ElderFamilyScreen.kt`：双入口（家庭关系 / 风险共享授权）+ 当前绑定状态摘要。
- `ui/elder/ElderFamilyRelationScreen.kt`：老人视角的生成邀请、输入邀请码、待确认、已绑定、解除（二次确认）。
- `ui/elder/ElderConsentScreen.kt`：当前授权列表 + 撤销、事先授权表单（风险类型多选、有效期 7 天 / 30 天 / 长期）、授权历史（已撤销/已过期）、「家庭绑定不等于授权」「仅本机模拟撤销，后续需服务端强制校验」说明。
- 老人端字号 ≥18sp、主按钮 ≥56dp、触控 ≥48dp。

### 6.7 E07 按事件分享（修改 `ElderRiskDetailScreen.kt` / `RiskDetailViewModel.kt` / `ElderAppScreen.kt`）

- `RiskDetailViewModel` 注入 `ConsentRepository`，暴露 `shareStatus: ConsentStatus` 与 `shareEvent()`。
- 分享按钮：无 ACTIVE 关系 → 「请先绑定家人」；有绑定未授权 → 确认后调用 `grantEventShare`，消息明确「已在本地记录分享授权（仅事件摘要），尚未跨设备发送」；已授权 → 显示「已分享（仅摘要）」及「撤销本次分享」。
- 不传入 `familySharingEnabled`（P04 遗留开关不再作为授权依据），改为读取真实 ConsentRecord。

---

## 7. 测试与验证

### 7.1 新增单测（JVM，`runBlocking` + `InMemoryFamilyLocalStore`）

`app/src/test/java/com/silvershield/app/family/FamilyRepositoryTest.kt`
- 生成 6 位邀请码；唯一性（碰撞重试）
- 正确码双向接受；错误码；格式错误；同角色接受拒绝
- 等待中存在关系为 PENDING 而非 ACTIVE
- 过期码不可接受；过期状态
- 重复使用被拒
- 拒绝、取消、解除
- 解除后状态 REVOKED
- 新建仓库复用同一 store → 重启恢复

`ConsentRepositoryTest.kt`
- 默认无授权（NOT_GRANTED）
- 未绑定不可授权
- 按事件授权仅覆盖该事件
- 范围授权覆盖指定风险类型，不覆盖其他类型
- 撤销后不有效
- 过期计算
- 解除绑定后全部授权失效
- 家属侧无写入 API（接口/ViewModel 方法断言）

### 7.2 构建与设备验证

```
cd apps/android
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug "-Pkotlin.compiler.execution.strategy=in-process" --console=plain
```

模拟器（API 35）实测：老人端 E12 → 生成邀请码 → 切换家属端 → 输入码 → 预览 → 接受 → 双方 ACTIVE → 家属端 F09 未授权 → 老人端范围授权 → 家属端只读可见 → 老人撤销 → 家属端显示已撤销 → 解除绑定 → 授权失效；杀进程重启验证状态恢复；确认 P06 风险记录未丢失。

### 7.3 交付报告

`docs/testing/P07_FAMILY_CONSENT_REPORT.md`，包含指令要求的 15 项内容；测试结果必须来自实际执行，不虚构。

---

## 8. 明确不做

- 不实现真实跨设备同步、推送、服务端对象级鉴权（P08/P13）。
- 不实现 F03/F05–F08/F11 业务。
- 不新增真实账号体系；全部本地模拟身份并显著标注。
- 不修改 P05 采集逻辑，不因 P07 页面启动采集。
