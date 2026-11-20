# 银盾智鉴 · P12 模型版本管理、审核发布与回滚报告

> 文档编号：P12-MODEL-REPORT
> 版本：v1.0
> 日期：2026-11-20
> 范围：A06 模型仓库、版本详情、审核清单、发布、撤回、回滚、审计
> 结论：**P12 前端与 Mock 业务链路完成；真实 FastAPI、模型文件与 Python Worker 尚未实现**

---

## 0. 一句话结论

本轮在 `apps/admin-web` 内完成 A06 模型版本管理全部页面与 Mock 业务适配层，
以「模型版本」为核心串联 P10 数据集版本与 P11 训练任务；没有真实模型文件时，
所有哈希、签名、评估与兼容性均明确标记为演示记录，正式渠道发布被审核清单阻断。
浏览器不读取模型文件、不执行量化或推理，发布 / 撤回 / 回滚的最终判定预留给服务端二次校验。

## 1. 执行前检查

| 检查项 | 结果 |
|---|---|
| Git 状态 | 仓库尚无提交，工作区包含 `apps/`、`docs/` 等目录（全部未跟踪） |
| `apps/admin-web` 工程 | 存在，Vue 3 + TS strict + Vite 6.4.6 + Element Plus + ECharts 5 |
| P09 通用组件 / API 层 | 存在，复用 `PageHeader / FilterPanel / DataTable / Pagination / StatCard`、`IS_MOCK`、`mockCall()`、`ApiError` |
| P10 数据集模块 | 存在：`datasetVersions`、`datasetSamples`、质检 / 去重 / 拆分 / 人工复核状态 |
| P11 训练模块 | 存在：`trainingTasks`、训练参数、执行模式、结果与评估摘要 |
| A06 旧占位页 | 存在：`src/views/admin/ModelVersionView.vue`，已由模型仓库取代 |
| `services/business-api` | **不存在** |
| `ml/training`、真实 Worker、真实模型文件 | **不存在** |
| 路由方式 | A06 子路由挂在 `/admin` 下，复用侧边栏与面包屑 |

必读文档已核对：`PRODUCT_BASELINE.md`、`ROLE_PERMISSION_MATRIX.md`、
`IMPLEMENTATION_PLAN.md`、`CONTRACT_DRAFT.md`、`P10_DATASET_REPORT.md`、
`P11_TRAINING_CONTRACT.md`、`P11_TRAINING_REPORT.md`。

## 2. 文件清单

### 新增（17）

| 文件 | 说明 |
|---|---|
| `src/types/model.ts` | 模型 / 版本 / 产物 / 兼容性 / 评估 / 发布 / 审计类型与标签 |
| `src/api/model.ts` | A06 Service，Mock / 真实接口切换 |
| `src/api/mock/modelData.ts` | 4 个模型、7 个版本、7 个产物、5 条发布记录、14 条审计记录 |
| `src/api/mock/modelAdapter.ts` | 仓库查询、审核清单、审核 / 发布 / 撤回 / 回滚、记录查询 |
| `src/components/model/ReleaseStatusTag.vue` | 发布状态标签 |
| `src/components/model/VerificationTag.vue` | 哈希 / 签名验证状态标签 |
| `src/components/model/CompatibilityTag.vue` | 运行时兼容性标签 |
| `src/components/model/ReviewChecklist.vue` | 11 项审核清单与阻断原因 |
| `src/components/model/ReleaseRecordTable.vue` | 发布记录表（含回滚来源 / 目标） |
| `src/components/model/AuditRecordTable.vue` | 只读审计记录表 |
| `src/views/model/ModelRepositoryView.vue` | 模型仓库：统计、筛选、列表、操作 |
| `src/views/model/ModelDetailView.vue` | 模型详情：版本概览、发布记录、审计记录 |
| `src/views/model/ModelVersionListView.vue` | 版本列表、筛选、版本对比 |
| `src/views/model/ModelVersionDetailView.vue` | 版本详情、审核、发布、撤回、回滚 |
| `docs/api/P12_MODEL_CONTRACT.md` | 预留 API 契约 |
| `docs/testing/P12_MODEL_REPORT.md` | 本报告 |

### 修改（2）

| 文件 | 改动 |
|---|---|
| `src/router/index.ts` | A06 指向模型仓库；新增 `/admin/models/:modelId`、`/versions`、`/versions/:versionId` |
| `src/api/mock/trainingData.ts` / `modelData.ts` 引用关系 | 仅读取 P11 数据，未修改 P11 状态机 |

### 删除（1）

| 文件 | 说明 |
|---|---|
| `src/views/admin/ModelVersionView.vue` | A06 旧占位页 |

未修改 Android 工程，未删除或重建 P09–P11 页面。

## 3. 数据模型

- `ModelArtifact`：`artifactId、modelId、modelName、modelType、framework、artifactPath、
  artifactFormat、fileSize、fileHash、fileHashStatus、signature、signatureStatus、
  createdAt、createdBy、artifactSource、isMock`。
- `ModelVersion`：`modelVersionId、modelId、modelVersion、datasetId、datasetVersionId、
  datasetVersion、trainingTaskId、artifactId、modelFormat、quantization、
  runtimeCompatibility、evaluationSummary、releaseStatus、createdAt、createdBy、
  reviewedAt、reviewedBy、publishedAt、withdrawnAt`。
- `RuntimeCompatibility`：平台、最低 / 最高系统版本、架构、推理运行时及版本、
  支持设备、内存要求、加速支持、兼容性状态、测试时间。
- `EvaluationSummary`：Accuracy / Precision / Recall / F1 / Validation Loss、
  测试集版本、评估时间、方法、状态。
- `ReleaseRecord`：发布单号、渠道、状态、操作人、审核人、审批 / 发布 / 撤回时间、
  回滚来源与目标、说明。
- `ModelAuditRecord`：操作时间、操作人、操作类型、对象、结果、说明，只读。

演示产物统一使用 `artifactSource = demo_record`、`fileHash = ""`、`fileHashStatus = demo_record`、
`signature = ""`、`signatureStatus = demo_record`，页面不会展示任何伪造的哈希或签名。

## 4. 页面能力

### 4.1 模型仓库

- 6 个动态统计：模型总数、版本总数、待审核、已发布、已撤回、兼容性未测试。
- 8 项筛选：名称 / ID、模型类型、框架、发布状态、兼容性、发布时间范围。
- 列表列：模型、类型、框架、最新版本、当前发布版本、训练来源、兼容性、更新时间、操作。
- 操作：模型详情、版本列表、评估（跳转最新版本）、发布记录。

### 4.2 版本列表

- 版本号、训练任务、数据集版本、格式、量化、文件大小、哈希、签名、兼容性、评估、发布状态。
- 支持发布状态 / 兼容性 / 关键词筛选。
- 勾选两个版本进行对比，表格展示字段差异。

### 4.3 版本详情

- 基础信息、数据集来源（含阻断样本、资格率、人工复核风险）、模型文件信息
  （明确提示「当前为模型版本演示记录，尚无真实模型文件」）、训练来源（P11）、
  评估结果、量化信息、Android 运行时兼容性。
- 评估页显式说明不能仅凭 Accuracy 判断反诈能力，同时展示 Precision / Recall / F1 / Validation Loss。
- 审核与发布页展示 11 项清单，内部测试与正式发布分开预览。
- 发布与审计记录页展示全部历史，含回滚来源 / 目标。

## 5. 审核与发布规则

- 内部测试为演示渠道：演示文件、哈希、签名、评估为「未验证」但不阻断，
  且发布时明确标注「不产生真实可用模型，不向任何 Android 设备下发」。
- 灰度 / 正式发布要求真实文件、真实哈希、真实签名、真实评估、完整设备兼容性；
  演示环境全部阻断。
- 发布权限限制为「系统管理员 / 发布管理员」角色。
- 已发布版本不可修改；撤回只停止分发，不声称远程卸载；回滚生成新记录，不覆盖历史。

## 6. 实际测试结果

### 6.1 静态检查

| 命令 | 结果 |
|---|---|
| `npm run typecheck` | 通过，0 错误 |
| `npm run build` | 通过，2333 modules transformed，built in 40.08s |

### 6.2 页面实测（`http://127.0.0.1:5175`）

| 场景 | 结果 | 证据 |
|---|---|---|
| 模型仓库加载 | 通过 | 4 行模型，6 张统计卡，含类型 / 兼容性 / 发布状态列 |
| 关键词「反诈」筛选 | 通过 | 4 行 → 1 行（M-2026-001），重置后恢复 4 行 |
| 版本详情七块内容 | 通过 | 基础信息、数据集、文件、训练、评估、量化、兼容性均存在；文件区提示无真实模型文件 |
| 正式发布清单阻断 | 通过 | 5 / 11 通过，6 项阻断：文件存在、完整性、签名、评估、测试集、部分兼容 |
| 提交审核 | 通过 | MV-2026-002-02 草稿 → 待审核 |
| 审核通过 | 通过 | MV-2026-001-03 待审核 → 审核通过（内部测试清单 6 / 11 通过，演示项非阻断） |
| 内部测试发布 | 通过 | 状态 → 已发布，生成 RL-2026-101，审计新增「审核通过」「发布」记录 |
| 回滚 | 通过 | MV-2026-001-02 → MV-2026-001-01：原版本已回滚，目标已发布；发布记录显示 `v1.1.0 → v1.0.0` |
| 撤回 | 通过 | MV-2026-001-01 → 已撤回；记录保留撤回时间、原因与影响范围 |
| 非法回滚目标 | 通过 | 回滚下拉只提供 `approved / published` 版本，未审核版本不可选择 |
| 审计只读 | 通过 | 页面无删除入口，历史记录完整保留 |

### 6.3 测试中发现并修复的问题

1. `MV-2026-001-03` 处于待审核但演示评估缺少 `testDatasetVersion`，
   导致内部测试清单存在阻断项，任何角色都无法完成审核通过。
   已补齐测试集版本 `VER-DS-2026-004-01`，审核 / 发布链路恢复可用。
2. Element Plus `el-radio-button` 仍使用 `label` 作为值，控制台持续告警；
   已改为 `value`，控制台无业务错误。
3. 模板中直接索引类型化标签映射导致 `vue-tsc` 报 TS7053 / TS7006；
   已改为显式辅助函数，类型检查通过。

## 7. 限制与未验证项

- `services/business-api`、`ml/training` 不存在，本轮未实现真实服务端二次校验。
- 没有真实模型文件，所有 `fileHash / signature / evaluation` 均为演示记录，
  不能作为真实发布依据。
- 当前登录页只有「系统管理员（模拟）」单一演示角色，权限不足（403）仅在
  Adapter 代码路径中验证，未做多角色 UI 实测。
- Mock 状态保存在内存，整页刷新后恢复种子数据（页面内 SPA 跳转保持状态）。
- 未修改 Android，未验证真实设备兼容性、灰度分发或远程撤回。

## 8. 下一轮边界

本轮只完成 P12。不自动进入 P13；真实 FastAPI、训练 Worker、模型产物存储与签名
需等待后续指令，并按 `P12_MODEL_CONTRACT.md` 实现服务端二次校验。
