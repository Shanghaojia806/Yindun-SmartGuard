# P10 · 数据集管理完整前端与数据标注工作台 · 交付报告

> 项目：银盾智鉴
> 工程：`apps/admin-web`（独立 Vue 3 Web 管理后台，不属于 Android App）
> 阶段：P10（仅 A04 数据集管理）
> 日期：2026-11-19
> 状态：**已完成，待验收**（2026-11-19 完成数据拆分专项修复，详见第 17 节）

---

## 1. 新增文件清单

| 文件 | 说明 |
|---|---|
| `apps/admin-web/src/types/dataset.ts` | A04 全部 TypeScript 类型（数据集、样本、标注、审核、质检、去重、拆分、版本、导入） |
| `apps/admin-web/src/api/mock/datasetData.ts` | 数据集演示仓库：6 个数据集、186 条样本、9 个预置标签、版本/质检/去重记录 |
| `apps/admin-web/src/api/mock/datasetAdapter.ts` | A04 Mock Adapter：训练资格、质检、去重、拆分、版本、导入全部规则 |
| `apps/admin-web/src/api/dataset.ts` | A04 API Service（唯一页面调用入口，Mock/真实接口由环境变量切换） |
| `apps/admin-web/src/components/dataset/DatasetWorkspaceHeader.vue` | 工作台头部：数据集摘要 + 标注/质检/去重/拆分/版本子导航 |
| `apps/admin-web/src/components/dataset/RiskLabelPicker.vue` | 多标签选择器（9 个预置演示标签，可扩展） |
| `apps/admin-web/src/components/dataset/EvidenceEditor.vue` | 证据位置编辑器（屏幕文本 / 视频时间 / 音频时间 / 页面区域 + 文字摘要） |
| `apps/admin-web/src/components/dataset/AnnotationPanel.vue` | 标注面板：多标签 + 证据 + 说明 + 状态 + 保存/提交/演示复核 |
| `apps/admin-web/src/views/dataset/DatasetListView.vue` | A04 数据集列表（统计、筛选、分页、详情抽屉、创建数据集） |
| `apps/admin-web/src/views/dataset/DatasetImportView.vue` | 五步数据导入向导 |
| `apps/admin-web/src/views/dataset/DatasetAnnotationView.vue` | 数据标注工作台 |
| `apps/admin-web/src/views/dataset/DatasetQualityView.vue` | 数据质量检查工作台 |
| `apps/admin-web/src/views/dataset/DatasetDedupeView.vue` | 数据去重（模拟哈希分组 + 人工确认 + 操作记录） |
| `apps/admin-web/src/views/dataset/DatasetSplitView.vue` | 训练/验证/测试拆分工作台 |
| `apps/admin-web/src/views/dataset/DatasetVersionsView.vue` | 数据集版本管理与版本差异 |
| `docs/testing/P10_DATASET_REPORT.md` | 本报告 |

## 2. 修改文件清单

| 文件 | 修改内容 |
|---|---|
| `apps/admin-web/src/components/StatusTag.vue` | 扩展数据集相关状态映射（数据集状态、标注状态、审核状态、许可状态、质检结果、重复状态、版本状态），沿用 P09 组件结构，未新建替代组件 |
| `apps/admin-web/src/router/index.ts` | A04 主路由指向 `DatasetListView.vue`；新增 `/admin/datasets/import`、`/admin/datasets/:id/annotation\|quality\|dedupe\|split\|versions` 子路由（`meta.moduleCode = 'A04'`） |
| `apps/admin-web/src/layouts/AdminLayout.vue` | 菜单高亮按模块路径前缀匹配；面包屑按 `meta.moduleCode` 支持子页面；页脚更新为 P10 口径 |
| `apps/admin-web/src/views/admin/DatasetManagementView.vue` | **删除**（A04 占位页，已由 `views/dataset/` 完整实现替代，删除前已确认无其他引用） |

未新建 Vue 工程，未覆盖 P09 的 DataTable / Pagination / StatusTag / DetailDrawer / PageHeader / ConfirmDialog / LoadingState / ErrorState，未修改 Android 工程，未改变 API 分层架构。

## 3. 数据集数据模型

`src/types/dataset.ts` 全部为严格 TypeScript 类型（无 `any`），字段在需求基础上按实际需要补充了少量必要字段：

| 类型 | 关键字段 |
|---|---|
| `Dataset` | datasetId、datasetName、description、datasetType、totalSamples、annotatedSamples、approvedSamples、datasetVersion、datasetStatus、createdAt、updatedAt、createdBy |
| `DatasetSummary` | 继承 Dataset，追加数据集层聚合：annotationRate、reviewRate、pendingReviewSamples、qualityPassedSamples、blockedSamples、eligibilityRate |
| `DatasetSample` | sampleId、datasetId、sampleName、sourceType、sourceName、sourceDescription、sourceObtainedAt、sourceEvidence、sourceLicense、riskCategories、evidenceLocations、annotationStatus、reviewStatus、duplicateStatus、licenseStatus、sampleStatus、qualityResult、fileHash、formatValid、createdAt、updatedAt |
| `AnnotationRecord` | annotationId、sampleId、annotatorId、riskCategories、evidenceLocations、annotationNotes、annotationStatus、createdAt、updatedAt |
| `ReviewRecord` | reviewId、sampleId、reviewerId、reviewResult、reviewNotes、reviewedAt |
| `DatasetVersion` | versionId、datasetId、versionNumber、sampleCount、trainCount、validationCount、testCount、versionDescription、createdAt、createdBy、versionStatus、changeLog |
| 辅助类型 | `DatasetType`、`DatasetStatus`、`AnnotationStatus`、`ReviewStatus`、`DuplicateStatus`、`LicenseStatus`、`SourceType`、`QualityResult`、`QualityIssueType`、`VersionStatus`、`SplitStrategy`、`RiskLabel`、`EvidenceLocation`、`QualityIssue`、`QualityReport`、`DedupeGroup`、`DedupeRecord`、`DedupeOverview`、`SplitConfig`、`SplitResult`、`SplitPreviewRow`、`TrainingEligibility`、`ImportRow`、`ImportSourceDraft`、`ImportLicenseDraft`、`ImportSummary`、`ImportResult`、`VersionCompareRow`、`DatasetOverviewStats`、`DatasetOptions`、`DatasetListQuery`、`SampleQuery`、`CreateDatasetInput`、`CreateVersionInput`、`AnnotationDraft` |

多标签标注：`riskCategories: string[]`（来自可扩展的 `RiskLabel[]` 仓库），证据位置为 `EvidenceLocation[]`，按模态区分：

- `screen_text`：页面区域标识 + 文本坐标
- `video_time`：开始/结束秒 + 文字摘要
- `audio_time`：开始/结束秒 + 文字摘要
- `page_region`：区域名称 + 位置描述

## 4. 导入向导完成情况

五步向导（`/admin/datasets/import`）：

1. **选择数据集**：选择已有数据集或创建新数据集（创建项复用 `POST /admin/datasets` 的 Service）。
2. **导入数据**：真实 `input[type=file]`（`.json/.csv/.txt`，≤5MB），浏览器本地读取内容并校验：
   - JSON 结构可解析且包含必要字段；
   - CSV 具备表头与至少一条数据；
   - 解析失败 → 标记「格式错误」，错误原因展示在文件表格中；
   - 同时展示文件名、大小、模拟哈希（`local-{size}-{name}`）与明确提示「未上传服务器，文件哈希为模拟值」。
   - 另提供三个演示包：正常（8 条）、格式错误、重复样本。
3. **来源登记**：来源类型、来源名称、来源说明、获取时间、来源证明/参考信息；提供「清空来源信息（演示缺失场景）」入口。
4. **许可登记**：许可类型、许可范围、是否允许模型训练、是否允许修改/标注、许可有效期、证明材料、审核状态；提供「清空许可信息（演示缺失场景）」入口。
5. **导入确认**：统计待导入总数、格式错误、来源缺失、许可缺失、重复样本、可导入数量；逐行展示样本与不可导入原因，只有「满足全部必要条件」的样本可勾选导入；导入后样本初始状态为「未标注」。

明确边界：界面标注「文件仅在浏览器本地读取，不会上传服务器」「演示数据（Mock）」，导入结果只写入本地 Mock 内存。

## 5. 标注工作台完成情况

`/admin/datasets/:id/annotation`：

- **样本列表**：样本 ID、文件/样本名、数据来源（来源类型 + 来源名称）、风险类别、标注状态、审核状态、许可状态、重复状态、质检结果；支持关键词、来源类型、标注状态、审核状态、许可状态筛选与分页（默认 10 条/页）。
- **多标签标注**：9 个预置演示标签可任意多选（虚假投资理财、冒充客服、刷单返利、虚假中奖、诱导转账、索取验证码、可疑链接、正常内容、信息不足），标签仓库独立，可扩展。
- **证据位置**：支持新增 / 内联修改 / 删除四类证据；视频与音频校验「结束时间必须大于开始时间」，文字摘要必填。
- **标注状态**：未标注、标注中、已提交、待复核、已完成（可选「正常内容不得与其他风险标签共存」的冲突规则由质检负责提示）。
- **保存**：点击保存实际调用 `saveSampleAnnotation` → 更新 Mock Repository 中样本的标签、证据、说明、标注状态与 `updatedAt`，并写入 `AnnotationRecord`；页面提示「标注已保存到本地 Mock Repository」。
- **提交复核 / 演示复核**：提交后样本进入 submitted/reviewing；可在面板内以「通过 / 退回 / 驳回」记录 `ReviewRecord`，并写回样本审核状态。
- **训练资格提示**：面板底部实时调用 `evaluateSampleEligibility` 展示阻止原因。

**持久化验证**：保存后返回数据集列表（SPA 路由返回，不刷新页面），再次进入标注工作台并选中同一样本，标签、证据条数、标注说明均保持不变。刷新浏览器会重置 Mock 内存数据（设计如此，非缺陷）。

## 6. 数据质检完成情况

`/admin/datasets/:id/quality`：

必须主动点击「开始质检」才生成报告，未检查时页面显示「尚未运行质检」提示，不会自动标记为通过。

检查项（`buildIssues`）：

1. 来源信息缺失（来源名称为空）
2. 许可信息缺失 / 许可不允许训练
3. 风险类别缺失
4. 证据位置缺失
5. 标注不完整（未标注 / 标注中）
6. 重复样本（疑似 / 已确认）
7. 标签冲突（「正常内容」与其他风险标签共存）
8. 格式异常

结果分级（`resultOf`）：

- 通过：无任何问题；
- 不通过：命中阻断项（格式异常、许可缺失/不允许训练）；
- 待人工复核：仅存在「疑似重复」一项问题；
- 待修正：其余可修正问题。

页面能力：4 张统计卡（通过 / 待修正 / 不通过 / 待人工复核 + 本次检查数与生成时间）、问题类型与严重程度筛选、问题表格、查看问题抽屉（展示样本许可/标注/审核/重复/质检状态与问题明细）、跳转修正标注、审核意见弹窗（通过 / 待修正 / 驳回 + 意见，写入 `ReviewRecord`）、重新质检。

## 7. 去重功能完成情况

`/admin/datasets/:id/dedupe`：

- 以样本 `fileHash`（演示数据中为固定模拟哈希）聚合疑似重复组；
- 页面顶部固定提示「**模拟去重**：基于演示哈希分组，未执行真实文件内容哈希计算；确认操作仅修改本地 Mock 数据」；
- 每个重复组展示：组 ID、依据（模拟哈希值 + 样本数）、每条样本的保留/排除选择、重复状态；
- 人工确认后调用 `resolveDedupe`：保留样本置为 unique，排除样本置为 excluded 并阻断训练资格；
- 写入 `DedupeRecord`，页面下方「操作记录」表格展示处理详情、操作人、时间；已处理分组锁定不可重复处理；
- 统计卡：重复分组数、待人工确认、已处理分组、已排除样本。

未声称已完成真实文件哈希去重。

## 8. 数据拆分功能完成情况

`/admin/datasets/:id/split`：

- 配置项：训练 / 验证 / 测试比例（默认 70 / 15 / 15，提供「恢复默认」）、随机种子（默认 42）、拆分策略（按来源分组（推荐）/ 随机拆分）；
- 比例校验：实时显示「比例合计 X%（必须等于 100%）」，合计不为 100 时按钮禁用；Adapter 侧仍以 `assertSplitConfig` 二次校验（`SPLIT_RATIO_SUM_INVALID`）；
- 拆分前置过滤：先经 `evaluateEligibility` 过滤，无许可、未质检、格式异常、未标注、未处理重复的样本全部排除，并在「被排除样本」表中逐条展示阻止原因；只有通过资格校验的样本参与正式拆分；
- 整数分配：采用**最大余数法**（`computeSplitTargets`）计算三个集合的目标数量 —— 先按比例向下取整，再将余数按小数部分从大到小分配，保证三者之和严格等于合格样本数；仅当三个比例**均大于 0** 且合格样本 ≥ 3 时，才对被算成 0 的集合执行最小 1 条约束，保证三个集合均非空；
- 零比例保护：训练 / 验证 / 测试任一比例为 0（如 100/0/0、50/50/0）时，**不擅自改成非零比例**；预览给出「训练集、验证集、测试集比例必须均大于 0」警告，页面禁用「以当前拆分创建版本」并显示红色原因，Service 层同时以 `SPLIT_ZERO_RATIO` 拒绝创建；
- 分配实现：按种子打乱分组/样本顺序后，以「连续前缀 + 精确目标数量」落位（`splitAt`），不再用 `index / total` 的浮点位置比较，避免边界误差导致测试集为空；
- 结果预览：参与拆分样本数、训练 / 验证 / 测试数量、预览表格（最多展示 200 行）；
- 数据泄漏提示：
  - 按来源分组策略且未拆分分组 → `leakageRisk = 'grouped'`，提示「已按来源名称分组拆分，Mock 环境无法验证内容相似度，仍需后端复核是否存在泄漏」；
  - 按来源分组策略但为满足比例必须拆分某个来源分组 → `leakageRisk = 'partial_group_split'`，逐组提示被拆分的来源名称与泄漏风险；
  - 随机策略 → `leakageRisk = 'unverified'`，提示随机拆分存在同来源进入训练与测试的泄漏风险；
- 数量不足保护：合格样本 < 3 条时，预览返回「不足以拆分为训练/验证/测试三个非空集合」警告，页面禁用「以当前拆分创建版本」并显示红色原因，Service 层同时以 `SPLIT_NOT_ENOUGH_SAMPLES` 拒绝；
- 随机种子使用固定 seed 的伪随机洗牌（mulberry32），同一种子 + 同一输入结果稳定可复现；
- 支持「以当前拆分创建版本」。

## 9. 版本管理完成情况

`/admin/datasets/:id/versions`：

- 版本列表：版本号、版本 ID、状态、样本数量、训练 / 验证 / 测试数量、版本说明、创建人、创建时间、操作；
- 创建版本：在版本页或拆分页发起，写入 `versionDescription` / `changeLog`，版本号按 `v主.次.0` 递增；无具备训练资质的样本时拒绝创建（`NO_ELIGIBLE_SAMPLE`）；三个比例任一为 0 时拒绝创建（`SPLIT_ZERO_RATIO`）；
- 版本详情抽屉：版本基础信息、变更记录列表、版本差异对比（可选择基线版本，逐项显示「已变化 / 一致」）；
- 状态流转：草稿 → 提交版本审核（reviewing）→ 发布（published）/ 退回草稿；每次流转写入 `changeLog` 并经 `ConfirmDialog` 二次确认；
- **同源拆分人工复核**：创建版本时若拆分结果为 `partial_group_split`，版本写入 `manualReviewStatus = 'pending'`（列表显示「同源待复核」标签）并记录 `leakageRisk`；未完成复核前，`更新状态为发布` 会被 Service 以 `VERSION_MANUAL_REVIEW_REQUIRED` 拒绝，列表「发布」按钮同时禁用；在版本页点击「人工复核」填写复核意见并确认后，状态变为 `approved`（「同源已复核」），复核人、时间与意见写入版本详情与变更记录，之后方可发布；
- 版本详情同时展示「数据泄漏风险」与「同源复核」状态；「部分来源分组被拆分」等原有泄漏警告全部保留；
- **已发布版本保护**：`updateVersionStatus` 对 published 版本拒绝任何状态回退（`VERSION_LOCKED`），列表中「已锁定」按钮禁用，详情抽屉显示「该版本已发布并锁定，不允许覆盖修改；如需调整数据请创建新版本」；
- 任何数据变更通过创建新版本记录，不覆盖历史版本。

## 10. 正式训练资格校验规则

统一由 `evaluateEligibility(sample)` 实现，样本同时满足以下全部条件才可标记为可训练：

| 序号 | 条件 | 不满足时的阻止原因 |
|---|---|---|
| 1 | 来源明确 | 来源信息缺失 |
| 2 | 使用许可明确 | 使用许可缺失 |
| 3 | 许可允许训练 | 许可未允许用于模型训练 |
| 4 | 文件格式有效 | 文件格式异常 |
| 5 | 标注完整（已完成） | 标注未完成 |
| 6 | 风险类别非空 | 风险类别缺失 |
| 7 | 证据位置非空 | 证据位置缺失 |
| 8 | 质检通过 | 质检未通过 |
| 9 | 无未处理重复冲突 | 存在未处理的疑似重复 / 已确认为重复样本 / 已被去重排除 |
| 10 | 样本未被阻断 | 样本状态为受阻 |

页面表现：列表统计「可训练率」、工作台头部「可训练率」、标注面板底部阻止原因、拆分页「被排除样本」逐条原因。仅存在于数据集中不会被视为可训练。

本轮为前端 + Mock 规则校验，真实训练前仍由后端复核。

## 11. Mock 与真实服务的区分方式

- 统一开关：`IS_MOCK = import.meta.env.VITE_USE_MOCK !== 'false'`（沿用 P09），页面不感知差异；
- 所有 Mock 调用统一经 `mockCall(factory, { latencyMs })`：模拟网络延迟、支持「演示控制 → 下一次请求失败」验证 Error 状态、返回 `structuredClone` 副本；
- 页面固定展示「演示数据（Mock）」标签；统计卡统一带「演示」角标；导入、质检、去重、版本、拆分页均说明「本地 Mock / 未接入真实数据服务」；
- 数据集仓库为演示数据：6 个数据集、186 条样本，全部为团队自建或授权演示素材描述，不含任何私人录屏、真实个人信息或爬取内容；
- Service 中真实接口路径按 `docs/api/CONTRACT_DRAFT.md` 的 `/admin/datasets*` 约定预留（`/stats`、`/options`、`/labels`、`/samples`、`/quality`、`/dedupe`、`/split`、`/versions`），切换 `VITE_USE_MOCK=false` 即走真实 HTTP；
- Mock 写入只影响当前浏览器会话内存，刷新页面即重置。

## 12. TypeScript 类型检查结果

```
npm run typecheck   # vue-tsc --noEmit
> silver-shield-admin-web@0.1.0 typecheck
（无输出，退出码 0）
```

**结果：通过，0 错误。**

## 13. Web 生产构建结果

```
npm run build       # vue-tsc --noEmit && vite build
✓ 1734 modules transformed.
✓ built in 13.72s
```

按路由拆包，数据集相关产物：`DatasetListView`、`DatasetImportView`、`DatasetAnnotationView`、`DatasetQualityView`、`DatasetDedupeView`、`DatasetSplitView`、`DatasetVersionsView`、`dataset`（Service）、`adapter`（Mock）。主包 `index-*.js` 约 1,089.95 kB（gzip 359.79 kB，主要为 Element Plus 全量引入，与 P09 一致）。

**结果：构建成功。**

## 14. 实际页面交互测试结果

测试环境：本地 dev server `http://127.0.0.1:5173`，浏览器实测（非仅编译验证）。

### 数据集列表
- 初始数据：6 个数据集；统计卡 数据集总数 6 / 样本总数 186 / 已完成标注 138 / 待审核 32 / 已通过质检 0 / 不可用于训练 21，均带「演示」角标，由 Mock 动态汇总；
- 搜索「语音」→ 1 条（冒充客服语音片段）；点击「重置」→ 恢复 6 条；
- 类型筛选「视频」→ 1 条（此前实测）；
- 通过「创建数据集」连续创建 5 个数据集 → 总数 11，分页出现第 2 页；点击第 2 页 → 1 条（混合模态复核样本）；
- 查看详情抽屉：基础信息 + 数据进度（样本/标注/审核/质检等）完整，页脚含「进入标注工作台 / 查看版本」。

### 导入向导
- **正常导入**：用 DataTransfer 模拟选择合法 CSV → 格式正确、1 条可导入 → 导入成功提示「已导入 1 条样本到本地 Mock 数据集」；
- **格式错误**：选择非法 JSON（`{ this is not valid json`）→ 本地读取成功、格式校验「格式错误 / JSON 结构无法解析」；格式错误演示包 → 6 条中 1 条格式错误、5 条可导入；
- **来源缺失**：正常演示包 + 清空来源 → 8 条全部「来源缺失」，可导入 0 条；
- **许可缺失**：正常演示包 + 清空许可 → 8 条全部「许可缺失」，可导入 0 条；
- **重复样本**：重复演示包 → 6 条中 1 条「本批次内重复」，可导入 5 条。

### 标注工作台
- 48 条样本、5 页；筛选与分页可用；
- 多标签选择（冒充客服 / 诱导转账 / 索取验证码 / 可疑链接）保存成功；
- 新增 2 条证据位置（屏幕文本 + 视频时间）保存成功；
- 提示「标注已保存到本地 Mock Repository」；
- 返回列表再重新进入工作台，选中同一样本：标签、证据条数、标注说明均保留；
- 训练资格区域正确显示阻止原因（质检未通过等）。

### 数据质检
- 未质检时显示「尚未运行质检」，未自动标记通过；
- 点击「开始质检」→ 48 条检查：通过 19 / 待修正 14 / 不通过 13 / 待人工复核 2，问题 43 条；
- 问题类型筛选「标签冲突」→ 3 条（sample-006 / 019 / 032）；
- 查看问题抽屉：显示样本状态与 2 项问题明细（许可未允许训练、标签冲突）；
- 记录审核意见：选择「待修正」+ 填写意见 → 提示「审核意见已记录到本地 Mock 数据」；
- 「重新质检」→ 提示「质检已完成，结果已写入本地 Mock 数据」，统计刷新；
- 「修正标注」跳转 `/annotation?sampleId=...` 定位成功。

### 数据去重
- DS-2026-002：2 个重复分组 / 2 待确认；默认保留第一条、排除其余；
- 点击「确认处理」→ 提示「已保留 1 条并排除 1 条重复样本（本地 Mock）」，统计变为 已处理 1 / 已排除 1；
- 操作记录表格新增一行（处理详情、操作人、时间）。

### 数据拆分
- 将训练集改为 60 → 显示「比例合计 90%（必须等于 100%）」，预览按钮不可用；
- 「恢复默认 70 / 15 / 15」→ 预览成功；
- 质检前：参与 0 条 / 排除 48 条，逐条展示阻止原因；创建版本按钮禁用，并提示「当前没有满足训练资格的样本，无法创建正式版本」；
- 运行质检后（SPA 内跳转）再预览：参与 15 条 → **训练 11 / 验证 2 / 测试 2**（合计 15），被排除 33 条；
- 同种子 42 连续生成两次，15 条样本的「样本 → 集合」映射完全一致；改为种子 7 后数量仍为 11/2/2，成员发生变化；
- 随机策略 70/15/15 → 11/2/2，提示「未验证分组隔离，存在泄漏风险」；
- 零比例边界（本轮修正）：**100/0/0 → 15/0/0**、**50/50/0 → 8/7/0**、70/0/30 → 11/0/4；三者合计均为 15，比例为 0 的集合保持 0 条（不擅自改为非零）；页面提示「训练集、验证集、测试集比例必须均大于 0，当前配置禁止创建正式版本」，「以当前拆分创建版本」按钮禁用；Service 层对 100/0/0、50/50/0 均抛出 `SPLIT_ZERO_RATIO`；
- 正常比例仍使用最大余数法：70/15/15 → 11/2/2；
- 泄漏提示：按来源分组 → `partial_group_split`，提示「为严格满足拆分比例，1 个来源分组被拆到多个集合（团队自建反诈演示素材），存在同源样本泄漏风险」；
- 合格样本不足：DS-2026-005 质检后仅 2 条合格 → 预览 2/0/0，创建版本按钮禁用并提示「当前仅有 2 条合格样本，不足以拆分为训练/验证/测试三个非空集合，无法创建正式版本」；
- 数据量校验：DS-2026-004 质检后 22 条合格 → 16/3/3（合计 22），无空集合。

### 版本管理
- 拆分页「以当前拆分创建版本」→ 生成 v1.3.0（15 条，11/2/2），提示「已创建版本 v1.3.0（草稿，本地 Mock）」；
- 版本列表展示 v1.3.0 / v1.2.0 / v1.0.0，已发布版本显示「已锁定」；
- 详情抽屉：版本信息、变更记录、版本差异对比（基线默认 v1.2.0，逐项「已变化 / 一致」）正常；
- 已发布 v1.0.0 详情：显示发布锁定说明，无「提交审核 / 发布」操作；
- **同源复核流程（本轮新增，浏览器实测）**：DS-2026-001 质检后创建 v1.3.0 → 列表显示「草稿 + 同源待复核」；提交审核后状态为「审核中 + 同源待复核」，「发布」按钮禁用；Service 返回 `VERSION_MANUAL_REVIEW_REQUIRED`「该版本存在同源分组拆分泄漏风险，未完成人工复核前不允许发布为正式可训练版本」；点击「人工复核」填写意见并确认 → 标签变为「同源已复核」，提示「版本 v1.3.0 已完成人工复核，可继续提交审核与发布」，「发布」按钮恢复可用。

### 控制台
- 复查页面控制台消息，无 error / warning，仅有 Mock 请求的 debug/info 日志。

## 15. 尚未实现的真实后端功能

本轮仅完成管理后台前端与 Mock Repository，以下能力尚未实现，需后续轮次与后端配合：

1. 真实数据存储服务（数据集、样本、标注、审核、质检、去重、版本持久化）；
2. 真实文件上传、病毒扫描、内容哈希（SHA-256 等）与真实去重；
3. 真实数据导入解析服务（大批量流式解析、断点续传、错误行回执）；
4. 真实身份认证与基于角色的权限控制（当前沿用 P09 本地模拟登录，页面未做细粒度权限拦截）；
5. 真实质检规则引擎与服务端二次校验（当前为前端 Mock 规则）；
6. 真实内容相似度 / 数据泄漏检测（当前按来源名称分组，且明确标记未验证）；
7. 真实版本快照、数据包导出与训练集物化；
8. 视频 / 音频标注引擎（当前只提供时间区间与文字摘要录入，无帧级/波形级标注）；
9. 训练管理（A05，按指令本轮不开发）。

## 16. P10 验收结论

- A04 数据集管理已按需求完成完整前端工作流：列表 → 导入向导 → 标注工作台 → 质检 → 去重 → 拆分 → 版本管理，并统一接入训练资格校验；
- 严格遵守工程约束：复用 P09 架构与通用组件，未重建工程、未覆盖 P09 组件、未修改 Android 工程、未改变 API 分层；
- 所有数据与操作均明确标记为演示 / 本地 Mock，未伪装真实上传、真实审核或真实去重；
- `npm run typecheck` 0 错误，`npm run build` 成功，本地 dev server 下关键交互均已实际验证。

**结论：P10 通过自检，等待验收。按指令本轮仅执行 P10，未进入 P11。**

---

## 17. 数据拆分专项修复记录（2026-11-19）

### 17.1 问题现象

15 条合格样本按 70 / 15 / 15 拆分，结果为 **11 / 4 / 0**，测试集为空。

### 17.2 根因

原实现按「累计位置占比」判断分组所属集合：

```ts
const position = cursor / eligible.length
const split = position < 0.7 ? 'train' : position < 0.85 ? 'validation' : 'test'
```

- 按整个来源分组落位，组内所有样本共用一个 `position`；
- 4 个来源分组大小约为 4 / 4 / 3 / 4，顺序累计后训练集恰好收满 11 条，验证集一次吞下整个 4 条分组，导致测试集剩余为 0；
- 同时未对三个集合做最小非空约束，也没有「合格样本不足」的创建拦截。

### 17.3 修复方案

| 修改文件 | 修改内容 |
|---|---|
| `apps/admin-web/src/api/mock/datasetAdapter.ts` | 新增 `computeSplitTargets`（最大余数法 + `total ≥ 3` 时最小 1 条约束）、`splitAt`（按累计目标数量落位）、`MIN_SPLIT_SAMPLES = 3`；重写 `previewSplit` 的分配逻辑；按来源分组时统计被拆分的分组并输出 `partial_group_split` 泄漏提示；`createVersion` 新增 `SPLIT_NOT_ENOUGH_SAMPLES` 拦截 |
| `apps/admin-web/src/types/dataset.ts` | `SplitResult.leakageRisk` 增加 `'partial_group_split'` |
| `apps/admin-web/src/views/dataset/DatasetSplitView.vue` | 泄漏标签与告警级别适配新状态；创建版本按钮改为 `eligibleCount < 3` 禁用，并显示红色阻止原因；`openVersionDialog` 增加 < 3 的前置提示 |

分配算法要点：

1. 目标数量：`floor(n × ratio)` 后按小数部分从大到小补齐余数，三者之和恒等于 `n`；`n ≥ 3` 时若某集合为 0，则从最多的集合借 1 条；
2. 落位方式：分组/样本按种子洗牌后连续排列，按前缀累计与目标数量比较决定归属，不再做浮点比例比较；
3. 分组隔离：仍以来源名称聚合分组，只有为满足精确比例必须跨越组边界时才拆分，并显式给出泄漏警告与来源名称；
4. 确定性：同一 `seed` + 同一输入 → 分组顺序、成员归属完全一致；不同 `seed` 仅改变成员，不改变各集合数量。

### 17.4 修复前后对比

| 场景 | 合格样本 | 修复前 | 修复后 |
|---|---|---|---|
| DS-2026-001 默认 70/15/15（按来源分组，seed 42） | 15 | 11 / 4 / 0（合计 15，测试集空） | **11 / 2 / 2**（合计 15） |
| 同种子重复预览 | 15 | — | 两次结果完全一致 |
| 更换种子 7 | 15 | — | 仍 11 / 2 / 2，成员变化 |
| 随机策略 70/15/15 | 15 | — | 11 / 2 / 2 |
| 100 / 0 / 0 | 15 | — | 13 / 1 / 1（非空约束生效） |
| 50 / 50 / 0 | 15 | — | 7 / 7 / 1 |
| 70 / 0 / 30 | 15 | — | 10 / 1 / 4 |
| DS-2026-004 70/15/15 | 22 | — | 16 / 3 / 3 |
| DS-2026-005（不足） | 2 | 允许进入创建流程 | 2 / 0 / 0，创建按钮禁用并给出原因；Service 抛 `SPLIT_NOT_ENOUGH_SAMPLES` |
| 新数据集（无样本） | 0 | — | 0 / 0 / 0，提示先完成标注与质检，创建按钮禁用 |

> 注：上表中 0% 比例场景（13/1/1、7/7/1、10/1/4）已被第 18 节规则取代 —— 现要求三个比例均大于 0，0% 时不再自动补 1 条，而是直接禁止创建版本。

### 17.5 真实测试日志（本地 dev server · 浏览器实测）

```
# DS-2026-001：先质检，再进入拆分预览
参与拆分样本 15（数据集共 48 条，已排除 33 条）
训练集 11 | 验证集 2 | 测试集 2
提示：为严格满足拆分比例，1 个来源分组被拆到多个集合（团队自建反诈演示素材），存在同源样本泄漏风险，需人工复核。
提示：33 个样本未通过训练资格校验，已从拆分中排除。

# 同种子 42 连续两次生成
same-seed-equal: true

# 修改随机种子为 7 后重新生成
训练集 11 | 验证集 2 | 测试集 2
seed7-changed-membership: true

# 随机策略
未验证分组隔离，存在泄漏风险；11 / 2 / 2

# 比例边界
100/0/0 -> 13 / 1 / 1
50/50/0 -> 7 / 7 / 1
70/0/30 -> 10 / 1 / 4

# 合格样本不足（DS-2026-005，质检通过 2 条）
参与拆分样本 2（已排除 20 条）
训练集 2 | 验证集 0 | 测试集 0
创建版本按钮 disabled = true
提示：当前仅有 2 条合格样本，不足以拆分为训练/验证/测试三个非空集合，无法创建正式版本。

# 数据量校验（DS-2026-004，质检通过 22 条）
训练集 16 | 验证集 3 | 测试集 3（合计 22）
```

构建与类型检查（修复后重新执行）：

```
npm run typecheck   # vue-tsc --noEmit，退出码 0，无输出
npm run build       # ✓ 1734 modules transformed. ✓ built in 12.38s
```

控制台复查：拆分相关页面无 error / warning。

### 17.6 未受影响项确认

- 训练资格过滤仍为拆分唯一入口，`evaluateEligibility` 未改动；
- 重复样本排除、去重结果、质检规则均未修改；
- 按来源分组、随机策略与泄漏风险提示均保留，并新增「分组被拆分」的显式风险提示；
- 本次仅修改拆分相关 3 个文件，未改动 Android 工程、未进入 P11。

---

## 18. 边界逻辑最终修正记录（2026-11-19）

本轮只处理「零比例配置」与「来源分组泄漏人工复核」两个问题，未重构其他功能。

### 18.1 修改文件

| 文件 | 修改内容 |
|---|---|
| `apps/admin-web/src/types/dataset.ts` | 新增 `ManualReviewStatus = 'not_required' \| 'pending' \| 'approved'`；`DatasetVersion` 新增 `leakageRisk`、`manualReviewStatus`、`manualReviewedBy`、`manualReviewedAt`、`manualReviewNotes` |
| `apps/admin-web/src/api/mock/datasetData.ts` | 种子版本补充 `manualReviewStatus: 'not_required'` |
| `apps/admin-web/src/api/mock/datasetAdapter.ts` | `computeSplitTargets` 的最小 1 条约束改为仅在三个比例均 > 0 且样本 ≥ 3 时生效；新增 `hasZeroRatio`、0% 预览警告、`createVersion` 的 `SPLIT_ZERO_RATIO` 拦截；创建版本写入复核状态；`updateVersionStatus` 新增 `VERSION_MANUAL_REVIEW_REQUIRED` 发布拦截；新增 `confirmVersionManualReview` |
| `apps/admin-web/src/api/dataset.ts` | 新增 `reviewDatasetVersion(versionId, notes)` Service 与真实接口 `/admin/dataset-versions/:id/manual-review` |
| `apps/admin-web/src/views/dataset/DatasetSplitView.vue` | 新增 `hasZeroRatio` 计算、创建前拦截、按钮禁用与 0% 阻止原因提示 |
| `apps/admin-web/src/views/dataset/DatasetVersionsView.vue` | 状态列新增「同源待复核 / 同源已复核」标签；操作列新增「人工复核」并在待复核时禁用「发布」；详情抽屉展示泄漏风险与复核状态、pending 提示；新增复核意见对话框 |

### 18.2 零比例规则

- 训练 / 验证 / 测试任一比例为 0 → 预览给出「比例必须均大于 0」警告，页面禁用「以当前拆分创建版本」，Service 抛 `SPLIT_ZERO_RATIO`；
- **不擅自把用户设置的 0% 改为非零比例**：预览数量中该集合保持 0 条；
- 70/15/15 等正常比例继续使用最大余数法，数量之和严格等于合格样本数。

### 18.3 同源分组泄漏规则

- 保留原按来源分组拆分逻辑与所有现有泄漏警告（`grouped` / `partial_group_split` / `unverified`）；
- 同一来源组被拆到多个集合 → `leakageRisk = 'partial_group_split'`，创建版本时写入 `manualReviewStatus = 'pending'`；
- 待复核版本在列表中显示「同源待复核」，发布被 Service 以 `VERSION_MANUAL_REVIEW_REQUIRED` 拒绝（UI 同步禁用发布按钮）；
- 人工填写复核意见并确认后 `manualReviewStatus = 'approved'`，复核记录写入变更记录，之后才允许发布为正式可训练版本。

### 18.4 真实测试日志

数据层（esbuild 产物 + Node 直接调用 Mock Adapter，DS-2026-001 质检通过 19 条）：

```
70/15/15        → eligible 15 | 11 / 2 / 2 | partial_group_split | 0% 警告 false
100/0/0         → eligible 15 | 15 / 0 / 0 | 0% 警告 true
50/50/0         → eligible 15 |  8 / 7 / 0 | 0% 警告 true
70/0/30         → eligible 15 | 11 / 0 / 4 | 0% 警告 true
70/15/15 seed 7 → 11 / 2 / 2（组名：合作机构授权样例）
70/15/15 random → 11 / 2 / 2 | unverified
create 100/0/0  → SPLIT_ZERO_RATIO「比例必须均大于 0%，当前为 100% / 0% / 0%」
create 50/50/0  → SPLIT_ZERO_RATIO「比例必须均大于 0%，当前为 50% / 50% / 0%」
create 70/15/15 → v1.3.0，manualReviewStatus = pending
提交审核         → 成功（reviewing）
未复核发布       → VERSION_MANUAL_REVIEW_REQUIRED（拒绝）
人工复核         → approved（演示管理员 + 复核意见）
复核后发布       → success（published）
DS-2026-005     → 合格 2 条，SPLIT_NOT_ENOUGH_SAMPLES
```

浏览器实测（SPA 内导航，本地 dev server）：

```
DS-2026-001 质检后 70/15/15：参与 15（排除 33）→ 训练 11 / 验证 2 / 测试 2
警告：部分来源分组被拆分（存在泄漏风险）→「1 个来源分组被拆到多个集合（团队自建反诈演示素材）」
100/0/0：出现「训练集、验证集、测试集比例必须均大于 0」提示，「以当前拆分创建版本」disabled
50/50/0：同上，创建按钮 disabled
版本页：v1.3.0 创建后「草稿 + 同源待复核」→ 提交审核 →「审核中 + 同源待复核」，发布 disabled
人工复核确认后 →「同源已复核」，发布按钮恢复可用
```

类型检查与生产构建（本轮重新执行）：

```
npm run typecheck   # vue-tsc --noEmit，退出码 0，无输出
npm run build       # ✓ 1734 modules transformed. ✓ built in 13.72s
```

### 18.5 边界确认

- 未改动训练资格过滤规则、重复排除、去重、质检逻辑；
- 未删除任何现有泄漏警告；
- 未修改 Android 工程，未进入 P11。
