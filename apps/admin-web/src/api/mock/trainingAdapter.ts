/**
 * A05 训练管理 Mock 适配器（P11）。
 *
 * 与真实后端的边界：
 * - 这里只模拟 FastAPI 的业务行为（创建、查询、取消、重试、资格校验）；
 * - 不模拟真实 PyTorch 训练过程，不使用计时器伪造训练进度；
 * - 所有任务 executionMode 固定为 mock，页面必须展示「模拟任务」标识。
 */

import { ApiError } from '../http'
import type { PageResult } from '../types'
import type { DatasetSample } from '@/types/dataset'
import {
  validateTrainingParameters,
  type CreateTrainingTaskInput,
  type ExecutionMode,
  type TrainingDatasetCheck,
  type TrainingDatasetCheckItem,
  type TrainingDatasetOption,
  type TrainingLogPage,
  type TrainingLogQuery,
  type TrainingMetricSeries,
  type TrainingOptions,
  type TrainingOverviewStats,
  type TrainingResult,
  type TrainingTask,
  type TrainingTaskQuery,
} from '@/types/training'
import { datasetSamples, datasetStore, datasetVersions, qualityReports } from './datasetData'
import { getDedupeOverview, MIN_SPLIT_SAMPLES } from './datasetAdapter'
import {
  appendTrainingLog,
  DEVICE_OPTIONS,
  EXECUTION_MODE_OPTIONS,
  MODEL_OPTIONS,
  OPTIMIZER_OPTIONS,
  STATUS_OPTIONS,
  TASK_TYPE_OPTIONS,
  TRAINING_DEMO_NOTICE,
  UNCONNECTED_WORKER_NOTICE,
  formatTrainingTime,
  trainingLogs,
  trainingMetrics,
  trainingResults,
  trainingTasks,
} from './trainingData'

/** Mock 环境固定为模拟执行模式 */
const EXECUTION_MODE: ExecutionMode = 'mock'

const CREATOR = '演示管理员'

function nowStamp(): string {
  return formatTrainingTime(new Date())
}

function findTask(taskId: string): TrainingTask {
  const task = trainingTasks.find((item) => item.taskId === taskId)
  if (!task) {
    throw new ApiError(404, 'TRAINING_TASK_NOT_FOUND', '未找到该训练任务，演示数据可能已被重置')
  }
  return task
}

function samplesOf(datasetId: string): DatasetSample[] {
  return datasetSamples.filter((item) => item.datasetId === datasetId)
}

function nextTaskId(): string {
  const year = new Date().getFullYear()
  const max = trainingTasks.reduce((acc, item) => {
    const matched = /^T-(\d{4})-(\d{3,})$/.exec(item.taskId)
    if (!matched || Number(matched[1]) !== year) return acc
    return Math.max(acc, Number(matched[2]))
  }, 0)
  return `T-${year}-${String(max + 1).padStart(3, '0')}`
}

/* ===== 概览与列表 ===== */

export function getTrainingStats(): TrainingOverviewStats {
  const stats: TrainingOverviewStats = {
    total: trainingTasks.length,
    pending: 0,
    running: 0,
    succeeded: 0,
    failed: 0,
    canceled: 0,
  }
  for (const task of trainingTasks) {
    stats[task.taskStatus] += 1
  }
  return stats
}

export function listTrainingTasks(query: TrainingTaskQuery): PageResult<TrainingTask> {
  const keyword = query.keyword.trim().toLowerCase()
  const filtered = trainingTasks
    .filter((task) => {
      if (keyword && !`${task.taskId} ${task.taskName}`.toLowerCase().includes(keyword)) return false
      if (query.modelType && task.modelType !== query.modelType) return false
      if (query.datasetId && task.datasetId !== query.datasetId) return false
      if (query.taskStatus && task.taskStatus !== query.taskStatus) return false
      if (query.executionMode && task.executionMode !== query.executionMode) return false
      const date = task.createdAt.slice(0, 10)
      if (query.createdFrom && date < query.createdFrom) return false
      if (query.createdTo && date > query.createdTo) return false
      return true
    })
    .sort((a, b) => (a.createdAt < b.createdAt ? 1 : a.createdAt > b.createdAt ? -1 : 0))

  const start = (query.page - 1) * query.pageSize
  return {
    items: filtered.slice(start, start + query.pageSize),
    page: query.page,
    pageSize: query.pageSize,
    total: filtered.length,
  }
}

export function getTrainingTask(taskId: string): TrainingTask {
  return findTask(taskId)
}

export function getTrainingOptions(): TrainingOptions {
  const datasets: TrainingDatasetOption[] = datasetStore.map((dataset) => ({
    datasetId: dataset.datasetId,
    datasetName: dataset.datasetName,
    datasetType: dataset.datasetType,
    datasetStatus: dataset.datasetStatus,
    versions: datasetVersions.filter((item) => item.datasetId === dataset.datasetId),
  }))

  return {
    modelTypes: MODEL_OPTIONS,
    optimizers: OPTIMIZER_OPTIONS,
    devices: DEVICE_OPTIONS,
    statuses: STATUS_OPTIONS,
    executionModes: EXECUTION_MODE_OPTIONS,
    taskTypes: TASK_TYPE_OPTIONS,
    datasets,
  }
}

/* ===== 数据集资格校验 ===== */

function percentage(value: number): string {
  return `${Math.round(value * 1000) / 10}%`
}

/**
 * 计算数据集版本的训练资格。
 *
 * 这是 Mock 环境下的近似校验：
 * - 版本状态、人工复核状态、拆分数量来自 A04 版本记录；
 * - 来源/许可/质检/去重按数据集聚合计算，真实后端必须按版本快照重新校验。
 */
export function checkTrainingDatasetVersion(datasetId: string, versionId: string): TrainingDatasetCheck {
  const dataset = datasetStore.find((item) => item.datasetId === datasetId)
  if (!dataset) {
    throw new ApiError(404, 'DATASET_NOT_FOUND', '未找到该数据集，演示数据可能已被重置')
  }
  const version = datasetVersions.find((item) => item.versionId === versionId)
  if (!version || version.datasetId !== datasetId) {
    throw new ApiError(404, 'DATASET_VERSION_NOT_FOUND', '未找到该数据集版本')
  }

  const samples = samplesOf(datasetId)
  const total = samples.length
  const sourceClear = samples.filter((item) => item.sourceName.trim().length > 0).length
  const licenseAllowed = samples.filter(
    (item) => item.licenseStatus === 'verified' && item.sourceLicense.allowTraining,
  ).length
  const sourceRatio = total === 0 ? 0 : sourceClear / total
  const licenseRatio = total === 0 ? 0 : licenseAllowed / total

  const report = qualityReports.get(datasetId) ?? null
  const qualityThreshold = Math.max(3, Math.ceil(version.sampleCount * 0.3))
  const dedupe = getDedupeOverview(datasetId)
  const pendingGroups = dedupe.groups.filter((item) => item.status === 'pending').length

  const checks: TrainingDatasetCheckItem[] = [
    {
      key: 'source',
      label: '数据集来源明确',
      passed: total > 0 && sourceRatio >= 0.9,
      detail:
        total === 0
          ? '数据集尚未导入样本，无法确认来源'
          : `${sourceClear}/${total} 条样本登记了来源（${percentage(sourceRatio)}，要求 ≥ 90%）`,
      blocking: true,
    },
    {
      key: 'license',
      label: '使用许可允许模型训练',
      passed: total > 0 && licenseRatio >= 0.75,
      detail:
        total === 0
          ? '数据集没有可用的许可登记，无法确认是否允许模型训练'
          : `${licenseAllowed}/${total} 条样本许可已核验且允许训练（${percentage(licenseRatio)}，Mock 近似要求 ≥ 75%）`,
      blocking: true,
    },
    {
      key: 'quality',
      label: '样本通过必要质检',
      passed: report !== null && report.passedCount >= qualityThreshold,
      detail:
        report === null
          ? '尚未生成质检报告，请在 A04 数据集管理中执行质检'
          : report.passedCount >= qualityThreshold
            ? `质检通过 ${report.passedCount} 条（该版本要求 ≥ ${qualityThreshold} 条）`
            : `质检通过 ${report.passedCount} 条，低于版本样本所需的 ${qualityThreshold} 条`,
      blocking: true,
    },
    {
      key: 'duplicate',
      label: '没有未处理的重复数据',
      passed: pendingGroups === 0,
      detail:
        pendingGroups === 0
          ? '没有待处理的疑似重复分组'
          : `存在 ${pendingGroups} 组待处理的疑似重复样本，请在 A04 去重页面完成确认`,
      blocking: true,
    },
    {
      key: 'version_status',
      label: '版本状态满足训练要求',
      passed: version.versionStatus === 'published',
      detail:
        version.versionStatus === 'published'
          ? '版本已发布，可用于训练'
          : `版本状态为 ${version.versionStatus}，仅已发布版本可用于训练`,
      blocking: true,
    },
    {
      key: 'manual_review',
      label: '同源泄漏人工复核完成',
      passed: version.manualReviewStatus !== 'pending',
      detail:
        version.manualReviewStatus === 'pending'
          ? '版本存在同源拆分泄漏风险，人工复核尚未完成'
          : version.manualReviewStatus === 'approved'
            ? `人工复核已通过${version.manualReviewedBy ? `（${version.manualReviewedBy}）` : ''}`
            : '该版本未触发同源拆分复核要求',
      blocking: true,
    },
    {
      key: 'split',
      label: '训练/验证/测试拆分有效',
      passed:
        version.sampleCount >= MIN_SPLIT_SAMPLES &&
        version.trainCount > 0 &&
        version.validationCount > 0 &&
        version.testCount > 0,
      detail:
        version.trainCount > 0 && version.validationCount > 0 && version.testCount > 0
          ? `训练集 ${version.trainCount} / 验证集 ${version.validationCount} / 测试集 ${version.testCount}`
          : '拆分结果存在空集合，无法用于训练',
      blocking: true,
    },
  ]

  const blockedReasons = checks.filter((item) => item.blocking && !item.passed).map((item) => item.detail)

  return {
    datasetId,
    datasetName: dataset.datasetName,
    datasetType: dataset.datasetType,
    versionId,
    versionNumber: version.versionNumber,
    versionStatus: version.versionStatus,
    manualReviewStatus: version.manualReviewStatus,
    sampleCount: version.sampleCount,
    trainCount: version.trainCount,
    validationCount: version.validationCount,
    testCount: version.testCount,
    eligible: blockedReasons.length === 0,
    checks,
    blockedReasons,
    checkedAt: nowStamp(),
    note: 'Mock 资格校验：基于 A04 演示数据实时计算；真实训练前必须由后端按版本快照再次校验。',
  }
}

export function listTrainingDatasetChecks(datasetId: string): TrainingDatasetCheck[] {
  const versions = datasetVersions.filter((item) => item.datasetId === datasetId)
  return versions.map((version) => checkTrainingDatasetVersion(datasetId, version.versionId))
}

function assertVersionTrainable(datasetId: string, versionId: string): TrainingDatasetCheck {
  const check = checkTrainingDatasetVersion(datasetId, versionId)
  if (!check.eligible) {
    throw new ApiError(
      422,
      'DATASET_NOT_TRAINABLE',
      `数据集版本不满足训练条件：${check.blockedReasons.join('；')}`,
    )
  }
  return check
}

/* ===== 创建 / 重试 / 取消 ===== */

function buildTaskFromInput(
  input: CreateTrainingTaskInput,
  check: TrainingDatasetCheck,
  context: { taskId: string; attempt: number; retryOfTaskId?: string },
): TrainingTask {
  const model = MODEL_OPTIONS.find((item) => item.value === input.modelType)
  if (!model) {
    throw new ApiError(400, 'MODEL_TYPE_INVALID', '请选择受支持的模型类型')
  }

  const createdAt = nowStamp()
  const task: TrainingTask = {
    taskId: context.taskId,
    taskName: input.taskName.trim(),
    taskDescription: input.taskDescription.trim(),
    modelType: input.modelType,
    modelConfig: {
      taskType: input.taskType,
      framework: 'pytorch',
      modelFamily: model.label,
      inputModalities: model.inputModalities,
      implementationStatus: model.implementationStatus,
      note: model.implementationNote,
    },
    datasetId: input.datasetId,
    datasetName: check.datasetName,
    datasetVersionId: input.datasetVersionId,
    datasetVersion: check.versionNumber,
    datasetSplit: {
      trainCount: check.trainCount,
      validationCount: check.validationCount,
      testCount: check.testCount,
    },
    trainingParameters: { ...input.trainingParameters },
    taskStatus: 'pending',
    executionMode: EXECUTION_MODE,
    createdAt,
    startedAt: '',
    completedAt: '',
    createdBy: CREATOR,
    errorMessage: '',
    progress: {
      currentEpoch: 0,
      totalEpochs: input.trainingParameters.epochs,
      stage: '等待训练执行器领取',
      updatedAt: createdAt,
    },
    retryOfTaskId: context.retryOfTaskId,
    attempt: context.attempt,
  }

  trainingTasks.unshift(task)
  trainingLogs.set(task.taskId, [])
  appendTrainingLog(
    task.taskId,
    'INFO',
    `任务已创建：${task.taskName}（数据集 ${task.datasetId}@${task.datasetVersion}）`,
    'api',
    createdAt,
  )
  appendTrainingLog(task.taskId, 'WARNING', TRAINING_DEMO_NOTICE, 'api', createdAt)
  return task
}

export function createTrainingTask(input: CreateTrainingTaskInput): TrainingTask {
  const name = input.taskName.trim()
  if (!name) {
    throw new ApiError(400, 'TASK_NAME_REQUIRED', '请填写任务名称')
  }
  if (name.length > 50) {
    throw new ApiError(400, 'TASK_NAME_TOO_LONG', '任务名称不能超过 50 个字符')
  }
  if (input.taskDescription.trim().length > 200) {
    throw new ApiError(400, 'TASK_DESCRIPTION_TOO_LONG', '任务描述不能超过 200 个字符')
  }

  const model = MODEL_OPTIONS.find((item) => item.value === input.modelType)
  if (!model) {
    throw new ApiError(400, 'MODEL_TYPE_INVALID', '请选择受支持的模型类型')
  }
  if (!model.taskTypes.includes(input.taskType)) {
    throw new ApiError(400, 'TASK_TYPE_INVALID', '当前模型类型不支持所选训练任务类型')
  }

  const parameterErrors = validateTrainingParameters(input.trainingParameters)
  const firstError = Object.values(parameterErrors)[0]
  if (firstError) {
    throw new ApiError(400, 'PARAMETER_INVALID', firstError)
  }

  const check = assertVersionTrainable(input.datasetId, input.datasetVersionId)

  return buildTaskFromInput(input, check, { taskId: nextTaskId(), attempt: 1 })
}

export function retryTrainingTask(taskId: string): TrainingTask {
  const original = findTask(taskId)
  if (original.taskStatus !== 'failed') {
    throw new ApiError(409, 'TASK_STATE_INVALID', '只有训练失败的任务允许重试')
  }

  // 重试前重新进行数据集资格校验，防止数据集状态在失败后发生变化
  const check = assertVersionTrainable(original.datasetId, original.datasetVersionId)

  const retried = buildTaskFromInput(
    {
      taskName: `${original.taskName} · 重试 ${original.attempt + 1}`,
      taskDescription: original.taskDescription,
      modelType: original.modelType,
      taskType: original.modelConfig.taskType,
      datasetId: original.datasetId,
      datasetVersionId: original.datasetVersionId,
      trainingParameters: { ...original.trainingParameters },
    },
    check,
    {
      taskId: nextTaskId(),
      attempt: original.attempt + 1,
      retryOfTaskId: original.taskId,
    },
  )

  original.retriedByTaskId = retried.taskId
  appendTrainingLog(
    original.taskId,
    'INFO',
    `已创建重试任务 ${retried.taskId}，原始失败记录与日志保留不变。`,
    'api',
  )
  return retried
}

export function cancelTrainingTask(taskId: string): TrainingTask {
  const task = findTask(taskId)

  if (task.taskStatus === 'canceled') {
    return task
  }

  if (task.taskStatus === 'pending') {
    task.taskStatus = 'canceled'
    task.completedAt = nowStamp()
    task.progress.stage = '已取消（任务未开始执行）'
    task.progress.updatedAt = task.completedAt
    appendTrainingLog(task.taskId, 'WARNING', '等待执行的任务已取消，未产生训练过程。', 'api')
    appendTrainingLog(task.taskId, 'INFO', '模拟执行器已确认任务停止（Mock，无真实训练进程）。', 'mock_demo')
    return task
  }

  if (task.taskStatus === 'running') {
    if (task.cancelRequestedAt) {
      return task
    }
    task.cancelRequestedAt = nowStamp()
    task.progress.stage = '正在取消：已发送取消请求，等待执行器确认'
    task.progress.updatedAt = task.cancelRequestedAt
    appendTrainingLog(
      task.taskId,
      'WARNING',
      '已向模拟执行器发送取消请求，等待执行器确认停止后才会标记为已取消。',
      'api',
    )
    return task
  }

  throw new ApiError(409, 'TASK_STATE_INVALID', '当前任务状态不允许取消')
}

/** 仅 Mock 模式：模拟执行器确认停止后，任务才进入 CANCELED */
export function confirmMockExecutorStop(taskId: string): TrainingTask {
  const task = findTask(taskId)
  if (task.executionMode !== 'mock') {
    throw new ApiError(409, 'NOT_MOCK_TASK', '该任务不是模拟任务，取消状态必须由真实执行器上报')
  }
  if (task.taskStatus !== 'running' || !task.cancelRequestedAt) {
    throw new ApiError(409, 'TASK_STATE_INVALID', '当前任务没有待确认的取消请求')
  }

  task.taskStatus = 'canceled'
  task.completedAt = nowStamp()
  task.progress.stage = '已取消（模拟执行器已确认停止）'
  task.progress.updatedAt = task.completedAt
  appendTrainingLog(
    task.taskId,
    'INFO',
    '模拟执行器确认停止，资源已释放；该过程为 Mock 演示，未涉及真实训练进程。',
    'mock_demo',
  )
  return task
}

/* ===== 日志 / 指标 / 结果 ===== */

export function queryTrainingLogs(taskId: string, query: TrainingLogQuery): TrainingLogPage {
  const task = findTask(taskId)
  const keyword = query.keyword.trim().toLowerCase()
  const source = trainingLogs.get(taskId) ?? []

  const filtered = source
    .filter((item) => {
      if (query.level && item.level !== query.level) return false
      if (keyword && !item.message.toLowerCase().includes(keyword)) return false
      return true
    })
    .sort((a, b) => {
      if (a.timestamp === b.timestamp) return a.logId < b.logId ? -1 : 1
      return a.timestamp < b.timestamp ? -1 : 1
    })

  const start = (query.page - 1) * query.pageSize
  return {
    items: filtered.slice(start, start + query.pageSize),
    page: query.page,
    pageSize: query.pageSize,
    total: filtered.length,
    executionMode: task.executionMode,
    notice:
      task.executionMode === 'mock'
        ? UNCONNECTED_WORKER_NOTICE
        : '日志来自真实训练执行器。',
  }
}

export function getTrainingMetrics(taskId: string): TrainingMetricSeries {
  const task = findTask(taskId)
  const metrics = trainingMetrics.get(taskId) ?? []
  return {
    taskId,
    source: task.executionMode === 'worker' ? 'worker' : 'mock',
    metrics,
    notice:
      task.executionMode === 'mock'
        ? '演示指标：由 Mock 生成，未执行真实 PyTorch 训练，不能作为模型能力结论。'
        : '指标来自真实训练执行器上报。',
  }
}

export function getTrainingResult(taskId: string): TrainingResult | null {
  findTask(taskId)
  return trainingResults.get(taskId) ?? null
}

