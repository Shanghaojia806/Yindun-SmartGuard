/**
 * 管理后台业务模型（P09）。
 *
 * 这些类型同时约束 Mock Adapter 与未来的 FastAPI 响应，
 * 页面只依赖本文件，不直接依赖 Mock 数据结构。
 */

export type UserRole = 'elder' | 'family'
export type AccountStatus = 'active' | 'disabled'
export type BindingStatus = 'bound' | 'unbound' | 'pending'
export type DeviceStatus = 'normal' | 'abnormal'
export type CompatibilityStatus = 'compatible' | 'limited' | 'unknown'
export type AuditResult = 'success' | 'failure'
export type ModuleHealth = 'running' | 'degraded' | 'offline'

/** 用户列表项 */
export interface AdminUser {
  userId: string
  name: string
  role: UserRole
  /** 已脱敏的联系方式，禁止展示完整号码 */
  phoneMasked: string
  status: AccountStatus
  registeredAt: string
  bindingStatus: BindingStatus
  lastActiveAt: string
}

/** 用户查询条件 */
export interface UserQuery {
  keyword: string
  userId: string
  role: UserRole | ''
  status: AccountStatus | ''
  binding: BindingStatus | ''
  page: number
  pageSize: number
}

/** 用户详情中的操作记录（占位） */
export interface UserOperationLog {
  logId: string
  action: string
  operator: string
  time: string
  result: AuditResult
}

export interface UserDetail {
  user: AdminUser
  operations: UserOperationLog[]
}

/** 设备列表项 */
export interface AdminDevice {
  deviceId: string
  userId: string
  userName: string
  brand: string
  model: string
  osName: 'Android' | 'HarmonyOS'
  osVersion: string
  appVersion: string
  status: DeviceStatus
  compatibility: CompatibilityStatus
  lastActiveAt: string
  abnormalNote: string
}

export interface DeviceQuery {
  deviceId: string
  model: string
  brand: string
  osVersion: string
  status: DeviceStatus | ''
  appVersion: string
  page: number
  pageSize: number
}

export interface DeviceAbnormalRecord {
  recordId: string
  time: string
  level: 'info' | 'warning' | 'danger'
  description: string
}

export interface DeviceDetail {
  device: AdminDevice
  records: DeviceAbnormalRecord[]
}

/** 工作台统计（全部为演示数据） */
export interface DashboardStats {
  registeredTotal: number
  elderCount: number
  familyCount: number
  boundFamilyCount: number
  deviceTotal: number
  abnormalDeviceCount: number
}

export interface DashboardTodo {
  id: string
  label: string
  description: string
  count: number
  moduleCode: string
}

export interface SystemModuleStatus {
  name: string
  status: ModuleHealth
  note: string
}

export interface DashboardOperation {
  logId: string
  operator: string
  action: string
  target: string
  time: string
  result: AuditResult
}

export interface DashboardTaskPlaceholder {
  id: string
  name: string
  note: string
}

export interface DashboardData {
  stats: DashboardStats
  todos: DashboardTodo[]
  modules: SystemModuleStatus[]
  operations: DashboardOperation[]
  tasks: DashboardTaskPlaceholder[]
  version: string
  buildNote: string
}

/** 管理员账户 */
export interface AdminAccount {
  adminId: string
  name: string
  role: string
  status: AccountStatus
  lastLoginAt: string
}

export interface RolePermission {
  roleId: string
  roleName: string
  description: string
  permissions: string[]
  memberCount: number
}

export interface AuditLog {
  logId: string
  operator: string
  actionType: string
  target: string
  time: string
  result: AuditResult
}

export interface AuditQuery {
  keyword: string
  page: number
  pageSize: number
}

export interface SystemConfigItem {
  configId: string
  label: string
  value: string
  description: string
}

/** 设备筛选下拉选项（真实接口接入后由服务端下发） */
export interface DeviceFilterOptions {
  brands: string[]
  osVersions: string[]
  appVersions: string[]
}

/** A10 系统管理首屏数据（管理员 / 角色 / 配置） */
export interface SystemOverviewData {
  admins: AdminAccount[]
  roles: RolePermission[]
  configs: SystemConfigItem[]
}
