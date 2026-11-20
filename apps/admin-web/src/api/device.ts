import { http } from './http'
import { getDeviceDetail, getDeviceFilterOptions, queryDevices } from './mock/adapter'
import type {
  AdminDevice,
  DeviceDetail,
  DeviceFilterOptions,
  DeviceQuery,
} from './models'
import { IS_MOCK, mockCall } from './request'
import type { PageResult } from './types'

/** A03 设备管理数据服务 */
export async function fetchDevices(query: DeviceQuery): Promise<PageResult<AdminDevice>> {
  if (IS_MOCK) {
    return mockCall(() => queryDevices(query))
  }
  return http.get<PageResult<AdminDevice>>('/admin/devices', {
    query: {
      deviceId: query.deviceId || undefined,
      model: query.model || undefined,
      brand: query.brand || undefined,
      osVersion: query.osVersion || undefined,
      status: query.status || undefined,
      appVersion: query.appVersion || undefined,
      page: query.page,
      pageSize: query.pageSize,
    },
  })
}

export async function fetchDeviceDetail(deviceId: string): Promise<DeviceDetail> {
  if (IS_MOCK) {
    return mockCall(() => getDeviceDetail(deviceId))
  }
  return http.get<DeviceDetail>(`/admin/devices/${deviceId}`)
}

/** 筛选下拉选项；后续可由服务端下发枚举值 */
export async function fetchDeviceFilterOptions(): Promise<DeviceFilterOptions> {
  if (IS_MOCK) {
    return mockCall(() => getDeviceFilterOptions())
  }
  return http.get<DeviceFilterOptions>('/admin/devices/filter-options')
}
