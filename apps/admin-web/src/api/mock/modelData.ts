import type {
  EvaluationSummary,
  ModelArtifact,
  ModelAuditRecord,
  ModelFormat,
  ModelQuantization,
  ModelSummary,
  ModelVersion,
  ReleaseRecord,
  RuntimeCompatibility,
} from '@/types/model'

/**
 * A06 模型仓库演示数据（P12）。
 *
 * 重要声明：
 * - 当前仓库没有真实模型文件，也没有真实 Worker 训练产物；
 * - 所有产物均为演示记录（isMock = true），fileHash / signature 为空字符串，
 *   不伪造任何“已校验通过”的哈希或签名；
 * - 所有评估指标为演示值，不代表真实反诈能力；
 * - 兼容性记录未在真实设备上测试。
 */

export const MODEL_DEMO_NOTE =
  '演示数据：模型仓库内没有真实模型文件，文件哈希、签名、评估指标与兼容性测试均为演示记录。'

export const MODEL_FILE_DEMO_NOTE = '当前为模型版本演示记录，尚无真实模型文件。'

export const MODEL_WORKER_NOTE =
  'P11 尚未接入真实 Python 训练 Worker，本页训练来源均为演示任务。'

export const MODEL_RELEASE_DEMO_NOTE =
  '当前发布记录为演示发布，不产生真实可用模型，也不会向任何 Android 设备下发。'

const DEMO_OPERATOR = '演示管理员'
const DEMO_REVIEWER = '审核员·王'

function makeArtifact(
  artifactId: string,
  modelId: string,
  modelName: string,
  modelType: ModelArtifact['modelType'],
  artifactFormat: ModelFormat,
  fileSizeBytes: number,
  createdAt: string,
  createdBy: string,
): ModelArtifact {
  return {
    artifactId,
    modelId,
    modelName,
    modelType,
    framework: 'pytorch',
    artifactPath: `ml/artifacts/demo/${modelId}/${artifactId}.${artifactFormat === 'pytorch_pt' ? 'pt' : artifactFormat}`,
    artifactFormat,
    fileSizeBytes,
    fileHash: '',
    fileHashStatus: 'demo_record',
    signature: '',
    signatureStatus: 'demo_record',
    createdAt,
    createdBy,
    artifactSource: 'demo_record',
    isMock: true,
  }
}

function makeQuantization(
  quantizationId: string,
  format: ModelQuantization['format'],
  originalSizeBytes: number,
  quantizedSizeBytes: number | null,
  method: string,
  quantizedAt: string | null,
  status: ModelQuantization['status'],
): ModelQuantization {
  return {
    quantizationId,
    format,
    originalSizeBytes,
    quantizedSizeBytes,
    method,
    quantizedAt,
    accuracyDeltaPercent: null,
    compatibleRuntimes:
      format === 'fp32' ? ['pytorch_mobile'] : format === 'fp16' ? ['onnxruntime_mobile'] : ['onnxruntime_mobile', 'tflite'],
    status,
    isMock: true,
  }
}

function makeCompatibility(
  compatibilityId: string,
  values: Partial<RuntimeCompatibility> & Pick<RuntimeCompatibility, 'compatibilityStatus'>,
): RuntimeCompatibility {
  return {
    compatibilityId,
    platform: 'android',
    minOsVersion: '8.0',
    maxOsVersion: '14',
    architecture: 'arm64-v8a',
    inferenceRuntime: 'onnxruntime_mobile',
    runtimeVersion: '1.16.3',
    supportedDevices: [],
    memoryRequirementMb: 256,
    acceleratorSupport: ['cpu'],
    testedAt: '',
    testedBy: '',
    notes: '',
    isMock: true,
    ...values,
  }
}

function demoEvaluation(accuracy: number, precision: number, recall: number, f1: number, loss: number): EvaluationSummary {
  return {
    accuracy,
    precision,
    recall,
    f1Score: f1,
    validationLoss: loss,
    testDatasetVersion: '',
    evaluatedAt: '2026-11-13 16:20',
    evaluationMethod: '演示评估：Mock 指标，未执行真实推理',
    evaluationStatus: 'demo',
    source: 'mock_demo',
  }
}

function emptyEvaluation(): EvaluationSummary {
  return {
    accuracy: null,
    precision: null,
    recall: null,
    f1Score: null,
    validationLoss: null,
    testDatasetVersion: '',
    evaluatedAt: '',
    evaluationMethod: '',
    evaluationStatus: 'not_evaluated',
    source: 'mock_demo',
  }
}

/* ===== 模型产物 ===== */

export const modelArtifacts: ModelArtifact[] = [
  makeArtifact('ART-2026-001', 'M-2026-001', '文本反诈风险分类模型', 'text_risk_classifier', 'pytorch_pt', 48_283_648, '2026-11-10 10:05', DEMO_OPERATOR),
  makeArtifact('ART-2026-002', 'M-2026-001', '文本反诈风险分类模型', 'text_risk_classifier', 'onnx', 46_137_344, '2026-11-12 15:10', DEMO_OPERATOR),
  makeArtifact('ART-2026-003', 'M-2026-001', '文本反诈风险分类模型', 'text_risk_classifier', 'onnx', 46_792_704, '2026-11-17 09:30', DEMO_OPERATOR),
  makeArtifact('ART-2026-004', 'M-2026-002', '屏幕多模态风险识别模型', 'screen_multimodal_risk', 'onnx', 112_407_552, '2026-11-08 09:00', DEMO_OPERATOR),
  makeArtifact('ART-2026-005', 'M-2026-002', '屏幕多模态风险识别模型', 'screen_multimodal_risk', 'pytorch_pt', 118_111_232, '2026-11-16 14:00', DEMO_OPERATOR),
  makeArtifact('ART-2026-006', 'M-2026-003', '语音通话诈骗识别模型', 'audio_call_risk', 'pytorch_pt', 32_112_640, '2026-11-17 15:00', DEMO_OPERATOR),
  makeArtifact('ART-2026-007', 'M-2026-004', '短视频风险识别模型', 'video_scam_risk', 'onnx', 87_031_808, '2026-11-18 10:00', DEMO_OPERATOR),
]

/* ===== 模型版本 ===== */

export const modelVersions: ModelVersion[] = [
  {
    modelVersionId: 'MV-2026-001-01',
    modelId: 'M-2026-001',
    modelVersion: 'v1.0.0',
    datasetId: 'DS-2026-004',
    datasetName: '团队合成诱导转账文本',
    datasetVersionId: 'VER-DS-2026-004-02',
    datasetVersion: 'v1.1.0',
    trainingTaskId: 'T-2026-001',
    artifactId: 'ART-2026-001',
    modelFormat: 'pytorch_pt',
    quantization: [
      makeQuantization('Q-2026-001', 'fp32', 48_283_648, 48_283_648, '原始精度（未量化）', null, 'demo_record'),
      makeQuantization('Q-2026-002', 'int8', 48_283_648, 12_582_912, '演示：训练后静态量化配置', '2026-11-13 15:40', 'demo_record'),
    ],
    runtimeCompatibility: [
      makeCompatibility('RC-2026-001', {
        compatibilityStatus: 'compatible',
        architecture: 'arm64-v8a',
        inferenceRuntime: 'onnxruntime_mobile',
        runtimeVersion: '1.16.3',
        supportedDevices: ['Redmi Note 12（演示）', 'Huawei nova 11（演示）'],
        memoryRequirementMb: 256,
        acceleratorSupport: ['cpu'],
        testedAt: '2026-11-13 15:00',
        testedBy: '演示测试员',
        notes: '演示记录：未在真实设备执行推理。',
      }),
    ],
    evaluationSummary: {
      ...demoEvaluation(0.902, 0.887, 0.879, 0.883, 0.287),
      testDatasetVersion: 'VER-DS-2026-004-02',
    },
    releaseStatus: 'approved',
    createdAt: '2026-11-10 10:05',
    createdBy: DEMO_OPERATOR,
    reviewedAt: '2026-11-13 16:00',
    reviewedBy: DEMO_REVIEWER,
    publishedAt: '',
    withdrawnAt: '',
    description: '文本反诈基线版本，用于验证模型仓库与发布流程。',
    isMock: true,
  },
  {
    modelVersionId: 'MV-2026-001-02',
    modelId: 'M-2026-001',
    modelVersion: 'v1.1.0',
    datasetId: 'DS-2026-004',
    datasetName: '团队合成诱导转账文本',
    datasetVersionId: 'VER-DS-2026-004-01',
    datasetVersion: 'v1.0.0',
    trainingTaskId: 'T-2026-006',
    artifactId: 'ART-2026-002',
    modelFormat: 'onnx',
    quantization: [
      makeQuantization('Q-2026-003', 'fp32', 46_137_344, 46_137_344, '原始精度（未量化）', null, 'demo_record'),
      makeQuantization('Q-2026-004', 'fp16', 46_137_344, 24_117_248, '演示：FP16 转换配置', '2026-11-14 09:10', 'demo_record'),
    ],
    runtimeCompatibility: [
      makeCompatibility('RC-2026-002', {
        compatibilityStatus: 'compatible',
        architecture: 'arm64-v8a',
        supportedDevices: ['Redmi Note 12（演示）'],
        testedAt: '2026-11-14 08:30',
        testedBy: '演示测试员',
        notes: '演示记录：未在真实设备执行推理。',
      }),
      makeCompatibility('RC-2026-003', {
        compatibilityStatus: 'partially_compatible',
        architecture: 'armeabi-v7a',
        runtimeVersion: '1.15.1',
        memoryRequirementMb: 384,
        testedAt: '2026-11-14 08:45',
        testedBy: '演示测试员',
        notes: '演示记录：旧架构设备内存占用偏高。',
      }),
    ],
    evaluationSummary: {
      ...demoEvaluation(0.918, 0.904, 0.891, 0.897, 0.241),
      testDatasetVersion: 'VER-DS-2026-004-01',
    },
    releaseStatus: 'published',
    createdAt: '2026-11-12 15:10',
    createdBy: DEMO_OPERATOR,
    reviewedAt: '2026-11-14 09:40',
    reviewedBy: DEMO_REVIEWER,
    publishedAt: '2026-11-14 10:20',
    withdrawnAt: '',
    description: '演示发布版本，未向任何真实设备下发。',
    isMock: true,
  },
  {
    modelVersionId: 'MV-2026-001-03',
    modelId: 'M-2026-001',
    modelVersion: 'v1.2.0-rc',
    datasetId: 'DS-2026-004',
    datasetName: '团队合成诱导转账文本',
    datasetVersionId: 'VER-DS-2026-004-01',
    datasetVersion: 'v1.0.0',
    trainingTaskId: 'T-2026-010',
    artifactId: 'ART-2026-003',
    modelFormat: 'onnx',
    quantization: [
      makeQuantization('Q-2026-005', 'fp32', 46_792_704, 46_792_704, '原始精度（未量化）', null, 'demo_record'),
    ],
    runtimeCompatibility: [
      makeCompatibility('RC-2026-004', {
        compatibilityStatus: 'testing',
        architecture: 'arm64-v8a',
        testedAt: '',
        testedBy: '',
        notes: '演示记录：兼容性测试尚未完成。',
      }),
    ],
    evaluationSummary: {
      ...demoEvaluation(0.921, 0.908, 0.896, 0.902, 0.233),
      testDatasetVersion: 'VER-DS-2026-004-01',
    },
    releaseStatus: 'pending_review',
    createdAt: '2026-11-17 09:30',
    createdBy: DEMO_OPERATOR,
    reviewedAt: '',
    reviewedBy: '',
    publishedAt: '',
    withdrawnAt: '',
    description: '候选版本，待审核；用于验证审核清单与发布前拦截。',
    isMock: true,
  },
  {
    modelVersionId: 'MV-2026-002-01',
    modelId: 'M-2026-002',
    modelVersion: 'v0.9.0',
    datasetId: 'DS-2026-001',
    datasetName: '反诈屏幕对话样本集',
    datasetVersionId: 'VER-DS-2026-001-02',
    datasetVersion: 'v1.2.0',
    trainingTaskId: 'T-2026-009',
    artifactId: 'ART-2026-004',
    modelFormat: 'onnx',
    quantization: [
      makeQuantization('Q-2026-006', 'fp32', 112_407_552, 112_407_552, '原始精度（未量化）', null, 'demo_record'),
    ],
    runtimeCompatibility: [
      makeCompatibility('RC-2026-005', {
        compatibilityStatus: 'untested',
        architecture: 'arm64-v8a',
        memoryRequirementMb: 512,
        notes: '演示记录：未在真实设备测试。',
      }),
    ],
    evaluationSummary: demoEvaluation(0.874, 0.842, 0.811, 0.826, 0.372),
    releaseStatus: 'withdrawn',
    createdAt: '2026-11-08 09:00',
    createdBy: DEMO_OPERATOR,
    reviewedAt: '2026-11-08 10:00',
    reviewedBy: DEMO_REVIEWER,
    publishedAt: '2026-11-08 11:00',
    withdrawnAt: '2026-11-16 11:00',
    description: '已撤回的演示版本，用于验证撤回记录与风险提示。',
    isMock: true,
  },
  {
    modelVersionId: 'MV-2026-002-02',
    modelId: 'M-2026-002',
    modelVersion: 'v1.0.0',
    datasetId: 'DS-2026-001',
    datasetName: '反诈屏幕对话样本集',
    datasetVersionId: 'VER-DS-2026-001-02',
    datasetVersion: 'v1.2.0',
    trainingTaskId: '',
    artifactId: 'ART-2026-005',
    modelFormat: 'pytorch_pt',
    quantization: [],
    runtimeCompatibility: [
      makeCompatibility('RC-2026-006', {
        compatibilityStatus: 'untested',
        memoryRequirementMb: 512,
        notes: '演示记录：未在真实设备测试。',
      }),
    ],
    evaluationSummary: emptyEvaluation(),
    releaseStatus: 'draft',
    createdAt: '2026-11-16 14:00',
    createdBy: DEMO_OPERATOR,
    reviewedAt: '',
    reviewedBy: '',
    publishedAt: '',
    withdrawnAt: '',
    description: '草稿版本：未关联训练任务、未评估、未测试兼容性。',
    isMock: true,
  },
  {
    modelVersionId: 'MV-2026-003-01',
    modelId: 'M-2026-003',
    modelVersion: 'v0.5.0',
    datasetId: 'DS-2026-002',
    datasetName: '冒充客服语音片段',
    datasetVersionId: 'VER-DS-2026-002-01',
    datasetVersion: 'v1.0.0',
    trainingTaskId: 'T-2026-003',
    artifactId: 'ART-2026-006',
    modelFormat: 'pytorch_pt',
    quantization: [],
    runtimeCompatibility: [
      makeCompatibility('RC-2026-007', {
        compatibilityStatus: 'untested',
        architecture: 'arm64-v8a',
        inferenceRuntime: 'pytorch_mobile',
        memoryRequirementMb: 640,
        notes: '演示记录：未在真实设备测试。',
      }),
    ],
    evaluationSummary: emptyEvaluation(),
    releaseStatus: 'rejected',
    createdAt: '2026-11-17 15:00',
    createdBy: DEMO_OPERATOR,
    reviewedAt: '2026-11-17 16:00',
    reviewedBy: DEMO_REVIEWER,
    publishedAt: '',
    withdrawnAt: '',
    description: '关联训练任务失败，审核拒绝。',
    isMock: true,
  },
  {
    modelVersionId: 'MV-2026-004-01',
    modelId: 'M-2026-004',
    modelVersion: 'v0.8.0',
    datasetId: 'DS-2026-003',
    datasetName: '虚假投资短视频样本',
    datasetVersionId: 'VER-DS-2026-003-01',
    datasetVersion: 'v1.0.0',
    trainingTaskId: '',
    artifactId: 'ART-2026-007',
    modelFormat: 'onnx',
    quantization: [],
    runtimeCompatibility: [
      makeCompatibility('RC-2026-008', {
        compatibilityStatus: 'test_failed',
        inferenceRuntime: 'tflite',
        runtimeVersion: '2.14.0',
        memoryRequirementMb: 768,
        testedAt: '2026-11-18 10:30',
        testedBy: '演示测试员',
        notes: '演示记录：模拟测试失败（运行时算子不支持）。',
      }),
    ],
    evaluationSummary: emptyEvaluation(),
    releaseStatus: 'draft',
    createdAt: '2026-11-18 10:00',
    createdBy: DEMO_OPERATOR,
    reviewedAt: '',
    reviewedBy: '',
    publishedAt: '',
    withdrawnAt: '',
    description: '兼容性测试失败的草稿版本，用于展示风险状态。',
    isMock: true,
  },
]

/* ===== 模型仓库 ===== */

interface ModelSeed {
  modelId: string
  modelName: string
  modelType: ModelSummary['modelType']
  description: string
  trainingTaskId: string
  trainingSourceLabel: string
}

const MODEL_SEEDS: ModelSeed[] = [
  {
    modelId: 'M-2026-001',
    modelName: '文本反诈风险分类模型',
    modelType: 'text_risk_classifier',
    description: '面向诱导转账、冒充客服等文本话术的风险分类模型（演示配置）。',
    trainingTaskId: 'T-2026-006',
    trainingSourceLabel: '训练任务 T-2026-006（模拟执行）',
  },
  {
    modelId: 'M-2026-002',
    modelName: '屏幕多模态风险识别模型',
    modelType: 'screen_multimodal_risk',
    description: '屏幕文本与页面结构联合识别的多模态模型（演示配置）。',
    trainingTaskId: 'T-2026-009',
    trainingSourceLabel: '训练任务 T-2026-009（失败，演示）',
  },
  {
    modelId: 'M-2026-003',
    modelName: '语音通话诈骗识别模型',
    modelType: 'audio_call_risk',
    description: '通话音频话术风险识别模型（演示配置）。',
    trainingTaskId: 'T-2026-003',
    trainingSourceLabel: '训练任务 T-2026-003（失败，演示）',
  },
  {
    modelId: 'M-2026-004',
    modelName: '短视频风险识别模型',
    modelType: 'video_scam_risk',
    description: '短视频画面与字幕风险识别模型（演示配置）。',
    trainingTaskId: '',
    trainingSourceLabel: '未关联训练任务',
  },
]

export const models: ModelSummary[] = MODEL_SEEDS.map((seed) => {
  const versions = modelVersions.filter((item) => item.modelId === seed.modelId)
  const latest = [...versions].sort((a, b) => b.createdAt.localeCompare(a.createdAt))[0]
  const published = versions
    .filter((item) => item.releaseStatus === 'published')
    .sort((a, b) => b.publishedAt.localeCompare(a.publishedAt))[0]
  return {
    modelId: seed.modelId,
    modelName: seed.modelName,
    modelType: seed.modelType,
    framework: 'pytorch',
    description: seed.description,
    latestVersion: latest?.modelVersion ?? '—',
    latestVersionId: latest?.modelVersionId ?? '',
    publishedVersion: published?.modelVersion ?? '',
    publishedVersionId: published?.modelVersionId ?? '',
    trainingTaskId: seed.trainingTaskId,
    trainingSourceLabel: seed.trainingSourceLabel,
    releaseStatus: latest?.releaseStatus ?? 'draft',
    compatibilityStatus: latest?.runtimeCompatibility[0]?.compatibilityStatus ?? 'untested',
    modelStatus: 'active',
    updatedAt: latest?.createdAt ?? '',
    versionCount: versions.length,
    isMock: true,
  }
})

/* ===== 发布记录 ===== */

export const releaseRecords: ReleaseRecord[] = [
  {
    releaseId: 'RL-2026-001',
    modelId: 'M-2026-001',
    modelVersionId: 'MV-2026-001-02',
    modelVersion: 'v1.1.0',
    releaseChannel: 'internal_test',
    releaseStatus: 'published',
    operator: DEMO_OPERATOR,
    reviewedBy: DEMO_REVIEWER,
    submittedAt: '2026-11-14 09:30',
    approvedAt: '2026-11-14 09:40',
    publishedAt: '2026-11-14 10:20',
    withdrawnAt: '',
    rollbackFrom: '',
    rollbackTo: '',
    operationNotes: '演示发布：仅用于流程验证，未向真实设备下发。',
    isMock: true,
  },
  {
    releaseId: 'RL-2026-002',
    modelId: 'M-2026-001',
    modelVersionId: 'MV-2026-001-01',
    modelVersion: 'v1.0.0',
    releaseChannel: 'internal_test',
    releaseStatus: 'approved',
    operator: DEMO_OPERATOR,
    reviewedBy: DEMO_REVIEWER,
    submittedAt: '2026-11-13 15:30',
    approvedAt: '2026-11-13 16:00',
    publishedAt: '',
    withdrawnAt: '',
    rollbackFrom: '',
    rollbackTo: '',
    operationNotes: '演示审核通过，等待发布。',
    isMock: true,
  },
  {
    releaseId: 'RL-2026-003',
    modelId: 'M-2026-001',
    modelVersionId: 'MV-2026-001-03',
    modelVersion: 'v1.2.0-rc',
    releaseChannel: 'canary',
    releaseStatus: 'pending_review',
    operator: DEMO_OPERATOR,
    reviewedBy: '',
    submittedAt: '2026-11-17 10:00',
    approvedAt: '',
    publishedAt: '',
    withdrawnAt: '',
    rollbackFrom: '',
    rollbackTo: '',
    operationNotes: '提交灰度审核，等待审核结果。',
    isMock: true,
  },
  {
    releaseId: 'RL-2026-004',
    modelId: 'M-2026-002',
    modelVersionId: 'MV-2026-002-01',
    modelVersion: 'v0.9.0',
    releaseChannel: 'internal_test',
    releaseStatus: 'withdrawn',
    operator: DEMO_OPERATOR,
    reviewedBy: DEMO_REVIEWER,
    submittedAt: '2026-11-08 09:30',
    approvedAt: '2026-11-08 10:00',
    publishedAt: '2026-11-08 11:00',
    withdrawnAt: '2026-11-16 11:00',
    rollbackFrom: '',
    rollbackTo: '',
    operationNotes: '演示撤回：兼容性未验证，停止分发。',
    isMock: true,
  },
  {
    releaseId: 'RL-2026-005',
    modelId: 'M-2026-003',
    modelVersionId: 'MV-2026-003-01',
    modelVersion: 'v0.5.0',
    releaseChannel: 'production',
    releaseStatus: 'rejected',
    operator: DEMO_OPERATOR,
    reviewedBy: DEMO_REVIEWER,
    submittedAt: '2026-11-17 15:30',
    approvedAt: '',
    publishedAt: '',
    withdrawnAt: '',
    rollbackFrom: '',
    rollbackTo: '',
    operationNotes: '审核拒绝：关联训练任务失败，评估未完成。',
    isMock: true,
  },
]

/* ===== 发布审计 ===== */

function audit(
  auditId: string,
  operator: string,
  operation: ModelAuditRecord['operation'],
  operationLabel: string,
  targetId: string,
  targetLabel: string,
  operatedAt: string,
  result: ModelAuditRecord['result'],
  notes: string,
): ModelAuditRecord {
  return {
    auditId,
    operator,
    operation,
    operationLabel,
    targetType: 'model_version',
    targetId,
    targetLabel,
    operatedAt,
    result,
    notes,
    isMock: true,
  }
}

export const modelAudits: ModelAuditRecord[] = [
  audit('AU-2026-001', DEMO_OPERATOR, 'create_version', '创建版本', 'MV-2026-001-01', 'v1.0.0', '2026-11-10 10:05', 'success', '创建演示模型版本记录。'),
  audit('AU-2026-002', DEMO_REVIEWER, 'submit_review', '提交审核', 'MV-2026-001-01', 'v1.0.0', '2026-11-13 15:30', 'success', '提交内部测试审核（演示）。'),
  audit('AU-2026-003', DEMO_REVIEWER, 'approve', '审核通过', 'MV-2026-001-01', 'v1.0.0', '2026-11-13 16:00', 'success', '演示审核通过。'),
  audit('AU-2026-004', DEMO_OPERATOR, 'create_version', '创建版本', 'MV-2026-001-02', 'v1.1.0', '2026-11-12 15:10', 'success', '创建演示模型版本记录。'),
  audit('AU-2026-005', DEMO_REVIEWER, 'submit_review', '提交审核', 'MV-2026-001-02', 'v1.1.0', '2026-11-14 09:30', 'success', '提交内部测试审核（演示）。'),
  audit('AU-2026-006', DEMO_REVIEWER, 'approve', '审核通过', 'MV-2026-001-02', 'v1.1.0', '2026-11-14 09:40', 'success', '演示审核通过。'),
  audit('AU-2026-007', DEMO_OPERATOR, 'publish', '发布', 'MV-2026-001-02', 'v1.1.0', '2026-11-14 10:20', 'success', '演示发布（内部测试），未向真实设备下发。'),
  audit('AU-2026-008', DEMO_OPERATOR, 'create_version', '创建版本', 'MV-2026-001-03', 'v1.2.0-rc', '2026-11-17 09:30', 'success', '创建候选版本（演示）。'),
  audit('AU-2026-009', DEMO_OPERATOR, 'submit_review', '提交审核', 'MV-2026-001-03', 'v1.2.0-rc', '2026-11-17 10:00', 'success', '提交灰度审核（演示）。'),
  audit('AU-2026-010', DEMO_OPERATOR, 'publish', '发布', 'MV-2026-002-01', 'v0.9.0', '2026-11-08 11:00', 'success', '历史演示发布记录。'),
  audit('AU-2026-011', DEMO_OPERATOR, 'withdraw', '撤回', 'MV-2026-002-01', 'v0.9.0', '2026-11-16 11:00', 'success', '演示撤回：停止分发，不涉及设备端卸载。'),
  audit('AU-2026-012', DEMO_OPERATOR, 'hash_verify_failed', '文件校验失败', 'MV-2026-001-03', 'v1.2.0-rc', '2026-11-17 10:05', 'blocked', '无真实模型文件，完整性校验无法执行。'),
  audit('AU-2026-013', DEMO_REVIEWER, 'reject', '审核拒绝', 'MV-2026-003-01', 'v0.5.0', '2026-11-17 16:00', 'success', '训练任务失败且评估未完成，拒绝发布。'),
  audit('AU-2026-014', DEMO_OPERATOR, 'compatibility_test_failed', '兼容性测试失败', 'MV-2026-004-01', 'v0.8.0', '2026-11-18 10:30', 'failed', '录制的演示兼容性测试失败记录。'),
]

/** 演示时间（用于新增记录） */
export function nowText(): string {
  const date = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}
