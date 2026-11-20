/**
 * A06 模型版本管理类型定义（P12）。
 *
 * 说明：
 * - 模型训练由独立 Python Worker 执行，本文件只描述模型仓库与发布流程的数据契约；
 * - 浏览器不执行量化、推理或模型文件读取；
 * - 当前仓库没有真实模型文件，所有模型产物均为演示记录：
 *   `isMock = true`、`fileHash = ''`、`signature = ''`，不得伪造已验证值。
 */

import type { DatasetVersion } from './dataset'
import type { ModelType, TrainingTaskStatus } from './training'

/* ===== 基础枚举 ===== */

export type ModelFramework = 'pytorch'

/** 模型文件格式：训练输出与后续导出格式 */
export type ModelFormat = 'pytorch_pt' | 'onnx' | 'tflite' | 'mnn'

/** 产物来源：真实训练 Worker 产出 / 演示记录 */
export type ArtifactSource = 'training_worker' | 'demo_record'

/** 校验状态：真实校验通过 / 未校验 / 校验失败 / 演示记录（无真实文件）/ 无可用文件 */
export type VerificationStatus = 'verified' | 'not_verified' | 'failed' | 'demo_record' | 'not_available'

export type QuantizationFormat = 'fp32' | 'fp16' | 'int8' | 'int4_dynamic'

export type QuantizationStatus = 'generated' | 'demo_record' | 'not_generated' | 'failed'

export type InferenceRuntime = 'onnxruntime_mobile' | 'tflite' | 'mnn' | 'pytorch_mobile'

export type AndroidArchitecture = 'arm64-v8a' | 'armeabi-v7a' | 'x86_64'

export type AcceleratorType = 'cpu' | 'gpu' | 'npu'

export type CompatibilityStatus =
  | 'untested'
  | 'testing'
  | 'compatible'
  | 'partially_compatible'
  | 'incompatible'
  | 'test_failed'

export type EvaluationStatus = 'not_evaluated' | 'demo' | 'evaluated'

export type EvaluationSource = 'training_task' | 'mock_demo'

export type ModelReleaseStatus =
  | 'draft'
  | 'pending_review'
  | 'approved'
  | 'rejected'
  | 'published'
  | 'withdrawn'
  | 'rolled_back'

export type ReleaseChannel = 'internal_test' | 'canary' | 'production'

export type ModelStatus = 'active' | 'archived'

/* ===== 模型产物 ===== */

export interface ModelArtifact {
  artifactId: string
  modelId: string
  modelName: string
  modelType: ModelType
  framework: ModelFramework
  artifactPath: string
  artifactFormat: ModelFormat
  /** 文件大小（字节）；演示记录为配置估值，不代表真实文件 */
  fileSizeBytes: number
  /** 真实文件哈希；没有真实文件时必须为空字符串 */
  fileHash: string
  fileHashStatus: VerificationStatus
  /** 数字签名；没有真实签名时必须为空字符串 */
  signature: string
  signatureStatus: VerificationStatus
  createdAt: string
  createdBy: string
  artifactSource: ArtifactSource
  isMock: boolean
  /** 真实后端扩展字段：签名密钥与复验信息 */
  signatureKeyLabel?: string
  signatureIsDevelopment?: boolean
  manifestPath?: string
  lastVerifiedAt?: string
}

/* ===== 量化 ===== */

export interface ModelQuantization {
  quantizationId: string
  format: QuantizationFormat
  /** 原始模型大小（字节） */
  originalSizeBytes: number
  /** 量化后大小；未生成时为 null */
  quantizedSizeBytes: number | null
  method: string
  quantizedAt: string | null
  /** 精度变化（百分点）；未实测时为 null，不得伪造提升 */
  accuracyDeltaPercent: number | null
  compatibleRuntimes: InferenceRuntime[]
  status: QuantizationStatus
  isMock: boolean
}

/* ===== 运行时兼容性 ===== */

export interface RuntimeCompatibility {
  compatibilityId: string
  platform: 'android'
  minOsVersion: string
  maxOsVersion: string
  architecture: AndroidArchitecture
  inferenceRuntime: InferenceRuntime
  runtimeVersion: string
  supportedDevices: string[]
  memoryRequirementMb: number
  acceleratorSupport: AcceleratorType[]
  compatibilityStatus: CompatibilityStatus
  testedAt: string
  testedBy: string
  notes: string
  /** true 表示未在真实设备上测试，仅为配置或演示记录 */
  isMock: boolean
}

/* ===== 评估 ===== */

export interface EvaluationSummary {
  accuracy: number | null
  precision: number | null
  recall: number | null
  f1Score: number | null
  validationLoss: number | null
  /** 测试集版本，必须与数据集版本对应 */
  testDatasetVersion: string
  evaluatedAt: string
  evaluationMethod: string
  evaluationStatus: EvaluationStatus
  source: EvaluationSource
}

/* ===== 模型版本 ===== */

export interface ModelVersion {
  modelVersionId: string
  modelId: string
  modelVersion: string
  datasetId: string
  datasetName: string
  datasetVersionId: string
  datasetVersion: string
  trainingTaskId: string
  artifactId: string
  modelFormat: ModelFormat
  quantization: ModelQuantization[]
  runtimeCompatibility: RuntimeCompatibility[]
  evaluationSummary: EvaluationSummary
  releaseStatus: ModelReleaseStatus
  createdAt: string
  createdBy: string
  reviewedAt: string
  reviewedBy: string
  publishedAt: string
  withdrawnAt: string
  description: string
  /** 演示版本：不代表真实模型能力 */
  isMock: boolean
  /** 以下为真实后端扩展字段（Mock 模式下可能为空） */
  artifactSource?: ArtifactSource | ''
  trainingProfile?: string
  datasetType?: string
  datasetOrigin?: 'real' | 'synthetic' | ''
  productionReady?: boolean
  publishBlockedReason?: string
}

/* ===== 模型仓库 ===== */

export interface ModelSummary {
  modelId: string
  modelName: string
  modelType: ModelType
  framework: ModelFramework
  description: string
  latestVersion: string
  latestVersionId: string
  /** 当前发布版本（按发布时间最新）；没有发布版本时为空 */
  publishedVersion: string
  publishedVersionId: string
  /** 最近一次关联的训练任务 */
  trainingTaskId: string
  trainingSourceLabel: string
  releaseStatus: ModelReleaseStatus
  compatibilityStatus: CompatibilityStatus
  modelStatus: ModelStatus
  updatedAt: string
  versionCount: number
  isMock: boolean
  /** 真实后端扩展字段 */
  trainingProfile?: string
  datasetOrigin?: 'real' | 'synthetic' | ''
  productionReady?: boolean
}

export interface ModelStats {
  totalModels: number
  totalVersions: number
  pendingReview: number
  published: number
  withdrawn: number
  compatibilityUntested: number
}

export interface ModelListQuery {
  keyword: string
  modelType: ModelType | ''
  framework: ModelFramework | ''
  releaseStatus: ModelReleaseStatus | ''
  compatibilityStatus: CompatibilityStatus | ''
  publishedFrom: string
  publishedTo: string
  page: number
  pageSize: number
}

export interface ModelVersionQuery {
  keyword: string
  releaseStatus: ModelReleaseStatus | ''
  compatibilityStatus: CompatibilityStatus | ''
  page: number
  pageSize: number
}

export interface ModelVersionCompareRow {
  label: string
  baseValue: string
  targetValue: string
  changed: boolean
}

/** 从真实训练任务导入模型产物（P12） */
export interface ModelImportInput {
  trainingTaskId: string
  modelName: string
  description: string
  operator: string
  roleLabel: string
}

/* ===== 审核检查 ===== */

export type ReviewCheckKey =
  | 'training_status'
  | 'dataset_version'
  | 'dataset_review'
  | 'leakage_review'
  | 'artifact_exists'
  | 'file_integrity'
  | 'signature'
  | 'evaluation'
  | 'test_evaluation'
  | 'runtime_compatibility'
  | 'publish_permission'

export type ReviewCheckStatus = 'passed' | 'failed' | 'unverified' | 'not_applicable'

export interface ModelReviewCheck {
  key: ReviewCheckKey
  label: string
  status: ReviewCheckStatus
  detail: string
  /** 该检查在当前发布渠道下是否阻断发布 */
  blocking: boolean
}

export interface ModelReviewResult {
  modelVersionId: string
  channel: ReleaseChannel
  checks: ModelReviewCheck[]
  /** 是否满足当前渠道的放行条件 */
  eligible: boolean
  blockedReasons: string[]
  checkedAt: string
  note: string
}

/* ===== 发布记录与审计 ===== */

export interface ReleaseRecord {
  releaseId: string
  modelId: string
  modelVersionId: string
  modelVersion: string
  releaseChannel: ReleaseChannel
  releaseStatus: ModelReleaseStatus
  operator: string
  reviewedBy: string
  submittedAt: string
  approvedAt: string
  publishedAt: string
  withdrawnAt: string
  /** 回滚来源版本（本次回滚时正在分发的版本） */
  rollbackFrom: string
  /** 回滚目标版本 */
  rollbackTo: string
  operationNotes: string
  isMock: boolean
}

export type ModelAuditOperation =
  | 'create_version'
  | 'submit_review'
  | 'approve'
  | 'reject'
  | 'publish'
  | 'withdraw'
  | 'rollback'
  | 'hash_verify_failed'
  | 'signature_verify_failed'
  | 'compatibility_test_failed'

export type AuditResult = 'success' | 'failed' | 'blocked'

export interface ModelAuditRecord {
  auditId: string
  operator: string
  operation: ModelAuditOperation
  operationLabel: string
  targetType: 'model' | 'model_version' | 'release'
  targetId: string
  targetLabel: string
  operatedAt: string
  result: AuditResult
  notes: string
  isMock: boolean
}

/* ===== 选项与查询辅助 ===== */

export interface ModelOptions {
  modelTypes: Array<{ value: ModelType; label: string }>
  frameworks: Array<{ value: ModelFramework; label: string }>
  modelFormats: Array<{ value: ModelFormat; label: string }>
  releaseStatuses: Array<{ value: ModelReleaseStatus; label: string }>
  compatibilityStatuses: Array<{ value: CompatibilityStatus; label: string }>
  channels: Array<{ value: ReleaseChannel; label: string; note: string }>
  quantizationFormats: Array<{ value: QuantizationFormat; label: string }>
  datasetVersions: Array<{
    datasetId: string
    datasetName: string
    versionId: string
    versionNumber: string
    versionStatus: string
    manualReviewStatus: string
  }>
  trainingTasks: Array<{
    taskId: string
    taskName: string
    taskStatus: TrainingTaskStatus
    executionMode: string
  }>
}

/* ===== 标签与权限 ===== */

export const RELEASE_STATUS_LABELS: Record<ModelReleaseStatus, string> = {
  draft: '草稿',
  pending_review: '待审核',
  approved: '审核通过',
  rejected: '审核拒绝',
  published: '已发布',
  withdrawn: '已撤回',
  rolled_back: '已回滚',
}

export const RELEASE_CHANNEL_LABELS: Record<ReleaseChannel, string> = {
  internal_test: '内部测试（演示）',
  canary: '灰度发布',
  production: '正式发布',
}

export const COMPATIBILITY_STATUS_LABELS: Record<CompatibilityStatus, string> = {
  untested: '未测试',
  testing: '测试中',
  compatible: '兼容',
  partially_compatible: '部分兼容',
  incompatible: '不兼容',
  test_failed: '测试失败',
}

export const VERIFICATION_STATUS_LABELS: Record<VerificationStatus, string> = {
  verified: '已验证',
  not_verified: '未验证',
  failed: '验证失败',
  demo_record: '演示记录（无真实文件）',
  not_available: '无可用文件',
}

export const QUANTIZATION_FORMAT_LABELS: Record<QuantizationFormat, string> = {
  fp32: 'FP32',
  fp16: 'FP16',
  int8: 'INT8',
  int4_dynamic: 'INT4（动态）',
}

export const QUANTIZATION_STATUS_LABELS: Record<QuantizationStatus, string> = {
  generated: '已生成',
  demo_record: '演示记录',
  not_generated: '未生成',
  failed: '生成失败',
}

export const MODEL_FORMAT_LABELS: Record<ModelFormat, string> = {
  pytorch_pt: 'PyTorch（.pt）',
  onnx: 'ONNX',
  tflite: 'TFLite',
  mnn: 'MNN',
}

export const MODEL_AUDIT_LABELS: Record<ModelAuditOperation, string> = {
  create_version: '创建版本',
  submit_review: '提交审核',
  approve: '审核通过',
  reject: '审核拒绝',
  publish: '发布',
  withdraw: '撤回',
  rollback: '回滚',
  hash_verify_failed: '文件校验失败',
  signature_verify_failed: '签名校验失败',
  compatibility_test_failed: '兼容性测试失败',
}

/** 具备模型审核 / 发布权限的管理端角色（真实系统由后端 RBAC 判定） */
export const MODEL_RELEASE_ROLES = ['系统管理员', '发布管理员'] as const

export const MODEL_TYPE_LABELS: Record<ModelType, string> = {
  text_risk_classifier: '文本风险分类',
  screen_multimodal_risk: '屏幕多模态风险',
  audio_call_risk: '通话语音风险',
  video_scam_risk: '短视频风险',
}

export function canManageModelRelease(roleLabel: string | null | undefined): boolean {
  if (!roleLabel) return false
  return MODEL_RELEASE_ROLES.some((role) => roleLabel.includes(role))
}

/** 供页面展示的数据集版本信息（只读快照） */
export interface ModelDatasetContext {
  datasetId: string
  datasetName: string
  datasetStatus: string
  version: DatasetVersion | null
}

/** 模型版本关联数据集的服务端快照（P13 统一真实数据来源） */
export interface ModelDatasetSnapshot {
  datasetId: string
  datasetName: string
  datasetType: string
  versionId: string
  versionNumber: string
  versionStatus: string
  manualReviewStatus: string
  sampleCount: number
  trainCount: number
  validationCount: number
  testCount: number
  sourceStatus: string
  licenseStatus: string
  qualityStatus: string
  duplicateStatus: string
  leakageRisk: string
  snapshotSource: string
  revoked: boolean
  checkedAt: string
  snapshotCreatedAt: string
  eligible: boolean
  blockedReasons: string[]
  note: string
  dataSource: string
}
