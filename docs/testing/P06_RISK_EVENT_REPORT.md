# P06 交付报告：风险提醒、风险详情与本地风险事件管理

- 阶段：P06（E06 风险提醒 / E07 风险详情 / E08 风险记录）
- 日期：2026-11-17
- 范围声明：本轮只实现 P06。未开发家属端风险同步（F05/F07）与管理后台风险反馈；未实现自动 OCR 或真实多模态诈骗识别；未进入 P07。

---

## 1. 新增文件清单

| 文件 | 说明 |
| --- | --- |
| `apps/android/app/src/main/java/com/silvershield/app/guard/risk/FakeRiskEventRepository.kt` | 模拟事件来源接口 `RiskEventSource` 与 Fake 实现 |
| `apps/android/app/src/test/java/com/silvershield/app/guard/risk/RiskEventTest.kt` | RiskEvent 模型 / Fake 源 / 双筛选单元测试（10 项，本轮重写） |

> 说明：P06 初版草稿中的 `RiskEvent.kt`、`RiskEventRepository.kt`、`RiskEventStore.kt`、`RoomRiskEventRepository.kt`、`RiskRecordsViewModel.kt`、`RiskDetailViewModel.kt`、`RiskPresentation.kt`、`RiskAlertCard.kt`、`ElderRecordsScreen.kt`、`ElderRiskDetailScreen.kt` 在本轮按正式规格**重写**（文件路径已存在，不计入新增）。

## 2. 修改文件清单

| 文件 | 变更 |
| --- | --- |
| `guard/risk/RiskEvent.kt` | 重写为统一风险事件模型（18 字段 + 6 个枚举 + `MockRiskEvents`） |
| `guard/risk/RiskEventRepository.kt` | 仓库接口（seedMockEvents/addMockEvent/updateHandleStatus/delete/clear）+ `RiskLevelFilter` + 类型筛选扩展 |
| `guard/risk/RiskEventStore.kt` | Room 实体、DAO、`SilverShieldDatabase`（含处理状态 UPDATE 语句） |
| `guard/risk/RoomRiskEventRepository.kt` | Room 实现，注入 Fake 事件源，示例数据只播种一次 |
| `ui/components/RiskPresentation.kt` | 领域等级 → UI 等级映射、处理状态文案、时间格式、模拟数据标签 |
| `ui/components/RiskAlertCard.kt` | E06 轻度 / 重点两档提醒，语音入口可按设置隐藏 |
| `ui/elder/RiskRecordsViewModel.kt` | 等级 + 类型双筛选、加载/失败状态、重试、删除/清空/新增 |
| `ui/elder/RiskDetailViewModel.kt` | 单条事件订阅 + 标记已处理 + 一次性提示 |
| `ui/elder/ElderRecordsScreen.kt` | E08 页面：双筛选、ErrorState、删除/清空二次确认、模拟标记 |
| `ui/elder/ElderRiskDetailScreen.kt` | E07 页面：基本信息、线索、证据、建议、信息不足、处理状态、分享前置 |
| `ui/elder/ElderAppScreen.kt` | 接入新 ViewModel 状态、E13 开关联动、E07 路由参数 |
| `navigation/AppRoutes.kt` | 新增 `ELDER_RISK_DETAIL`、`ARG_RISK_ID`、`elderRiskDetailRoute()` |
| `navigation/ElderSubPage.kt` | 新增 `RiskDetail`（E07）与带路径参数的路由匹配 |
| `app/src/main/AndroidManifest.xml` | 增加 TTS 服务 `<queries>`（不申请录音权限） |

## 3. RiskEvent 数据结构说明

`apps/android/app/src/main/java/com/silvershield/app/guard/risk/RiskEvent.kt`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `eventId` | String | 事件唯一标识 |
| `occurredAt` | Long | 发生时间（毫秒） |
| `riskType` | RiskType | 冒充公检法 / 虚假投资理财 / 假冒客服退款 / 刷单返利 / 暂无法判断 |
| `riskLevel` | RiskLevel | 高风险 / 中风险 / 低风险 / 待评估 |
| `title` / `description` | String | 标题与风险说明 |
| `clues` | List\<String\> | 风险线索 |
| `evidenceSummary` | String? | 证据摘要，无证据时为 null |
| `availableModalities` | List\<RiskModality\> | 已获取证据模态（屏幕文字 / 声音 / 其他模态） |
| `missingModalities` | List\<RiskModality\> | 尚未获取的模态，UI 显示"未获取" |
| `suggestedActions` | List\<String\> | 处理建议 |
| `modelVersion` | String | 模型版本，模拟事件为 `fake-risk-1.0` |
| `sourceType` | RiskSourceType | 模拟数据 / 屏幕采集 / 手动录入 / 未知来源 |
| `syncStatus` | RiskSyncStatus | 仅本机 / 待同步 / 已同步（本轮全部仅本机） |
| `handleStatus` | RiskHandleStatus | 待查看 / 待核实 / 已处理（**用户处理状态，与 AI 判断分离**） |
| `confidence` | Float? | 仅在明确依据时记录；**不作为诈骗概率展示**，模拟事件一律 null |
| `isMock` | Boolean | 是否模拟事件 |

派生属性：`isUrgent`（高风险）、`hasEnoughEvidence`、`isInformationInsufficient`。

等级枚举说明：V1.3 需求文档未规定正式风险等级标准，本轮按产品基线定义内部可扩展枚举（高风险/中风险/低风险/待评估），**未声称其为 V1.3 已规定内容**；接入后端后以接口字段为准。

事件覆盖状态：正常风险事件（高风险）、信息不足事件（Unknown + 无线索/无证据）、待核实事件（`handleStatus = NeedsVerification`）、已处理事件（`handleStatus = Handled`）。

## 4. Room 数据库实现说明

`apps/android/app/src/main/java/com/silvershield/app/guard/risk/RiskEventStore.kt`

- 数据库：`SilverShieldDatabase`（`silver_shield.db`，version = 1，`exportSchema = false`）。
- 表：`risk_events`，主键 `eventId`；列表字段以换行分隔存储；枚举以 `name` 存储，反序列化失败回落安全默认值。
- DAO：`observeAll()`（按 `occurredAt DESC`）、`upsert()`、`updateHandleStatus(eventId, status)`、`deleteById()`、`clear()`、`count()`。
- 处理状态更新使用独立 UPDATE 语句，不重写整行，确保 AI 判断字段不被覆盖。
- 迁移说明（如实记录）：该表在 P06 才正式引入，此前仅存在于未交付的初版草稿中，因此未编写迁移；模拟器测试前执行 `adb shell pm clear com.silvershield.app` 清除了草稿库，正式安装包首次启动自动建表。

## 5. FakeRiskEventRepository 实现说明

- `RiskEventSource` 接口：`initialEvents()` + `suspend next()`，为后续真实 OCR / AI 推理实现预留同一注入点。
- `FakeRiskEventRepository`：内部序号轮换三类模拟事件（冒充公检法 / 虚假投资理财 / 假冒客服退款），事件 id 带 UUID，`isMock = true`、`sourceType = Mock`、`syncStatus = LocalOnly`、`confidence = null`。
- `MockRiskEvents.initial()` 提供三条固定示例：高风险待查看、信息不足待核实、中风险已处理。
- 真实数据与 Mock 数据的区分：`isMock` 字段 + 列表/详情"模拟数据"标签 + 详情页模型版本说明，页面不会把模拟事件描述为真实识别结果。

## 6. E06 / E07 / E08 完成情况

### E06 风险提醒
- 轻度提醒：紧凑布局、警示色 + Info 图标、标题 + "查看详情"，不使用恐吓性语言。
- 重点提醒：危险色 + Warning 图标、`重点提醒 · 类型 · 等级` 文字标签、"查看详情"与"语音提醒"入口；等级同时以图标与文字表达，不依赖颜色。
- 点击提醒进入 E07（`elder/risk/{riskId}`）。
- 语音使用系统 TTS；E13 开关联动：风险提醒关闭不显示提醒卡；语音提醒关闭时保留文字、隐藏语音入口；不会擅自开启语音。
- TTS 初始化失败或合成失败时通过 Snackbar 给出文字提示，不崩溃。

### E07 风险详情
- 基本信息：标题、类型 + 等级徽章、发生时间、当前处理状态。
- 风险线索：编号列表；无线索时显示"暂未识别到明确线索"并提示"没有发现线索不代表内容安全"。
- 证据摘要：区分"已获取"与"尚未获取"模态，缺失模态显示"未获取"，无可用证据时明确说明，不编造。
- 处理建议：按事件提供可执行建议。
- 信息不足：显示"当前信息不足，暂时无法作出可靠判断，请进一步核实。"，全页无"已确认安全"，不展示模型分数。
- 处理状态：可"标记为已处理"，仅更新 `handleStatus`。
- 家庭分享：入口保留，点击弹出前置说明；未授权时明确"不会分享任何内容"；已授权时说明服务尚未接入、本次不会真正发送；两种路径均不宣称分享成功，不涉及任何上传。

### E08 风险记录
- Room 列表、按发生时间倒序、等级筛选（全部/高/中/低/待评估）、类型筛选（全部类型 + 4 类）。
- 点击记录进入 E07。
- 删除单条、清空全部，均有二次确认，真实作用于 Room。
- 空数据 EmptyState；读取失败 ErrorState（含重试）。
- 模拟记录显示"模拟数据"标签。

## 7. 本地事件数据闭环测试结果（模拟器实测）

设备：Android 模拟器 `sdk_gphone64_x86_64`，API 35，1080×2400。

| 步骤 | 结果 |
| --- | --- |
| 首次进入 E08 自动播种 3 条示例 | 通过（高风险待查看 / 信息不足待核实 / 中风险已处理） |
| 点击「模拟识别到一条风险（测试）」 | 通过，新增记录并提示"已写入一条模拟风险事件" |
| 详情页"标记为已处理" | 通过，返回列表显示"已处理"，顶部提醒自动切换到下一条未处理记录 |
| 按高风险筛选 | 通过，仅显示 2 条高风险记录 |
| 按"虚假投资理财"类型筛选 | 通过，仅显示 1 条 |
| 删除单条（二次确认） | 通过，列表减少一条 |
| 清空全部（二次确认） | 通过，显示"暂无风险记录"空态 |
| 强制停止后重启 | 通过：处理状态保留；清空后重启仍为空，示例数据未回填 |

## 8. Android APK 构建结果

- `./gradlew :app:assembleDebug` → **BUILD SUCCESSFUL in 2m 53s**
- 产物：`apps/android/app/build/outputs/apk/debug/app-debug.apk`，**11,281,128 字节**
- 安装：`adb install -r` 成功，启动正常。

## 9. 单元测试结果

命令：`./gradlew :app:testDebugUnitTest "-Pkotlin.compiler.execution.strategy=in-process"`

| 测试类 | 用例数 | 失败 |
| --- | --- | --- |
| `RiskEventTest`（P06） | 10 | 0 |
| `ScreenCaptureStateTest`（P05） | 15 | 0 |
| `GuardStateMachineTest`（P04） | 11 | 0 |
| `RoleNavigationTest`（P03） | 9 | 0 |
| `DesignTokensTest`（P02） | 7 | 0 |
| **合计** | **52** | **0** |

P06 用例覆盖：实体往返映射、未知枚举回落、紧急判定、信息不足判定、处理状态与 AI 判断解耦、模拟事件标记与无置信度、示例三态覆盖、Fake 源轮换、等级筛选、类型筛选。

## 10. 模拟器实际测试结果

- E06：轻度提醒与重点提醒均正确显示，点击进详情正常；关闭"风险提醒"后提醒卡消失而列表保留；关闭"语音提醒"后重点提醒仍显示文字与详情入口、语音按钮消失。
- TTS：模拟器语音引擎存在但合成失败（logcat `Synthesis failure`），界面正确提示"语音播报失败，请检查系统语音包后重试"，应用未崩溃；"无可用引擎"分支为代码保证，未单独构造该环境实测。
- E07：高风险页与信息不足页均验证；证据区正确显示"屏幕文字"已获取、"声音/其他模态"未获取；信息不足文案逐字符合要求。
- 分享：默认未授权，弹出"尚未获得家庭共享授权"，不宣称成功。
- E08 + Room 闭环：见第 7 节，全部通过。
- 与 P04 解耦：进入 E07/E08 不会启动屏幕采集（本页无任何采集调用，守护状态未变化）；守护未开启时不会产生真实采集事件。本轮未做自动 OCR / 真实多模态分类。

未覆盖项（如实说明）：ErrorState 的实际触发需数据库读取异常，模拟器未构造该故障；单元测试未包含 Room 仪器测试（删除/清空由模拟器手工验证覆盖）。

## 11. 尚未实现的真实 AI 能力

- 屏幕文本 OCR、音频转写与多模态风险分类；
- 真实风险等级判定阈值与置信度校准（当前 `confidence` 恒为 null）；
- 实时风险事件自动产生（当前仅手工注入模拟事件）；
- 家属端风险同步（F05/F07）、家庭共享传输通道（P07/P08）；
- 管理后台风险反馈与策略下发。

P05 屏幕采集只提供帧数据，**不能视为已完成 AI 诈骗识别**。

## 12. P06 验收结论

E06、E07、E08 与统一 RiskEvent 数据管理已按本轮规格完成；UI → ViewModel → Repository → Room 分层清晰，页面不直接访问数据库；删除/清空真实作用于数据库并在重启后保持；模拟数据全程显著标记；合规文案（信息不足、不宣称安全、不伪造分享成功、不上传画面）已落实。构建与单元测试均通过，模拟器手工闭环验证通过。

结论：**P06 达到本轮验收标准**，其中 ErrorState 故障注入与 Room 仪器测试列为待补充项。本轮未进入 P07。
