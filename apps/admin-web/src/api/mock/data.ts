import type {
  AdminAccount,
  AdminDevice,
  AdminUser,
  AuditLog,
  DashboardOperation,
  DashboardTaskPlaceholder,
  RolePermission,
  SystemConfigItem,
  SystemModuleStatus,
  UserOperationLog,
} from '../models'

/**
 * 管理后台演示数据（Mock）。
 *
 * 约束：
 * - 全部为虚构数据，姓名、ID、联系方式、设备信息均不对应真实个人；
 * - 页面必须显式标注"演示数据"，禁止当作真实运营指标展示；
 * - 数据在内存中生成，刷新页面即恢复初始状态。
 */

function pad2(value: number): string {
  return String(value).padStart(2, '0')
}

/* ===== 用户 ===== */

const USER_NAMES = [
  '周桂英',
  '李长海',
  '王秀兰',
  '陈国强',
  '刘玉梅',
  '张建国',
  '赵淑芬',
  '孙德海',
  '马春华',
  '朱文斌',
  '胡秀珍',
  '郭永福',
  '何丽娟',
  '高长胜',
  '林美华',
  '罗文山',
  '梁玉兰',
  '宋建平',
  '唐秀英',
  '许志远',
  '韩桂芳',
  '冯国栋',
  '曹淑华',
  '彭德明',
  '董玉珍',
  '袁立中',
]

/** 家属角色下标（其余为老人） */
const FAMILY_INDEXES = new Set([2, 5, 8, 11, 14, 17, 20, 23])
/** 已禁用账户下标 */
const DISABLED_INDEXES = new Set([6, 16, 21])
/** 绑定待确认下标 */
const BINDING_PENDING = new Set([4, 17])
/** 未绑定下标 */
const BINDING_UNBOUND = new Set([9, 13, 19, 23, 25])

function buildUsers(): AdminUser[] {
  return USER_NAMES.map((name, index) => {
    const isFamily = FAMILY_INDEXES.has(index)
    const bindingStatus = BINDING_PENDING.has(index)
      ? 'pending'
      : BINDING_UNBOUND.has(index)
        ? 'unbound'
        : 'bound'
    const registeredMonth = (index % 9) + 1
    const registeredDay = ((index * 3) % 27) + 1
    const activeDay = 19 - (index % 5)
    const activeHour = 8 + (index % 12)
    const activeMinute = (index * 7) % 60
    const phoneTail = String(2000 + index * 37).slice(-4)

    return {
      userId: `U-2026-${String(index + 1).padStart(4, '0')}`,
      name,
      role: isFamily ? 'family' : 'elder',
      phoneMasked: `1${[3, 5, 8, 9][index % 4]}${index % 2 === 0 ? '8' : '7'}****${phoneTail}`,
      status: DISABLED_INDEXES.has(index) ? 'disabled' : 'active',
      registeredAt: `2026-${pad2(registeredMonth)}-${pad2(registeredDay)} 10:${pad2(index % 60)}`,
      bindingStatus,
      lastActiveAt: `2026-11-${pad2(activeDay)} ${pad2(activeHour)}:${pad2(activeMinute)}`,
    }
  })
}

/* ===== 设备 ===== */

interface DeviceSeed {
  userIndex: number
  brand: string
  model: string
  osName: 'Android' | 'HarmonyOS'
  osVersion: string
  appVersion: string
  status: 'normal' | 'abnormal'
  compatibility: 'compatible' | 'limited' | 'unknown'
  abnormalNote: string
}

const DEVICE_SEEDS: DeviceSeed[] = [
  { userIndex: 0, brand: '华为', model: 'Mate 60', osName: 'HarmonyOS', osVersion: 'HarmonyOS 4.2.0', appVersion: '3.1.0', status: 'normal', compatibility: 'compatible', abnormalNote: '' },
  { userIndex: 1, brand: '小米', model: 'Xiaomi 14', osName: 'Android', osVersion: 'Android 14', appVersion: '3.1.0', status: 'normal', compatibility: 'compatible', abnormalNote: '' },
  { userIndex: 3, brand: 'OPPO', model: 'Reno11', osName: 'Android', osVersion: 'Android 14', appVersion: '3.0.2', status: 'abnormal', compatibility: 'limited', abnormalNote: '屏幕采集授权已失效，需用户在设备端重新授权' },
  { userIndex: 4, brand: 'vivo', model: 'X100', osName: 'Android', osVersion: 'Android 14', appVersion: '3.1.0', status: 'normal', compatibility: 'compatible', abnormalNote: '' },
  { userIndex: 6, brand: '荣耀', model: 'Magic6', osName: 'Android', osVersion: 'Android 14', appVersion: '2.9.7', status: 'abnormal', compatibility: 'limited', abnormalNote: 'App 版本低于当前最低支持版本 3.0.0' },
  { userIndex: 7, brand: '华为', model: 'P50', osName: 'HarmonyOS', osVersion: 'HarmonyOS 4.0.0', appVersion: '3.0.2', status: 'normal', compatibility: 'compatible', abnormalNote: '' },
  { userIndex: 9, brand: '红米', model: 'Redmi Note 13', osName: 'Android', osVersion: 'Android 13', appVersion: '3.1.0', status: 'normal', compatibility: 'compatible', abnormalNote: '' },
  { userIndex: 10, brand: '三星', model: 'Galaxy S24', osName: 'Android', osVersion: 'Android 14', appVersion: '3.0.2', status: 'abnormal', compatibility: 'unknown', abnormalNote: '无障碍服务未启用，兼容性尚未完成检测' },
  { userIndex: 12, brand: '华为', model: 'nova 12', osName: 'HarmonyOS', osVersion: 'HarmonyOS 4.2.0', appVersion: '3.1.0', status: 'normal', compatibility: 'compatible', abnormalNote: '' },
  { userIndex: 13, brand: '小米', model: 'Xiaomi 13', osName: 'Android', osVersion: 'Android 14', appVersion: '3.0.2', status: 'normal', compatibility: 'compatible', abnormalNote: '' },
  { userIndex: 15, brand: 'OPPO', model: 'A2', osName: 'Android', osVersion: 'Android 13', appVersion: '2.9.7', status: 'abnormal', compatibility: 'limited', abnormalNote: 'App 版本过低，部分风险提示不可用' },
  { userIndex: 16, brand: 'vivo', model: 'Y100', osName: 'Android', osVersion: 'Android 13', appVersion: '3.1.0', status: 'normal', compatibility: 'compatible', abnormalNote: '' },
  { userIndex: 18, brand: '荣耀', model: 'X50', osName: 'Android', osVersion: 'Android 13', appVersion: '3.0.2', status: 'normal', compatibility: 'unknown', abnormalNote: '' },
  { userIndex: 19, brand: '华为', model: 'Mate 50', osName: 'HarmonyOS', osVersion: 'HarmonyOS 3.1.0', appVersion: '3.0.2', status: 'normal', compatibility: 'compatible', abnormalNote: '' },
  { userIndex: 21, brand: '红米', model: 'Redmi 12C', osName: 'Android', osVersion: 'Android 12', appVersion: '2.9.7', status: 'abnormal', compatibility: 'limited', abnormalNote: '系统版本低于支持范围（Android 13 及以上）' },
  { userIndex: 22, brand: '一加', model: 'Ace 3', osName: 'Android', osVersion: 'Android 14', appVersion: '3.1.0', status: 'normal', compatibility: 'compatible', abnormalNote: '' },
]

function buildDevices(users: AdminUser[]): AdminDevice[] {
  return DEVICE_SEEDS.map((seed, index) => {
    const user = users[seed.userIndex]
    return {
      deviceId: `DEV-2026-${String(index + 1).padStart(4, '0')}`,
      userId: user.userId,
      userName: user.name,
      brand: seed.brand,
      model: seed.model,
      osName: seed.osName,
      osVersion: seed.osVersion,
      appVersion: seed.appVersion,
      status: seed.status,
      compatibility: seed.compatibility,
      lastActiveAt: `2026-11-${pad2(19 - (index % 4))} ${pad2(7 + (index % 13))}:${pad2((index * 11) % 60)}`,
      abnormalNote: seed.abnormalNote,
    }
  })
}

/* ===== 运行期可变副本（仅演示启用 / 禁用交互） ===== */

export const mockUserStore: AdminUser[] = buildUsers()
export const mockDeviceStore: AdminDevice[] = buildDevices(mockUserStore)

/* ===== 系统管理 ===== */

export const MOCK_ADMINS: AdminAccount[] = [
  { adminId: 'ADM-001', name: '张明远', role: '超级管理员', status: 'active', lastLoginAt: '2026-11-19 08:52' },
  { adminId: 'ADM-002', name: '李静', role: '普通管理员', status: 'active', lastLoginAt: '2026-11-18 17:26' },
  { adminId: 'ADM-003', name: '王海涛', role: '数据审核人员', status: 'active', lastLoginAt: '2026-11-18 15:04' },
  { adminId: 'ADM-004', name: '陈晓雯', role: '模型管理人员', status: 'active', lastLoginAt: '2026-11-19 09:31' },
  { adminId: 'ADM-005', name: '赵鹏', role: '普通管理员', status: 'disabled', lastLoginAt: '2026-10-30 11:12' },
  { adminId: 'ADM-006', name: '孙彤', role: '数据审核人员', status: 'active', lastLoginAt: '2026-11-17 14:48' },
]

export const MOCK_ROLES: RolePermission[] = [
  {
    roleId: 'ROLE-01',
    roleName: '超级管理员',
    description: '拥有全部菜单可见性与系统配置入口（真实 RBAC 待服务端接入）。',
    permissions: ['工作台', '用户管理', '设备管理', '数据集管理', '训练管理', '模型版本', '规则管理', '知识库', '风险反馈', '系统管理'],
    memberCount: 1,
  },
  {
    roleId: 'ROLE-02',
    roleName: '普通管理员',
    description: '负责日常运营查看与用户、设备信息维护。',
    permissions: ['工作台', '用户管理', '设备管理', '风险反馈'],
    memberCount: 2,
  },
  {
    roleId: 'ROLE-03',
    roleName: '数据审核人员',
    description: '负责数据集质检、知识库内容审核与风险反馈核对。',
    permissions: ['工作台', '数据集管理', '知识库', '风险反馈'],
    memberCount: 2,
  },
  {
    roleId: 'ROLE-04',
    roleName: '模型管理人员',
    description: '负责训练任务、模型版本与规则阈值的配置。',
    permissions: ['工作台', '训练管理', '模型版本', '规则管理'],
    memberCount: 1,
  },
]

const AUDIT_OPERATORS = ['张明远', '李静', '王海涛', '陈晓雯', '孙彤']
const AUDIT_ACTIONS = [
  '登录系统',
  '查看用户详情',
  '修改用户状态',
  '查看设备详情',
  '查看工作台统计',
  '更新角色说明',
  '导出审计日志',
]
const AUDIT_TARGETS = [
  '管理后台',
  '用户 U-2026-0004',
  '设备 DEV-2026-0003',
  '角色：数据审核人员',
  '审计日志',
  '工作台',
]

function buildAuditLogs(): AuditLog[] {
  return Array.from({ length: 24 }, (_, index) => {
    const day = 19 - (index % 6)
    const hour = 9 + (index % 10)
    const minute = (index * 13) % 60
    return {
      logId: `LOG-2026-${String(index + 1).padStart(4, '0')}`,
      operator: AUDIT_OPERATORS[index % AUDIT_OPERATORS.length],
      actionType: AUDIT_ACTIONS[index % AUDIT_ACTIONS.length],
      target: AUDIT_TARGETS[index % AUDIT_TARGETS.length],
      time: `2026-11-${pad2(day)} ${pad2(hour)}:${pad2(minute)}`,
      result: index % 7 === 3 ? 'failure' : 'success',
    }
  })
}

export const MOCK_AUDIT_LOGS: AuditLog[] = buildAuditLogs()

export const MOCK_SYSTEM_CONFIGS: SystemConfigItem[] = [
  { configId: 'CFG-01', label: '系统名称', value: '银盾智鉴管理后台', description: '展示在浏览器标题与登录页，真实环境由服务端下发。' },
  { configId: 'CFG-02', label: '会话超时', value: '120 分钟', description: '安全策略配置项，当前仅为占位，尚未生效。' },
  { configId: 'CFG-03', label: '登录失败锁定', value: '5 次 / 30 分钟', description: '安全策略配置项，需接入认证服务后启用。' },
  { configId: 'CFG-04', label: '审计日志保留', value: '180 天', description: '日志管理配置项，当前仅展示默认值。' },
  { configId: 'CFG-05', label: '数据备份周期', value: '每日 02:00', description: '备份配置占位，P09 不执行真实备份任务。' },
  { configId: 'CFG-06', label: '备份保留份数', value: '7 份', description: '备份配置占位，需由服务端存储策略接管。' },
]

export const MOCK_MODULE_STATUSES: SystemModuleStatus[] = [
  { name: '管理后台前端', status: 'running', note: '当前页面由本地 Vite 开发服务提供' },
  { name: 'Mock 数据服务', status: 'running', note: '本地演示数据，未连接数据库' },
  { name: '业务 API（FastAPI）', status: 'offline', note: '尚未接入，P13 联调时启用' },
  { name: '端侧风险识别引擎', status: 'offline', note: '运行在 Android 设备本地，管理端不读取原始数据' },
  { name: '模型训练执行器', status: 'offline', note: '未接入真实训练任务' },
  { name: '审计日志服务', status: 'offline', note: '当前审计记录为演示数据' },
]

export const MOCK_DASHBOARD_OPERATIONS: DashboardOperation[] = [
  { logId: 'OP-001', operator: '张明远', action: '查看用户详情', target: '用户 U-2026-0004', time: '2026-11-19 09:42', result: 'success' },
  { logId: 'OP-002', operator: '李静', action: '禁用账户', target: '用户 U-2026-0022', time: '2026-11-19 09:18', result: 'success' },
  { logId: 'OP-003', operator: '王海涛', action: '查看设备详情', target: '设备 DEV-2026-0008', time: '2026-11-19 08:57', result: 'success' },
  { logId: 'OP-004', operator: '陈晓雯', action: '更新角色说明', target: '角色：模型管理人员', time: '2026-11-18 18:06', result: 'failure' },
  { logId: 'OP-005', operator: '孙彤', action: '登录系统', target: '管理后台', time: '2026-11-18 17:35', result: 'success' },
]

export const MOCK_DASHBOARD_TASKS: DashboardTaskPlaceholder[] = [
  { id: 'TASK-01', name: '训练任务队列', note: '未接入训练执行器，暂不展示任务进度' },
  { id: 'TASK-02', name: '模型发布审批', note: '模型版本流程将在 P12 实现' },
  { id: 'TASK-03', name: '数据集标注质检', note: '数据集流程将在 P10 实现' },
]

/** 用户详情中的操作记录（占位，明确标记为演示） */
export function buildUserOperationLogs(userId: string): UserOperationLog[] {
  return [
    { logId: 'ULOG-001', action: '账户创建（演示）', operator: '系统', time: '2026-09-12 10:20', result: 'success' },
    { logId: 'ULOG-002', action: '查看用户详情（演示）', operator: '演示管理员', time: '2026-11-18 16:05', result: 'success' },
    { logId: 'ULOG-003', action: `绑定关系校验（演示，${userId}）`, operator: '演示管理员', time: '2026-11-19 09:12', result: 'success' },
  ]
}
