# P07 任务清单

## T1 数据模型与持久化
- [x] 新建 `family/FamilyModels.kt`：枚举、FamilyRelation、FamilyInvite、ConsentRecord、LocalFamilyAccounts、InviteLookup。
- [x] 新建 `family/FamilyStore.kt`：3 个 Entity、3 个 DAO、`FamilyLocalStore`、`RoomFamilyLocalStore`、`InMemoryFamilyLocalStore`。
- [x] 修改 `guard/risk/RiskEventStore.kt`：Database version=2、新 Entity/DAO、`MIGRATION_1_2` 且保留 `risk_events`。

## T2 仓库与业务规则
- [x] 新建 `family/FamilyRepository.kt`：接口 + `FakeFamilyRepository`（邀请码、有效期、单次使用、双向确认、解除）。
- [x] 新建 `family/ConsentRepository.kt`：接口 + `FakeConsentRepository`（默认关闭、事件/范围授权、撤销、过期、解绑失效）。

## T3 ViewModel
- [x] 新建 `family/FamilyViewModel.kt`（老人/家属共用，按 role 限制动作）。
- [x] 新建 `family/ConsentViewModel.kt`（家属无写入方法）。

## T4 家属端页面
- [x] 新建 `ui/family/FamilySubPage.kt`；修改 `FamilyAppScreen.kt` 支持二级页与返回。
- [x] 新建 `ui/family/FamilyBindScreen.kt`（F02）。
- [x] 新建 `ui/family/FamilyRelationScreen.kt`（F04）。
- [x] 新建 `ui/family/FamilyConsentScreen.kt`（F09）。
- [x] 修改 `FamilyProfileScreen.kt` 增加三个入口与绑定状态摘要。
- [x] 修正邀请方向文案（发起方/被邀请方按 `initiatedBy` 展示），邀请码编辑时清空旧预览。

## T5 老人端入口与授权页
- [x] 修改 `navigation/AppRoutes.kt`、`ElderSubPage.kt`。
- [x] 修改 `ElderProfileScreen.kt` 增加家庭关系管理入口。
- [x] 新建 `ui/elder/ElderFamilyScreen.kt`、`ElderFamilyRelationScreen.kt`、`ElderConsentScreen.kt`。
- [x] 修改 `ElderAppScreen.kt` 注册路由并注入仓库。

## T6 E07 按事件分享
- [x] 修改 `RiskDetailViewModel.kt`：注入 ConsentRepository，暴露分享状态与 `shareEvent()`。
- [x] 修改 `ElderRiskDetailScreen.kt`：分享确认、已分享/撤销、未绑定/未授权提示。
- [x] 修改 `ElderAppScreen.kt` 详情页调用参数。

## T7 测试与构建
- [x] 新增 `family/FamilyRepositoryTest.kt`、`family/ConsentRepositoryTest.kt`。
- [x] 执行 `:app:testDebugUnitTest` 与 `:app:assembleDebug`，记录实际结果。
- [x] 模拟器实测绑定/授权/撤销/解绑/重启持久化，确认 P06 记录保留。

## T8 交付
- [x] 输出 `docs/testing/P07_FAMILY_CONSENT_REPORT.md`（15 项）。
- [x] 停止，不进入 P08。
