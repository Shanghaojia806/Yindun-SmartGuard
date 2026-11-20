import { http } from './http'
import {
  compareVersions,
  confirmVersionManualReview,
  createDataset as createDatasetMock,
  createVersion,
  evaluateEligibility,
  getDatasetDetail,
  getDatasetOptions,
  getDatasetStats,
  getDedupeOverview,
  getImportDemoRows,
  getLabels,
  getQualityReport,
  getSample,
  getVersion,
  importSamples,
  listDatasets,
  listVersions,
  prepareImport,
  previewSplit,
  querySamples,
  resolveDedupeGroup,
  reviewSample,
  runQualityCheck,
  saveAnnotation,
  updateVersionStatus,
} from './mock/datasetAdapter'
import { IS_MOCK, mockCall } from './request'
import type { PageResult } from './types'
import type {
  AnnotationDraft,
  AnnotationRecord,
  CreateDatasetInput,
  CreateVersionInput,
  DatasetListQuery,
  DatasetOptions,
  DatasetOverviewStats,
  DatasetSample,
  DatasetSummary,
  DatasetVersion,
  DedupeOverview,
  ImportLicenseDraft,
  ImportResult,
  ImportRow,
  ImportSourceDraft,
  ImportSummary,
  QualityReport,
  ReviewRecord,
  RiskLabel,
  SampleQuery,
  SplitConfig,
  SplitResult,
  TrainingEligibility,
  VersionCompareRow,
  VersionStatus,
} from '@/types/dataset'

/**
 * A04 数据集 Service。
 *
 * 页面只调用本文件；Mock 与真实接口由 IS_MOCK 切换，页面无需感知。
 * 真实接口路径按 CONTRACT_DRAFT.md 的 /admin/datasets 约定预留。
 */

const ANNOTATOR_ID = 'demo-annotator'

export async function fetchDatasetStats(): Promise<DatasetOverviewStats> {
  if (IS_MOCK) return mockCall(() => getDatasetStats())
  return http.get<DatasetOverviewStats>('/admin/datasets/stats')
}

export async function fetchDatasetOptions(): Promise<DatasetOptions> {
  if (IS_MOCK) return mockCall(() => getDatasetOptions())
  return http.get<DatasetOptions>('/admin/datasets/options')
}

export async function fetchDatasets(query: DatasetListQuery): Promise<PageResult<DatasetSummary>> {
  if (IS_MOCK) return mockCall(() => listDatasets(query))
  return http.get<PageResult<DatasetSummary>>('/admin/datasets', {
    query: {
      keyword: query.keyword || undefined,
      datasetType: query.datasetType || undefined,
      status: query.status || undefined,
      createdFrom: query.createdFrom || undefined,
      createdTo: query.createdTo || undefined,
      annotationProgress: query.annotationProgress || undefined,
      page: query.page,
      pageSize: query.pageSize,
    },
  })
}

export async function fetchDatasetDetail(datasetId: string): Promise<DatasetSummary> {
  if (IS_MOCK) return mockCall(() => getDatasetDetail(datasetId))
  return http.get<DatasetSummary>(`/admin/datasets/${datasetId}`)
}

export async function createDataset(
  input: CreateDatasetInput,
): Promise<DatasetSummary> {
  if (IS_MOCK) return mockCall(() => createDatasetMock(input))
  return http.post<DatasetSummary>('/admin/datasets', input)
}

export async function fetchDatasetLabels(): Promise<RiskLabel[]> {
  if (IS_MOCK) return mockCall(() => getLabels())
  return http.get<RiskLabel[]>('/admin/datasets/labels')
}

export async function fetchDatasetSamples(
  query: SampleQuery,
): Promise<PageResult<DatasetSample>> {
  if (IS_MOCK) return mockCall(() => querySamples(query))
  return http.get<PageResult<DatasetSample>>(`/admin/datasets/${query.datasetId}/samples`, {
    query: {
      keyword: query.keyword || undefined,
      sourceType: query.sourceType || undefined,
      annotationStatus: query.annotationStatus || undefined,
      reviewStatus: query.reviewStatus || undefined,
      licenseStatus: query.licenseStatus || undefined,
      page: query.page,
      pageSize: query.pageSize,
    },
  })
}

export async function fetchDatasetSample(
  datasetId: string,
  sampleId: string,
): Promise<DatasetSample> {
  if (IS_MOCK) return mockCall(() => getSample(datasetId, sampleId))
  return http.get<DatasetSample>(`/admin/datasets/${datasetId}/samples/${sampleId}`)
}

export async function saveSampleAnnotation(
  datasetId: string,
  sampleId: string,
  draft: AnnotationDraft,
): Promise<AnnotationRecord> {
  if (IS_MOCK) return mockCall(() => saveAnnotation(datasetId, sampleId, draft, ANNOTATOR_ID))
  return http.put<AnnotationRecord>(`/admin/datasets/${datasetId}/samples/${sampleId}/annotation`, draft)
}

export async function submitSampleReview(
  datasetId: string,
  sampleId: string,
  reviewResult: ReviewRecord['reviewResult'],
  reviewNotes: string,
): Promise<ReviewRecord> {
  if (IS_MOCK) {
    return mockCall(() => reviewSample(datasetId, sampleId, reviewResult, reviewNotes, 'demo-reviewer'))
  }
  return http.post<ReviewRecord>(`/admin/datasets/${datasetId}/samples/${sampleId}/review`, {
    reviewResult,
    reviewNotes,
  })
}

export async function fetchQualityReport(datasetId: string): Promise<QualityReport | null> {
  if (IS_MOCK) return mockCall(() => getQualityReport(datasetId))
  return http.get<QualityReport | null>(`/admin/datasets/${datasetId}/quality-report`)
}

export async function runDatasetQualityCheck(datasetId: string): Promise<QualityReport> {
  if (IS_MOCK) return mockCall(() => runQualityCheck(datasetId))
  return http.post<QualityReport>(`/admin/datasets/${datasetId}/quality-check`)
}

export async function fetchDedupeOverview(datasetId: string): Promise<DedupeOverview> {
  if (IS_MOCK) return mockCall(() => getDedupeOverview(datasetId))
  return http.get<DedupeOverview>(`/admin/datasets/${datasetId}/dedupe`)
}

export async function resolveDedupe(
  datasetId: string,
  groupId: string,
  keepSampleId: string,
  excludeSampleIds: string[],
): Promise<DedupeOverview> {
  if (IS_MOCK) {
    return mockCall(() => resolveDedupeGroup(datasetId, groupId, keepSampleId, excludeSampleIds))
  }
  return http.post<DedupeOverview>(`/admin/datasets/${datasetId}/dedupe/${groupId}/resolve`, {
    keepSampleId,
    excludeSampleIds,
  })
}

export async function fetchSplitPreview(
  datasetId: string,
  config: SplitConfig,
): Promise<SplitResult> {
  if (IS_MOCK) return mockCall(() => previewSplit(datasetId, config))
  return http.post<SplitResult>(`/admin/datasets/${datasetId}/split-preview`, config)
}

export async function fetchDatasetVersions(datasetId: string): Promise<DatasetVersion[]> {
  if (IS_MOCK) return mockCall(() => listVersions(datasetId))
  return http.get<DatasetVersion[]>(`/admin/datasets/${datasetId}/versions`)
}

export async function fetchDatasetVersion(versionId: string): Promise<DatasetVersion> {
  if (IS_MOCK) return mockCall(() => getVersion(versionId))
  return http.get<DatasetVersion>(`/admin/dataset-versions/${versionId}`)
}

export async function createDatasetVersion(
  datasetId: string,
  input: CreateVersionInput,
): Promise<DatasetVersion> {
  if (IS_MOCK) return mockCall(() => createVersion(datasetId, input))
  return http.post<DatasetVersion>(`/admin/datasets/${datasetId}/versions`, input)
}

export async function changeVersionStatus(
  versionId: string,
  status: VersionStatus,
): Promise<DatasetVersion> {
  if (IS_MOCK) return mockCall(() => updateVersionStatus(versionId, status))
  return http.patch<DatasetVersion>(`/admin/dataset-versions/${versionId}/status`, { status })
}

export async function fetchVersionCompare(
  versionId: string,
  baseVersionId: string,
): Promise<VersionCompareRow[]> {
  if (IS_MOCK) return mockCall(() => compareVersions(versionId, baseVersionId))
  return http.get<VersionCompareRow[]>(`/admin/dataset-versions/${versionId}/compare`, {
    query: { baseVersionId },
  })
}

/** 记录同源拆分人工复核结论（Mock 规则：复核通过后才允许发布）。 */
export async function reviewDatasetVersion(
  versionId: string,
  notes: string,
): Promise<DatasetVersion> {
  if (IS_MOCK) return mockCall(() => confirmVersionManualReview(versionId, notes))
  return http.post<DatasetVersion>(`/admin/dataset-versions/${versionId}/manual-review`, { notes })
}

export async function fetchImportPreview(
  datasetId: string,
  rows: ImportRow[],
  source: ImportSourceDraft,
  license: ImportLicenseDraft,
): Promise<ImportSummary> {
  if (IS_MOCK) return mockCall(() => prepareImport(datasetId, rows, source, license))
  return http.post<ImportSummary>(`/admin/datasets/${datasetId}/import/preview`, {
    rows,
    source,
    license,
  })
}

export async function confirmDatasetImport(
  datasetId: string,
  rows: ImportRow[],
  source: ImportSourceDraft,
  license: ImportLicenseDraft,
  importableRowIds: string[],
): Promise<ImportResult> {
  if (IS_MOCK) {
    return mockCall(() => importSamples(datasetId, rows, source, license, importableRowIds))
  }
  return http.post<ImportResult>(`/admin/datasets/${datasetId}/import`, {
    rows,
    source,
    license,
    importableRowIds,
  })
}

/** 导入向导的演示数据包：仅 Mock 模式返回，真实环境返回空数组 */
export async function fetchImportDemoRows(
  mode: 'normal' | 'format-error' | 'missing-info' | 'duplicate',
): Promise<ImportRow[]> {
  if (IS_MOCK) return mockCall(() => getImportDemoRows(mode))
  return []
}

/** 训练资格判断（前端规则，真实训练前仍需后端复核） */
export async function evaluateSampleEligibility(
  datasetId: string,
  sampleId: string,
): Promise<TrainingEligibility> {
  if (IS_MOCK) {
    return mockCall(() => {
      const sample = getSample(datasetId, sampleId)
      return evaluateEligibility(sample)
    })
  }
  return http.get<TrainingEligibility>(
    `/admin/datasets/${datasetId}/samples/${sampleId}/eligibility`,
  )
}
