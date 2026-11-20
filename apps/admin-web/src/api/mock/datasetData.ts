import type {
  AnnotationRecord,
  Dataset,
  DatasetSample,
  DatasetVersion,
  DedupeRecord,
  EvidenceLocation,
  EvidenceModality,
  LicenseStatus,
  QualityReport,
  ReviewRecord,
  RiskLabel,
  SampleLicense,
  SourceType,
} from '@/types/dataset'

/**
 * A04 数据集演示数据（Mock Repository）。
 *
 * 数据来源声明：
 * - 全部为团队自建演示素材或合成数据，不包含真实个人隐私内容；
 * - 不自动爬取任何平台内容，不使用平台私有接口；
 * - 文件哈希、审核结果、质检结果均为模拟值，不代表真实数据处理结果。
 */

export const DATASET_DEMO_NOTE =
  '演示数据：仅用于验证数据集管理流程，不含真实用户数据，也不代表已完成真实去重、审核或模型训练。'

export const DATASET_LABELS: RiskLabel[] = [
  { labelId: 'L01', labelName: '虚假投资理财', description: '承诺高收益、稳赚不赔等投资诱导', group: 'risk', enabled: true },
  { labelId: 'L02', labelName: '冒充客服', description: '冒充平台客服、公检法或银行工作人员', group: 'risk', enabled: true },
  { labelId: 'L03', labelName: '刷单返利', description: '以兼职刷单、点赞返利为名诱导', group: 'risk', enabled: true },
  { labelId: 'L04', labelName: '虚假中奖', description: '虚假抽奖、中奖需先缴费', group: 'risk', enabled: true },
  { labelId: 'L05', labelName: '诱导转账', description: '要求向指定账户或二维码转账', group: 'risk', enabled: true },
  { labelId: 'L06', labelName: '索取验证码', description: '索要短信验证码、支付密码', group: 'risk', enabled: true },
  { labelId: 'L07', labelName: '可疑链接', description: '包含不明短链、下载链接或仿冒域名', group: 'risk', enabled: true },
  { labelId: 'L08', labelName: '正常内容', description: '与风险无关的日常内容', group: 'normal', enabled: true },
  { labelId: 'L09', labelName: '信息不足', description: '样本信息不足以判断，需人工复核', group: 'unknown', enabled: true },
]

const LABEL_SETS: string[][] = [
  ['虚假投资理财'],
  ['冒充客服', '索取验证码'],
  ['刷单返利'],
  ['虚假中奖', '可疑链接'],
  ['诱导转账', '可疑链接'],
  ['正常内容'],
]

const SOURCE_TYPES: SourceType[] = ['team_record', 'partner_authorized', 'synthetic', 'public_licensed']

const SOURCE_NAMES: Record<SourceType, string> = {
  team_record: '团队自建反诈演示素材',
  partner_authorized: '合作机构授权样例',
  synthetic: '合成话术生成样本',
  public_licensed: '公开许可研究素材',
}

interface DatasetSeed {
  datasetId: string
  datasetName: string
  description: string
  datasetType: Dataset['datasetType']
  datasetStatus: Dataset['datasetStatus']
  datasetVersion: string
  createdBy: string
  createdAt: string
  updatedAt: string
  sampleCount: number
  completed: number
  submitted: number
  reviewing: number
  inProgress: number
}

const DATASET_SEEDS: DatasetSeed[] = [
  {
    datasetId: 'DS-2026-001',
    datasetName: '反诈屏幕对话样本集',
    description: '团队自建的屏幕对话演示样本，覆盖投资理财、冒充客服等典型话术。',
    datasetType: 'screen',
    datasetStatus: 'annotating',
    datasetVersion: 'v1.2.0',
    createdBy: '张明远',
    createdAt: '2026-09-02 10:12',
    updatedAt: '2026-11-18 17:40',
    sampleCount: 48,
    completed: 26,
    submitted: 6,
    reviewing: 4,
    inProgress: 6,
  },
  {
    datasetId: 'DS-2026-002',
    datasetName: '冒充客服语音片段',
    description: '合作机构授权的客服诈骗语音片段，已完成脱敏处理。',
    datasetType: 'audio',
    datasetStatus: 'reviewing',
    datasetVersion: 'v1.0.0',
    createdBy: '王海涛',
    createdAt: '2026-09-15 14:30',
    updatedAt: '2026-11-17 09:22',
    sampleCount: 32,
    completed: 20,
    submitted: 4,
    reviewing: 4,
    inProgress: 4,
  },
  {
    datasetId: 'DS-2026-003',
    datasetName: '虚假投资短视频样本',
    description: '团队自行录制的短视频演示素材，用于验证视频时间位置标注。',
    datasetType: 'video',
    datasetStatus: 'annotating',
    datasetVersion: 'v0.9.0',
    createdBy: '陈晓雯',
    createdAt: '2026-10-04 09:05',
    updatedAt: '2026-11-16 16:08',
    sampleCount: 26,
    completed: 12,
    submitted: 4,
    reviewing: 2,
    inProgress: 4,
  },
  {
    datasetId: 'DS-2026-004',
    datasetName: '团队合成诱导转账文本',
    description: '合成生成的诱导转账文本，可安全用于训练流程验证。',
    datasetType: 'text',
    datasetStatus: 'ready',
    datasetVersion: 'v1.1.0',
    createdBy: '李静',
    createdAt: '2026-08-20 11:45',
    updatedAt: '2026-11-12 10:18',
    sampleCount: 40,
    completed: 38,
    submitted: 2,
    reviewing: 0,
    inProgress: 0,
  },
  {
    datasetId: 'DS-2026-005',
    datasetName: '可疑链接页面截图',
    description: '仿冒页面演示截图，仅用于界面风险提示能力验证。',
    datasetType: 'screen',
    datasetStatus: 'draft',
    datasetVersion: 'v0.3.0',
    createdBy: '孙彤',
    createdAt: '2026-10-28 15:36',
    updatedAt: '2026-11-15 11:52',
    sampleCount: 22,
    completed: 4,
    submitted: 2,
    reviewing: 0,
    inProgress: 6,
  },
  {
    datasetId: 'DS-2026-006',
    datasetName: '混合模态复核样本',
    description: '覆盖屏幕、音频、文本的混合模态演示样本，用于质检与复核流程验证。',
    datasetType: 'multimodal',
    datasetStatus: 'quality_blocked',
    datasetVersion: 'v0.5.0',
    createdBy: '赵鹏',
    createdAt: '2026-10-11 13:20',
    updatedAt: '2026-11-14 18:03',
    sampleCount: 18,
    completed: 6,
    submitted: 2,
    reviewing: 2,
    inProgress: 2,
  },
]

function pad(value: number, length: number): string {
  return String(value).padStart(length, '0')
}

function buildLicense(index: number): SampleLicense {
  const status: LicenseStatus = index % 19 === 7 ? 'missing' : index % 9 === 3 ? 'pending' : 'verified'
  return {
    licenseType: status === 'missing' ? '' : status === 'pending' ? '合作方授权（待核验）' : '团队自建样本授权',
    licenseScope: status === 'missing' ? '' : '仅用于本项目反诈模型训练与评测',
    allowTraining: status === 'verified' && index % 17 !== 5,
    allowModify: status !== 'missing',
    validUntil: status === 'missing' ? '' : '2027-12-31',
    proof: status === 'missing' ? '' : `许可证明演示编号 LIC-${pad(index + 1, 4)}`,
    reviewStatus: status,
  }
}

function buildEvidence(datasetType: Dataset['datasetType'], index: number, label: string): EvidenceLocation[] {
  const modalities: EvidenceModality[] =
    datasetType === 'multimodal'
      ? index % 2 === 0
        ? ['screen']
        : ['video']
      : datasetType === 'screen'
        ? ['screen']
        : datasetType === 'video'
          ? ['video']
          : datasetType === 'audio'
            ? ['audio']
            : ['region']

  return modalities.map((modality, offset) => {
    const base: EvidenceLocation = {
      evidenceId: `EV-${pad(index + 1, 4)}-${offset + 1}`,
      modality,
      textSnippet: `演示证据：${label} 相关表述（第 ${(index % 5) + 1} 处）`,
    }
    if (modality === 'screen') {
      base.screenPage = `第 ${(index % 4) + 1} 个对话页面`
      base.region = index % 2 === 0 ? '页面中部气泡区域' : '页面底部输入区域'
    }
    if (modality === 'video' || modality === 'audio') {
      base.timeStart = 8 + (index % 20)
      base.timeEnd = base.timeStart + 6
    }
    if (modality === 'region') {
      base.region = `正文第 ${(index % 6) + 1} 段`
    }
    return base
  })
}

function annotationStatusOf(seed: DatasetSeed, index: number): DatasetSample['annotationStatus'] {
  if (index < seed.completed) return 'completed'
  if (index < seed.completed + seed.submitted) return 'submitted'
  if (index < seed.completed + seed.submitted + seed.reviewing) return 'reviewing'
  if (index < seed.completed + seed.submitted + seed.reviewing + seed.inProgress) return 'in_progress'
  return 'unannotated'
}

function reviewStatusOf(annotationStatus: DatasetSample['annotationStatus'], index: number): DatasetSample['reviewStatus'] {
  if (annotationStatus === 'completed') {
    if (index % 7 === 1) return 'needs_fix'
    if (index % 5 === 0) return 'pending'
    return 'approved'
  }
  return 'pending'
}

function sampleStatusOf(sample: Pick<DatasetSample, 'licenseStatus' | 'formatValid' | 'annotationStatus' | 'reviewStatus'>): DatasetSample['sampleStatus'] {
  if (sample.licenseStatus === 'missing' || !sample.formatValid) return 'blocked'
  if (sample.annotationStatus === 'unannotated') return 'pending'
  if (sample.annotationStatus === 'in_progress') return 'annotating'
  if (sample.reviewStatus === 'approved') return 'approved'
  if (sample.reviewStatus === 'rejected' || sample.reviewStatus === 'needs_fix') return 'rejected'
  if (sample.annotationStatus === 'submitted' || sample.annotationStatus === 'reviewing') return 'reviewing'
  return 'annotated'
}

const FILE_EXT: Record<Dataset['datasetType'], string> = {
  screen: 'png',
  video: 'mp4',
  audio: 'wav',
  text: 'txt',
  multimodal: 'json',
}

export function buildDatasetSamples(seed: DatasetSeed): DatasetSample[] {
  return Array.from({ length: seed.sampleCount }, (_, index) => {
    const annotationStatus = annotationStatusOf(seed, index)
    const reviewStatus = reviewStatusOf(annotationStatus, index)
    const license = buildLicense(index)
    const formatValid = index % 23 !== 0
    const sourceType = SOURCE_TYPES[index % SOURCE_TYPES.length]
    const sourceName = index % 23 === 4 ? '' : SOURCE_NAMES[sourceType]
    const labelSet =
      annotationStatus === 'unannotated'
        ? []
        : index % 13 === 5
          ? ['正常内容', '诱导转账']
          : LABEL_SETS[index % LABEL_SETS.length]
    const hasEvidence = annotationStatus === 'completed' || annotationStatus === 'submitted' || index % 3 !== 0
    const evidence =
      annotationStatus === 'unannotated' || !hasEvidence
        ? []
        : buildEvidence(seed.datasetType, index, labelSet[0] ?? '信息不足')
    const duplicateStatus: DatasetSample['duplicateStatus'] =
      index > 0 && index % 11 === 10 ? 'suspected' : 'unique'
    const hashIndex = duplicateStatus === 'suspected' ? index - 1 : index

    const sample: DatasetSample = {
      sampleId: `S-${seed.datasetId.slice(-3)}-${pad(index + 1, 4)}`,
      datasetId: seed.datasetId,
      sampleName: `sample-${pad(index + 1, 3)}.${FILE_EXT[seed.datasetType]}`,
      modality: seed.datasetType,
      sourceType,
      sourceName,
      sourceNote: sourceName ? '演示来源说明：团队自建或已获授权的演示素材' : '',
      acquiredAt: `2026-${pad((index % 9) + 1, 2)}-${pad((index % 27) + 1, 2)} 10:30`,
      sourceProof: sourceName ? `来源证明演示编号 SRC-${pad(index + 1, 4)}` : '',
      sourceLicense: license,
      licenseStatus: license.reviewStatus,
      riskCategories: labelSet,
      evidenceLocations: evidence,
      annotationStatus,
      reviewStatus,
      duplicateStatus,
      sampleStatus: 'pending',
      qualityResult: 'unchecked',
      formatValid,
      fileHash: `sim-${seed.datasetType}-${pad(hashIndex, 4)}`,
      createdAt: `2026-${pad((index % 9) + 1, 2)}-${pad((index % 27) + 1, 2)} 10:30`,
      updatedAt: seed.updatedAt,
      annotatorId: annotationStatus === 'unannotated' ? undefined : 'demo-annotator',
      annotationNotes: annotationStatus === 'completed' ? '演示标注记录：已由演示标注员完成。' : '',
    }
    sample.sampleStatus = sampleStatusOf(sample)
    return sample
  })
}

/* ===== 运行期存储 ===== */

export const datasetStore: Dataset[] = DATASET_SEEDS.map((seed) => ({
  datasetId: seed.datasetId,
  datasetName: seed.datasetName,
  description: seed.description,
  datasetType: seed.datasetType,
  totalSamples: seed.sampleCount,
  annotatedSamples: seed.completed + seed.submitted,
  approvedSamples: 0,
  datasetVersion: seed.datasetVersion,
  datasetStatus: seed.datasetStatus,
  createdAt: seed.createdAt,
  updatedAt: seed.updatedAt,
  createdBy: seed.createdBy,
}))

export const datasetSamples: DatasetSample[] = DATASET_SEEDS.flatMap((seed) => buildDatasetSamples(seed))

export function buildDatasetVersions(seed: DatasetSeed): DatasetVersion[] {
  const olderCount = Math.max(10, Math.floor(seed.sampleCount * 0.6))
  const versions: DatasetVersion[] = [
    {
      versionId: `VER-${seed.datasetId}-01`,
      datasetId: seed.datasetId,
      versionNumber: 'v1.0.0',
      sampleCount: olderCount,
      trainCount: Math.floor(olderCount * 0.7),
      validationCount: Math.floor(olderCount * 0.15),
      testCount: olderCount - Math.floor(olderCount * 0.7) - Math.floor(olderCount * 0.15),
      versionDescription: '首个内部演示版本，用于验证导入与标注流程。',
      createdAt: '2026-10-20 16:00',
      createdBy: seed.createdBy,
      versionStatus: 'published',
      manualReviewStatus: 'not_required',
      changeLog: ['首次创建数据集版本', '完成基础来源登记'],
    },
  ]

  const currentStatus: DatasetVersion['versionStatus'] =
    seed.datasetStatus === 'ready'
      ? 'published'
      : seed.datasetStatus === 'reviewing'
        ? 'reviewing'
        : 'draft'

  versions.push({
    versionId: `VER-${seed.datasetId}-02`,
    datasetId: seed.datasetId,
    versionNumber: seed.datasetVersion,
    sampleCount: seed.sampleCount,
    trainCount: Math.floor(seed.sampleCount * 0.7),
    validationCount: Math.floor(seed.sampleCount * 0.15),
    testCount: seed.sampleCount - Math.floor(seed.sampleCount * 0.7) - Math.floor(seed.sampleCount * 0.15),
    versionDescription: '当前工作版本，包含最新导入与标注结果。',
    createdAt: seed.updatedAt,
    createdBy: seed.createdBy,
    versionStatus: currentStatus,
    manualReviewStatus: 'not_required',
    changeLog: ['补充来源与许可信息', '更新标注结果并重新生成拆分'],
  })

  return versions
}

export const datasetVersions: DatasetVersion[] = DATASET_SEEDS.flatMap((seed) =>
  buildDatasetVersions(seed),
)

export const annotationRecords: AnnotationRecord[] = []
export const reviewRecords: ReviewRecord[] = []
export const dedupeRecords: DedupeRecord[] = []
export const qualityReports = new Map<string, QualityReport>()

export { DATASET_SEEDS }
export type { DatasetSeed }
