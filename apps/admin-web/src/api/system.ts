import { http } from './http'
import { getSystemOverview, queryAuditLogs } from './mock/adapter'
import type { AuditLog, AuditQuery, SystemOverviewData } from './models'
import { IS_MOCK, mockCall } from './request'
import type { PageResult } from './types'

/** A10 系统管理数据服务 */
export async function fetchSystemOverview(): Promise<SystemOverviewData> {
  if (IS_MOCK) {
    return mockCall(() => getSystemOverview())
  }
  return http.get<SystemOverviewData>('/admin/system/overview')
}

export async function fetchAuditLogs(query: AuditQuery): Promise<PageResult<AuditLog>> {
  if (IS_MOCK) {
    return mockCall(() => queryAuditLogs(query))
  }
  return http.get<PageResult<AuditLog>>('/admin/system/audit-logs', {
    query: {
      keyword: query.keyword || undefined,
      page: query.page,
      pageSize: query.pageSize,
    },
  })
}
