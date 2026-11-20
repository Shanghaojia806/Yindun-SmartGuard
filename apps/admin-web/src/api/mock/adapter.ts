import { ApiError } from '../http'
import type {
  AdminDevice,
  AdminUser,
  AuditLog,
  AuditQuery,
  DashboardData,
  DashboardStats,
  DashboardTodo,
  DeviceDetail,
  DeviceFilterOptions,
  DeviceAbnormalRecord,
  DeviceQuery,
  SystemOverviewData,
  UserDetail,
  UserQuery,
  AccountStatus,
} from '../models'
import type { PageResult as PageResultType } from '../types'
import {
  buildUserOperationLogs,
  MOCK_ADMINS,
  MOCK_AUDIT_LOGS,
  MOCK_DASHBOARD_OPERATIONS,
  MOCK_DASHBOARD_TASKS,
  MOCK_MODULE_STATUSES,
  MOCK_ROLES,
  MOCK_SYSTEM_CONFIGS,
  mockDeviceStore,
  mockUserStore,
} from './data'

/**
 * Mock Adapter（P09）。
 *
 * 只负责"按契约形状返回数据"，不处理 loading / 消息提示；
 * 延迟与失败模拟统一由 api/request.ts 的 mockCall 包裹。
 */

function paginate<T>(items: T[], page: number, pageSize: number): PageResultType<T> {
  const safePage = Math.max(1, page)
  const safeSize = Math.max(1, pageSize)
  const start = (safePage - 1) * safeSize
  return {
    items: items.slice(start, start + safeSize),
    page: safePage,
    pageSize: safeSize,
    total: items.length,
  }
}

/* ===== 用户 ===== */

export function queryUsers(query: UserQuery): PageResultType<AdminUser> {
  const keyword = query.keyword.trim().toLowerCase()
  const userId = query.userId.trim().toLowerCase()

  const filtered = mockUserStore.filter((user) => {
    if (keyword && !user.name.toLowerCase().includes(keyword)) {
      return false
    }
    if (userId && !user.userId.toLowerCase().includes(userId)) {
      return false
    }
    if (query.role && user.role !== query.role) {
      return false
    }
    if (query.status && user.status !== query.status) {
      return false
    }
    if (query.binding && user.bindingStatus !== query.binding) {
      return false
    }
    return true
  })

  return paginate(filtered, query.page, query.pageSize)
}

export function getUserDetail(userId: string): UserDetail {
  const user = mockUserStore.find((item) => item.userId === userId)
  if (!user) {
    throw new ApiError(404, 'USER_NOT_FOUND', '未找到该用户，演示数据可能已被重置')
  }
  return {
    user,
    operations: buildUserOperationLogs(user.userId),
  }
}

export function updateUserStatus(userId: string, status: AccountStatus): AdminUser {
  const user = mockUserStore.find((item) => item.userId === userId)
  if (!user) {
    throw new ApiError(404, 'USER_NOT_FOUND', '未找到该用户，演示数据可能已被重置')
  }
  user.status = status
  return user
}

/* ===== 设备 ===== */

export function queryDevices(query: DeviceQuery): PageResultType<AdminDevice> {
  const deviceId = query.deviceId.trim().toLowerCase()
  const model = query.model.trim().toLowerCase()

  const filtered = mockDeviceStore.filter((device) => {
    if (deviceId && !device.deviceId.toLowerCase().includes(deviceId)) {
      return false
    }
    if (model && !device.model.toLowerCase().includes(model)) {
      return false
    }
    if (query.brand && device.brand !== query.brand) {
      return false
    }
    if (query.osVersion && device.osVersion !== query.osVersion) {
      return false
    }
    if (query.status && device.status !== query.status) {
      return false
    }
    if (query.appVersion && device.appVersion !== query.appVersion) {
      return false
    }
    return true
  })

  return paginate(filtered, query.page, query.pageSize)
}

function buildDeviceRecords(device: AdminDevice): DeviceAbnormalRecord[] {
  const records: DeviceAbnormalRecord[] = [
    {
      recordId: 'DR-001',
      time: device.lastActiveAt,
      level: 'info',
      description: '最近一次设备心跳上报（演示记录）',
    },
    {
      recordId: 'DR-002',
      time: device.lastActiveAt,
      level: device.compatibility === 'compatible' ? 'info' : 'warning',
      description:
        device.compatibility === 'compatible'
          ? '兼容性检查通过（演示结果，不代表真实设备检测）'
          : '兼容性存在风险，建议用户在设备端确认系统授权状态',
    },
  ]

  if (device.status === 'abnormal') {
    records.push({
      recordId: 'DR-003',
      time: device.lastActiveAt,
      level: 'danger',
      description: device.abnormalNote || '设备存在未处理的异常（演示记录）',
    })
  }

  return records
}

export function getDeviceDetail(deviceId: string): DeviceDetail {
  const device = mockDeviceStore.find((item) => item.deviceId === deviceId)
  if (!device) {
    throw new ApiError(404, 'DEVICE_NOT_FOUND', '未找到该设备，演示数据可能已被重置')
  }
  return {
    device,
    records: buildDeviceRecords(device),
  }
}

export function getDeviceFilterOptions(): DeviceFilterOptions {
  const unique = (values: string[]) => Array.from(new Set(values)).sort()
  return {
    brands: unique(mockDeviceStore.map((item) => item.brand)),
    osVersions: unique(mockDeviceStore.map((item) => item.osVersion)),
    appVersions: unique(mockDeviceStore.map((item) => item.appVersion)),
  }
}

/* ===== 工作台 ===== */

export function getDashboardData(): DashboardData {
  const stats: DashboardStats = {
    registeredTotal: mockUserStore.length,
    elderCount: mockUserStore.filter((item) => item.role === 'elder').length,
    familyCount: mockUserStore.filter((item) => item.role === 'family').length,
    boundFamilyCount: mockUserStore.filter(
      (item) => item.role === 'family' && item.bindingStatus === 'bound',
    ).length,
    deviceTotal: mockDeviceStore.length,
    abnormalDeviceCount: mockDeviceStore.filter((item) => item.status === 'abnormal').length,
  }

  const todos: DashboardTodo[] = [
    {
      id: 'TODO-DATASET',
      label: '待审核数据集',
      description: '数据集审核流程将在 P10 实现，当前数量为演示值',
      count: 3,
      moduleCode: 'A04',
    },
    {
      id: 'TODO-FEEDBACK',
      label: '待处理用户反馈',
      description: '风险反馈处理流程尚未接入，当前数量为演示值',
      count: 5,
      moduleCode: 'A09',
    },
    {
      id: 'TODO-MODEL',
      label: '待审核模型版本',
      description: '模型审核与发布将在 P12 实现，当前数量为演示值',
      count: 2,
      moduleCode: 'A06',
    },
    {
      id: 'TODO-DEVICE',
      label: '待处理设备异常',
      description: '来自演示设备列表中的异常记录统计',
      count: stats.abnormalDeviceCount,
      moduleCode: 'A03',
    },
  ]

  return {
    stats,
    todos,
    modules: MOCK_MODULE_STATUSES,
    operations: MOCK_DASHBOARD_OPERATIONS,
    tasks: MOCK_DASHBOARD_TASKS,
    version: '管理后台 V1.3 · P09',
    buildNote: '演示数据模式：统计数字由本地 Mock 用户与设备数据实时计算，不代表真实运营指标。',
  }
}

/* ===== 系统管理 ===== */

export function getSystemOverview(): SystemOverviewData {
  return {
    admins: MOCK_ADMINS,
    roles: MOCK_ROLES,
    configs: MOCK_SYSTEM_CONFIGS,
  }
}

export function queryAuditLogs(query: AuditQuery): PageResultType<AuditLog> {
  const keyword = query.keyword.trim().toLowerCase()
  const filtered = MOCK_AUDIT_LOGS.filter((log) => {
    if (!keyword) {
      return true
    }
    return (
      log.operator.toLowerCase().includes(keyword) ||
      log.actionType.toLowerCase().includes(keyword) ||
      log.target.toLowerCase().includes(keyword)
    )
  })
  return paginate(filtered, query.page, query.pageSize)
}
