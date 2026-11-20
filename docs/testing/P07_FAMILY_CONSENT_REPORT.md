# P07 交付报告：家属绑定、家庭关系与隐私授权管理

- 阶段：P07（F02 绑定老人 / F04 关联关系 / F09 共享授权 / E12 老人端家庭管理入口 / E07 按事件分享）
- 日期：2026-11-17
- 范围声明：本轮只实现 P07 的本地模拟能力。未开发 Web 管理后台业务，未接入真实账号体系、真实跨设备同步或服务端对象级鉴权，未进入 P08。
- 核心原则：**家庭绑定 ≠ 数据共享授权**。绑定成功后默认不产生任何授权，授权只能由老人主动签发，家属只读。

---

## 1. 新增文件清单

| 文件 | 说明 |
| --- | --- |
| `family/FamilyModels.kt` | 枚举、`FamilyRelation`、`FamilyInvite`、`ConsentRecord`、`InviteLookup`、`LocalFamilyAccounts` |
| `family/FamilyStore.kt` | 3 个 Room Entity + 3 个 DAO + `FamilyLocalStore` 抽象 + Room/InMemory 两种实现 |
| `family/FamilyRepository.kt` | `FamilyRepository` 接口 + `FakeFamilyRepository`（邀请码、有效期、单次使用、双向确认、解除） |
| `family/ConsentRepository.kt` | `ConsentRepository` 接口 + `FakeConsentRepository`（默认关闭、事件/范围授权、撤销、过期、解绑失效） |
| `family/FamilyViewModel.kt` | 老人/家属共用绑定 ViewModel（按 `role` 限制动作） |
| `family/ConsentViewModel.kt` | 只读 `ConsentViewModel`（家属）+ `ElderConsentViewModel`（老人写入） |
| `ui/components/FamilyCommon.kt` | FamilySection、InfoRow、InviteCodeBox、StatusInline、PrivacyNote、ConsentCard |
| `ui/family/FamilySubPage.kt` | 家属端二级页定义与路由匹配 |
| `ui/family/FamilyBindScreen.kt` | F02 绑定老人 |
| `ui/family/FamilyRelationScreen.kt` | F04 关联关系 |
| `ui/family/FamilyConsentScreen.kt` | F09 共享授权（只读） |
| `ui/elder/ElderFamilyScreen.kt` | 老人端家庭关系中心（两个独立入口） |
| `ui/elder/ElderFamilyRelationScreen.kt` | 老人端家庭关系管理 |
| `ui/elder/ElderConsentScreen.kt` | 老人端风险共享授权管理 + 授权历史 |
| `app/src/test/java/com/silvershield/app/family/FamilyRepositoryTest.kt` | 家庭关系单元测试（13 项） |
| `app/src/test/java/com/silvershield/app/family/ConsentRepositoryTest.kt` | 授权单元测试（12 项） |

## 2. 修改文件清单

| 文件 | 变更 |
| --- | --- |
| `guard/risk/RiskEventStore.kt` | 数据库 `version = 2`；新增 3 个 Entity/DAO；新增 `MIGRATION_1_2`（仅建表与索引，保留 `risk_events`） |
| `navigation/AppRoutes.kt` | 新增 `FAMILY_BIND` / `FAMILY_RELATION` / `FAMILY_CONSENT` / `ELDER_FAMILY` / `ELDER_FAMILY_RELATION` / `ELDER_FAMILY_CONSENT` |
| `navigation/ElderSubPage.kt` | 新增三个 E12 二级页并纳入返回栏与底栏隐藏逻辑 |
| `ui/family/FamilyAppScreen.kt` | 注入 `RoomFamilyLocalStore` + `FakeFamilyRepository` + `FakeConsentRepository`，注册 3 条二级路由 |
| `ui/family/FamilyProfileScreen.kt` | F12 增加绑定状态卡、`绑定老人（F02）`、`共享授权（F09 · 只读）` 与模拟数据说明；邀请方向文案按 `initiatedBy` 区分 |
| `ui/elder/ElderAppScreen.kt` | 注入家庭/授权仓库与两个 ViewModel；注册 E12 三条路由；E07 传入授权仓库 |
| `ui/elder/ElderProfileScreen.kt` | E12 增加家庭绑定状态卡与「家庭关系与共享授权」入口 |
| `ui/elder/RiskDetailViewModel.kt` | 注入 `ConsentRepository` / `FamilyRepository`，暴露 `shareStatus`、`canShare`、`shareEvent()`、`revokeShare()` |
| `ui/elder/ElderRiskDetailScreen.kt` | E07 新增分享区：未绑定不可分享、已分享可撤销、确认弹窗、撤销/过期状态 |

## 3. FamilyRelation 数据结构

```kotlin
data class FamilyRelation(
    val relationId: String,
    val elderUserId: String,
    val familyUserId: String,
    val elderDisplayName: String,
    val familyDisplayName: String,
    val relationStatus: FamilyRelationStatus,   // PENDING / ACTIVE / REJECTED / EXPIRED / REVOKED
    val initiatedBy: InviteParty,              // Elder / Family
    val createdAt: Long,
    val confirmedAt: Long?,
    val expiresAt: Long,
    val revokedAt: Long?,
    val isMock: Boolean = true,
)
```

- `effectiveStatus(now)`：撤销/拒绝优先，`PENDING` 超期自动视为 `EXPIRED`，应用重启后同样成立。
- 关系表 `family_relations` 持久化全部字段，解除绑定写 `REVOKED + revokedAt`，不删除历史记录。

## 4. FamilyInvite 数据结构

```kotlin
data class FamilyInvite(
    val inviteId: String,
    val inviteCode: String,                    // 6 位本地模拟码，字符集去歧义，唯一索引
    val inviterRole: InviteParty,
    val inviterUserId: String,
    val inviterDisplayName: String,
    val inviteeRole: InviteParty,
    val createdAt: Long,
    val expiresAt: Long,                       // 生成后 24 小时
    val status: InviteStatus,                  // PENDING / ACCEPTED / REJECTED / EXPIRED / CANCELLED
    val usedAt: Long? = null,
    val relationId: String? = null,
    val isMock: Boolean = true,
)
```

- 邀请与家庭关系分表管理，邀请状态不承担关系状态。
- 邀请码单次使用：接受/拒绝后写入终态，重复输入返回「已被使用」。
- 本地模拟码标注 `isMock = true`，不使用真实用户信息作为测试码。

## 5. ConsentRecord 数据结构

```kotlin
data class ConsentRecord(
    val consentId: String,
    val elderUserId: String,
    val familyUserId: String,
    val relationId: String,
    val authorizationType: AuthorizationType,  // EventShare / ScopeGrant
    val scopeType: ConsentScopeType,           // EventIds / RiskTypes / AllTypes
    val scopeValues: List<String>,             // eventId 或 RiskType.name
    val sourceEventId: String?,
    val grantedAt: Long,
    val expiresAt: Long?,
    val revokedAt: Long?,
    val consentStatus: ConsentStatus,          // NOT_GRANTED / ACTIVE / EXPIRED / REVOKED
    val updatedAt: Long,
    val note: String? = null,
    val isMock: Boolean = true,
)
```

- 模型层没有「实时屏幕」「原始录像」「完整浏览历史」字段，从数据结构上禁止这些共享范围。
- `covers(eventId, riskType)` 决定某条事件是否落在授权范围内；`effectiveStatus(now)` 保证撤销永远优先于有效。
- 撤销与解绑写入数据库，重启后不会恢复为 `ACTIVE`。

## 6. F02 / F04 / F09 页面实现情况

| 页面 | 实现内容 | 状态 |
| --- | --- | --- |
| F02 绑定老人 | 当前绑定状态卡、生成邀请码、邀请码展示（含有效期与模拟标记）、输入邀请码查询、对方身份预览、接受/拒绝、取消邀请、隐私说明 | 完成 |
| F04 关联关系 | 未绑定说明 + 入口；等待确认（发起方、等待对象、有效期、取消邀请）；已绑定（成员、状态、绑定时间、解除绑定）；已拒绝/已过期/已解除提示 + 重新邀请；解除二次确认并说明影响 | 完成 |
| F09 共享授权（家属） | 只读展示有效授权与已失效历史、范围说明、刷新授权、未绑定提示；无任何开启/修改授权按钮 | 完成 |

## 7. 老人端家属管理入口

`E12 我的` 新增「家庭关系与共享授权」入口 → `E12 家庭关系` 页面，页面内**两个相互独立的入口**：

1. `管理家庭关系`：生成邀请码、输入邀请码、待确认邀请、已绑定家属、解除家庭关系。
2. `管理风险共享授权`：选择风险类型与有效期、保存事先授权、撤销、查看授权历史。

两个功能没有合并成单一开关；页面底部固定说明「家庭绑定只是建立联系，不会自动共享风险记录」。

## 8. 邀请与双向确认流程

```
发起方生成邀请码（24h 有效，单次使用）
        ↓
接收方输入邀请码 → 校验（格式/存在/过期/已使用/自己发起）
        ↓
展示对方身份与邀请信息 → 接受 / 拒绝
        ↓
接受：写入 ACTIVE 关系 + confirmedAt，冻结邀请码
拒绝：关系 REJECTED，邀请终态，输入方收到明确结果
```

- 角色中立：老人与家属均可生成、均可输入（实测中老人发起、家属发起两条路径都已跑通）。
- 仅邀请的**被邀请方**可以接受；发起方无法自我接受（`SelfInvite` 拦截）。
- 接受前会检查是否已存在其他 PENDING/ACTIVE 关系，避免同时并存多条关系。
- 页面在邀请未确认时只显示「等待确认 / 待你确认」，不会提前显示绑定成功。

## 9. 授权与撤销流程

- 默认：绑定成功后 `consent_records` 为空，所有风险事件对家属不可见（实测数据库 0 条）。
- 按事件分享：E07 风险详情 →「分享给家人」→ 二次确认 → 生成 `EventShare`（7 天），只记录 `eventId` 与授权元数据，不复制事件正文、不上传画面。
- 事先授权：老人端勾选风险类型 + 选择 7 天 / 30 天 / 长期 → `ScopeGrant`；未选择类型时拒绝保存。
- 撤销：老人端「撤销」→ 写入 `REVOKED + revokedAt`，立即移入授权历史；家属端刷新后不再显示为有效。
- 解绑：解除关系时调用 `revokeForRelation`，该关系下全部授权批量置为 `REVOKED`。
- 界面明确提示「本轮撤销为本地模拟，尚未接入服务端强制校验」。

## 10. 本地持久化测试

使用模拟器 `sdk_gphone64_x86_64`（Android 16 / API 36）在**旧 P06 安装包**基础上覆盖安装 P07，不使用 `pm clear`。

| 项目 | 结果 |
| --- | --- |
| 迁移前数据库 | `user_version = 1`，表：`risk_events`（1 条记录 `mock-63ec5ade-…`） |
| 覆盖安装 + 启动 | 不崩溃，`user_version = 2`，新增 `family_relations` / `family_invites` / `consent_records` |
| P06 记录保留 | `risk_events` 仍为 1 条，事件 ID 与迁移前一致 |
| 关系持久化 | 生成邀请 → 拒绝 → 重新邀请 → 接受 → ACTIVE 均落库，`confirmedAt` 正确 |
| 授权持久化 | 范围授权与事件授权各 1 条，字段与范围正确 |
| 撤销持久化 | 撤销后 `consentStatus = REVOKED` 且 `revokedAt` 保留，重启后仍为 REVOKED |
| 解绑持久化 | 关系 `REVOKED`，两条授权同时 `REVOKED`，风险记录仍为 1 条 |
| 杀进程重启 | `am force-stop` 后重新启动，角色、绑定状态（未绑定）、授权状态均与退出前一致 |
| 覆盖升级数据保留 | 修复文案后二次 `adb install -r`，已绑定关系与授权仍在 |

## 11. 权限边界测试

| 场景 | 实测结果 |
| --- | --- |
| 错误邀请码 | 提示「没有找到这个邀请码」，不生成关系 |
| 已拒绝/已使用邀请码 | 提示「已被使用」，无法重复绑定 |
| 邀请码格式不符 | 仅允许 6 位字母数字，提示格式错误 |
| 未确认邀请 | 双方仅显示等待状态，不出现「已绑定」 |
| 已绑定但未授权 | 家属 F09 有效授权为空；数据库无授权记录 |
| 家属不能自行授权 | 家属端无任何授权写入入口，`ConsentViewModel` 不含写入方法 |
| 家属只读 | 家属 F09 仅展示授权与「刷新授权状态」 |
| 未绑定不可分享 | E07 分享区显示「未绑定家人」，无分享按钮 |
| 授权撤销后 | 家属端刷新后该授权进入已失效，不再显示为有效 |
| 解绑后分享失效 | E07 分享区回到未绑定态；数据库全部授权 REVOKED |
| 解绑不删本地记录 | `risk_events` 数量保持 1 条 |
| 重启不回滚 | 撤销/解绑状态重启后仍为撤销，不会恢复 ACTIVE |

## 12. APK 构建结果

- 命令：`./gradlew :app:assembleDebug`（工作目录 `apps/android`）
- 结果：`BUILD SUCCESSFUL`
- 产物：`apps/android/app/build/outputs/apk/debug/app-debug.apk`，11,617,244 字节，构建时间 2026-11-17 19:02
- 安装：`adb install -r app-debug.apk` 覆盖安装成功，保留原有数据库

## 13. 单元测试结果

- 命令：`./gradlew :app:testDebugUnitTest`
- 结果：`BUILD SUCCESSFUL`，共 **77** 个测试，0 失败、0 错误。

| 测试类 | 数量 |
| --- | --- |
| `FamilyRepositoryTest` | 13 |
| `ConsentRepositoryTest` | 12 |
| `ScreenCaptureStateTest` | 15 |
| `GuardStateMachineTest` | 11 |
| `RiskEventTest` | 10 |
| `RoleNavigationTest` | 9 |
| `DesignTokensTest` | 7 |

P07 覆盖的单元用例包括：邀请码生成/正确/错误/格式错误/自邀请/重复使用/过期、接受、拒绝、取消、解除、解绑撤销授权、默认未授权、事件授权幂等、范围授权、撤销、过期、未绑定拒绝授权、重启后状态恢复。

## 14. 尚未实现的真实服务端能力

1. 真实账号体系与登录态：当前老人/家属为同机切换的本地模拟身份（`isMock = true`），无服务端用户 ID。
2. 跨设备邀请与关系同步：邀请码、接受、拒绝、解除均只作用于本机数据库，另一端不会实时收到。
3. 服务端对象级访问控制（OLAC）：前端隐藏入口只是交互层保护，未授权访问仍需要服务端强制校验。
4. 风险事件真实同步：E07 分享仅记录「事件 ID + 授权」，不传输事件正文、不上传截图/录像，跨设备获取将在 P08 及后端联调实现。
5. 服务端撤销强制生效与审计：当前撤销为本地立即生效，服务端吊销与审计日志待后端接入。
6. 授权到期服务端回收与推送提醒：过期判断依赖本机时间，未做服务端定时任务。
7. 邀请码防爆破、频控、短信/扫码邀请等生产级风控能力。

## 15. P07 验收结论

- 家庭关系与共享授权在数据模型、仓库、ViewModel、界面四层完全分离，绑定不会自动扩权。
- 双向确认、邀请有效期、单次使用、拒绝/取消/解除/过期等状态均按规格实现并有单元测试覆盖。
- 老人端在 E12 提供独立「家庭关系」与「风险共享授权」两个入口，支持授权历史与随时撤销。
- 家属端 F02 / F04 / F09 均可在模拟器上完成完整流程，授权页面只读。
- Room v1 → v2 迁移正确，P06 风险记录完整保留，撤销与解绑状态在重启后不回滚。
- `assembleDebug` 与 `testDebugUnitTest` 均通过（77 项测试 0 失败），APK 已在模拟器覆盖安装验证。
- 结论：**P07 通过本地模拟验收**；真实跨设备同步与服务端强制鉴权待后续阶段实现。

> 本轮仅执行 P07，未修改 Web 管理后台业务，未进入 P08。所有家庭关系与授权数据均为本地模拟数据。
