import { ApiError } from '../http'
import type { PageResult } from '../types'
import type {
  CompatibilityStatus,
  ModelArtifact,
  ModelListQuery,
  ModelOptions,
  ModelReleaseStatus,
  ModelReviewCheck,
  ModelReviewResult,
  ModelStats,
  ModelSummary,
  ModelVersion,
  ModelVersionCompareRow,
  ModelVersionQuery,
  ReleaseChannel,
  ReleaseRecord,
  ModelAuditRecord,
} from '@/types/model'
import {
  COMPATIBILITY_STATUS_LABELS,
  MODEL_FORMAT_LABELS,
  QUANTIZATION_FORMAT_LABELS,
  RELEASE_STATUS_LABELS,
  canManageModelRelease,
} from '@/types/model'
import { datasetVersions } from './datasetData'
import { getDatasetDetail } from './datasetAdapter'
import { trainingTasks } from './trainingData'
import {
  modelArtifacts,
  modelAudits,
  modelVersions,
  models,
  nowText,
  releaseRecords,
} from './modelData'

/**
 * A06 模型版本管理 Mock 业务实现（P12）。
 *
 * 约束：
 * - 所有模型均标记 isMock，没有真实模型文件；
 * - 审核与发布在此处执行“服务端”二次校验；
 * - 正式渠道（灰度 / 正式）要求真实文件与签名，演示环境必然被阻断；
 * - 内部测试渠道允许演示发布，但记录明确标记为演示，不产生真实可用模型。
 */

const EXECUTION_SCOPE = '演示环境'

let releaseSeq = 100
let auditSeq = 100

function nextReleaseId(): string {
  releaseSeq += 1
  return `RL-2026-${String(releaseSeq).padStart(3, '0')}`
}

function nextAuditId(): string {
  auditSeq += 1
  return `AU-2026-${String(auditSeq).padStart(3, '0')}`
}

export function findModel(modelId: string): ModelSummary {
  const model = models.find((item) => item.modelId === modelId)
  if (!model) {
    throw new ApiError(404, 'MODEL_NOT_FOUND', '模型不存在或已被删除')
  }
  return model
}

export function findModelVersion(modelVersionId: string): ModelVersion {
  const version = modelVersions.find((item) => item.modelVersionId === modelVersionId)
  if (!version) {
    throw new ApiError(404, 'MODEL_VERSION_NOT_FOUND', '模型版本不存在')
  }
  return version
}

export function findArtifact(artifactId: string): ModelArtifact {
  const artifact = modelArtifacts.find((item) => item.artifactId === artifactId)
  if (!artifact) {
    throw new ApiError(404, 'ARTIFACT_NOT_FOUND', '模型文件记录不存在')
  }
  return artifact
}

export function listArtifacts(modelId?: string): ModelArtifact[] {
  return modelArtifacts.filter((item) => (modelId ? item.modelId === modelId : true))
}

/** 演示环境的文件大小格式化 */
export function formatFileSize(bytes: number): string {
  if (!bytes) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function isFormalChannel(channel: ReleaseChannel): boolean {
  return channel === 'canary' || channel === 'production'
}

/* ===== 统计与列表 ===== */

export function getModelStats(): ModelStats {
  return {
    totalModels: models.length,
    totalVersions: modelVersions.length,
    pendingReview: modelVersions.filter((item) => item.releaseStatus === 'pending_review').length,
    published: modelVersions.filter((item) => item.releaseStatus === 'published').length,
    withdrawn: modelVersions.filter((item) => item.releaseStatus === 'withdrawn').length,
    compatibilityUntested: modelVersions.filter((item) =>
      item.runtimeCompatibility.some((compat) => compat.compatibilityStatus === 'untested'),
    ).length,
  }
}

export function listModels(query: ModelListQuery): PageResult<ModelSummary> {
  const filtered = models.filter((model) => {
    if (query.keyword) {
      const keyword = query.keyword.trim().toLowerCase()
      const hit =
        model.modelName.toLowerCase().includes(keyword) ||
        model.modelId.toLowerCase().includes(keyword) ||
        model.latestVersion.toLowerCase().includes(keyword)
      if (!hit) return false
    }
    if (query.modelType && model.modelType !== query.modelType) return false
    if (query.framework && model.framework !== query.framework) return false
    if (query.releaseStatus && model.releaseStatus !== query.releaseStatus) return false
    if (query.compatibilityStatus && model.compatibilityStatus !== query.compatibilityStatus) return false
    if (query.publishedFrom || query.publishedTo) {
      const published = modelVersions.filter(
        (item) => item.modelId === model.modelId && item.publishedAt,
      )
      const hit = published.some((item) => {
        const day = item.publishedAt.slice(0, 10)
        if (query.publishedFrom && day < query.publishedFrom.slice(0, 10)) return false
        if (query.publishedTo && day > query.publishedTo.slice(0, 10)) return false
        return true
      })
      if (!hit) return false
    }
    return true
  })

  const start = (query.page - 1) * query.pageSize
  return {
    items: filtered.slice(start, start + query.pageSize),
    total: filtered.length,
    page: query.page,
    pageSize: query.pageSize,
  }
}

export function getModelDetail(modelId: string): ModelSummary {
  return findModel(modelId)
}

export function listModelVersions(
  modelId: string,
  query: ModelVersionQuery,
): PageResult<ModelVersion> {
  findModel(modelId)
  const filtered = modelVersions
    .filter((item) => item.modelId === modelId)
    .filter((item) => {
      if (query.keyword) {
        const keyword = query.keyword.trim().toLowerCase()
        const hit =
          item.modelVersion.toLowerCase().includes(keyword) ||
          item.trainingTaskId.toLowerCase().includes(keyword) ||
          item.modelVersionId.toLowerCase().includes(keyword)
        if (!hit) return false
      }
      if (query.releaseStatus && item.releaseStatus !== query.releaseStatus) return false
      if (
        query.compatibilityStatus &&
        !item.runtimeCompatibility.some(
          (compat) => compat.compatibilityStatus === query.compatibilityStatus,
        )
      ) {
        return false
      }
      return true
    })
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))

  const start = (query.page - 1) * query.pageSize
  return {
    items: filtered.slice(start, start + query.pageSize),
    total: filtered.length,
    page: query.page,
    pageSize: query.pageSize,
  }
}

export function getAllModelVersions(modelId: string): ModelVersion[] {
  return modelVersions.filter((item) => item.modelId === modelId)
}

/* ===== 选项 ===== */

export function getModelOptions(): ModelOptions {
  return {
    modelTypes: [
      { value: 'text_risk_classifier', label: '文本风险分类' },
      { value: 'screen_multimodal_risk', label: '屏幕多模态风险' },
      { value: 'audio_call_risk', label: '通话语音风险' },
      { value: 'video_scam_risk', label: '短视频风险' },
    ],
    frameworks: [{ value: 'pytorch', label: 'PyTorch' }],
    modelFormats: [
      { value: 'pytorch_pt', label: MODEL_FORMAT_LABELS.pytorch_pt },
      { value: 'onnx', label: MODEL_FORMAT_LABELS.onnx },
      { value: 'tflite', label: MODEL_FORMAT_LABELS.tflite },
      { value: 'mnn', label: MODEL_FORMAT_LABELS.mnn },
    ],
    releaseStatuses: (Object.keys(RELEASE_STATUS_LABELS) as ModelReleaseStatus[]).map((value) => ({
      value,
      label: RELEASE_STATUS_LABELS[value],
    })),
    compatibilityStatuses: (Object.keys(COMPATIBILITY_STATUS_LABELS) as CompatibilityStatus[]).map(
      (value) => ({ value, label: COMPATIBILITY_STATUS_LABELS[value] }),
    ),
    channels: [
      { value: 'internal_test', label: '内部测试（演示）', note: '仅演示环境可用，不向真实设备下发。' },
      { value: 'canary', label: '灰度发布', note: '需要真实模型文件、签名校验与完整兼容性测试。' },
      { value: 'production', label: '正式发布', note: '需要全部审核项通过并由服务端二次校验。' },
    ],
    quantizationFormats: [
      { value: 'fp32', label: QUANTIZATION_FORMAT_LABELS.fp32 },
      { value: 'fp16', label: QUANTIZATION_FORMAT_LABELS.fp16 },
      { value: 'int8', label: QUANTIZATION_FORMAT_LABELS.int8 },
      { value: 'int4_dynamic', label: QUANTIZATION_FORMAT_LABELS.int4_dynamic },
    ],
    datasetVersions: datasetVersions.map((item) => ({
      datasetId: item.datasetId,
      datasetName: getDatasetDetail(item.datasetId).datasetName,
      versionId: item.versionId,
      versionNumber: item.versionNumber,
      versionStatus: item.versionStatus,
      manualReviewStatus: item.manualReviewStatus,
    })),
    trainingTasks: trainingTasks.map((item) => ({
      taskId: item.taskId,
      taskName: item.taskName,
      taskStatus: item.taskStatus,
      executionMode: item.executionMode,
    })),
  }
}

/* ===== 资格与审核 ===== */

function reviewCheck(
  key: ModelReviewCheck['key'],
  label: string,
  status: ModelReviewCheck['status'],
  detail: string,
  blocking: boolean,
): ModelReviewCheck {
  return { key, label, status, detail, blocking }
}

/**
 * 生成审核清单。
 *
 * 内部测试（演示）渠道允许“演示记录”类检查以 unverified 通过（blocking = false），
 * 因为该渠道明确不产生真实可用模型；灰度 / 正式渠道必须全部真实校验通过。
 */
export function evaluateModelVersion(
  modelVersionId: string,
  channel: ReleaseChannel,
  roleLabel: string,
): ModelReviewResult {
  const version = findModelVersion(modelVersionId)
  const formal = isFormalChannel(channel)
  const checks: ModelReviewCheck[] = []

  // 1. 训练任务状态
  const task = trainingTasks.find((item) => item.taskId === version.trainingTaskId)
  if (!version.trainingTaskId) {
    checks.push(
      reviewCheck('training_status', '关联训练任务成功', 'failed', '该版本未关联训练任务', true),
    )
  } else if (!task) {
    checks.push(
      reviewCheck('training_status', '关联训练任务成功', 'failed', `训练任务 ${version.trainingTaskId} 不存在`, true),
    )
  } else if (task.taskStatus !== 'succeeded') {
    checks.push(
      reviewCheck(
        'training_status',
        '关联训练任务成功',
        'failed',
        `训练任务 ${task.taskId} 状态为 ${task.taskStatus}，不能作为发布依据`,
        true,
      ),
    )
  } else {
    checks.push(
      reviewCheck(
        'training_status',
        '关联训练任务成功',
        'passed',
        `训练任务 ${task.taskId} 已成功（执行模式：${task.executionMode === 'mock' ? '模拟' : '真实 Worker'}）`,
        true,
      ),
    )
  }

  // 2. 数据集版本有效
  const datasetVersion = datasetVersions.find((item) => item.versionId === version.datasetVersionId)
  if (!datasetVersion) {
    checks.push(
      reviewCheck('dataset_version', '数据集版本有效', 'failed', '数据集版本记录不存在', true),
    )
  } else if (datasetVersion.versionStatus !== 'published') {
    checks.push(
      reviewCheck(
        'dataset_version',
        '数据集版本有效',
        'failed',
        `数据集版本 ${datasetVersion.versionNumber} 状态为 ${datasetVersion.versionStatus}，未发布`,
        true,
      ),
    )
  } else {
    checks.push(
      reviewCheck(
        'dataset_version',
        '数据集版本有效',
        'passed',
        `数据集版本 ${datasetVersion.versionNumber} 已发布`,
        true,
      ),
    )
  }

  // 3. 数据集必要审核
  let datasetStatus = 'unknown'
  try {
    datasetStatus = getDatasetDetail(version.datasetId).datasetStatus
  } catch {
    datasetStatus = 'unknown'
  }
  if (datasetStatus === 'quality_blocked' || datasetStatus === 'archived' || datasetStatus === 'unknown') {
    checks.push(
      reviewCheck(
        'dataset_review',
        '数据集通过必要审核',
        'failed',
        `数据集 ${version.datasetName} 状态为 ${datasetStatus}，不满足发布要求`,
        true,
      ),
    )
  } else {
    checks.push(
      reviewCheck('dataset_review', '数据集通过必要审核', 'passed', `数据集状态：${datasetStatus}`, true),
    )
  }

  // 4. 同源泄漏人工复核
  if (datasetVersion && datasetVersion.manualReviewStatus === 'pending') {
    checks.push(
      reviewCheck(
        'leakage_review',
        '同源数据泄漏人工复核',
        'failed',
        '数据集版本存在未完成的同源拆分人工复核',
        true,
      ),
    )
  } else {
    checks.push(
      reviewCheck(
        'leakage_review',
        '同源数据泄漏人工复核',
        datasetVersion ? 'passed' : 'failed',
        datasetVersion ? '无待处理的人工复核事项' : '无法确认复核状态',
        true,
      ),
    )
  }

  // 5. 模型文件存在
  const artifact = modelArtifacts.find((item) => item.artifactId === version.artifactId)
  if (!artifact) {
    checks.push(
      reviewCheck('artifact_exists', '模型文件存在', 'failed', '模型文件记录不存在', true),
    )
  } else if (artifact.artifactSource === 'demo_record') {
    checks.push(
      reviewCheck(
        'artifact_exists',
        '模型文件存在',
        'unverified',
        '当前仅有演示记录，没有真实模型文件',
        formal,
      ),
    )
  } else {
    checks.push(
      reviewCheck('artifact_exists', '模型文件存在', 'passed', artifact.artifactPath, true),
    )
  }

  // 6. 文件完整性
  if (!artifact || artifact.fileHashStatus === 'demo_record' || artifact.fileHashStatus === 'not_available') {
    checks.push(
      reviewCheck(
        'file_integrity',
        '文件完整性验证',
        'unverified',
        '没有真实模型文件，无法进行哈希校验',
        formal,
      ),
    )
  } else if (artifact.fileHashStatus === 'verified') {
    checks.push(reviewCheck('file_integrity', '文件完整性验证', 'passed', '文件哈希校验通过', true))
  } else {
    checks.push(
      reviewCheck('file_integrity', '文件完整性验证', 'failed', '文件哈希校验失败', true),
    )
  }

  // 7. 数字签名
  if (!artifact || artifact.signatureStatus === 'demo_record' || artifact.signatureStatus === 'not_available') {
    checks.push(
      reviewCheck(
        'signature',
        '数字签名验证',
        'unverified',
        '没有真实签名，无法验证模型来源',
        formal,
      ),
    )
  } else if (artifact.signatureStatus === 'verified') {
    checks.push(reviewCheck('signature', '数字签名验证', 'passed', '签名验证通过', true))
  } else {
    checks.push(reviewCheck('signature', '数字签名验证', 'failed', '签名验证失败', true))
  }

  // 8. 模型评估
  const evaluation = version.evaluationSummary
  if (evaluation.evaluationStatus === 'not_evaluated') {
    checks.push(
      reviewCheck('evaluation', '模型评估完成', 'failed', '尚未完成模型评估', true),
    )
  } else if (evaluation.evaluationStatus === 'demo') {
    checks.push(
      reviewCheck(
        'evaluation',
        '模型评估完成',
        'unverified',
        '当前为演示指标，未执行真实推理评估',
        formal,
      ),
    )
  } else {
    checks.push(
      reviewCheck('evaluation', '模型评估完成', 'passed', evaluation.evaluationMethod, true),
    )
  }

  // 9. 测试集评估有效
  if (!evaluation.testDatasetVersion) {
    checks.push(
      reviewCheck('test_evaluation', '测试集评估有效', 'failed', '未记录测试集版本', true),
    )
  } else if (evaluation.evaluationStatus === 'demo') {
    checks.push(
      reviewCheck(
        'test_evaluation',
        '测试集评估有效',
        'unverified',
        `测试集 ${evaluation.testDatasetVersion} 仅有演示指标`,
        formal,
      ),
    )
  } else {
    checks.push(
      reviewCheck(
        'test_evaluation',
        '测试集评估有效',
        'passed',
        `测试集版本：${evaluation.testDatasetVersion}`,
        true,
      ),
    )
  }

  // 10. 运行时兼容性
  const compatStatuses = version.runtimeCompatibility.map((item) => item.compatibilityStatus)
  if (compatStatuses.length === 0 || compatStatuses.includes('untested')) {
    checks.push(
      reviewCheck(
        'runtime_compatibility',
        '目标运行时兼容性',
        'unverified',
        '存在未测试的运行时兼容性记录',
        true,
      ),
    )
  } else if (compatStatuses.includes('test_failed') || compatStatuses.includes('incompatible')) {
    checks.push(
      reviewCheck(
        'runtime_compatibility',
        '目标运行时兼容性',
        'failed',
        '存在兼容性测试失败或不兼容记录',
        true,
      ),
    )
  } else if (compatStatuses.every((status) => status === 'compatible')) {
    checks.push(
      reviewCheck('runtime_compatibility', '目标运行时兼容性', 'passed', '目标运行时为兼容状态', true),
    )
  } else {
    checks.push(
      reviewCheck(
        'runtime_compatibility',
        '目标运行时兼容性',
        formal ? 'failed' : 'passed',
        formal ? '存在部分兼容记录，不满足正式发布要求' : '存在部分兼容记录，仅允许内部测试',
        true,
      ),
    )
  }

  // 11. 发布权限
  if (canManageModelRelease(roleLabel)) {
    checks.push(
      reviewCheck('publish_permission', '发布审批权限', 'passed', `操作角色：${roleLabel}`, true),
    )
  } else {
    checks.push(
      reviewCheck(
        'publish_permission',
        '发布审批权限',
        'failed',
        `当前角色（${roleLabel || '未知'}）不具备模型发布权限`,
        true,
      ),
    )
  }

  const blockedReasons = checks
    .filter((item) => item.blocking && item.status !== 'passed')
    .map((item) => `${item.label}：${item.detail}`)

  return {
    modelVersionId,
    channel,
    checks,
    eligible: blockedReasons.length === 0,
    blockedReasons,
    checkedAt: nowText(),
    note: formal
      ? '灰度 / 正式发布要求真实模型文件、签名与完整设备测试；当前演示环境不具备这些条件。'
      : '内部测试为演示渠道：不产生真实可用模型，也不会向任何 Android 设备下发。',
  }
}

export interface ReviewInput {
  channel: ReleaseChannel
  notes: string
  operator: string
  roleLabel: string
}

function appendAudit(
  operator: string,
  operation: ModelAuditRecord['operation'],
  operationLabel: string,
  version: ModelVersion,
  result: ModelAuditRecord['result'],
  notes: string,
): void {
  modelAudits.unshift({
    auditId: nextAuditId(),
    operator,
    operation,
    operationLabel,
    targetType: 'model_version',
    targetId: version.modelVersionId,
    targetLabel: `${version.modelVersion}`,
    operatedAt: nowText(),
    result,
    notes,
    isMock: true,
  })
}

export function submitModelReview(modelVersionId: string, input: ReviewInput): ReleaseRecord {
  const version = findModelVersion(modelVersionId)
  if (!['draft', 'rejected', 'pending_review'].includes(version.releaseStatus)) {
    throw new ApiError(409, 'MODEL_STATE_INVALID', '当前状态不允许提交审核')
  }
  version.releaseStatus = 'pending_review'
  version.reviewedAt = ''
  version.reviewedBy = ''

  const record: ReleaseRecord = {
    releaseId: nextReleaseId(),
    modelId: version.modelId,
    modelVersionId: version.modelVersionId,
    modelVersion: version.modelVersion,
    releaseChannel: input.channel,
    releaseStatus: 'pending_review',
    operator: input.operator,
    reviewedBy: '',
    submittedAt: nowText(),
    approvedAt: '',
    publishedAt: '',
    withdrawnAt: '',
    rollbackFrom: '',
    rollbackTo: '',
    operationNotes: input.notes || '演示提交审核',
    isMock: true,
  }
  releaseRecords.unshift(record)
  appendAudit(
    input.operator,
    'submit_review',
    '提交审核',
    version,
    'success',
    `${RELEASE_STATUS_LABELS.pending_review} · 渠道：${input.channel}`,
  )
  return record
}

export function approveModelReview(modelVersionId: string, input: ReviewInput): ReleaseRecord {
  const version = findModelVersion(modelVersionId)
  if (version.releaseStatus !== 'pending_review') {
    throw new ApiError(409, 'MODEL_STATE_INVALID', '只有待审核版本可以执行审核通过')
  }
  const result = evaluateModelVersion(modelVersionId, input.channel, input.roleLabel)
  if (!result.eligible) {
    appendAudit(
      input.operator,
      'approve',
      '审核通过',
      version,
      'blocked',
      result.blockedReasons.join('；'),
    )
    throw new ApiError(422, 'MODEL_REVIEW_BLOCKED', `审核未通过：${result.blockedReasons.join('；')}`)
  }

  version.releaseStatus = 'approved'
  version.reviewedAt = nowText()
  version.reviewedBy = input.operator

  const pending = releaseRecords.find(
    (item) => item.modelVersionId === modelVersionId && item.releaseStatus === 'pending_review',
  )
  if (pending) {
    pending.releaseStatus = 'approved'
    pending.reviewedBy = input.operator
    pending.approvedAt = version.reviewedAt
    pending.operationNotes = input.notes || pending.operationNotes
  }

  appendAudit(input.operator, 'approve', '审核通过', version, 'success', input.notes || '演示审核通过')
  return (
    pending ?? {
      releaseId: nextReleaseId(),
      modelId: version.modelId,
      modelVersionId: version.modelVersionId,
      modelVersion: version.modelVersion,
      releaseChannel: input.channel,
      releaseStatus: 'approved',
      operator: input.operator,
      reviewedBy: input.operator,
      submittedAt: nowText(),
      approvedAt: version.reviewedAt,
      publishedAt: '',
      withdrawnAt: '',
      rollbackFrom: '',
      rollbackTo: '',
      operationNotes: input.notes,
      isMock: true,
    }
  )
}

export function rejectModelReview(
  modelVersionId: string,
  reviewer: string,
  roleLabel: string,
  reason: string,
): ModelVersion {
  const version = findModelVersion(modelVersionId)
  if (version.releaseStatus !== 'pending_review') {
    throw new ApiError(409, 'MODEL_STATE_INVALID', '只有待审核版本可以执行审核拒绝')
  }
  if (!canManageModelRelease(roleLabel)) {
    throw new ApiError(403, 'MODEL_PERMISSION_DENIED', '当前角色不具备审核权限')
  }
  version.releaseStatus = 'rejected'
  version.reviewedAt = nowText()
  version.reviewedBy = reviewer

  const pending = releaseRecords.find(
    (item) => item.modelVersionId === modelVersionId && item.releaseStatus === 'pending_review',
  )
  if (pending) {
    pending.releaseStatus = 'rejected'
    pending.reviewedBy = reviewer
    pending.operationNotes = reason
  }
  appendAudit(reviewer, 'reject', '审核拒绝', version, 'success', reason)
  return version
}

export interface PublishInput {
  channel: ReleaseChannel
  deviceScope: string
  notes: string
  operator: string
  roleLabel: string
}

export function publishModelVersion(modelVersionId: string, input: PublishInput): ReleaseRecord {
  const version = findModelVersion(modelVersionId)

  if (!canManageModelRelease(input.roleLabel)) {
    appendAudit(
      input.operator,
      'publish',
      '发布',
      version,
      'blocked',
      `角色 ${input.roleLabel} 无发布权限`,
    )
    throw new ApiError(403, 'MODEL_PERMISSION_DENIED', '当前角色不具备模型发布权限')
  }
  if (version.releaseStatus !== 'approved') {
    throw new ApiError(409, 'MODEL_STATE_INVALID', '只有审核通过的版本可以发布')
  }

  const result = evaluateModelVersion(modelVersionId, input.channel, input.roleLabel)
  if (!result.eligible) {
    appendAudit(
      input.operator,
      'publish',
      '发布',
      version,
      'blocked',
      result.blockedReasons.join('；'),
    )
    throw new ApiError(422, 'MODEL_RELEASE_BLOCKED', `发布前置条件未满足：${result.blockedReasons.join('；')}`)
  }

  const publishedAt = nowText()
  version.releaseStatus = 'published'
  version.publishedAt = publishedAt

  const record: ReleaseRecord = {
    releaseId: nextReleaseId(),
    modelId: version.modelId,
    modelVersionId: version.modelVersionId,
    modelVersion: version.modelVersion,
    releaseChannel: input.channel,
    releaseStatus: 'published',
    operator: input.operator,
    reviewedBy: version.reviewedBy,
    submittedAt: publishedAt,
    approvedAt: version.reviewedAt,
    publishedAt,
    withdrawnAt: '',
    rollbackFrom: '',
    rollbackTo: '',
    operationNotes: `${input.notes || '演示发布'}${input.deviceScope ? ` · 适配范围：${input.deviceScope}` : ''}（${EXECUTION_SCOPE}）`,
    isMock: true,
  }
  releaseRecords.unshift(record)
  appendAudit(
    input.operator,
    'publish',
    '发布',
    version,
    'success',
    `渠道：${input.channel}；演示发布，不向真实设备下发`,
  )
  return record
}

export interface WithdrawInput {
  reason: string
  impactScope: string
  operator: string
  roleLabel: string
}

export function withdrawModelVersion(modelVersionId: string, input: WithdrawInput): ModelVersion {
  const version = findModelVersion(modelVersionId)
  if (!canManageModelRelease(input.roleLabel)) {
    throw new ApiError(403, 'MODEL_PERMISSION_DENIED', '当前角色不具备撤回权限')
  }
  if (version.releaseStatus !== 'published') {
    throw new ApiError(409, 'MODEL_STATE_INVALID', '只有已发布版本可以撤回')
  }
  if (!input.reason.trim()) {
    throw new ApiError(400, 'WITHDRAW_REASON_REQUIRED', '请填写撤回原因')
  }

  const withdrawnAt = nowText()
  version.releaseStatus = 'withdrawn'
  version.withdrawnAt = withdrawnAt

  const published = releaseRecords.find(
    (item) => item.modelVersionId === modelVersionId && item.releaseStatus === 'published',
  )
  if (published) {
    published.releaseStatus = 'withdrawn'
    published.withdrawnAt = withdrawnAt
    published.operationNotes = `${published.operationNotes}；撤回原因：${input.reason}；影响范围：${input.impactScope || '未填写'}`
  }

  appendAudit(
    input.operator,
    'withdraw',
    '撤回',
    version,
    'success',
    `原因：${input.reason}；影响范围：${input.impactScope || '未填写'}；仅停止分发，不涉及设备端卸载`,
  )
  return version
}

export interface RollbackInput {
  fromVersionId: string
  toVersionId: string
  operator: string
  roleLabel: string
  notes: string
}

export function rollbackModelVersion(modelId: string, input: RollbackInput): ReleaseRecord {
  findModel(modelId)
  if (!canManageModelRelease(input.roleLabel)) {
    throw new ApiError(403, 'MODEL_PERMISSION_DENIED', '当前角色不具备回滚权限')
  }
  if (input.fromVersionId === input.toVersionId) {
    throw new ApiError(400, 'ROLLBACK_TARGET_INVALID', '回滚目标不能与当前发布版本相同')
  }

  const from = modelVersions.find(
    (item) => item.modelId === modelId && item.modelVersionId === input.fromVersionId,
  )
  if (!from || from.releaseStatus !== 'published') {
    throw new ApiError(409, 'ROLLBACK_SOURCE_INVALID', '当前发布版本不存在或已不是发布状态')
  }
  const target = modelVersions.find(
    (item) => item.modelId === modelId && item.modelVersionId === input.toVersionId,
  )
  if (!target) {
    throw new ApiError(404, 'ROLLBACK_TARGET_NOT_FOUND', '回滚目标版本不存在')
  }
  if (!['approved', 'published'].includes(target.releaseStatus)) {
    throw new ApiError(409, 'ROLLBACK_TARGET_INVALID', '回滚目标版本尚未审核通过，不能作为回滚目标')
  }

  const result = evaluateModelVersion(target.modelVersionId, 'internal_test', input.roleLabel)
  if (!result.eligible) {
    appendAudit(
      input.operator,
      'rollback',
      '回滚',
      target,
      'blocked',
      result.blockedReasons.join('；'),
    )
    throw new ApiError(422, 'ROLLBACK_TARGET_NOT_READY', `回滚目标不满足要求：${result.blockedReasons.join('；')}`)
  }

  const publishedAt = nowText()
  from.releaseStatus = 'rolled_back'
  target.releaseStatus = 'published'
  target.publishedAt = publishedAt

  const record: ReleaseRecord = {
    releaseId: nextReleaseId(),
    modelId,
    modelVersionId: target.modelVersionId,
    modelVersion: target.modelVersion,
    releaseChannel: 'internal_test',
    releaseStatus: 'published',
    operator: input.operator,
    reviewedBy: target.reviewedBy,
    submittedAt: publishedAt,
    approvedAt: target.reviewedAt,
    publishedAt,
    withdrawnAt: '',
    rollbackFrom: from.modelVersion,
    rollbackTo: target.modelVersion,
    operationNotes: `${input.notes || '演示回滚'}（演示环境，未影响真实设备）`,
    isMock: true,
  }
  releaseRecords.unshift(record)
  appendAudit(
    input.operator,
    'rollback',
    '回滚',
    target,
    'success',
    `从 ${from.modelVersion} 回滚到 ${target.modelVersion}`,
  )
  return record
}

/* ===== 记录查询 ===== */

export function listReleaseRecords(modelId?: string, modelVersionId?: string): ReleaseRecord[] {
  return releaseRecords
    .filter((item) => (modelId ? item.modelId === modelId : true))
    .filter((item) => (modelVersionId ? item.modelVersionId === modelVersionId : true))
    .sort((a, b) => b.submittedAt.localeCompare(a.submittedAt))
}

export function listModelAudits(modelId?: string, modelVersionId?: string): ModelAuditRecord[] {
  const versionIds = modelId
    ? modelVersions.filter((item) => item.modelId === modelId).map((item) => item.modelVersionId)
    : null
  return modelAudits
    .filter((item) => (versionIds ? versionIds.includes(item.targetId) : true))
    .filter((item) => (modelVersionId ? item.targetId === modelVersionId : true))
    .sort((a, b) => b.operatedAt.localeCompare(a.operatedAt))
}

/* ===== 版本对比 ===== */

export function compareModelVersions(
  baseVersionId: string,
  targetVersionId: string,
): ModelVersionCompareRow[] {
  const base = findModelVersion(baseVersionId)
  const target = findModelVersion(targetVersionId)

  const rows: Array<[string, string, string]> = [
    ['模型版本', base.modelVersion, target.modelVersion],
    ['关联训练任务', base.trainingTaskId || '未关联', target.trainingTaskId || '未关联'],
    ['数据集版本', `${base.datasetVersion}（${base.datasetVersionId}）`, `${target.datasetVersion}（${target.datasetVersionId}）`],
    ['模型格式', MODEL_FORMAT_LABELS[base.modelFormat], MODEL_FORMAT_LABELS[target.modelFormat]],
    ['量化方式', base.quantization.map((item) => QUANTIZATION_FORMAT_LABELS[item.format]).join(' / ') || '无', target.quantization.map((item) => QUANTIZATION_FORMAT_LABELS[item.format]).join(' / ') || '无'],
    ['发布状态', RELEASE_STATUS_LABELS[base.releaseStatus], RELEASE_STATUS_LABELS[target.releaseStatus]],
    [
      '评估状态',
      base.evaluationSummary.evaluationStatus === 'demo'
        ? '演示指标'
        : base.evaluationSummary.evaluationStatus === 'evaluated'
          ? '已评估'
          : '未评估',
      target.evaluationSummary.evaluationStatus === 'demo'
        ? '演示指标'
        : target.evaluationSummary.evaluationStatus === 'evaluated'
          ? '已评估'
          : '未评估',
    ],
    ['创建时间', base.createdAt, target.createdAt],
  ]

  return rows.map(([label, baseValue, targetValue]) => ({
    label,
    baseValue,
    targetValue,
    changed: baseValue !== targetValue,
  }))
}
