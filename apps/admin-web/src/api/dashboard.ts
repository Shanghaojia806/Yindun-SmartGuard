import { http } from './http'
import { getDashboardData } from './mock/adapter'
import type { DashboardData } from './models'
import { IS_MOCK, mockCall } from './request'

/**
 * A01 工作台数据服务。
 * Mock 模式下由本地 Adapter 返回演示数据；接入 FastAPI 后自动切换到真实接口。
 */
export async function fetchDashboard(): Promise<DashboardData> {
  if (IS_MOCK) {
    return mockCall(() => getDashboardData())
  }
  return http.get<DashboardData>('/admin/dashboard')
}
