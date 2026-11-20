import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

import { ADMIN_HOME_PATH, ADMIN_MODULES } from './modules'
import { pinia } from '@/stores'
import { useSessionStore } from '@/stores/session'

/**
 * 管理端路由（P03）。
 *
 * 路由表由 router/modules.ts 的 ADMIN_MODULES 生成，
 * 侧边菜单、面包屑与页面标题共用同一份配置，避免多处硬编码。
 *
 * 当前会话为本地模拟登录，守卫仅判断本地会话是否存在，
 * 不代表真实的身份认证与权限校验。
 */

declare module 'vue-router' {
  interface RouteMeta {
    /** 页面标题，用于 document.title 与面包屑 */
    title?: string
    /** 仅开发环境可访问 */
    devOnly?: boolean
    /** 需要登录会话（当前为本地模拟会话） */
    requiresAuth?: boolean
    /** 对应需求文档中的页面编号 */
    moduleCode?: string
  }
}

/** 模块编号 → 页面组件。页面文件与菜单配置一一对应。 */
const MODULE_VIEWS: Record<string, () => Promise<unknown>> = {
  A01: () => import('@/views/admin/WorkbenchView.vue'),
  A02: () => import('@/views/admin/UserManagementView.vue'),
  A03: () => import('@/views/admin/DeviceManagementView.vue'),
  A04: () => import('@/views/dataset/DatasetListView.vue'),
  A05: () => import('@/views/training/TrainingListView.vue'),
  A06: () => import('@/views/model/ModelRepositoryView.vue'),
  A07: () => import('@/views/admin/RuleManagementView.vue'),
  A08: () => import('@/views/admin/KnowledgeBaseView.vue'),
  A09: () => import('@/views/admin/RiskFeedbackView.vue'),
  A10: () => import('@/views/admin/SystemManagementView.vue'),
}

const adminChildren: RouteRecordRaw[] = ADMIN_MODULES.map((item) => {
  const view = MODULE_VIEWS[item.code]
  if (!view) {
    throw new Error(`模块 ${item.code} 缺少页面组件映射`)
  }
  return {
    path: item.path,
    name: item.routeName,
    component: view,
    meta: { title: item.title, requiresAuth: true, moduleCode: item.code },
  }
})

/**
 * A04 数据集子页面（P10）。
 *
 * 这些页面属于同一模块，使用绝对路径声明为 /admin 的子路由，
 * 面包屑与侧边菜单通过 meta.moduleCode 保持与 A04 一致。
 */
adminChildren.push(
  {
    path: '/admin/datasets/import',
    name: 'dataset-import',
    component: () => import('@/views/dataset/DatasetImportView.vue'),
    meta: { title: '数据导入向导', requiresAuth: true, moduleCode: 'A04' },
  },
  {
    path: '/admin/datasets/:id/annotation',
    name: 'dataset-annotation',
    component: () => import('@/views/dataset/DatasetAnnotationView.vue'),
    meta: { title: '标注工作台', requiresAuth: true, moduleCode: 'A04' },
  },
  {
    path: '/admin/datasets/:id/quality',
    name: 'dataset-quality',
    component: () => import('@/views/dataset/DatasetQualityView.vue'),
    meta: { title: '数据质检', requiresAuth: true, moduleCode: 'A04' },
  },
  {
    path: '/admin/datasets/:id/dedupe',
    name: 'dataset-dedupe',
    component: () => import('@/views/dataset/DatasetDedupeView.vue'),
    meta: { title: '数据去重', requiresAuth: true, moduleCode: 'A04' },
  },
  {
    path: '/admin/datasets/:id/split',
    name: 'dataset-split',
    component: () => import('@/views/dataset/DatasetSplitView.vue'),
    meta: { title: '数据拆分', requiresAuth: true, moduleCode: 'A04' },
  },
  {
    path: '/admin/datasets/:id/versions',
    name: 'dataset-versions',
    component: () => import('@/views/dataset/DatasetVersionsView.vue'),
    meta: { title: '数据集版本', requiresAuth: true, moduleCode: 'A04' },
  },
)

/**
 * A05 训练管理子页面（P11）。
 *
 * 创建向导与任务详情共用 A05 菜单归属；顺序上静态路径 'new' 先于动态 ':taskId'。
 */
adminChildren.push(
  {
    path: '/admin/training/new',
    name: 'training-create',
    component: () => import('@/views/training/TrainingCreateView.vue'),
    meta: { title: '创建训练任务', requiresAuth: true, moduleCode: 'A05' },
  },
  {
    path: '/admin/training/:taskId',
    name: 'training-detail',
    component: () => import('@/views/training/TrainingDetailView.vue'),
    meta: { title: '训练任务详情', requiresAuth: true, moduleCode: 'A05' },
  },
)

/**
 * A06 模型版本子页面（P12）。
 *
 * 仓库 → 模型详情 → 版本列表 → 版本详情；
 * 静态段位于动态参数之前，避免 'versions' 被当作 modelId。
 */
adminChildren.push(
  {
    path: '/admin/models/:modelId/versions',
    name: 'model-versions',
    component: () => import('@/views/model/ModelVersionListView.vue'),
    meta: { title: '模型版本列表', requiresAuth: true, moduleCode: 'A06' },
  },
  {
    path: '/admin/models/:modelId/versions/:versionId',
    name: 'model-version-detail',
    component: () => import('@/views/model/ModelVersionDetailView.vue'),
    meta: { title: '模型版本详情', requiresAuth: true, moduleCode: 'A06' },
  },
  {
    path: '/admin/models/:modelId',
    name: 'model-detail',
    component: () => import('@/views/model/ModelDetailView.vue'),
    meta: { title: '模型详情', requiresAuth: true, moduleCode: 'A06' },
  },
)

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: ADMIN_HOME_PATH,
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: '登录' },
  },
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    redirect: ADMIN_HOME_PATH,
    meta: { requiresAuth: true },
    children: adminChildren,
  },
]

// 开发环境专用路由：P02 设计规范预览、P01 工程基线页。生产构建中不存在。
if (import.meta.env.DEV) {
  routes.push(
    {
      path: '/dev/design-system',
      name: 'design-system',
      component: () => import('@/views/DesignSystemView.vue'),
      meta: { title: '设计规范预览', devOnly: true },
    },
    {
      path: '/dev/project-baseline',
      name: 'project-baseline',
      component: () => import('@/views/HomeView.vue'),
      meta: { title: '工程基线', devOnly: true },
    },
  )
}

// 未匹配路径回到工作台，避免出现空白页或控制台报错。
routes.push({
  path: '/:pathMatch(.*)*',
  redirect: ADMIN_HOME_PATH,
})

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach((to) => {
  if (to.meta.devOnly && !import.meta.env.DEV) {
    return { path: ADMIN_HOME_PATH }
  }

  const sessionStore = useSessionStore(pinia)

  if (to.meta.requiresAuth && !sessionStore.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  // 已有模拟会话时，登录页不再重复展示。
  if (to.name === 'login' && sessionStore.isAuthenticated) {
    return { path: ADMIN_HOME_PATH }
  }

  return true
})

router.afterEach((to) => {
  const appTitle = import.meta.env.VITE_APP_TITLE ?? '银盾智鉴 · 管理后台'
  const routeTitle = typeof to.meta.title === 'string' ? to.meta.title : ''
  document.title = routeTitle ? `${routeTitle} · 银盾智鉴` : appTitle
})

export default router
