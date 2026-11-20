# 银盾智鉴 · P13 全项目功能覆盖矩阵

> 文档编号：P13-ACCEPTANCE-MATRIX
> 日期：2026-11-20
> 判定口径：REAL 真实实现 / MOCK 模拟实现 / PARTIAL 部分实现 / PENDING 未实现 / BLOCKED 阻断 / N/A 不适用
> 说明：标注「本地」表示功能在本机真实运行，但没有真实远程后端；标注「演示」表示数据为预制 Mock。

## 1. 老人端 E01–E15

| 编号 | 功能 | 代码位置 | 数据来源 | 实现方式 | 测试方式 | 结果 | 尚存问题 |
|---|---|---|---|---|---|---|---|
| E01 | 首次引导 | `ui/onboarding/OnboardingScreen.kt` | DataStore 本地标记 | REAL（本地） | 单元测试 + 代码审查 | 通过 | 未在本轮设备复测 |
| E02 | 角色 / 登录 | `ui/onboarding/RoleSelectionScreen.kt` | 本地 DataStore | REAL（本地角色选择，无账号认证） | `RoleNavigationTest` | 通过 | 无真实登录与身份校验 |
| E03 | 守护首页 | `ui/elder/ElderGuardScreen.kt` | `FakeGuardService` | PARTIAL（界面真实，守护为模拟） | 单元测试 | 通过 | 不采集屏幕、不做 AI 推理 |
| E04 | 授权说明 | `ui/elder/ElderAuthorizationScreen.kt` | 本地文案 + 系统授权 | REAL（界面与系统授权入口） | 代码审查 | 通过 | MediaProjection 真机授权未在本轮复测 |
| E05 | 运行状态 | `ui/elder/ElderGuardStatusScreen.kt`、`guard/capture/*` | FakeGuardService / 真实采集 PoC | PARTIAL（P04 模拟 + P05 PoC 并存） | 单元测试 | 通过 | 真实采集仅 PoC，无识别链路 |
| E06 | 风险提醒组件 | `ui/components/RiskAlert*.kt` | 本地模拟 RiskEvent | MOCK | 代码审查 | 通过 | 无真实端侧检测触发 |
| E07 | 风险详情 | `ui/elder/ElderRiskDetailScreen.kt` | 本地 RiskEvent | PARTIAL（页面真实，事件模拟） | 单元测试 | 通过 | 共享仅写本地仓库 |
| E08 | 风险记录 | `ui/elder/ElderRiskRecordsScreen.kt` + Room | 本机 Room | REAL（本地持久化） | 单元测试 | 通过 | 无云端同步 |
| E09 | 语音帮助 | `ui/components/VoiceAnnouncer.kt` | Android TTS | PARTIAL（仅播报，无规则问答） | 代码审查 | 通过 | 无独立语音帮助页与问答 |
| E10 | 反诈知识 | — | — | PENDING | — | 未实现 | 未分配实施轮次 |
| E11 | 情景测试 | — | — | PENDING | — | 未实现 | 未分配实施轮次 |
| E12 | 我的 | `ui/elder/ElderProfileScreen.kt` | 本地状态 | REAL（本地） | 单元测试 | 通过 | 无账号体系 |
| E13 | 守护设置 | `ui/elder/ElderGuardSettingsScreen.kt` | DataStore | REAL（本地） | 单元测试 | 通过 | 仅本地偏好 |
| E14 | 隐私与授权 | `ui/elder/ElderPrivacyScreen.kt` | DataStore + ConsentRepository | REAL（本地撤销） | 单元测试 | 通过 | 撤销只影响本机模拟共享 |
| E15 | 反馈帮助 | — | — | PENDING | — | 未实现 | 无反馈工单入口 |

老人端导航：底部「守护 / 记录 / 我的」，二级页 E04/E05/E07/E13/E14 独立注册，见 `navigation/ElderTab.kt`、`ElderSubPage.kt`、`ElderAppScreen.kt`。

## 2. 家属端 F01–F12

| 编号 | 功能 | 代码位置 | 数据来源 | 实现方式 | 测试方式 | 结果 | 尚存问题 |
|---|---|---|---|---|---|---|---|
| F01 | 家属登录 | 角色选择入口 | 本地 DataStore | PARTIAL | 单元测试 | 通过 | 无真实登录 / 会话 |
| F02 | 绑定老人 | `ui/family/FamilyBindingScreen.kt` | `FakeFamilyRepository` | MOCK | 单元测试 | 通过 | 邀请码不跨设备 |
| F03 | 家庭首页 | `ui/family/FamilyHomeScreen.kt` | Fake 仓库 | MOCK | 单元测试 | 通过 | 无服务端家庭数据 |
| F04 | 关联关系 | `ui/family/FamilyRelationScreen.kt` | Fake 仓库 | MOCK | 单元测试 | 通过 | 解绑不影响真实服务 |
| F05 | 风险列表 | `ui/family/FamilyRiskListScreen.kt` | `FakeSharedRiskEventRepository` | MOCK | 单元测试 | 通过 | 仅本地共享事件 |
| F06 | 风险详情 | `ui/family/FamilyRiskDetailScreen.kt` | Fake 共享事件 | MOCK | 单元测试 | 通过 | 不展示实时屏幕与原始录像 |
| F07 | 协同处理 | `ui/family/FamilyHandlingScreen.kt` | Fake 仓库 | MOCK | 单元测试 | 通过 | 处理状态不同步到老人真机 |
| F08 | 处理详情 | `ui/family/FamilyHandlingDetailScreen.kt` | Fake 仓库 | MOCK | 单元测试 | 通过 | 同上 |
| F09 | 共享授权 | `ui/family/FamilyConsentScreen.kt` | `FakeConsentRepository` | MOCK | 单元测试 | 通过 | 只读展示，撤销不跨进程 |
| F10 | 反诈知识 | — | — | PENDING | — | 未实现 | 未分配实施轮次 |
| F11 | 消息中心 | `ui/family/FamilyMessageCenterScreen.kt` | `FakeFamilyMessageRepository` | MOCK | 单元测试 | 通过 | 无推送通道 |
| F12 | 个人中心 | `ui/family/FamilyProfileScreen.kt` | 本地状态 | PARTIAL | 单元测试 | 通过 | 无账号设置 |

家属端导航：底部「家庭 / 风险 / 协同 / 我的」，与老人端使用独立 `FamilyAppScreen` 与路由枚举，未复用老人底部导航。

## 3. 管理端 A01–A10

| 编号 | 模块 | 代码位置 | 数据来源 | 实现方式 | 测试方式 | 结果 | 尚存问题 |
|---|---|---|---|---|---|---|---|
| A01 | 工作台 | `views/WorkbenchView.vue` | 前端 Mock | MOCK | 构建 + P12 浏览器实测 | 通过 | 统计卡标注「演示」，无真实运营接口 |
| A02 | 用户管理 | `views/user/UserManagementView.vue` | Mock 数组 | MOCK | 构建 + 浏览器实测 | 通过 | 无用户后端与真实绑定关系 |
| A03 | 设备管理 | `views/device/DeviceManagementView.vue` | Mock 数组 | MOCK | 构建 | 通过 | 无设备上报接口 |
| A04 | 数据集管理 | `views/dataset/*` | Mock 业务数据；后端仅有快照与资格接口 | PARTIAL | 构建 + P12 浏览器实测 | 通过 | 列表 / 详情 / 标注无真实 API，后端无 `/admin/datasets` |
| A05 | 训练管理 | `views/training/*` + `/admin/training-tasks/*` | FastAPI + Worker | REAL | pytest 19 + 真实 Smoke Test | 通过 | 仅 CPU Smoke Test，无 GPU 调度 |
| A06 | 模型版本 | `views/model/*` + `/admin/models/*` | FastAPI + P11-R 产物 | REAL | pytest + 真实产物导入 | 通过 | 无真实反诈模型可发布 |
| A07 | 规则管理 | `views/rule/RuleManagementView.vue` | 无 | PENDING（占位页） | 构建 | 未实现 | 仅菜单占位 |
| A08 | 知识库 | `views/knowledge/KnowledgeBaseView.vue` | 无 | PENDING（占位页） | 构建 | 未实现 | 仅菜单占位 |
| A09 | 风险反馈 | `views/feedback/RiskFeedbackView.vue` | 无 | PENDING（占位页） | 构建 | 未实现 | 仅菜单占位 |
| A10 | 系统管理 | `views/system/SystemManagementView.vue` | 本地状态 | PARTIAL | 构建 | 通过 | 模拟管理员，无真实 RBAC / 审计后端 |

通用交互：列表页统一使用 `DataTable`（Loading / Empty / Error / 重试）、`FilterPanel`、`Pagination`；发布、撤回、回滚使用二次确认。真实模式下 `IS_MOCK=false`，由 Vite 代理 `/api` 到 FastAPI。

## 4. 跨端与跨模块链路

| 链路 | 状态 | 证据 | 说明 |
|---|---|---|---|
| 数据集快照 → 训练任务 → Worker | REAL | `dataset_eligibility.py`、`training_service.py`、`worker_runner.py` | 资格失效返回 422；Worker 执行前二次校验 |
| TrainingTask → ModelArtifact → ModelVersion → Release | REAL | `training_results.manifest_path`、`model_repository.import_from_training_task` | 任务 ID、数据版本、路径、SHA-256 可追溯 |
| 老人端 RiskEvent → 家属端 | MOCK | `FakeSharedRiskEventRepository`、`FakeConsentRepository` | 仅本机内存，无两台手机远程同步 |
| 管理端 → 用户 / 设备 / 规则 / 知识 / 反馈 | MOCK / PENDING | 对应 Mock 数据或占位页 | 不得描述为真实运营能力 |
