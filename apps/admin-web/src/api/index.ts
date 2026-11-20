import { http, request, ApiError } from './http'
export type { RequestOptions } from './http'
export type { ApiEnvelope, ApiErrorBody, ApiMeta, DataSource, PageResult } from './types'

export {
  IS_MOCK,
  DATA_SOURCE,
  MOCK_NOTICE,
  mockCall,
  simulateNextRequestFailure,
  clearSimulatedFailure,
  hasSimulatedFailure,
} from './request'

export { fetchDashboard } from './dashboard'
export { fetchUsers, fetchUserDetail, changeUserStatus } from './user'
export { fetchDevices, fetchDeviceDetail, fetchDeviceFilterOptions } from './device'
export { fetchSystemOverview, fetchAuditLogs } from './system'

export { http, request, ApiError }
