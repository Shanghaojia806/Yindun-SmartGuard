/**
 * 接口通用类型：与 docs/api/CONTRACT_DRAFT.md 的响应结构保持一致。
 * 契约为草案，最终以 FastAPI 导出的 OpenAPI 为准。
 */

/** 数据来源标识：Mock 必须可被前端识别并展示 */
export type DataSource = 'mock' | 'real'

export interface ApiMeta {
  traceId?: string
  dataSource?: DataSource
}

export interface ApiEnvelope<T> {
  data: T
  meta?: ApiMeta
}

export interface PageResult<T> {
  items: T[]
  page: number
  pageSize: number
  total: number
}

export interface ApiErrorBody {
  code: string
  message: string
  details?: unknown[]
  traceId?: string
}
