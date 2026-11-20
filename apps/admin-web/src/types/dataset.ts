/**
 * A04 数据集管理类型定义（P10）。
 *
 * 全部为前端与 Mock Repository 阶段的数据契约：
 * - 字段命名与后端 FastAPI 契约草案保持一致，后续可直接映射；
 * - 所有类型在 strict 模式下显式声明，不使用 any；
 * - 演示数据由 src/api/mock/datasetData.ts 生成，不包含真实个人数据。
 */

export type DatasetType = 'screen' | 'video' | 'audio' | 'text' | 'multimodal'

export type DatasetStatus =
  | 'draft'
  | 'importing'
  | 'annotating'
  | 'reviewing'
  | 'quality_blocked'
  | 'ready'
  | 'archived'

export type AnnotationStatus =
  | 'unannotated'
  | 'in_progress'
  | 'submitted'
  | 'reviewing'
  | 'completed'

export type ReviewStatus = 'pending' | 'approved' | 'rejected' | 'needs_fix'

export type DuplicateStatus = 'unique' | 'suspected' | 'confirmed_duplicate' | 'excluded'

export type SampleStatus =
  | 'pending'
  | 'annotating'
  | 'annotated'
  | 'reviewing'
  | 'approved'
  | 'rejected'
  | 'blocked'

export type LicenseStatus = 'verified' | 'pending' | 'missing'

export type SourceType = 'team_record' | 'partner_authorized' | 'synthetic' | 'public_licensed'

export type EvidenceModality = 'screen' | 'video' | 'audio' | 'region'

export type QualityResult = 'unchecked' | 'passed' | 'needs_fix' | 'failed' | 'manual_review'

export type QualityIssueType =
  | 'missing_source'
  | 'missing_license'
  | 'missing_labels'
  | 'missing_evidence'
  | 'incomplete_annotation'
  | 'duplicate'
  | 'label_conflict'
  | 'format_error'

export type VersionStatus = 'draft' | 'reviewing' | 'published'

/** 同源拆分人工复核状态：无风险 / 待复核 / 已复核通过 */
export type ManualReviewStatus = 'not_required' | 'pending' | 'approved'

export type SplitStrategy = 'group_by_source' | 'random'

/* ===== 风险标签（可扩展的演示标签集） ===== */

export interface RiskLabel {
  labelId: string
  labelName: string
  description: string
  group: 'risk' | 'normal' | 'unknown'
  enabled: boolean
}

/* ===== 证据位置 ===== */

export interface EvidenceLocation {
  evidenceId: string
  /** 证据模态：屏幕文本 / 视频时间 / 音频时间 / 页面区域 */
  modality: EvidenceModality
  /** 屏幕页面标识（screen） */
  screenPage?: string
  /** 视频或音频起止时间，单位秒（video / audio） */
  timeStart?: number
  timeEnd?: number
  /** 页面区域描述（region） */
  region?: string
  /** 证据文字摘要，各模态均可填写 */
  textSnippet: string
}

/* ===== 数据集 ===== */

export interface Dataset {
  datasetId: string
  datasetName: string
  description: string
  datasetType: DatasetType
  totalSamples: number
  annotatedSamples: number
  approvedSamples: number
  datasetVersion: string
  datasetStatus: DatasetStatus
  createdAt: string
  updatedAt: string
  createdBy: string
}

/** 列表使用的派生统计（由样本实时计算，不手工维护） */
export interface DatasetSummary extends Dataset {
  annotationRate: number
  reviewRate: number
  pendingReviewSamples: number
  qualityPassedSamples: number
  blockedSamples: number
  eligibilityRate: number
}

export interface DatasetListQuery {
  keyword: string
  datasetType: DatasetType | ''
  status: DatasetStatus | ''
  /** yyyy-MM-dd */
  createdFrom: string
  createdTo: string
  annotationProgress: '' | 'not_started' | 'in_progress' | 'completed'
  page: number
  pageSize: number
}

export interface DatasetOverviewStats {
  datasetTotal: number
  sampleTotal: number
  annotatedTotal: number
  pendingReviewTotal: number
  qualityPassedTotal: number
  blockedTotal: number
}

export interface DatasetOptions {
  types: Array<{ value: DatasetType; label: string }>
  statuses: Array<{ value: DatasetStatus; label: string }>
}

/* ===== 样本 ===== */

export interface SampleLicense {
  licenseType: string
  licenseScope: string
  allowTraining: boolean
  allowModify: boolean
  validUntil: string
  proof: string
  reviewStatus: LicenseStatus
}

export interface DatasetSample {
  sampleId: string
  datasetId: string
  sampleName: string
  modality: DatasetType
  sourceType: SourceType
  sourceName: string
  sourceNote: string
  acquiredAt: string
  sourceProof: string
  sourceLicense: SampleLicense
  licenseStatus: LicenseStatus
  riskCategories: string[]
  evidenceLocations: EvidenceLocation[]
  annotationStatus: AnnotationStatus
  reviewStatus: ReviewStatus
  duplicateStatus: DuplicateStatus
  sampleStatus: SampleStatus
  qualityResult: QualityResult
  formatValid: boolean
  fileHash: string
  createdAt: string
  updatedAt: string
  annotatorId?: string
  annotationNotes?: string
}

export interface SampleQuery {
  datasetId: string
  keyword: string
  sourceType: SourceType | ''
  annotationStatus: AnnotationStatus | ''
  reviewStatus: ReviewStatus | ''
  licenseStatus: LicenseStatus | ''
  page: number
  pageSize: number
}

/* ===== 标注与复核 ===== */

export interface AnnotationRecord {
  annotationId: string
  sampleId: string
  annotatorId: string
  riskCategories: string[]
  evidenceLocations: EvidenceLocation[]
  annotationNotes: string
  annotationStatus: AnnotationStatus
  createdAt: string
  updatedAt: string
}

export interface AnnotationDraft {
  riskCategories: string[]
  evidenceLocations: EvidenceLocation[]
  annotationNotes: string
  annotationStatus: AnnotationStatus
}

export interface ReviewRecord {
  reviewId: string
  sampleId: string
  reviewerId: string
  reviewResult: ReviewStatus
  reviewNotes: string
  reviewedAt: string
}

/* ===== 质检 ===== */

export interface QualityIssue {
  issueId: string
  sampleId: string
  sampleName: string
  issueType: QualityIssueType
  description: string
  severity: 'info' | 'warning' | 'danger'
}

export interface QualityReport {
  reportId: string
  datasetId: string
  generatedAt: string
  totalChecked: number
  passedCount: number
  needsFixCount: number
  failedCount: number
  manualReviewCount: number
  issues: QualityIssue[]
  sampleResults: Record<string, QualityResult>
}

/* ===== 去重 ===== */

export interface DedupeGroup {
  groupId: string
  fileHash: string
  reason: string
  sampleIds: string[]
  sampleNames: string[]
  status: 'pending' | 'resolved'
  keepSampleId: string
  excludedSampleIds: string[]
  resolvedAt: string
  resolvedBy: string
  /** 模拟去重标识：当前没有真实文件内容哈希 */
  simulated: boolean
}

export interface DedupeRecord {
  recordId: string
  groupId: string
  action: string
  detail: string
  operatedAt: string
  operator: string
}

export interface DedupeOverview {
  datasetId: string
  groups: DedupeGroup[]
  records: DedupeRecord[]
  note: string
}

/* ===== 拆分 ===== */

export interface SplitConfig {
  trainRatio: number
  validationRatio: number
  testRatio: number
  seed: number
  strategy: SplitStrategy
}

export interface SplitExcludedSample {
  sampleId: string
  sampleName: string
  reasons: string[]
}

export interface SplitPreviewRow {
  sampleId: string
  sampleName: string
  sourceName: string
  split: 'train' | 'validation' | 'test'
}

export interface SplitResult {
  datasetId: string
  config: SplitConfig
  totalSamples: number
  eligibleCount: number
  trainCount: number
  validationCount: number
  testCount: number
  excluded: SplitExcludedSample[]
  preview: SplitPreviewRow[]
  /** 分组隔离是否已验证：Mock 环境下按来源名称分组，但无法验证真实内容相似度 */
  leakageRisk: 'grouped' | 'partial_group_split' | 'unverified'
  warnings: string[]
}

/* ===== 版本 ===== */

export interface DatasetVersion {
  versionId: string
  datasetId: string
  versionNumber: string
  sampleCount: number
  trainCount: number
  validationCount: number
  testCount: number
  versionDescription: string
  createdAt: string
  createdBy: string
  versionStatus: VersionStatus
  changeLog: string[]
  /** 创建版本时拆分的数据泄漏风险标记 */
  leakageRisk?: SplitResult['leakageRisk']
  /** 同源拆分人工复核状态；未拆分组时为 not_required */
  manualReviewStatus: ManualReviewStatus
  manualReviewedBy?: string
  manualReviewedAt?: string
  manualReviewNotes?: string
}

export interface CreateVersionInput {
  versionDescription: string
  changeLog: string
  splitConfig?: SplitConfig
}

export interface VersionCompareRow {
  label: string
  baseValue: string
  targetValue: string
  changed: boolean
}

/* ===== 训练资格 ===== */

export interface TrainingEligibility {
  sampleId: string
  eligible: boolean
  reasons: string[]
}

/* ===== 导入 ===== */

export interface ImportRow {
  rowId: string
  fileName: string
  sampleName: string
  modality: DatasetType
  formatValid: boolean
  formatIssue: string
  sourceType: SourceType
  sourceName: string
  sourceNote: string
  acquiredAt: string
  sourceProof: string
  licenseType: string
  licenseScope: string
  allowTraining: boolean
  allowModify: boolean
  validUntil: string
  licenseProof: string
  riskCategory: string
  evidenceText: string
  fileHash: string
}

export interface ImportSourceDraft {
  sourceType: SourceType
  sourceName: string
  sourceNote: string
  acquiredAt: string
  sourceProof: string
}

export interface ImportLicenseDraft {
  licenseType: string
  licenseScope: string
  allowTraining: boolean
  allowModify: boolean
  validUntil: string
  licenseProof: string
  reviewStatus: LicenseStatus
}

export interface ImportCandidate {
  rowId: string
  fileName: string
  formatValid: boolean
  hasSource: boolean
  hasLicense: boolean
  duplicate: boolean
  duplicateOf: string
  importable: boolean
  reasons: string[]
}

export interface ImportSummary {
  total: number
  formatErrorCount: number
  missingSourceCount: number
  missingLicenseCount: number
  duplicateCount: number
  importableCount: number
  candidates: ImportCandidate[]
}

export interface ImportResult {
  datasetId: string
  importedCount: number
  skippedCount: number
}

export interface CreateDatasetInput {
  datasetName: string
  datasetType: DatasetType
  description: string
}
