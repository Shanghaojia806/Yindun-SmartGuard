/**
 * A05 训练管理类型定义（P11）。
 *
 * 说明：
 * - 训练实际由独立 Python Worker 执行，浏览器只负责配置与结果展示；
 * - 本文件仅定义前端与 FastAPI 之间的数据契约，不包含任何模型训练实现；
 * - 所有 Mock 任务必须通过 executionMode 与数据来源字段明确区分，禁止冒充真实训练。
 */

import type { DatasetStatus, DatasetType, DatasetVersion, VersionStatus, ManualReviewStatus } from './dataset'

/** 训练任务状态机：PENDING → RUNNING → SUCCEEDED；PENDING/RUNNING → CANCELED；RUNNING → FAILED。 */
export type TrainingTaskStatus = 'pending' | 'running' | 'succeeded' | 'failed' | 'canceled'

/** 执行模式：mock = 本地演示（无真实 Worker），worker = 真实 Python 训练执行器 */
export type ExecutionMode = 'mock' | 'worker'

/** 模型类型：仅作为配置项，不代表对应模型架构已完成研发 */
export type ModelType =
  | 'text_risk_classifier'
  | 'screen_multimodal_risk'
  | 'audio_call_risk'
  | 'video_scam_risk'

/** 训练任务类型：二分类风险判定 / 多标签风险分类 */
export type TrainingTaskType = 'risk_binary' | 'risk_multilabel'

export type OptimizerType = 'adam' | 'adamw' | 'sgd'

export type TrainingDevice = 'cpu' | 'cuda' | 'mps'

export type LogLevel = 'INFO' | 'WARNING' | 'ERROR' | 'DEBUG'

/** 日志来源：worker = 真实训练执行器，mock_demo = 演示日志，api = 业务接口写入 */
export type LogSource = 'worker' | 'mock_demo' | 'api'

export type MetricSource = 'worker' | 'mock'

export type EvaluationStatus = 'not_evaluated' | 'pending' | 'evaluated'

/** 模型配置实现状态：未实现 / 计划中 / Worker 已注册 */
export type ModelImplementationStatus = 'not_implemented' | 'planned' | 'worker_ready'

/* ===== 参数 ===== */

export interface TrainingParameters {
  /** 训练轮数 */
  epochs: number
  /** 批次大小 */
  batchSize: number
  /** 学习率 */
  learningRate: number
  optimizer: OptimizerType
  /** 随机种子 */
  randomSeed: number
  device: TrainingDevice
  /** 验证集比例（百分比，5–50） */
  validationSplit: number
  /** 模型检查点保存间隔（以 Epoch 为单位） */
  checkpointInterval: number
}

/** 参数范围：前端表单与 Mock 服务端共用同一份限制，避免两处规则不一致 */
export const TRAINING_PARAMETER_LIMITS = {
  epochs: { min: 1, max: 200, step: 1 },
  batchSize: { min: 1, max: 512, step: 1 },
  learningRate: { min: 0.00001, max: 0.1, step: 0.00001 },
  randomSeed: { min: 0, max: 4294967295, step: 1 },
  validationSplit: { min: 5, max: 50, step: 1 },
  checkpointInterval: { min: 1, max: 50, step: 1 },
} as const

export const DEFAULT_TRAINING_PARAMETERS: TrainingParameters = {
  epochs: 20,
  batchSize: 32,
  learningRate: 0.001,
  optimizer: 'adamw',
  randomSeed: 42,
  device: 'cpu',
  validationSplit: 15,
  checkpointInterval: 5,
}

/**
 * 参数校验：返回「字段 → 错误文案」，无错误时返回空对象。
 * UI 表单与服务端（Mock Adapter）都调用本函数，真实 FastAPI 仍需再次校验。
 */
export function validateTrainingParameters(
  params: TrainingParameters,
): Record<string, string> {
  const errors: Record<string, string> = {}
  const limits = TRAINING_PARAMETER_LIMITS

  if (
    !Number.isInteger(params.epochs) ||
    params.epochs < limits.epochs.min ||
    params.epochs > limits.epochs.max
  ) {
    errors.epochs = `训练轮数必须是 ${limits.epochs.min}–${limits.epochs.max} 的整数`
  }
  if (
    !Number.isInteger(params.batchSize) ||
    params.batchSize < limits.batchSize.min ||
    params.batchSize > limits.batchSize.max
  ) {
    errors.batchSize = `批次大小必须是 ${limits.batchSize.min}–${limits.batchSize.max} 的整数`
  }
  if (
    !Number.isFinite(params.learningRate) ||
    params.learningRate < limits.learningRate.min ||
    params.learningRate > limits.learningRate.max
  ) {
    errors.learningRate = `学习率必须在 ${limits.learningRate.min}–${limits.learningRate.max} 之间`
  }
  if (!['adam', 'adamw', 'sgd'].includes(params.optimizer)) {
    errors.optimizer = '请选择受支持的优化器'
  }
  if (
    !Number.isInteger(params.randomSeed) ||
    params.randomSeed < limits.randomSeed.min ||
    params.randomSeed > limits.randomSeed.max
  ) {
    errors.randomSeed = `随机种子必须是 ${limits.randomSeed.min}–${limits.randomSeed.max} 的整数`
  }
  if (!['cpu', 'cuda', 'mps'].includes(params.device)) {
    errors.device = '请选择受支持的训练设备'
  }
  if (
    !Number.isInteger(params.validationSplit) ||
    params.validationSplit < limits.validationSplit.min ||
    params.validationSplit > limits.validationSplit.max
  ) {
    errors.validationSplit = `验证集比例必须是 ${limits.validationSplit.min}–${limits.validationSplit.max} 的整数百分比`
  }
  if (
    !Number.isInteger(params.checkpointInterval) ||
    params.checkpointInterval < limits.checkpointInterval.min ||
    params.checkpointInterval > limits.checkpointInterval.max
  ) {
    errors.checkpointInterval = `检查点间隔必须是 ${limits.checkpointInterval.min}–${limits.checkpointInterval.max} 的整数`
  } else if (Number.isInteger(params.epochs) && params.checkpointInterval > params.epochs) {
    errors.checkpointInterval = '检查点间隔不能大于训练轮数'
  }

  return errors
}

/* ===== 模型与配置项 ===== */

export interface TrainingModelOption {
  value: ModelType
  label: string
  description: string
  taskTypes: TrainingTaskType[]
  inputModalities: DatasetType[]
  implementationStatus: ModelImplementationStatus
  implementationNote: string
}

export interface TrainingModelConfig {
  taskType: TrainingTaskType
  framework: 'pytorch'
  modelFamily: string
  inputModalities: DatasetType[]
  implementationStatus: ModelImplementationStatus
  note: string
}

export interface TrainingParameterOption<T extends string> {
  value: T
  label: string
  note: string
}

/* ===== 任务 ===== */

export interface DatasetSplitSnapshot {
  trainCount: number
  validationCount: number
  testCount: number
}

export interface TrainingProgress {
  currentEpoch: number
  totalEpochs: number
  stage: string
  updatedAt: string
}

export interface TrainingTask {
  taskId: string
  taskName: string
  taskDescription: string
  modelType: ModelType
  modelConfig: TrainingModelConfig
  datasetId: string
  datasetName: string
  datasetVersionId: string
  datasetVersion: string
  datasetSplit: DatasetSplitSnapshot
  trainingParameters: TrainingParameters
  taskStatus: TrainingTaskStatus
  executionMode: ExecutionMode
  createdAt: string
  startedAt: string
  completedAt: string
  createdBy: string
  errorMessage: string
  progress: TrainingProgress
  /** 已发出取消请求但执行器尚未确认停止的时间 */
  cancelRequestedAt?: string
  /** 重试来源任务 */
  retryOfTaskId?: string
  /** 由本次任务重试生成的后续任务 */
  retriedByTaskId?: string
  /** 第几次执行（从 1 开始） */
  attempt: number
}

export interface TrainingTaskQuery {
  keyword: string
  modelType: ModelType | ''
  datasetId: string
  taskStatus: TrainingTaskStatus | ''
  executionMode: ExecutionMode | ''
  createdFrom: string
  createdTo: string
  page: number
  pageSize: number
}

export interface TrainingOverviewStats {
  total: number
  pending: number
  running: number
  succeeded: number
  failed: number
  canceled: number
}

export interface CreateTrainingTaskInput {
  taskName: string
  taskDescription: string
  modelType: ModelType
  taskType: TrainingTaskType
  datasetId: string
  datasetVersionId: string
  trainingParameters: TrainingParameters
}

export interface TrainingDatasetOption {
  datasetId: string
  datasetName: string
  datasetType: DatasetType
  datasetStatus: DatasetStatus
  versions: DatasetVersion[]
}

export interface TrainingOptions {
  modelTypes: TrainingModelOption[]
  optimizers: Array<TrainingParameterOption<OptimizerType>>
  devices: Array<TrainingParameterOption<TrainingDevice>>
  statuses: Array<{ value: TrainingTaskStatus; label: string }>
  executionModes: Array<{ value: ExecutionMode; label: string }>
  taskTypes: Array<{ value: TrainingTaskType; label: string }>
  datasets: TrainingDatasetOption[]
}

/* ===== 数据集资格校验 ===== */

export interface TrainingDatasetCheckItem {
  key: 'source' | 'license' | 'quality' | 'duplicate' | 'version_status' | 'manual_review' | 'split'
  label: string
  passed: boolean
  detail: string
  /** 是否为阻断项；Mock 阶段所有未通过项均阻断创建 */
  blocking: boolean
}

export interface TrainingDatasetCheck {
  datasetId: string
  datasetName: string
  datasetType: DatasetType
  versionId: string
  versionNumber: string
  versionStatus: VersionStatus
  manualReviewStatus: ManualReviewStatus
  sampleCount: number
  trainCount: number
  validationCount: number
  testCount: number
  eligible: boolean
  checks: TrainingDatasetCheckItem[]
  blockedReasons: string[]
  checkedAt: string
  note: string
}

/* ===== 日志 ===== */

export interface TrainingLog {
  logId: string
  taskId: string
  timestamp: string
  level: LogLevel
  message: string
  source: LogSource
}

export interface TrainingLogQuery {
  level: LogLevel | ''
  keyword: string
  page: number
  pageSize: number
}

export interface TrainingLogPage {
  items: TrainingLog[]
  page: number
  pageSize: number
  total: number
  executionMode: ExecutionMode
  /** 日志来源说明，Mock 任务必须明确提示尚未连接真实执行器 */
  notice: string
}

/* ===== 指标与结果 ===== */

export interface TrainingMetric {
  epoch: number
  /** 训练集损失 */
  trainLoss: number
  /** 验证集损失 */
  validationLoss: number
  /** 训练集准确率 */
  trainAccuracy: number
  /** 验证集准确率 */
  validationAccuracy: number
  /** 验证集精确率 */
  validationPrecision: number
  /** 验证集召回率 */
  validationRecall: number
  /** 验证集 F1 */
  validationF1: number
  recordedAt: string
}

export interface TrainingMetricSeries {
  taskId: string
  source: MetricSource
  metrics: TrainingMetric[]
  notice: string
}

export interface TrainingCheckpoint {
  epoch: number
  checkpointPath: string
  validationLoss: number
}

export interface TrainingResult {
  resultId: string
  taskId: string
  finalMetrics: TrainingMetric
  bestCheckpoint: TrainingCheckpoint
  modelArtifactPath: string
  datasetVersion: string
  completedAt: string
  evaluationStatus: EvaluationStatus
  evaluationSummary: string
  /** 是否为 Mock 演示结果：true 表示未执行真实 PyTorch 训练 */
  simulated: boolean
}
