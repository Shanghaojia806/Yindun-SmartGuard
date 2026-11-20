import type { PageResult } from './types'
import { http } from './http'
import { IS_MOCK, mockCall } from './request'
import type {
  CreateTrainingTaskInput,
  TrainingDatasetCheck,
  TrainingLogPage,
  TrainingLogQuery,
  TrainingMetricSeries,
  TrainingOptions,
  TrainingOverviewStats,
  TrainingResult,
  TrainingTask,
  TrainingTaskQuery,
} from '@/types/training'
import {
  cancelTrainingTask as cancelTrainingTaskMock,
  checkTrainingDatasetVersion,
  confirmMockExecutorStop,
  createTrainingTask as createTrainingTaskMock,
  getTrainingMetrics,
  getTrainingOptions,
  getTrainingResult,
  getTrainingStats,
  getTrainingTask,
  listTrainingDatasetChecks,
  listTrainingTasks,
  queryTrainingLogs,
  retryTrainingTask as retryTrainingTaskMock,
} from './mock/trainingAdapter'

/**
 * A05 训练管理 Service。
 *
 * 架构边界（见 docs/requirements/IMPLEMENTATION_PLAN.md P11）：
 * 浏览器不执行模型训练。本文件只对接 FastAPI 业务接口，
 * 训练执行由独立 Python Worker 完成；Mock 模式下所有任务均为模拟任务。
 *
 * 真实接口路径按 docs/api/P11_TRAINING_CONTRACT.md 预留。
 */

export async function fetchTrainingStats(): Promise<TrainingOverviewStats> {
  if (IS_MOCK) return mockCall(() => getTrainingStats())
  return http.get<TrainingOverviewStats>('/admin/training-tasks/stats')
}

export async function fetchTrainingOptions(): Promise<TrainingOptions> {
  if (IS_MOCK) return mockCall(() => getTrainingOptions())
  return http.get<TrainingOptions>('/admin/training-tasks/options')
}

export async function fetchTrainingTasks(
  query: TrainingTaskQuery,
): Promise<PageResult<TrainingTask>> {
  if (IS_MOCK) return mockCall(() => listTrainingTasks(query))
  return http.get<PageResult<TrainingTask>>('/admin/training-tasks', {
    query: {
      keyword: query.keyword || undefined,
      modelType: query.modelType || undefined,
      datasetId: query.datasetId || undefined,
      taskStatus: query.taskStatus || undefined,
      executionMode: query.executionMode || undefined,
      createdFrom: query.createdFrom || undefined,
      createdTo: query.createdTo || undefined,
      page: query.page,
      pageSize: query.pageSize,
    },
  })
}

export async function fetchTrainingTask(taskId: string): Promise<TrainingTask> {
  if (IS_MOCK) return mockCall(() => getTrainingTask(taskId))
  return http.get<TrainingTask>(`/admin/training-tasks/${taskId}`)
}

export async function fetchTrainingDatasetChecks(
  datasetId: string,
): Promise<TrainingDatasetCheck[]> {
  if (IS_MOCK) return mockCall(() => listTrainingDatasetChecks(datasetId))
  return http.get<TrainingDatasetCheck[]>(`/admin/datasets/${datasetId}/training-checks`)
}

export async function fetchTrainingDatasetCheck(
  datasetId: string,
  versionId: string,
): Promise<TrainingDatasetCheck> {
  if (IS_MOCK) return mockCall(() => checkTrainingDatasetVersion(datasetId, versionId))
  return http.get<TrainingDatasetCheck>(
    `/admin/dataset-versions/${versionId}/training-check`,
    { query: { datasetId } },
  )
}

export async function createTrainingTask(
  input: CreateTrainingTaskInput,
): Promise<TrainingTask> {
  if (IS_MOCK) return mockCall(() => createTrainingTaskMock(input))
  return http.post<TrainingTask>('/admin/training-tasks', input)
}

export async function fetchTrainingLogs(
  taskId: string,
  query: TrainingLogQuery,
): Promise<TrainingLogPage> {
  if (IS_MOCK) return mockCall(() => queryTrainingLogs(taskId, query))
  return http.get<TrainingLogPage>(`/admin/training-tasks/${taskId}/logs`, {
    query: {
      level: query.level || undefined,
      keyword: query.keyword || undefined,
      page: query.page,
      pageSize: query.pageSize,
    },
  })
}

export async function fetchTrainingMetrics(taskId: string): Promise<TrainingMetricSeries> {
  if (IS_MOCK) return mockCall(() => getTrainingMetrics(taskId))
  return http.get<TrainingMetricSeries>(`/admin/training-tasks/${taskId}/metrics`)
}

export async function fetchTrainingResult(taskId: string): Promise<TrainingResult | null> {
  if (IS_MOCK) return mockCall(() => getTrainingResult(taskId))
  return http.get<TrainingResult | null>(`/admin/training-tasks/${taskId}/result`)
}

/** 失败重试：保留原任务与失败日志，由后端创建新的执行记录 */
export async function retryTrainingTask(taskId: string): Promise<TrainingTask> {
  if (IS_MOCK) return mockCall(() => retryTrainingTaskMock(taskId))
  return http.post<TrainingTask>(`/admin/training-tasks/${taskId}/retry`)
}

/** 提交取消请求；RUNNING 任务必须等待执行器确认后才会变为 CANCELED */
export async function cancelTrainingTask(taskId: string): Promise<TrainingTask> {
  if (IS_MOCK) return mockCall(() => cancelTrainingTaskMock(taskId))
  return http.post<TrainingTask>(`/admin/training-tasks/${taskId}/cancel`)
}

/**
 * 仅在 Mock 模式下可用：模拟执行器确认停止。
 * 真实 Worker 场景下取消状态必须由 Worker 上报，前端不允许自行确认停止。
 */
export async function confirmSimulatedExecutorStop(taskId: string): Promise<TrainingTask> {
  if (IS_MOCK) return mockCall(() => confirmMockExecutorStop(taskId))
  throw new Error('真实训练任务不允许前端确认停止，请等待训练执行器上报取消结果')
}
