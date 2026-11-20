/**
 * A05 训练管理演示数据（P11）。
 *
 * 重要约束：
 * - 当前仓库不存在 services/business-api 与 ml/training 真实训练执行器；
 * - 因此本文件中的任务、日志、指标、结果均为明确标注的演示数据，
 *   不代表真实 PyTorch 训练过程，也不能作为模型反诈能力的结论；
 * - 数据集名称与版本信息读取 A04 的 Mock 数据，保持跨模块一致。
 */

import type { DatasetVersion } from '@/types/dataset'
import type {
  ExecutionMode,
  ModelType,
  OptimizerType,
  TrainingDevice,
  TrainingLog,
  TrainingMetric,
  TrainingParameters,
  TrainingResult,
  TrainingTask,
  TrainingTaskStatus,
  TrainingTaskType,
  TrainingModelOption,
  TrainingParameterOption,
} from '@/types/training'
import { DEFAULT_TRAINING_PARAMETERS } from '@/types/training'
import { datasetStore, datasetVersions } from './datasetData'

export const TRAINING_DEMO_NOTICE =
  '演示数据：当前未连接真实 Python 训练执行器，任务不会执行真实 PyTorch 训练'

export const UNCONNECTED_WORKER_NOTICE = '当前为演示日志，尚未连接真实训练执行器。'

/* ===== 模型类型配置 ===== */

export const MODEL_OPTIONS: TrainingModelOption[] = [
  {
    value: 'text_risk_classifier',
    label: '文本反诈分类模型',
    description: '面向聊天文本、诱导话术的风险二分类/多标签分类配置。',
    taskTypes: ['risk_binary', 'risk_multilabel'],
    inputModalities: ['text'],
    implementationStatus: 'not_implemented',
    implementationNote: '仅提供训练配置项，仓库内尚无对应 PyTorch 模型实现。',
  },
  {
    value: 'screen_multimodal_risk',
    label: '屏幕多模态风险模型',
    description: '面向屏幕截图与上下文文本的混合模态风险识别配置。',
    taskTypes: ['risk_binary', 'risk_multilabel'],
    inputModalities: ['screen', 'text', 'multimodal'],
    implementationStatus: 'not_implemented',
    implementationNote: '仅提供训练配置项，屏幕特征编码与融合网络尚未实现。',
  },
  {
    value: 'audio_call_risk',
    label: '通话语音风险模型',
    description: '面向冒充客服、诱导转账等语音话术的风险识别配置。',
    taskTypes: ['risk_binary'],
    inputModalities: ['audio'],
    implementationStatus: 'not_implemented',
    implementationNote: '仅提供训练配置项，语音特征提取流程尚未实现。',
  },
  {
    value: 'video_scam_risk',
    label: '短视频风险模型',
    description: '面向虚假投资、刷单返利类短视频的风险识别配置。',
    taskTypes: ['risk_binary', 'risk_multilabel'],
    inputModalities: ['video', 'multimodal'],
    implementationStatus: 'not_implemented',
    implementationNote: '仅提供训练配置项，视频时序建模尚未实现。',
  },
]

export const OPTIMIZER_OPTIONS: Array<TrainingParameterOption<OptimizerType>> = [
  { value: 'adam', label: 'Adam', note: '通用默认优化器' },
  { value: 'adamw', label: 'AdamW', note: '带解耦权重衰减，推荐用于 Transformer 类结构' },
  { value: 'sgd', label: 'SGD', note: '需配合学习率调度使用' },
]

export const DEVICE_OPTIONS: Array<TrainingParameterOption<TrainingDevice>> = [
  { value: 'cpu', label: 'CPU', note: '模拟环境默认设备，不执行真实训练' },
  { value: 'cuda', label: 'CUDA（GPU）', note: '需训练 Worker 所在机器具备可用 GPU' },
  { value: 'mps', label: 'MPS（Apple GPU）', note: '需 Apple Silicon 环境' },
]

export const TASK_TYPE_OPTIONS: Array<{ value: TrainingTaskType; label: string }> = [
  { value: 'risk_binary', label: '风险二分类' },
  { value: 'risk_multilabel', label: '风险多标签分类' },
]

export const STATUS_OPTIONS: Array<{ value: TrainingTaskStatus; label: string }> = [
  { value: 'pending', label: '等待执行' },
  { value: 'running', label: '运行中' },
  { value: 'succeeded', label: '训练成功' },
  { value: 'failed', label: '训练失败' },
  { value: 'canceled', label: '已取消' },
]

export const EXECUTION_MODE_OPTIONS: Array<{ value: ExecutionMode; label: string }> = [
  { value: 'mock', label: '模拟任务（无真实执行器）' },
  { value: 'worker', label: '真实 Worker' },
]

/* ===== 工具函数 ===== */

function pad(value: number, length = 2): string {
  return String(value).padStart(length, '0')
}

function addMinutes(base: string, minutes: number): string {
  const parsed = new Date(base.replace(' ', 'T'))
  parsed.setMinutes(parsed.getMinutes() + minutes)
  return `${parsed.getFullYear()}-${pad(parsed.getMonth() + 1)}-${pad(parsed.getDate())} ${pad(
    parsed.getHours(),
  )}:${pad(parsed.getMinutes())}:${pad(parsed.getSeconds())}`
}

export function formatTrainingTime(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(
    date.getHours(),
  )}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function createRng(seed: number): () => number {
  let state = seed >>> 0
  return () => {
    state = (state * 1664525 + 1013904223) >>> 0
    return state / 4294967296
  }
}

function clamp01(value: number): number {
  return Math.min(1, Math.max(0, value))
}

function round3(value: number): number {
  return Math.round(value * 1000) / 1000
}

/* ===== 任务种子 ===== */

interface TaskSeed {
  taskId: string
  taskName: string
  taskDescription: string
  modelType: ModelType
  taskType: TrainingTaskType
  datasetId: string
  versionId: string
  taskStatus: TrainingTaskStatus
  createdAt: string
  startedAt: string
  completedAt: string
  errorMessage: string
  currentEpoch: number
  stage: string
  createdBy: string
  parameters?: Partial<TrainingParameters>
  cancelRequestedAt?: string
  attempt?: number
  retryOfTaskId?: string
}

const TASK_SEEDS: TaskSeed[] = [
  {
    taskId: 'T-2026-012',
    taskName: '冒充客服语音-重训准备',
    taskDescription: '使用已发布语音版本重新训练，验证取消与排队流程。',
    modelType: 'audio_call_risk',
    taskType: 'risk_binary',
    datasetId: 'DS-2026-002',
    versionId: 'VER-DS-2026-002-01',
    taskStatus: 'pending',
    createdAt: '2026-11-19 11:15',
    startedAt: '',
    completedAt: '',
    errorMessage: '',
    currentEpoch: 0,
    stage: '等待训练执行器领取',
    createdBy: '演示管理员',
    parameters: { epochs: 12, batchSize: 16, learningRate: 0.0008 },
  },
  {
    taskId: 'T-2026-011',
    taskName: '投资理财-短视频试验',
    taskDescription: '短视频风险配置的首轮试验任务，因参数配置调整被取消。',
    modelType: 'video_scam_risk',
    taskType: 'risk_multilabel',
    datasetId: 'DS-2026-003',
    versionId: 'VER-DS-2026-003-02',
    taskStatus: 'canceled',
    createdAt: '2026-11-08 10:20',
    startedAt: '2026-11-08 10:22',
    completedAt: '2026-11-08 10:41',
    errorMessage: '用户取消任务（模拟执行器已确认停止）',
    currentEpoch: 3,
    stage: '已取消（模拟执行器已确认停止）',
    createdBy: '陈晓雯',
    parameters: { epochs: 25, batchSize: 8, learningRate: 0.002 },
  },
  {
    taskId: 'T-2026-010',
    taskName: '文本反诈-基线对照训练',
    taskDescription: '使用早期版本做基线对照，仅用于流程演示。',
    modelType: 'text_risk_classifier',
    taskType: 'risk_binary',
    datasetId: 'DS-2026-004',
    versionId: 'VER-DS-2026-004-01',
    taskStatus: 'succeeded',
    createdAt: '2026-11-05 09:12',
    startedAt: '2026-11-05 09:15',
    completedAt: '2026-11-05 09:46',
    errorMessage: '',
    currentEpoch: 10,
    stage: '训练完成（模拟）',
    createdBy: '李静',
    parameters: { epochs: 10, batchSize: 32, learningRate: 0.001, optimizer: 'adam' },
  },
  {
    taskId: 'T-2026-009',
    taskName: '混合模态-超参搜索',
    taskDescription: '探索学习率与批次大小组合，训练进程在早期轮次异常退出。',
    modelType: 'video_scam_risk',
    taskType: 'risk_multilabel',
    datasetId: 'DS-2026-004',
    versionId: 'VER-DS-2026-004-02',
    taskStatus: 'failed',
    createdAt: '2026-11-18 15:40',
    startedAt: '2026-11-18 15:42',
    completedAt: '2026-11-18 15:47',
    errorMessage: '训练进程在第 3 轮因显存不足退出（演示错误信息）',
    currentEpoch: 0,
    stage: '训练失败',
    createdBy: '赵鹏',
    parameters: { epochs: 30, batchSize: 64, learningRate: 0.0003, optimizer: 'adamw' },
  },
  {
    taskId: 'T-2026-008',
    taskName: '语音话术-大学习率试验',
    taskDescription: '验证较大学习率对收敛速度的影响，已提交取消请求。',
    modelType: 'audio_call_risk',
    taskType: 'risk_binary',
    datasetId: 'DS-2026-002',
    versionId: 'VER-DS-2026-002-02',
    taskStatus: 'running',
    createdAt: '2026-11-19 09:40',
    startedAt: '2026-11-19 09:41',
    completedAt: '',
    errorMessage: '',
    currentEpoch: 4,
    stage: '训练中：第 4 / 18 轮',
    createdBy: '王海涛',
    parameters: { epochs: 18, batchSize: 16, learningRate: 0.01 },
    cancelRequestedAt: '2026-11-19 09:58',
  },
  {
    taskId: 'T-2026-007',
    taskName: '可疑链接页面-风险训练',
    taskDescription: '等待执行器领取的排队任务。',
    modelType: 'screen_multimodal_risk',
    taskType: 'risk_binary',
    datasetId: 'DS-2026-005',
    versionId: 'VER-DS-2026-005-02',
    taskStatus: 'pending',
    createdAt: '2026-11-19 10:35',
    startedAt: '',
    completedAt: '',
    errorMessage: '',
    currentEpoch: 0,
    stage: '等待训练执行器领取',
    createdBy: '孙彤',
    parameters: { epochs: 15, batchSize: 16, learningRate: 0.0006 },
  },
  {
    taskId: 'T-2026-006',
    taskName: '文本反诈分类-第二轮',
    taskDescription: '在基线基础上调整优化器后的第二轮训练。',
    modelType: 'text_risk_classifier',
    taskType: 'risk_binary',
    datasetId: 'DS-2026-004',
    versionId: 'VER-DS-2026-004-01',
    taskStatus: 'succeeded',
    createdAt: '2026-11-12 14:02',
    startedAt: '2026-11-12 14:05',
    completedAt: '2026-11-12 14:52',
    errorMessage: '',
    currentEpoch: 24,
    stage: '训练完成（模拟）',
    createdBy: '李静',
    parameters: { epochs: 24, batchSize: 32, learningRate: 0.0008, optimizer: 'adamw' },
  },
  {
    taskId: 'T-2026-005',
    taskName: '投资短视频-风险识别',
    taskDescription: '首次短视频风险训练尝试，因样本准备不足被取消。',
    modelType: 'video_scam_risk',
    taskType: 'risk_binary',
    datasetId: 'DS-2026-003',
    versionId: 'VER-DS-2026-003-01',
    taskStatus: 'canceled',
    createdAt: '2026-11-16 16:20',
    startedAt: '2026-11-16 16:22',
    completedAt: '2026-11-16 16:31',
    errorMessage: '用户取消任务（模拟执行器已确认停止）',
    currentEpoch: 2,
    stage: '已取消（模拟执行器已确认停止）',
    createdBy: '陈晓雯',
    parameters: { epochs: 20, batchSize: 8, learningRate: 0.0015 },
  },
  {
    taskId: 'T-2026-004',
    taskName: '混合模态复核-增量训练',
    taskDescription: '混合模态样本的首轮训练任务，当前排队等待。',
    modelType: 'screen_multimodal_risk',
    taskType: 'risk_multilabel',
    datasetId: 'DS-2026-006',
    versionId: 'VER-DS-2026-006-01',
    taskStatus: 'pending',
    createdAt: '2026-11-19 10:02',
    startedAt: '',
    completedAt: '',
    errorMessage: '',
    currentEpoch: 0,
    stage: '等待训练执行器领取',
    createdBy: '赵鹏',
    parameters: { epochs: 20, batchSize: 8, learningRate: 0.0005 },
  },
  {
    taskId: 'T-2026-003',
    taskName: '语音冒充客服-模型训练',
    taskDescription: '语音话术风险模型首轮训练，训练进程异常退出。',
    modelType: 'audio_call_risk',
    taskType: 'risk_binary',
    datasetId: 'DS-2026-002',
    versionId: 'VER-DS-2026-002-01',
    taskStatus: 'failed',
    createdAt: '2026-11-17 14:10',
    startedAt: '2026-11-17 14:11',
    completedAt: '2026-11-17 14:19',
    errorMessage: '训练进程异常退出：DataLoader 读取音频片段失败（演示错误信息）',
    currentEpoch: 2,
    stage: '训练失败',
    createdBy: '王海涛',
    parameters: { epochs: 16, batchSize: 8, learningRate: 0.001 },
  },
  {
    taskId: 'T-2026-002',
    taskName: '屏幕多模态-对话风险训练',
    taskDescription: '屏幕对话样本的持续训练任务，用于展示运行中状态。',
    modelType: 'screen_multimodal_risk',
    taskType: 'risk_multilabel',
    datasetId: 'DS-2026-001',
    versionId: 'VER-DS-2026-001-02',
    taskStatus: 'running',
    createdAt: '2026-11-19 09:05',
    startedAt: '2026-11-19 09:06',
    completedAt: '',
    errorMessage: '',
    currentEpoch: 7,
    stage: '训练中：第 7 / 30 轮',
    createdBy: '张明远',
    parameters: { epochs: 30, batchSize: 16, learningRate: 0.0005, optimizer: 'adam', device: 'cuda' },
  },
  {
    taskId: 'T-2026-001',
    taskName: '文本反诈分类-基线训练',
    taskDescription: '文本反诈分类的基线训练任务，用于验证训练管理流程。',
    modelType: 'text_risk_classifier',
    taskType: 'risk_binary',
    datasetId: 'DS-2026-004',
    versionId: 'VER-DS-2026-004-02',
    taskStatus: 'succeeded',
    createdAt: '2026-11-10 09:20',
    startedAt: '2026-11-10 09:22',
    completedAt: '2026-11-10 09:58',
    errorMessage: '',
    currentEpoch: 20,
    stage: '训练完成（模拟）',
    createdBy: '李静',
    parameters: { epochs: 20, batchSize: 32, learningRate: 0.001, optimizer: 'adamw' },
  },
]

/* ===== 任务构建 ===== */

function findVersion(versionId: string): DatasetVersion | undefined {
  return datasetVersions.find((item) => item.versionId === versionId)
}

function buildTask(seed: TaskSeed): TrainingTask {
  const dataset = datasetStore.find((item) => item.datasetId === seed.datasetId)
  const version = findVersion(seed.versionId)
  const modelOption = MODEL_OPTIONS.find((item) => item.value === seed.modelType) ?? MODEL_OPTIONS[0]
  const parameters: TrainingParameters = {
    ...DEFAULT_TRAINING_PARAMETERS,
    ...seed.parameters,
    epochs: seed.parameters?.epochs ?? seed.currentEpoch ?? DEFAULT_TRAINING_PARAMETERS.epochs,
  }

  return {
    taskId: seed.taskId,
    taskName: seed.taskName,
    taskDescription: seed.taskDescription,
    modelType: seed.modelType,
    modelConfig: {
      taskType: seed.taskType,
      framework: 'pytorch',
      modelFamily: modelOption.label,
      inputModalities: modelOption.inputModalities,
      implementationStatus: modelOption.implementationStatus,
      note: modelOption.implementationNote,
    },
    datasetId: seed.datasetId,
    datasetName: dataset?.datasetName ?? '未知数据集',
    datasetVersionId: seed.versionId,
    datasetVersion: version?.versionNumber ?? '未知版本',
    datasetSplit: {
      trainCount: version?.trainCount ?? 0,
      validationCount: version?.validationCount ?? 0,
      testCount: version?.testCount ?? 0,
    },
    trainingParameters: parameters,
    taskStatus: seed.taskStatus,
    executionMode: 'mock',
    createdAt: seed.createdAt,
    startedAt: seed.startedAt,
    completedAt: seed.completedAt,
    createdBy: seed.createdBy,
    errorMessage: seed.errorMessage,
    progress: {
      currentEpoch: seed.currentEpoch,
      totalEpochs: parameters.epochs,
      stage: seed.stage,
      updatedAt: seed.completedAt || seed.startedAt || seed.createdAt,
    },
    cancelRequestedAt: seed.cancelRequestedAt,
    retryOfTaskId: seed.retryOfTaskId,
    attempt: seed.attempt ?? 1,
  }
}

export const trainingTasks: TrainingTask[] = TASK_SEEDS.map(buildTask)

/* ===== 演示指标 ===== */

function buildDemoMetrics(task: TrainingTask): TrainingMetric[] {
  const total = task.trainingParameters.epochs
  const rng = createRng(Number(task.taskId.replace(/\D/g, '')) + task.trainingParameters.randomSeed)
  const metrics: TrainingMetric[] = []

  for (let epoch = 1; epoch <= total; epoch += 1) {
    const progress = epoch / total
    const noise = rng()
    const trainLoss = round3(Math.max(0.08, 0.95 - 0.72 * progress + noise * 0.03))
    const validationLoss = round3(Math.max(0.12, 1.01 - 0.68 * progress + (1 - noise) * 0.04))
    const trainAccuracy = round3(clamp01(0.6 + 0.32 * progress - noise * 0.015))
    const validationAccuracy = round3(clamp01(0.56 + 0.28 * progress - (1 - noise) * 0.02))
    const validationPrecision = round3(clamp01(0.54 + 0.27 * progress - noise * 0.03))
    const validationRecall = round3(clamp01(0.52 + 0.26 * progress - (1 - noise) * 0.03))
    const validationF1 = round3(
      validationPrecision + validationRecall === 0
        ? 0
        : (2 * validationPrecision * validationRecall) / (validationPrecision + validationRecall),
    )

    metrics.push({
      epoch,
      trainLoss,
      validationLoss,
      trainAccuracy,
      validationAccuracy,
      validationPrecision,
      validationRecall,
      validationF1,
      recordedAt: addMinutes(task.startedAt || task.createdAt, epoch * 2),
    })
  }

  return metrics
}

function buildDemoResult(task: TrainingTask, metrics: TrainingMetric[]): TrainingResult {
  const finalMetrics = metrics[metrics.length - 1]
  const best = metrics.reduce((prev, current) =>
    current.validationLoss < prev.validationLoss ? current : prev,
  )

  return {
    resultId: `RES-${task.taskId}`,
    taskId: task.taskId,
    finalMetrics,
    bestCheckpoint: {
      epoch: best.epoch,
      checkpointPath: `ml/training/artifacts/mock/${task.taskId}/checkpoint-epoch-${best.epoch}.pt`,
      validationLoss: best.validationLoss,
    },
    modelArtifactPath: `ml/training/artifacts/mock/${task.taskId}/model.pt`,
    datasetVersion: task.datasetVersion,
    completedAt: task.completedAt,
    evaluationStatus: 'not_evaluated',
    evaluationSummary: '未执行独立测试集评估：Mock 演示阶段不运行真实推理，不能据此判断模型效果。',
    simulated: true,
  }
}

/* ===== 演示日志 ===== */

function buildDemoLogs(task: TrainingTask, metrics: TrainingMetric[]): TrainingLog[] {
  const logs: TrainingLog[] = []
  let sequence = 1
  const push = (
    timestamp: string,
    level: TrainingLog['level'],
    message: string,
    source: TrainingLog['source'] = 'mock_demo',
  ) => {
    logs.push({
      logId: `${task.taskId}-LOG-${pad(sequence, 4)}`,
      taskId: task.taskId,
      timestamp,
      level,
      message,
      source,
    })
    sequence += 1
  }

  push(task.createdAt, 'INFO', `任务已创建：${task.taskName}（执行模式：模拟任务）`, 'api')
  push(
    task.createdAt,
    'WARNING',
    '当前未连接真实 Python 训练执行器，任务不会执行真实 PyTorch 训练。',
  )

  if (task.taskStatus === 'pending') {
    push(task.createdAt, 'INFO', '任务已进入演示队列，等待（不存在的）训练执行器领取。')
    return logs
  }

  push(
    task.startedAt,
    'INFO',
    `模拟执行器启动：model=${task.modelType} dataset=${task.datasetId}@${task.datasetVersion}`,
  )
  push(
    task.startedAt,
    'DEBUG',
    `训练参数：epochs=${task.trainingParameters.epochs} batch_size=${task.trainingParameters.batchSize} lr=${task.trainingParameters.learningRate} optimizer=${task.trainingParameters.optimizer} device=${task.trainingParameters.device} seed=${task.trainingParameters.randomSeed}`,
  )

  const visibleMetrics = metrics.slice(0, Math.max(task.progress.currentEpoch, 0))
  for (const metric of visibleMetrics) {
    push(
      metric.recordedAt,
      'INFO',
      `Epoch ${metric.epoch}/${task.trainingParameters.epochs} 完成：train_loss=${metric.trainLoss} validation_loss=${metric.validationLoss} validation_f1=${metric.validationF1}`,
    )
    push(
      metric.recordedAt,
      'DEBUG',
      `Epoch ${metric.epoch} 学习率=${task.trainingParameters.learningRate} checkpoint_interval=${task.trainingParameters.checkpointInterval}`,
    )
    if (task.trainingParameters.epochs >= 10 && metric.epoch === Math.ceil(task.trainingParameters.epochs * 0.4)) {
      push(metric.recordedAt, 'WARNING', '验证集指标提升缓慢，建议关注类别不平衡与召回率表现（演示告警）。')
    }
  }

  switch (task.taskStatus) {
    case 'running':
      if (task.cancelRequestedAt) {
        push(task.cancelRequestedAt, 'WARNING', '已发送取消请求，等待模拟执行器确认停止。')
      }
      push(task.progress.updatedAt, 'INFO', `模拟训练进行中：第 ${task.progress.currentEpoch} 轮。`)
      break
    case 'succeeded':
      push(task.completedAt, 'INFO', `模拟训练结束：共 ${task.progress.totalEpochs} 轮，已生成演示指标与检查点。`)
      push(task.completedAt, 'WARNING', '以上指标为 Mock 演示数据，未执行真实 PyTorch 训练，不能作为模型效果结论。')
      break
    case 'failed':
      push(task.completedAt, 'ERROR', task.errorMessage || '训练失败（演示错误信息）。')
      push(task.completedAt, 'INFO', '失败现场已保留：原始任务与日志不会被重试覆盖。')
      break
    case 'canceled':
      push(task.completedAt, 'WARNING', '模拟执行器已确认停止，训练资源已释放。')
      push(task.completedAt, 'INFO', '任务标记为已取消，历史日志保留。')
      break
    default:
      break
  }

  return logs
}

export const trainingMetrics = new Map<string, TrainingMetric[]>()
export const trainingResults = new Map<string, TrainingResult>()
export const trainingLogs = new Map<string, TrainingLog[]>()

for (const task of trainingTasks) {
  const metrics = buildDemoMetrics(task)
  trainingMetrics.set(task.taskId, metrics)
  if (task.taskStatus === 'succeeded') {
    trainingResults.set(task.taskId, buildDemoResult(task, metrics))
  }
  trainingLogs.set(task.taskId, buildDemoLogs(task, metrics))
}

/** 上一次 Mock 创建的日志序号，保证同一会话内 logId 不重复 */
let dynamicLogSequence = 1

export function appendTrainingLog(
  taskId: string,
  level: TrainingLog['level'],
  message: string,
  source: TrainingLog['source'] = 'api',
  timestamp = formatTrainingTime(new Date()),
): void {
  const current = trainingLogs.get(taskId) ?? []
  current.push({
    logId: `${taskId}-LOG-${pad(dynamicLogSequence, 4)}`,
    taskId,
    timestamp,
    level,
    message,
    source,
  })
  dynamicLogSequence += 1
  trainingLogs.set(taskId, current)
}
