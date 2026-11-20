<script setup lang="ts">
import { ArrowDown, Bell, Expand, Fold, Operation, SwitchButton } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { clearSimulatedFailure, IS_MOCK, simulateNextRequestFailure } from '@/api/request'
import { ADMIN_HOME_PATH, ADMIN_MODULE_GROUPS, ADMIN_MODULES } from '@/router/modules'
import { useSessionStore } from '@/stores/session'

/**
 * 管理后台统一布局（P03）。
 *
 * 由三部分组成：左侧一级菜单、顶部工具栏（面包屑 + 当前用户占位）、内容工作区。
 * 菜单项全部来自 router/modules.ts 的 ADMIN_MODULES，
 * 布局组件不判断具体菜单名称，只按分组渲染配置数据。
 */

const route = useRoute()
const router = useRouter()
const sessionStore = useSessionStore()

const collapsed = ref(false)

/** 当前选中菜单：子页面（如数据集工作台）仍高亮所属模块 */
const activePath = computed(() => {
  const matched = ADMIN_MODULES.find(
    (item) => route.path === item.path || route.path.startsWith(`${item.path}/`),
  )
  return matched?.path ?? route.path
})

/** 当前模块配置（用于面包屑），按路由 meta.moduleCode 匹配以支持子页面 */
const currentModule = computed(() => {
  const code = typeof route.meta.moduleCode === 'string' ? route.meta.moduleCode : ''
  return ADMIN_MODULES.find((item) => item.code === code)
})

/** 当前页面标题（子页面标题与模块标题不同时展示） */
const pageTitle = computed(() => (typeof route.meta.title === 'string' ? route.meta.title : ''))

const modulesByGroup = computed(() =>
  ADMIN_MODULE_GROUPS.map((group) => ({
    group,
    modules: ADMIN_MODULES.filter((item) => item.group === group),
  })),
)

const initials = computed(() => sessionStore.displayName.slice(0, 1) || '管')

function handleSelect(path: string) {
  if (path === route.path) return
  void router.push(path)
}

function handleCommand(command: string) {
  if (command !== 'logout') return
  sessionStore.signOut()
  ElMessage({ type: 'info', message: '已退出模拟登录会话' })
  void router.replace({ name: 'login' })
}

/** 消息中心占位：当前没有真实消息通道 */
function handleMessages() {
  ElMessage.info('消息中心将在后续轮次接入，当前没有真实消息')
}

/**
 * 演示控制：用于验证页面 Error 状态。
 * 该操作只影响本地 Mock 请求，不涉及任何真实接口。
 */
function handleDemoCommand(command: string) {
  if (command === 'fail') {
    simulateNextRequestFailure()
    ElMessage.warning('已设置：下一次数据请求将模拟失败（仅演示模式）')
    return
  }
  if (command === 'clear') {
    clearSimulatedFailure()
    ElMessage.info('已清除模拟失败设置')
  }
}
</script>

<template>
  <div class="admin-layout">
    <aside class="admin-sidebar" :class="{ 'is-collapsed': collapsed }">
      <router-link class="admin-brand" :to="ADMIN_HOME_PATH">
        <span class="admin-brand__mark" aria-hidden="true">盾</span>
        <span v-if="!collapsed" class="admin-brand__text">
          <strong>银盾智鉴</strong>
          <small>管理后台</small>
        </span>
      </router-link>

      <el-scrollbar class="admin-sidebar__scroll">
        <el-menu
          :default-active="activePath"
          :collapse="collapsed"
          :collapse-transition="false"
          class="admin-menu"
          @select="handleSelect"
        >
          <template v-for="section in modulesByGroup" :key="section.group">
            <li v-if="section.modules.length" class="admin-menu__group">
              <span v-if="!collapsed">{{ section.group }}</span>
              <span v-else class="admin-menu__group-divider" aria-hidden="true"></span>
            </li>
            <el-menu-item
              v-for="item in section.modules"
              :key="item.code"
              :index="item.path"
              :title="`${item.code} ${item.title}`"
            >
              <el-icon><component :is="item.icon" /></el-icon>
              <template #title>{{ item.title }}</template>
            </el-menu-item>
          </template>
        </el-menu>
      </el-scrollbar>

      <div v-if="!collapsed" class="admin-sidebar__footer">
        <p>P11 · 训练任务与结果管理</p>
        <p class="admin-sidebar__footer-note">演示数据 · 未接入真实数据服务</p>
      </div>
    </aside>

    <div class="admin-body">
      <header class="admin-header">
        <div class="admin-header__left">
          <el-button
            text
            :icon="collapsed ? Expand : Fold"
            :aria-label="collapsed ? '展开侧边菜单' : '收起侧边菜单'"
            @click="collapsed = !collapsed"
          />
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: ADMIN_HOME_PATH }">管理后台</el-breadcrumb-item>
            <el-breadcrumb-item v-if="currentModule">{{ currentModule.group }}</el-breadcrumb-item>
            <el-breadcrumb-item v-if="currentModule" :to="{ path: currentModule.path }">
              {{ currentModule.title }}
            </el-breadcrumb-item>
            <el-breadcrumb-item v-if="pageTitle && pageTitle !== currentModule?.title">
              {{ pageTitle }}
            </el-breadcrumb-item>
            <el-breadcrumb-item v-if="!currentModule">未知页面</el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="admin-header__right">
          <el-tag type="warning" effect="plain" size="small">本地模拟登录</el-tag>

          <el-dropdown v-if="IS_MOCK" trigger="click" @command="handleDemoCommand">
            <el-button text size="small" :icon="Operation">演示控制</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="fail">模拟下一次接口失败</el-dropdown-item>
                <el-dropdown-item command="clear">清除模拟失败</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>

          <el-tooltip content="消息中心将在后续轮次接入" placement="bottom">
            <el-badge is-dot type="warning" class="admin-header__badge">
              <el-button
                text
                circle
                :icon="Bell"
                aria-label="消息中心（占位）"
                @click="handleMessages"
              />
            </el-badge>
          </el-tooltip>

          <el-dropdown trigger="click" @command="handleCommand">
            <button class="admin-user" type="button">
              <el-avatar :size="30" class="admin-user__avatar">{{ initials }}</el-avatar>
              <span class="admin-user__meta">
                <span class="admin-user__name">{{ sessionStore.displayName }}</span>
                <span class="admin-user__role">{{ sessionStore.roleLabel }}</span>
              </span>
              <el-icon class="admin-user__arrow"><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>账号设置（尚未实现）</el-dropdown-item>
                <el-dropdown-item command="logout" divided :icon="SwitchButton">
                  退出登录（模拟）
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="admin-content">
        <div class="admin-content__inner">
          <router-view />
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped>
.admin-layout {
  display: flex;
  min-height: 100%;
  background: var(--ss-bg-page);
}

/* ===== 侧边导航 ===== */
.admin-sidebar {
  position: sticky;
  top: 0;
  display: flex;
  flex-direction: column;
  width: var(--ss-sidebar-width);
  height: 100vh;
  flex-shrink: 0;
  border-right: 1px solid var(--ss-border);
  background: var(--ss-bg-card);
  transition: width 0.18s ease;
}

.admin-sidebar.is-collapsed {
  width: 64px;
}

.admin-brand {
  display: flex;
  align-items: center;
  gap: var(--ss-space-3);
  height: var(--ss-header-height);
  padding: 0 var(--ss-space-4);
  border-bottom: 1px solid var(--ss-divider);
  color: var(--ss-text-primary);
}

.admin-brand:hover {
  color: var(--ss-text-primary);
}

.admin-brand__mark {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  border-radius: var(--ss-radius-md);
  background: var(--ss-primary);
  color: var(--ss-text-inverse);
  font-size: var(--ss-font-size-body);
  font-weight: var(--ss-font-weight-semibold);
}

.admin-brand__text {
  display: flex;
  flex-direction: column;
  min-width: 0;
  line-height: 1.25;
}

.admin-brand__text strong {
  font-size: var(--ss-font-size-body-lg);
  letter-spacing: 0.04em;
}

.admin-brand__text small {
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
}

.admin-sidebar__scroll {
  flex: 1;
}

.admin-menu {
  border-right: 0;
  padding: var(--ss-space-3) var(--ss-space-2);
  --el-menu-bg-color: transparent;
  --el-menu-text-color: var(--ss-text-secondary);
  --el-menu-hover-bg-color: var(--ss-bg-hover);
  --el-menu-hover-text-color: var(--ss-primary);
  --el-menu-active-color: var(--ss-primary);
  --el-menu-item-height: 42px;
}

.admin-menu:deep(.el-menu-item) {
  margin: var(--ss-space-1) 0;
  border-radius: var(--ss-radius-md);
  font-size: var(--ss-font-size-body);
}

.admin-menu:deep(.el-menu-item.is-active) {
  background: var(--ss-primary-light);
  font-weight: var(--ss-font-weight-semibold);
}

.admin-menu__group {
  margin: var(--ss-space-4) 0 var(--ss-space-1);
  padding: 0 var(--ss-space-3);
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
  letter-spacing: 0.08em;
  list-style: none;
}

.admin-menu__group-divider {
  display: block;
  width: 100%;
  height: 1px;
  background: var(--ss-divider);
}

.admin-sidebar__footer {
  padding: var(--ss-space-4);
  border-top: 1px solid var(--ss-divider);
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
  line-height: var(--ss-line-height-base);
}

.admin-sidebar__footer-note {
  color: var(--ss-warning);
}

/* ===== 右侧主体 ===== */
.admin-body {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}

.admin-header {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ss-space-4);
  height: var(--ss-header-height);
  padding: 0 var(--ss-space-6);
  border-bottom: 1px solid var(--ss-border);
  background: var(--ss-bg-card);
}

.admin-header__left {
  display: flex;
  align-items: center;
  gap: var(--ss-space-3);
  min-width: 0;
}

.admin-header__right {
  display: flex;
  align-items: center;
  gap: var(--ss-space-3);
}

.admin-header__badge {
  display: inline-flex;
  align-items: center;
}

.admin-user {
  display: flex;
  align-items: center;
  gap: var(--ss-space-2);
  padding: var(--ss-space-1) var(--ss-space-2);
  border: 0;
  border-radius: var(--ss-radius-md);
  background: transparent;
  cursor: pointer;
  color: var(--ss-text-primary);
  font-family: inherit;
  transition: background-color 0.18s ease;
}

.admin-user:hover {
  background: var(--ss-bg-hover);
}

.admin-user__avatar {
  background: var(--ss-primary-light);
  color: var(--ss-primary);
  font-size: var(--ss-font-size-sm);
  font-weight: var(--ss-font-weight-semibold);
}

.admin-user__meta {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  line-height: 1.3;
}

.admin-user__name {
  font-size: var(--ss-font-size-sm);
  font-weight: var(--ss-font-weight-medium);
}

.admin-user__role {
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
}

.admin-user__arrow {
  color: var(--ss-text-tertiary);
  font-size: 12px;
}

.admin-content {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.admin-content__inner {
  width: 100%;
  max-width: var(--ss-page-max-width);
  margin: 0 auto;
  padding: var(--ss-page-padding);
}

@media (max-width: 900px) {
  .admin-sidebar {
    width: 64px;
  }

  .admin-brand__text,
  .admin-sidebar__footer {
    display: none;
  }

  .admin-header {
    padding: 0 var(--ss-space-4);
  }

  .admin-user__meta {
    display: none;
  }
}
</style>
