# P08 任务清单

## T1 数据模型与持久化
- [x] 新建 `family/FamilyRiskModels.kt`：ShareStatus / HandlingStatus / FamilyMessageType / MessageDeliveryState / FamilyNetworkMode / SharedRiskEvent / HandlingRecord / FamilyMessage / ShareAccess / SharedEventView / FamilyEventItem。
- [x] 新建 `family/FamilyRiskStore.kt`：3 个 Entity、3 个 DAO、`FamilyRiskLocalStore`、`RoomFamilyRiskLocalStore`、`InMemoryFamilyRiskLocalStore`。
- [x] 修改 `guard/risk/RiskEventStore.kt`：Database version=3、注册 Entity/DAO、新增 `MIGRATION_2_3`（仅建新表与索引）。

## T2 仓库与权限校验
- [x] 新建 `family/SharedRiskEventRepository.kt`：接口 + `FakeSharedRiskEventRepository`（sync 物化分享、三层校验、observeAccess、markViewed）。
- [x] 新建 `family/HandlingRecordRepository.kt`：接口 + `FakeHandlingRecordRepository`（追加历史、权限校验、previousStatus）。
- [x] 新建 `family/FamilyMessageRepository.kt`：接口 + `FakeFamilyMessageRepository`（确定性消息 ID、网络模式、已读持久化）。

## T3 ViewModel
- [x] 新建 `family/FamilyRiskViewModel.kt`：首页/列表/协同清单/消息中心状态与统计。
- [x] 新建 `family/FamilyEventViewModel.kt`：按 shareId 加载详情、标记已查看、更新处理状态、授权撤销即时拦截。

## T4 导航与页面
- [x] 修改 `navigation/AppRoutes.kt`：F06/F07/F08/F11 路由与构造器。
- [x] 修改 `ui/family/FamilySubPage.kt`：新增二级页并支持 `{arg}` 路由匹配。
- [x] 新建 `ui/family/FamilyRiskComponents.kt`：事件卡、协同状态标签、统计卡等复用组件。
- [x] 重写 `ui/family/FamilyHomeScreen.kt`（F03）。
- [x] 重写 `ui/family/FamilyRisksScreen.kt`（F05，四组筛选 + 五种空态）。
- [x] 新建 `ui/family/FamilyRiskDetailScreen.kt`（F06）。
- [x] 重写 `ui/family/FamilyCooperationScreen.kt`（F07 待协同清单）。
- [x] 新建 `ui/family/FamilyCooperateScreen.kt`（F07 联系老人 + 状态更新 + 备注）。
- [x] 新建 `ui/family/FamilyHandlingDetailScreen.kt`（F08 时间线）。
- [x] 新建 `ui/family/FamilyMessagesScreen.kt`（F11 消息中心）。
- [x] 修改 `ui/family/FamilyAppScreen.kt`：注入仓库/ViewModel、注册路由。

## T5 测试
- [x] 新增 `SharedRiskEventRepositoryTest.kt`（7 项共享事件场景）。
- [x] 新增 `HandlingRecordRepositoryTest.kt`（5 项协同处理场景）。
- [x] 新增 `FamilyMessageRepositoryTest.kt`（消息、离线/延迟、已读持久化）。
- [x] 执行 `:app:testDebugUnitTest` 并记录实际通过/失败结果。

## T6 构建与模拟器实测
- [x] 执行 `:app:assembleDebug`，记录 APK 路径与结果。
- [x] 模拟器实测：分享→可见→查看→协同→撤销→拦截→重启持久化；确认 P06 记录保留。

## T7 交付
- [x] 输出 `docs/testing/P08_FAMILY_RISK_REPORT.md`（15 项）。
- [x] 停止，不进入 P09。
