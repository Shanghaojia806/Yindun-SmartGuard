import { ApiError } from '../http'
import type { PageResult } from '../types'
import type {
  AnnotationDraft,
  AnnotationRecord,
  CreateDatasetInput,
  CreateVersionInput,
  Dataset,
  DatasetListQuery,
  DatasetOptions,
  DatasetOverviewStats,
  DatasetSample,
  DatasetSummary,
  DatasetVersion,
  DatasetType,
  DedupeGroup,
  DedupeOverview,
  DedupeRecord,
  ImportLicenseDraft,
  ImportResult,
  ImportRow,
  ImportSourceDraft,
  ImportSummary,
  QualityIssue,
  QualityIssueType,
  QualityReport,
  QualityResult,
  ReviewRecord,
  RiskLabel,
  SampleQuery,
  SplitConfig,
  SplitPreviewRow,
  SplitResult,
  TrainingEligibility,
  VersionCompareRow,
  VersionStatus,
} from '@/types/dataset'
import {
  annotationRecords,
  DATASET_DEMO_NOTE,
  DATASET_LABELS,
  datasetSamples,
  datasetStore,
  datasetVersions,
  dedupeRecords,
  qualityReports,
  reviewRecords,
} from './datasetData'

/**
 * A04 Mock Adapter。
 *
 * 所有写操作只修改内存中的演示数据，刷新页面后恢复初始状态。
 * 训练资格、质检、去重、拆分规则在前端完整实现，但真实训练前仍需后端再次校验。
 */

const OPERATOR = '演示管理员'

const TYPE_OPTIONS: DatasetOptions['types'] = [
  { value: 'screen', label: '屏幕对话' },
  { value: 'video', label: '视频' },
  { value: 'audio', label: '音频' },
  { value: 'text', label: '文本' },
  { value: 'multimodal', label: '多模态混合' },
]

const STATUS_OPTIONS: DatasetOptions['statuses'] = [
  { value: 'draft', label: '草稿' },
  { value: 'importing', label: '导入中' },
  { value: 'annotating', label: '标注中' },
  { value: 'reviewing', label: '审核中' },
  { value: 'quality_blocked', label: '质检阻塞' },
  { value: 'ready', label: '可训练' },
  { value: 'archived', label: '已归档' },
]

function nowText(): string {
  const now = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}`
}

function paginate<T>(items: T[], page: number, pageSize: number): PageResult<T> {
  const safePage = Math.max(1, page)
  const safeSize = Math.max(1, pageSize)
  const start = (safePage - 1) * safeSize
  return {
    items: items.slice(start, start + safeSize),
    page: safePage,
    pageSize: safeSize,
    total: items.length,
  }
}

function samplesOf(datasetId: string): DatasetSample[] {
  return datasetSamples.filter((item) => item.datasetId === datasetId)
}

function findDataset(datasetId: string): Dataset {
  const dataset = datasetStore.find((item) => item.datasetId === datasetId)
  if (!dataset) {
    throw new ApiError(404, 'DATASET_NOT_FOUND', '未找到该数据集，演示数据可能已被重置')
  }
  return dataset
}

/* ===== 训练资格规则 ===== */

export function evaluateEligibility(sample: DatasetSample): TrainingEligibility {
  const reasons: string[] = []
  if (!sample.sourceName.trim()) reasons.push('来源信息缺失')
  if (!sample.sourceLicense.licenseType || sample.licenseStatus === 'missing') {
    reasons.push('使用许可缺失')
  } else if (!sample.sourceLicense.allowTraining) {
    reasons.push('许可未允许用于模型训练')
  }
  if (!sample.formatValid) reasons.push('文件格式异常')
  if (sample.annotationStatus !== 'completed') reasons.push('标注未完成')
  if (sample.riskCategories.length === 0) reasons.push('风险类别缺失')
  if (sample.evidenceLocations.length === 0) reasons.push('证据位置缺失')
  if (sample.qualityResult !== 'passed') reasons.push('质检未通过')
  if (sample.duplicateStatus === 'suspected') reasons.push('存在未处理的疑似重复')
  if (sample.duplicateStatus === 'confirmed_duplicate') reasons.push('已确认为重复样本')
  if (sample.duplicateStatus === 'excluded') reasons.push('已被去重排除')
  if (sample.sampleStatus === 'blocked') reasons.push('样本状态为受阻')
  return {
    sampleId: sample.sampleId,
    eligible: reasons.length === 0,
    reasons,
  }
}

/* ===== 数据集 ===== */

export function summarize(dataset: Dataset): DatasetSummary {
  const samples = samplesOf(dataset.datasetId)
  const total = samples.length
  const annotated = samples.filter(
    (item) =>
      item.annotationStatus === 'completed' ||
      item.annotationStatus === 'submitted' ||
      item.annotationStatus === 'reviewing',
  ).length
  const approved = samples.filter((item) => item.reviewStatus === 'approved').length
  const pendingReview = samples.filter(
    (item) => item.annotationStatus === 'submitted' || item.annotationStatus === 'reviewing',
  ).length
  const qualityPassed = samples.filter((item) => item.qualityResult === 'passed').length
  const blocked = samples.filter((item) => item.sampleStatus === 'blocked').length
  const eligible = samples.filter((item) => evaluateEligibility(item).eligible).length

  return {
    ...dataset,
    totalSamples: total,
    annotatedSamples: annotated,
    approvedSamples: approved,
    annotationRate: total === 0 ? 0 : Math.round((annotated / total) * 100),
    reviewRate: total === 0 ? 0 : Math.round((approved / total) * 100),
    pendingReviewSamples: pendingReview,
    qualityPassedSamples: qualityPassed,
    blockedSamples: blocked,
    eligibilityRate: total === 0 ? 0 : Math.round((eligible / total) * 100),
  }
}

export function getDatasetStats(): DatasetOverviewStats {
  const summaries = datasetStore.map(summarize)
  return {
    datasetTotal: summaries.length,
    sampleTotal: summaries.reduce((sum, item) => sum + item.totalSamples, 0),
    annotatedTotal: summaries.reduce((sum, item) => sum + item.annotatedSamples, 0),
    pendingReviewTotal: summaries.reduce((sum, item) => sum + item.pendingReviewSamples, 0),
    qualityPassedTotal: summaries.reduce((sum, item) => sum + item.qualityPassedSamples, 0),
    blockedTotal: summaries.reduce((sum, item) => sum + item.blockedSamples, 0),
  }
}

export function getDatasetOptions(): DatasetOptions {
  return { types: TYPE_OPTIONS, statuses: STATUS_OPTIONS }
}

export function listDatasets(query: DatasetListQuery): PageResult<DatasetSummary> {
  const keyword = query.keyword.trim().toLowerCase()
  const filtered = datasetStore.map(summarize).filter((item) => {
    if (keyword && !item.datasetName.toLowerCase().includes(keyword)) return false
    if (query.datasetType && item.datasetType !== query.datasetType) return false
    if (query.status && item.datasetStatus !== query.status) return false
    if (query.createdFrom && item.createdAt.slice(0, 10) < query.createdFrom) return false
    if (query.createdTo && item.createdAt.slice(0, 10) > query.createdTo) return false
    if (query.annotationProgress === 'not_started' && item.annotationRate > 0) return false
    if (query.annotationProgress === 'completed' && item.annotationRate < 100) return false
    if (
      query.annotationProgress === 'in_progress' &&
      (item.annotationRate === 0 || item.annotationRate >= 100)
    ) {
      return false
    }
    return true
  })
  return paginate(filtered, query.page, query.pageSize)
}

export function getDatasetDetail(datasetId: string): DatasetSummary {
  return summarize(findDataset(datasetId))
}

export function createDataset(input: CreateDatasetInput): DatasetSummary {
  const name = input.datasetName.trim()
  if (!name) {
    throw new ApiError(400, 'DATASET_NAME_REQUIRED', '请填写数据集名称')
  }
  const maxSeq = datasetStore.reduce((max, item) => {
    const seq = Number(item.datasetId.split('-').pop() ?? 0)
    return Number.isFinite(seq) ? Math.max(max, seq) : max
  }, 0)
  const dataset: Dataset = {
    datasetId: `DS-2026-${String(maxSeq + 1).padStart(3, '0')}`,
    datasetName: name,
    description: input.description.trim(),
    datasetType: input.datasetType,
    totalSamples: 0,
    annotatedSamples: 0,
    approvedSamples: 0,
    datasetVersion: 'v0.1.0',
    datasetStatus: 'draft',
    createdAt: nowText(),
    updatedAt: nowText(),
    createdBy: OPERATOR,
  }
  datasetStore.unshift(dataset)
  datasetVersions.push({
    versionId: `VER-${dataset.datasetId}-01`,
    datasetId: dataset.datasetId,
    versionNumber: 'v0.1.0',
    sampleCount: 0,
    trainCount: 0,
    validationCount: 0,
    testCount: 0,
    versionDescription: '数据集创建时自动生成的初始版本。',
    createdAt: dataset.createdAt,
    createdBy: OPERATOR,
    versionStatus: 'draft',
    leakageRisk: 'unverified',
    manualReviewStatus: 'not_required',
    changeLog: ['创建数据集'],
  })
  return summarize(dataset)
}

/* ===== 样本 ===== */

export function querySamples(query: SampleQuery): PageResult<DatasetSample> {
  findDataset(query.datasetId)
  const keyword = query.keyword.trim().toLowerCase()
  const filtered = samplesOf(query.datasetId).filter((sample) => {
    if (keyword && !sample.sampleName.toLowerCase().includes(keyword)) return false
    if (query.sourceType && sample.sourceType !== query.sourceType) return false
    if (query.annotationStatus && sample.annotationStatus !== query.annotationStatus) return false
    if (query.reviewStatus && sample.reviewStatus !== query.reviewStatus) return false
    if (query.licenseStatus && sample.licenseStatus !== query.licenseStatus) return false
    return true
  })
  return paginate(filtered, query.page, query.pageSize)
}

export function getSample(datasetId: string, sampleId: string): DatasetSample {
  findDataset(datasetId)
  const sample = datasetSamples.find(
    (item) => item.datasetId === datasetId && item.sampleId === sampleId,
  )
  if (!sample) {
    throw new ApiError(404, 'SAMPLE_NOT_FOUND', '未找到该样本，演示数据可能已被重置')
  }
  return sample
}

export function getLabels(): RiskLabel[] {
  return DATASET_LABELS
}

export function saveAnnotation(
  datasetId: string,
  sampleId: string,
  draft: AnnotationDraft,
  annotatorId: string,
): AnnotationRecord {
  const sample = getSample(datasetId, sampleId)
  if (draft.riskCategories.length === 0 && draft.annotationStatus === 'completed') {
    throw new ApiError(400, 'LABELS_REQUIRED', '标记为已完成时至少需要一个风险类别')
  }

  const createdAt = nowText()
  const record: AnnotationRecord = {
    annotationId: `ANN-${sampleId}-${annotationRecords.length + 1}`,
    sampleId,
    annotatorId,
    riskCategories: [...draft.riskCategories],
    evidenceLocations: structuredClone(draft.evidenceLocations),
    annotationNotes: draft.annotationNotes,
    annotationStatus: draft.annotationStatus,
    createdAt,
    updatedAt: createdAt,
  }
  annotationRecords.push(record)

  sample.riskCategories = [...draft.riskCategories]
  sample.evidenceLocations = structuredClone(draft.evidenceLocations)
  sample.annotationNotes = draft.annotationNotes
  sample.annotationStatus = draft.annotationStatus
  sample.annotatorId = annotatorId
  sample.updatedAt = createdAt
  sample.qualityResult = 'unchecked'

  if (draft.annotationStatus === 'submitted' || draft.annotationStatus === 'reviewing') {
    sample.reviewStatus = 'pending'
    sample.sampleStatus = 'reviewing'
  } else if (draft.annotationStatus === 'completed') {
    sample.reviewStatus = 'pending'
    sample.sampleStatus = 'annotated'
  } else if (draft.annotationStatus === 'in_progress') {
    sample.sampleStatus = 'annotating'
  } else {
    sample.sampleStatus = 'pending'
  }

  const dataset = findDataset(datasetId)
  dataset.updatedAt = createdAt
  dataset.datasetStatus = dataset.datasetStatus === 'draft' ? 'annotating' : dataset.datasetStatus

  return structuredClone(record)
}

export function reviewSample(
  datasetId: string,
  sampleId: string,
  reviewResult: ReviewRecord['reviewResult'],
  reviewNotes: string,
  reviewerId: string,
): ReviewRecord {
  const sample = getSample(datasetId, sampleId)
  const record: ReviewRecord = {
    reviewId: `REV-${sampleId}-${reviewRecords.length + 1}`,
    sampleId,
    reviewerId,
    reviewResult,
    reviewNotes,
    reviewedAt: nowText(),
  }
  reviewRecords.push(record)
  sample.reviewStatus = reviewResult
  if (reviewResult === 'approved') {
    sample.sampleStatus = 'approved'
    sample.annotationStatus = 'completed'
  } else if (reviewResult === 'rejected') {
    sample.sampleStatus = 'rejected'
  } else if (reviewResult === 'needs_fix') {
    sample.sampleStatus = 'rejected'
  }
  sample.updatedAt = record.reviewedAt
  findDataset(datasetId).updatedAt = record.reviewedAt
  return record
}

/* ===== 质检 ===== */

function buildIssues(sample: DatasetSample): QualityIssue[] {
  const issues: QualityIssue[] = []
  const add = (issueType: QualityIssueType, description: string, severity: QualityIssue['severity']) => {
    issues.push({
      issueId: `${sample.sampleId}-${issueType}`,
      sampleId: sample.sampleId,
      sampleName: sample.sampleName,
      issueType,
      description,
      severity,
    })
  }

  if (!sample.sourceName.trim()) add('missing_source', '来源名称为空，无法确认素材出处', 'warning')
  if (sample.licenseStatus === 'missing') {
    add('missing_license', '使用许可缺失，样本不得进入训练集', 'danger')
  } else if (!sample.sourceLicense.allowTraining) {
    add('missing_license', '许可未明确允许用于模型训练', 'danger')
  }
  if (sample.annotationStatus === 'unannotated' || sample.annotationStatus === 'in_progress') {
    add('incomplete_annotation', '标注尚未完成', 'warning')
  }
  if (sample.annotationStatus !== 'unannotated' && sample.riskCategories.length === 0) {
    add('missing_labels', '缺少风险类别标签', 'warning')
  }
  if (sample.evidenceLocations.length === 0) {
    add('missing_evidence', '缺少证据位置记录', 'warning')
  }
  if (sample.duplicateStatus === 'suspected') {
    add('duplicate', '疑似重复样本，需人工确认', 'warning')
  }
  if (sample.duplicateStatus === 'confirmed_duplicate') {
    add('duplicate', '已确认为重复样本', 'danger')
  }
  if (sample.riskCategories.includes('正常内容') && sample.riskCategories.length > 1) {
    add('label_conflict', '正常内容与风险标签冲突', 'danger')
  }
  if (!sample.formatValid) add('format_error', '文件格式异常或结构无法解析', 'danger')

  return issues
}

function resultOf(sample: DatasetSample, issues: QualityIssue[]): QualityResult {
  if (issues.length === 0) return 'passed'
  const hasBlocking = issues.some(
    (item) => item.issueType === 'format_error' || item.issueType === 'missing_license',
  )
  if (hasBlocking) return 'failed'
  const onlyDuplicate =
    issues.length === 1 && issues[0].issueType === 'duplicate' && sample.duplicateStatus === 'suspected'
  if (onlyDuplicate) return 'manual_review'
  return 'needs_fix'
}

export function runQualityCheck(datasetId: string): QualityReport {
  findDataset(datasetId)
  const samples = samplesOf(datasetId)
  const issues: QualityIssue[] = []
  const sampleResults: Record<string, QualityResult> = {}

  for (const sample of samples) {
    const sampleIssues = buildIssues(sample)
    issues.push(...sampleIssues)
    const result = resultOf(sample, sampleIssues)
    sample.qualityResult = result
    sampleResults[sample.sampleId] = result
  }

  const report: QualityReport = {
    reportId: `QR-${datasetId}-${Date.now()}`,
    datasetId,
    generatedAt: nowText(),
    totalChecked: samples.length,
    passedCount: Object.values(sampleResults).filter((item) => item === 'passed').length,
    needsFixCount: Object.values(sampleResults).filter((item) => item === 'needs_fix').length,
    failedCount: Object.values(sampleResults).filter((item) => item === 'failed').length,
    manualReviewCount: Object.values(sampleResults).filter((item) => item === 'manual_review').length,
    issues,
    sampleResults,
  }
  qualityReports.set(datasetId, report)
  findDataset(datasetId).updatedAt = report.generatedAt
  return report
}

export function getQualityReport(datasetId: string): QualityReport | null {
  findDataset(datasetId)
  return qualityReports.get(datasetId) ?? null
}

/* ===== 去重 ===== */

export function getDedupeOverview(datasetId: string): DedupeOverview {
  findDataset(datasetId)
  const samples = samplesOf(datasetId)
  const byHash = new Map<string, DatasetSample[]>()
  for (const sample of samples) {
    const group = byHash.get(sample.fileHash) ?? []
    group.push(sample)
    byHash.set(sample.fileHash, group)
  }

  const groups: DedupeGroup[] = []
  let index = 0
  for (const [hash, groupSamples] of byHash) {
    if (groupSamples.length < 2) continue
    index += 1
    const existing = groups.find((item) => item.groupId === `GRP-${datasetId}-${index}`)
    const excluded = groupSamples
      .filter((item) => item.duplicateStatus === 'excluded')
      .map((item) => item.sampleId)
    const resolved = groupSamples.every(
      (item) => item.duplicateStatus === 'unique' || item.duplicateStatus === 'excluded',
    )
    groups.push({
      groupId: `GRP-${datasetId}-${String(index).padStart(2, '0')}`,
      fileHash: hash,
      reason: '模拟文件哈希一致（未真实计算文件内容哈希）',
      sampleIds: groupSamples.map((item) => item.sampleId),
      sampleNames: groupSamples.map((item) => item.sampleName),
      status: resolved ? 'resolved' : 'pending',
      keepSampleId: groupSamples[0]?.sampleId ?? '',
      excludedSampleIds: excluded,
      resolvedAt: existing?.resolvedAt ?? '',
      resolvedBy: existing?.resolvedBy ?? '',
      simulated: true,
    })
  }

  return {
    datasetId,
    groups,
    records: dedupeRecords.filter((item) => item.groupId.includes(datasetId)),
    note: '模拟去重：基于演示哈希分组，未执行真实文件内容哈希计算；确认操作仅修改本地 Mock 数据。',
  }
}

export function resolveDedupeGroup(
  datasetId: string,
  groupId: string,
  keepSampleId: string,
  excludeSampleIds: string[],
): DedupeOverview {
  const target = groupSamplesOfHash(datasetId, groupId)
  if (target.length === 0) {
    throw new ApiError(404, 'DEDUPE_GROUP_NOT_FOUND', '未找到该重复分组')
  }
  if (!target.some((item) => item.sampleId === keepSampleId)) {
    throw new ApiError(400, 'KEEP_SAMPLE_INVALID', '保留样本不属于该重复分组')
  }

  for (const sample of target) {
    if (excludeSampleIds.includes(sample.sampleId)) {
      sample.duplicateStatus = 'excluded'
      sample.sampleStatus = 'blocked'
    } else if (sample.sampleId === keepSampleId) {
      sample.duplicateStatus = 'unique'
    }
    sample.qualityResult = 'unchecked'
    sample.updatedAt = nowText()
  }

  const record: DedupeRecord = {
    recordId: `DEDUPE-${groupId}-${dedupeRecords.length + 1}`,
    groupId,
    action: excludeSampleIds.length > 0 ? '保留并排除重复样本' : '人工确认保留全部样本',
    detail:
      excludeSampleIds.length > 0
        ? `保留 ${keepSampleId}，排除 ${excludeSampleIds.join('、')}`
        : `确认保留 ${target.map((item) => item.sampleId).join('、')}（仍标记为模拟重复校验结果）`,
    operatedAt: nowText(),
    operator: OPERATOR,
  }
  dedupeRecords.push(record)
  findDataset(datasetId).updatedAt = record.operatedAt

  return getDedupeOverview(datasetId)
}

/** 内部工具：按分组 ID 找回哈希对应的样本 */
function groupSamplesOfHash(datasetId: string, groupId: string): DatasetSample[] {
  const overview = getDedupeOverview(datasetId)
  const group = overview.groups.find((item) => item.groupId === groupId)
  if (!group) return []
  return datasetSamples.filter(
    (item) => item.datasetId === datasetId && group.sampleIds.includes(item.sampleId),
  )
}

/* ===== 拆分 ===== */

function seededRandom(seed: number): () => number {
  let state = seed >>> 0
  return () => {
    state = (state + 0x6d2b79f5) >>> 0
    let t = state
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

function shuffle<T>(items: T[], seed: number): T[] {
  const random = seededRandom(seed)
  const copy = [...items]
  for (let i = copy.length - 1; i > 0; i -= 1) {
    const j = Math.floor(random() * (i + 1))
    ;[copy[i], copy[j]] = [copy[j], copy[i]]
  }
  return copy
}

function assertSplitConfig(config: SplitConfig): void {
  const values = [config.trainRatio, config.validationRatio, config.testRatio]
  if (values.some((item) => !Number.isFinite(item) || item < 0)) {
    throw new ApiError(400, 'SPLIT_RATIO_INVALID', '拆分比例必须为非负数字')
  }
  const total = config.trainRatio + config.validationRatio + config.testRatio
  if (Math.abs(total - 100) > 0.001) {
    throw new ApiError(400, 'SPLIT_RATIO_SUM_INVALID', `训练集、验证集、测试集比例之和必须等于 100%，当前为 ${total}%`)
  }
}

/** 拆分三个集合至少需要的合格样本数（保证三个集合均非空）。 */
export const MIN_SPLIT_SAMPLES = 3

interface SplitTargets {
  train: number
  validation: number
  test: number
}

/**
 * 最大余数法计算三个集合的整数目标数量。
 *
 * - 先按比例向下取整，再把剩余名额按小数部分从大到小依次分配，保证三者之和严格等于 total；
 * - 仅当三个比例均大于 0 且 total ≥ 3 时，才把被算成 0 的集合修正为 1 条（保证三个集合均非空）；
 * - 若用户配置了 0% 比例，则不擅自调整该集合数量，由上层阻止创建正式版本。
 */
function computeSplitTargets(total: number, config: SplitConfig): SplitTargets {
  if (total <= 0) return { train: 0, validation: 0, test: 0 }
  const ratios = [config.trainRatio, config.validationRatio, config.testRatio]
  const raw = ratios.map((ratio) => (total * ratio) / 100)
  const counts = raw.map((value) => Math.floor(value))
  let remaining = total - counts.reduce((sum, value) => sum + value, 0)
  const order = [0, 1, 2].sort(
    (a, b) => raw[b] - counts[b] - (raw[a] - counts[a]) || a - b,
  )
  let cursor = 0
  while (remaining > 0) {
    counts[order[cursor % 3]] += 1
    cursor += 1
    remaining -= 1
  }
  const allPositive = ratios.every((ratio) => ratio > 0)
  if (allPositive && total >= MIN_SPLIT_SAMPLES) {
    for (let index = 0; index < 3; index += 1) {
      if (counts[index] > 0) continue
      let donor = -1
      for (let candidate = 0; candidate < 3; candidate += 1) {
        if (counts[candidate] > 1 && (donor === -1 || counts[candidate] > counts[donor])) {
          donor = candidate
        }
      }
      if (donor !== -1) {
        counts[donor] -= 1
        counts[index] += 1
      }
    }
  }
  return { train: counts[0], validation: counts[1], test: counts[2] }
}

/** 是否配置了 0% 比例：存在 0% 时禁止创建正式版本，且不擅自改为非零。 */
export function hasZeroRatio(config: SplitConfig): boolean {
  return config.trainRatio <= 0 || config.validationRatio <= 0 || config.testRatio <= 0
}

/** 按累计目标数量确定第 index 条样本属于哪个集合。 */
function splitAt(index: number, targets: SplitTargets): SplitPreviewRow['split'] {
  if (index < targets.train) return 'train'
  if (index < targets.train + targets.validation) return 'validation'
  return 'test'
}

export function previewSplit(datasetId: string, config: SplitConfig): SplitResult {
  findDataset(datasetId)
  assertSplitConfig(config)

  const samples = samplesOf(datasetId)
  const excluded = samples
    .map((sample) => ({ sample, result: evaluateEligibility(sample) }))
    .filter((item) => !item.result.eligible)
    .map((item) => ({
      sampleId: item.sample.sampleId,
      sampleName: item.sample.sampleName,
      reasons: item.result.reasons,
    }))
  const eligible = samples.filter((item) => evaluateEligibility(item).eligible)
  const targets = computeSplitTargets(eligible.length, config)

  const preview: SplitPreviewRow[] = []
  const warnings: string[] = []
  let leakageRisk: SplitResult['leakageRisk'] = 'unverified'

  if (config.strategy === 'group_by_source') {
    leakageRisk = 'grouped'
    const groups = new Map<string, DatasetSample[]>()
    for (const sample of eligible) {
      const key = sample.sourceName || sample.sampleId
      const list = groups.get(key) ?? []
      list.push(sample)
      groups.set(key, list)
    }
    // 按种子打乱分组的先后顺序，组内样本保持原有相对顺序；同一分组的样本连续排列。
    const ordered = shuffle(Array.from(groups.values()), config.seed)
    const splitGroups: string[] = []
    let index = 0
    for (const group of ordered) {
      const splits = new Set<SplitPreviewRow['split']>()
      for (const sample of group) {
        const split = splitAt(index, targets)
        index += 1
        splits.add(split)
        preview.push({
          sampleId: sample.sampleId,
          sampleName: sample.sampleName,
          sourceName: sample.sourceName,
          split,
        })
      }
      if (splits.size > 1) {
        splitGroups.push(group[0]?.sourceName || group[0]?.sampleId || '未知来源')
      }
    }
    if (splitGroups.length > 0) {
      leakageRisk = 'partial_group_split'
      warnings.push(
        `为严格满足拆分比例，${splitGroups.length} 个来源分组被拆到多个集合（${splitGroups.join('、')}），存在同源样本泄漏风险，需人工复核。`,
      )
    } else if (eligible.length > 0) {
      warnings.push('已按来源名称分组拆分，Mock 环境无法验证内容相似度，仍需后端复核是否存在泄漏。')
    }
  } else {
    const shuffled = shuffle(eligible, config.seed)
    for (const [index, sample] of shuffled.entries()) {
      preview.push({
        sampleId: sample.sampleId,
        sampleName: sample.sampleName,
        sourceName: sample.sourceName,
        split: splitAt(index, targets),
      })
    }
    warnings.push('当前为随机拆分，未按来源分组，存在同一来源样本进入训练集与测试集的泄漏风险。')
  }

  if (excluded.length > 0) {
    warnings.push(`${excluded.length} 个样本未通过训练资格校验，已从拆分中排除。`)
  }
  if (eligible.length === 0) {
    warnings.push('当前没有满足训练资格的样本，请先完成标注并运行质检。')
  } else if (eligible.length < MIN_SPLIT_SAMPLES) {
    warnings.push(
      `合格样本仅 ${eligible.length} 条，不足以拆分为训练/验证/测试三个非空集合，禁止创建正式版本。`,
    )
  }
  if (hasZeroRatio(config)) {
    warnings.push(
      `训练集 ${config.trainRatio}% / 验证集 ${config.validationRatio}% / 测试集 ${config.testRatio}% 中存在 0% 比例。训练集、验证集、测试集比例必须均大于 0，当前配置禁止创建正式版本。`,
    )
  }

  const trainCount = preview.filter((item) => item.split === 'train').length
  const validationCount = preview.filter((item) => item.split === 'validation').length
  const testCount = preview.filter((item) => item.split === 'test').length

  return {
    datasetId,
    config,
    totalSamples: samples.length,
    eligibleCount: eligible.length,
    trainCount,
    validationCount,
    testCount,
    excluded,
    preview,
    leakageRisk,
    warnings,
  }
}

/* ===== 版本 ===== */

export function listVersions(datasetId: string): DatasetVersion[] {
  findDataset(datasetId)
  return datasetVersions
    .filter((item) => item.datasetId === datasetId)
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
}

function findVersion(versionId: string): DatasetVersion {
  const version = datasetVersions.find((item) => item.versionId === versionId)
  if (!version) {
    throw new ApiError(404, 'VERSION_NOT_FOUND', '未找到该版本记录')
  }
  return version
}

export function getVersion(versionId: string): DatasetVersion {
  return structuredClone(findVersion(versionId))
}

export function createVersion(datasetId: string, input: CreateVersionInput): DatasetVersion {
  const dataset = findDataset(datasetId)
  const config = input.splitConfig ?? {
    trainRatio: 70,
    validationRatio: 15,
    testRatio: 15,
    seed: 42,
    strategy: 'group_by_source' as const,
  }
  const split = previewSplit(datasetId, config)
  if (hasZeroRatio(config)) {
    throw new ApiError(
      400,
      'SPLIT_ZERO_RATIO',
      `训练集、验证集、测试集比例必须均大于 0%，当前为 ${config.trainRatio}% / ${config.validationRatio}% / ${config.testRatio}%，无法创建正式版本`,
    )
  }
  if (split.eligibleCount === 0) {
    throw new ApiError(400, 'NO_ELIGIBLE_SAMPLE', '当前没有满足训练资格的样本，无法创建版本，请先完成标注与质检')
  }
  if (split.eligibleCount < MIN_SPLIT_SAMPLES) {
    throw new ApiError(
      400,
      'SPLIT_NOT_ENOUGH_SAMPLES',
      `当前仅有 ${split.eligibleCount} 条满足训练资格的样本，不足以拆分为训练/验证/测试三个非空集合，无法创建正式版本`,
    )
  }

  const current = dataset.datasetVersion.replace(/^v/, '').split('.').map(Number)
  const nextNumber = `v${current[0] ?? 1}.${(current[1] ?? 0) + 1}.0`
  const version: DatasetVersion = {
    versionId: `VER-${datasetId}-${String(datasetVersions.length + 1).padStart(3, '0')}`,
    datasetId,
    versionNumber: nextNumber,
    sampleCount: split.eligibleCount,
    trainCount: split.trainCount,
    validationCount: split.validationCount,
    testCount: split.testCount,
    versionDescription: input.versionDescription.trim() || '数据变更后创建的新版本。',
    createdAt: nowText(),
    createdBy: OPERATOR,
    versionStatus: 'draft',
    leakageRisk: split.leakageRisk,
    manualReviewStatus: split.leakageRisk === 'partial_group_split' ? 'pending' : 'not_required',
    changeLog: [
      input.changeLog.trim() || '数据变更',
      `训练/验证/测试：${split.trainCount}/${split.validationCount}/${split.testCount}`,
      ...(split.leakageRisk === 'partial_group_split'
        ? ['存在同源分组拆分风险，需完成人工复核后方可发布为可训练版本']
        : []),
    ],
  }
  datasetVersions.push(version)
  dataset.datasetVersion = nextNumber
  dataset.updatedAt = version.createdAt
  return structuredClone(version)
}

export function updateVersionStatus(versionId: string, status: VersionStatus): DatasetVersion {
  const version = findVersion(versionId)
  if (version.versionStatus === 'published' && status !== 'published') {
    throw new ApiError(400, 'VERSION_LOCKED', '已发布版本不允许修改状态或覆盖内容，请创建新版本')
  }
  if (status === 'published' && version.manualReviewStatus === 'pending') {
    throw new ApiError(
      400,
      'VERSION_MANUAL_REVIEW_REQUIRED',
      '该版本存在同源分组拆分泄漏风险，未完成人工复核前不允许发布为正式可训练版本',
    )
  }
  version.versionStatus = status
  version.changeLog.push(`${nowText()} 版本状态更新为 ${status}`)
  const dataset = findDataset(version.datasetId)
  if (status === 'published') {
    dataset.datasetStatus = 'ready'
    dataset.datasetVersion = version.versionNumber
    dataset.updatedAt = nowText()
  }
  return structuredClone(version)
}

/**
 * 记录同源拆分人工复核结论（本地 Mock）。
 * 复核通过后版本才允许发布为正式可训练版本。
 */
export function confirmVersionManualReview(versionId: string, notes: string): DatasetVersion {
  const version = findVersion(versionId)
  if (version.manualReviewStatus !== 'pending') {
    throw new ApiError(400, 'VERSION_REVIEW_NOT_REQUIRED', '该版本不存在待处理的人工复核任务')
  }
  const reviewedAt = nowText()
  version.manualReviewStatus = 'approved'
  version.manualReviewedBy = OPERATOR
  version.manualReviewedAt = reviewedAt
  version.manualReviewNotes = notes.trim() || '已确认同源拆分风险并补充复核说明（本地演示）。'
  version.changeLog.push(`${reviewedAt} 完成同源拆分人工复核：${version.manualReviewNotes}`)
  return structuredClone(version)
}

export function compareVersions(versionId: string, baseVersionId: string): VersionCompareRow[] {
  const target = findVersion(versionId)
  const base = findVersion(baseVersionId)
  const rows = [
    { label: '版本号', baseValue: base.versionNumber, targetValue: target.versionNumber },
    { label: '样本数量', baseValue: String(base.sampleCount), targetValue: String(target.sampleCount) },
    { label: '训练集', baseValue: String(base.trainCount), targetValue: String(target.trainCount) },
    { label: '验证集', baseValue: String(base.validationCount), targetValue: String(target.validationCount) },
    { label: '测试集', baseValue: String(base.testCount), targetValue: String(target.testCount) },
    { label: '审核状态', baseValue: base.versionStatus, targetValue: target.versionStatus },
    { label: '创建时间', baseValue: base.createdAt, targetValue: target.createdAt },
  ]
  return rows.map((row) => ({ ...row, changed: row.baseValue !== row.targetValue }))
}

/* ===== 导入 ===== */

export function prepareImport(
  datasetId: string,
  rows: ImportRow[],
  source: ImportSourceDraft,
  license: ImportLicenseDraft,
): ImportSummary {
  findDataset(datasetId)
  const existingHashes = new Set(samplesOf(datasetId).map((item) => item.fileHash))
  const batchHashes = new Set<string>()
  const candidates = rows.map((row) => {
    const reasons: string[] = []
    const hasSource = Boolean(source.sourceName.trim() && source.acquiredAt)
    const hasLicense = Boolean(
      license.licenseType.trim() && license.licenseScope.trim() && license.allowTraining,
    )
    const duplicate = existingHashes.has(row.fileHash) || batchHashes.has(row.fileHash)
    batchHashes.add(row.fileHash)
    const duplicateOf = existingHashes.has(row.fileHash) ? '已存在样本' : duplicate ? '本批次内重复' : ''

    if (!row.formatValid) reasons.push(row.formatIssue || '文件格式错误')
    if (!hasSource) reasons.push('来源信息缺失')
    if (!hasLicense) reasons.push('许可信息缺失或未允许训练')
    if (duplicate) reasons.push('重复样本')
    if (row.riskCategory.trim() === '') reasons.push('风险类别缺失')

    return {
      rowId: row.rowId,
      fileName: row.fileName,
      formatValid: row.formatValid,
      hasSource,
      hasLicense,
      duplicate,
      duplicateOf,
      importable: reasons.length === 0,
      reasons,
    }
  })

  return {
    total: candidates.length,
    formatErrorCount: candidates.filter((item) => !item.formatValid).length,
    missingSourceCount: candidates.filter((item) => !item.hasSource).length,
    missingLicenseCount: candidates.filter((item) => !item.hasLicense).length,
    duplicateCount: candidates.filter((item) => item.duplicate).length,
    importableCount: candidates.filter((item) => item.importable).length,
    candidates,
  }
}

export function importSamples(
  datasetId: string,
  rows: ImportRow[],
  source: ImportSourceDraft,
  license: ImportLicenseDraft,
  importableRowIds: string[],
): ImportResult {
  const dataset = findDataset(datasetId)
  const summary = prepareImport(datasetId, rows, source, license)
  const accepted = summary.candidates.filter(
    (item) => item.importable && importableRowIds.includes(item.rowId),
  )
  const maxSeq = samplesOf(datasetId).reduce((max, item) => {
    const seq = Number(item.sampleId.split('-').pop() ?? 0)
    return Number.isFinite(seq) ? Math.max(max, seq) : max
  }, 0)

  accepted.forEach((candidate, index) => {
    const row = rows.find((item) => item.rowId === candidate.rowId)
    if (!row) return
    const seq = maxSeq + index + 1
    const sample: DatasetSample = {
      sampleId: `S-${datasetId.slice(-3)}-${padNumber(seq, 4)}`,
      datasetId,
      sampleName: row.sampleName,
      modality: row.modality,
      sourceType: source.sourceType,
      sourceName: source.sourceName,
      sourceNote: source.sourceNote,
      acquiredAt: source.acquiredAt,
      sourceProof: source.sourceProof,
      sourceLicense: {
        licenseType: license.licenseType,
        licenseScope: license.licenseScope,
        allowTraining: license.allowTraining,
        allowModify: license.allowModify,
        validUntil: license.validUntil,
        proof: license.licenseProof,
        reviewStatus: license.reviewStatus,
      },
      licenseStatus: license.reviewStatus,
      riskCategories: [row.riskCategory],
      evidenceLocations: row.evidenceText
        ? [
            {
              evidenceId: `EV-${seq}-1`,
              modality: row.modality === 'video' ? 'video' : row.modality === 'audio' ? 'audio' : 'region',
              textSnippet: row.evidenceText,
              region: row.modality === 'text' ? '正文第 1 段' : '导入时的证据摘要区域',
            },
          ]
        : [],
      annotationStatus: 'unannotated',
      reviewStatus: 'pending',
      duplicateStatus: 'unique',
      sampleStatus: 'pending',
      qualityResult: 'unchecked',
      formatValid: true,
      fileHash: row.fileHash,
      createdAt: nowText(),
      updatedAt: nowText(),
    }
    datasetSamples.push(sample)
  })

  dataset.totalSamples = samplesOf(datasetId).length
  dataset.updatedAt = nowText()
  dataset.datasetStatus = accepted.length > 0 ? 'annotating' : dataset.datasetStatus

  return {
    datasetId,
    importedCount: accepted.length,
    skippedCount: rows.length - accepted.length,
  }
}

function padNumber(value: number, length: number): string {
  return String(value).padStart(length, '0')
}

export function getImportDemoRows(mode: 'normal' | 'format-error' | 'missing-info' | 'duplicate'): ImportRow[] {
  const base = Array.from({ length: mode === 'normal' ? 8 : 6 }, (_, index) => {
    const seq = index + 1
    return {
      rowId: `ROW-${mode}-${seq}`,
      fileName: `import-${padNumber(seq, 2)}.json`,
      sampleName: `import-${padNumber(seq, 2)}.json`,
      modality: 'text' as DatasetType,
      formatValid: true,
      formatIssue: '',
      sourceType: 'team_record' as ImportRow['sourceType'],
      sourceName: '团队自建反诈演示素材',
      sourceNote: '演示导入批次',
      acquiredAt: '2026-11-19',
      sourceProof: 'SRC-DEMO',
      licenseType: '团队自建样本授权',
      licenseScope: '仅用于本项目模型训练与评测',
      allowTraining: true,
      allowModify: true,
      validUntil: '2027-12-31',
      licenseProof: 'LIC-DEMO',
      riskCategory: DATASET_LABELS[index % 5]?.labelName ?? '诱导转账',
      evidenceText: '演示证据：要求向指定账户转账',
      fileHash: `import-${mode}-${seq}`,
    }
  })

  if (mode === 'format-error') {
    base[1] = { ...base[1], formatValid: false, formatIssue: 'JSON 结构缺少 content 字段' }
  }
  if (mode === 'missing-info') {
    base[2] = { ...base[2], sourceName: '' }
    base[3] = { ...base[3], licenseType: '', licenseScope: '', allowTraining: false }
  }
  if (mode === 'duplicate') {
    base[4] = { ...base[4], fileHash: base[0].fileHash }
  }
  return base
}

export { DATASET_DEMO_NOTE, DATASET_LABELS }
