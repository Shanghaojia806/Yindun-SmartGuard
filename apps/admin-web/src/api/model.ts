import { http, ApiError } from './http'
import {
  approveModelReview as approveModelReviewMock,
  compareModelVersions,
  evaluateModelVersion as evaluateModelVersionMock,
  findArtifact,
  findModelVersion,
  getModelDetail,
  getModelOptions,
  getModelStats,
  listArtifacts,
  listModelAudits,
  listModelVersions,
  listModels,
  listReleaseRecords,
  publishModelVersion as publishModelVersionMock,
  rejectModelReview as rejectModelReviewMock,
  rollbackModelVersion as rollbackModelVersionMock,
  submitModelReview as submitModelReviewMock,
  withdrawModelVersion as withdrawModelVersionMock,
} from './mock/modelAdapter'
import { IS_MOCK, mockCall } from './request'
import type { PageResult } from './types'
import type {
  ModelArtifact,
  ModelAuditRecord,
  ModelDatasetSnapshot,
  ModelImportInput,
  ModelListQuery,
  ModelOptions,
  ModelReviewResult,
  ModelStats,
  ModelSummary,
  ModelVersion,
  ModelVersionCompareRow,
  ModelVersionQuery,
  ReleaseChannel,
  ReleaseRecord,
} from '@/types/model'
import type { PublishInput, ReviewInput, RollbackInput, WithdrawInput } from './mock/modelAdapter'

/**
 * A06 模型版本管理 Service（P12）。
 *
 * 页面只调用本文件；Mock 与真实 FastAPI 由 IS_MOCK 切换。
 * 真实接口路径按 docs/api/P12_MODEL_CONTRACT.md 预留：
 * 发布、撤回、回滚均需后端二次校验权限与模型文件状态，
 * 前端按钮状态不能代替服务端校验。
 */

export async function fetchModelStats(): Promise<ModelStats> {
  if (IS_MOCK) return mockCall(() => getModelStats())
  return http.get<ModelStats>('/admin/models/stats')
}

export async function fetchModelOptions(): Promise<ModelOptions> {
  if (IS_MOCK) return mockCall(() => getModelOptions())
  return http.get<ModelOptions>('/admin/models/options')
}

export async function fetchModels(query: ModelListQuery): Promise<PageResult<ModelSummary>> {
  if (IS_MOCK) return mockCall(() => listModels(query))
  return http.get<PageResult<ModelSummary>>('/admin/models', {
    query: {
      keyword: query.keyword || undefined,
      modelType: query.modelType || undefined,
      framework: query.framework || undefined,
      releaseStatus: query.releaseStatus || undefined,
      compatibilityStatus: query.compatibilityStatus || undefined,
      publishedFrom: query.publishedFrom || undefined,
      publishedTo: query.publishedTo || undefined,
      page: query.page,
      pageSize: query.pageSize,
    },
  })
}

export async function fetchModelDetail(modelId: string): Promise<ModelSummary> {
  if (IS_MOCK) return mockCall(() => getModelDetail(modelId))
  return http.get<ModelSummary>(`/admin/models/${modelId}`)
}

export async function fetchModelVersions(
  modelId: string,
  query: ModelVersionQuery,
): Promise<PageResult<ModelVersion>> {
  if (IS_MOCK) return mockCall(() => listModelVersions(modelId, query))
  return http.get<PageResult<ModelVersion>>(`/admin/models/${modelId}/versions`, {
    query: {
      keyword: query.keyword || undefined,
      releaseStatus: query.releaseStatus || undefined,
      compatibilityStatus: query.compatibilityStatus || undefined,
      page: query.page,
      pageSize: query.pageSize,
    },
  })
}

export async function fetchModelVersion(
  modelId: string,
  modelVersionId: string,
): Promise<ModelVersion> {
  if (IS_MOCK) {
    return mockCall(() => {
      const found = findModelVersion(modelVersionId)
      if (found.modelId !== modelId) {
        throw new Error('模型版本不属于当前模型')
      }
      return found
    })
  }
  return http.get<ModelVersion>(`/admin/models/${modelId}/versions/${modelVersionId}`)
}

export async function fetchModelArtifact(artifactId: string): Promise<ModelArtifact> {
  if (IS_MOCK) return mockCall(() => findArtifact(artifactId))
  return http.get<ModelArtifact>(`/admin/model-artifacts/${artifactId}`)
}

/**
 * 模型版本关联数据集的服务端快照。
 * 真实模式下数据集卡片只使用该接口，禁止回退到 P10 Mock 数据。
 */
export async function fetchModelDatasetSnapshot(
  modelVersionId: string,
): Promise<ModelDatasetSnapshot | null> {
  if (IS_MOCK) return null
  return http.get<ModelDatasetSnapshot>(
    `/admin/model-versions/${modelVersionId}/dataset-snapshot`,
  )
}

export async function fetchModelArtifacts(modelId: string): Promise<ModelArtifact[]> {
  if (IS_MOCK) return mockCall(() => listArtifacts(modelId))
  return http.get<ModelArtifact[]>('/admin/model-artifacts', { query: { modelId } })
}

/**
 * 从真实训练任务导入模型产物。
 * 服务端会重新读取产物清单、计算 SHA-256 并验签；前端不参与任何校验。
 */
export async function importModelVersion(input: ModelImportInput): Promise<ModelVersion> {
  if (IS_MOCK) {
    throw new ApiError(409, 'MODEL_IMPORT_INVALID', '演示模式不支持导入真实训练产物')
  }
  return http.post<ModelVersion>('/admin/model-versions/import', input)
}

/** 审核清单：前端预览，发布时后端必须重新生成并校验 */
export async function evaluateModelVersion(
  modelVersionId: string,
  channel: ReleaseChannel,
  roleLabel: string,
): Promise<ModelReviewResult> {
  if (IS_MOCK) return mockCall(() => evaluateModelVersionMock(modelVersionId, channel, roleLabel))
  return http.get<ModelReviewResult>(`/admin/model-versions/${modelVersionId}/review-check`, {
    query: { channel },
  })
}

export async function submitModelReview(
  modelVersionId: string,
  input: ReviewInput,
): Promise<ReleaseRecord> {
  if (IS_MOCK) return mockCall(() => submitModelReviewMock(modelVersionId, input))
  return http.post<ReleaseRecord>(`/admin/model-versions/${modelVersionId}/submit-review`, input)
}

export async function approveModelReview(
  modelVersionId: string,
  input: ReviewInput,
): Promise<ReleaseRecord> {
  if (IS_MOCK) return mockCall(() => approveModelReviewMock(modelVersionId, input))
  return http.post<ReleaseRecord>(`/admin/model-versions/${modelVersionId}/approve`, input)
}

export async function rejectModelReview(
  modelVersionId: string,
  reviewer: string,
  roleLabel: string,
  reason: string,
): Promise<ModelVersion> {
  if (IS_MOCK) {
    return mockCall(() => rejectModelReviewMock(modelVersionId, reviewer, roleLabel, reason))
  }
  return http.post<ModelVersion>(`/admin/model-versions/${modelVersionId}/reject`, {
    reviewer,
    roleLabel,
    reason,
  })
}

export async function publishModelVersion(
  modelVersionId: string,
  input: PublishInput,
): Promise<ReleaseRecord> {
  if (IS_MOCK) return mockCall(() => publishModelVersionMock(modelVersionId, input))
  return http.post<ReleaseRecord>(`/admin/model-versions/${modelVersionId}/publish`, input)
}

export async function withdrawModelVersion(
  modelVersionId: string,
  input: WithdrawInput,
): Promise<ModelVersion> {
  if (IS_MOCK) return mockCall(() => withdrawModelVersionMock(modelVersionId, input))
  return http.post<ModelVersion>(`/admin/model-versions/${modelVersionId}/withdraw`, input)
}

export async function rollbackModelVersion(
  modelId: string,
  input: RollbackInput,
): Promise<ReleaseRecord> {
  if (IS_MOCK) return mockCall(() => rollbackModelVersionMock(modelId, input))
  return http.post<ReleaseRecord>(`/admin/models/${modelId}/rollback`, input)
}

export async function fetchReleaseRecords(
  modelId?: string,
  modelVersionId?: string,
): Promise<ReleaseRecord[]> {
  if (IS_MOCK) return mockCall(() => listReleaseRecords(modelId, modelVersionId))
  if (modelVersionId) {
    return http.get<ReleaseRecord[]>(`/admin/model-versions/${modelVersionId}/releases`)
  }
  return http.get<ReleaseRecord[]>(`/admin/models/${modelId}/releases`)
}

export async function fetchModelAudits(
  modelId?: string,
  modelVersionId?: string,
): Promise<ModelAuditRecord[]> {
  if (IS_MOCK) return mockCall(() => listModelAudits(modelId, modelVersionId))
  if (modelVersionId) {
    return http.get<ModelAuditRecord[]>(`/admin/model-versions/${modelVersionId}/audits`)
  }
  return http.get<ModelAuditRecord[]>(`/admin/models/${modelId}/audits`)
}

export async function fetchModelVersionCompare(
  baseVersionId: string,
  targetVersionId: string,
): Promise<ModelVersionCompareRow[]> {
  if (IS_MOCK) return mockCall(() => compareModelVersions(baseVersionId, targetVersionId))
  return http.get<ModelVersionCompareRow[]>('/admin/model-versions/compare', {
    query: { baseVersionId, targetVersionId },
  })
}
