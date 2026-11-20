import type { Component } from 'vue'
import {
  Box,
  Collection,
  Cpu,
  DataBoard,
  FolderOpened,
  Iphone,
  SetUp,
  Setting,
  User,
  Warning,
} from '@element-plus/icons-vue'

/**
 * 管理端一级菜单配置（V1.3 · A01–A10）。
 *
 * 单一数据源：侧边导航、路由表、面包屑、模块占位页均从此读取，
 * 避免在组件中散落 if/else 判断当前菜单。
 *
 * P03 仅建立路由与页面骨架，所有模块业务功能尚未实现。
 */

export interface AdminModule {
  /** 页面编号，与需求文档 PAGE_INDEX 对应 */
  code: string
  /** 路由名称 */
  routeName: string
  /** 访问路径 */
  path: string
  /** 菜单名称（全中文） */
  title: string
  /** 一句话功能说明 */
  description: string
  /** 计划能力，仅作说明，不代表已实现 */
  plannedFeatures: string[]
  /** 菜单分组 */
  group: string
  /** 侧边菜单图标 */
  icon: Component
}

export const ADMIN_MODULE_GROUPS = ['概览', '用户与设备', '数据与模型', '安全运营', '系统'] as const

export const ADMIN_MODULES: AdminModule[] = [
  {
    code: 'A01',
    routeName: 'admin-workbench',
    path: '/admin/workbench',
    title: '工作台',
    description: '汇总风险识别、待处理事项与系统运行概况。',
    plannedFeatures: [
      '识别量与风险等级分布统计',
      '待处理风险与协同任务汇总',
      '模型与规则版本运行状态',
      '数据标注与训练队列概况',
    ],
    group: '概览',
    icon: DataBoard,
  },
  {
    code: 'A02',
    routeName: 'admin-users',
    path: '/admin/users',
    title: '用户管理',
    description: '管理老人与家属账户，以及两者之间的绑定关系。',
    plannedFeatures: [
      '老人 / 家属账户列表与检索',
      '家庭绑定关系与授权范围',
      '账号状态与注销处理',
      '敏感操作审计留痕',
    ],
    group: '用户与设备',
    icon: User,
  },
  {
    code: 'A03',
    routeName: 'admin-devices',
    path: '/admin/devices',
    title: '设备管理',
    description: '查看设备兼容性、客户端版本分布与异常情况。',
    plannedFeatures: [
      '设备列表与系统版本分布',
      'App 版本与升级覆盖情况',
      '采集授权失效与异常排查',
      '设备解绑与风险标记',
    ],
    group: '用户与设备',
    icon: Iphone,
  },
  {
    code: 'A04',
    routeName: 'admin-datasets',
    path: '/admin/datasets',
    title: '数据集管理',
    description: '管理训练数据集的来源许可、标注、质检与版本。',
    plannedFeatures: [
      '数据集导入与来源许可登记',
      '多标签标注与质检流程',
      '去重、拆分与数据版本管理',
      '敏感样本处置记录',
    ],
    group: '数据与模型',
    icon: FolderOpened,
  },
  {
    code: 'A05',
    routeName: 'admin-training',
    path: '/admin/training',
    title: '训练管理',
    description: '创建与跟踪模型训练任务，查看执行日志与评估结果。',
    plannedFeatures: [
      '训练任务列表与创建表单',
      '执行器状态与任务日志',
      '指标曲线与评估结果对比',
      '失败重试与任务取消',
    ],
    group: '数据与模型',
    icon: Cpu,
  },
  {
    code: 'A06',
    routeName: 'admin-models',
    path: '/admin/models',
    title: '模型版本',
    description: '管理模型文件、量化信息、审核发布与回滚。',
    plannedFeatures: [
      '模型仓库与版本详情',
      '量化信息与设备兼容性',
      '测试、审核与发布审批',
      '撤回与版本回滚',
    ],
    group: '数据与模型',
    icon: Box,
  },
  {
    code: 'A07',
    routeName: 'admin-rules',
    path: '/admin/rules',
    title: '规则管理',
    description: '维护风险类型、判定条件与阈值，并支持规则测试。',
    plannedFeatures: [
      '风险类型与判定条件配置',
      '阈值调整与灰度生效',
      '规则测试与命中预览',
      '变更记录与回滚',
    ],
    group: '安全运营',
    icon: SetUp,
  },
  {
    code: 'A08',
    routeName: 'admin-knowledge',
    path: '/admin/knowledge',
    title: '知识库',
    description: '管理反诈知识资料，支撑提示文案与解释依据。',
    plannedFeatures: [
      '资料来源登记与分类',
      '内容审核与发布 / 下架',
      '与风险提示文案的关联',
      '版本与引用记录',
    ],
    group: '安全运营',
    icon: Collection,
  },
  {
    code: 'A09',
    routeName: 'admin-risk-feedback',
    path: '/admin/risk-feedback',
    title: '风险反馈',
    description: '查看脱敏后的误报、漏报统计与用户反馈。',
    plannedFeatures: [
      '风险识别效果统计',
      '误报 / 漏报反馈列表',
      '反馈样本回流与标注',
      '规则与模型优化建议',
    ],
    group: '安全运营',
    icon: Warning,
  },
  {
    code: 'A10',
    routeName: 'admin-system',
    path: '/admin/system',
    title: '系统管理',
    description: '配置管理员权限、审计日志、备份与安全选项。',
    plannedFeatures: [
      '管理员与角色权限（RBAC）',
      '操作审计日志查询',
      '备份与恢复策略',
      '安全配置与登录策略',
    ],
    group: '系统',
    icon: Setting,
  },
]

/** 起始菜单：工作台 */
export const ADMIN_HOME_PATH = ADMIN_MODULES[0].path

/** 按编号读取模块配置；编号缺失视为配置错误，尽早暴露。 */
export function requireModule(code: string): AdminModule {
  const found = ADMIN_MODULES.find((item) => item.code === code)
  if (!found) {
    throw new Error(`未定义的模块编号：${code}`)
  }
  return found
}

/** 按路径读取模块配置 */
export function findModuleByPath(path: string): AdminModule | undefined {
  return ADMIN_MODULES.find((item) => item.path === path)
}
