<script setup lang="ts">
import { Refresh } from '@element-plus/icons-vue'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { fetchDashboard } from '@/api/dashboard'
import { ApiError } from '@/api/http'
import type { DashboardData } from '@/api/models'
import { ErrorState, LoadingState, PageHeader, StatCard, StatusTag } from '@/components'
import { requireModule } from '@/router/modules'
import { useSessionStore } from '@/stores/session'

/**
 * A01 工作台。
 *
 * 所有统计与待办均来自本地 Mock Adapter，
 * 页面通过"演示"标识、说明文案与版本信息反复强调数据来源，
 * 不把演示数字描述为真实运营指标。
 */

const router = useRouter()
const sessionStore = useSessionStore()

const loading = ref(false)
const error = ref('')
const data = ref<DashboardData | null>(null)

const today = new Intl.DateTimeFormat('zh-CN', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
  weekday: 'long',
}).format(new Date())

async function load() {
  loading.value = true
  error.value = ''
  try {
    data.value = await fetchDashboard()
  } catch (cause) {
    data.value = null
    error.value =
      cause instanceof ApiError ? cause.message : '工作台数据加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

onMounted(load)

const statCards = computed(() => {
  const stats = data.value?.stats
  if (!stats) return []
  return [
    { label: '注册用户总数', value: stats.registeredTotal, tone: 'primary', hint: '老人与家属账户合计' },
    { label: '老人用户数量', value: stats.elderCount, tone: 'accent', hint: '被守护对象账户' },
    { label: '家属用户数量', value: stats.familyCount, tone: 'safe', hint: '协同处理者账户' },
    { label: '已绑定家庭关系', value: stats.boundFamilyCount, tone: 'safe', hint: '家属侧已完成绑定' },
    { label: '设备总数', value: stats.deviceTotal, tone: 'neutral', hint: '已登记的演示设备' },
    { label: '异常设备数量', value: stats.abnormalDeviceCount, tone: 'danger', hint: '含授权失效与版本过低' },
  ] as const
})

function goModule(code: string) {
  void router.push(requireModule(code).path)
}
</script>

<template>
  <div class="wb">
    <PageHeader
      eyebrow="A01 · 概览"
      title="工作台"
      description="汇总演示环境中的用户、设备、待处理事项与系统接入概况。"
    >
      <template #extra>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新数据</el-button>
      </template>
    </PageHeader>

    <section class="wb-hero">
      <div class="wb-hero__main">
        <p class="wb-hero__eyebrow">银盾智鉴管理后台</p>
        <h2 class="wb-hero__title">欢迎回来，{{ sessionStore.displayName }}</h2>
        <p class="wb-hero__meta">{{ sessionStore.roleLabel }} · 今天是 {{ today }}</p>
        <p class="wb-hero__note">
          本页统计与待办均来自本地 Mock Adapter，用于验证后台页面结构，
          不代表真实运营指标，也不包含任何真实用户数据。
        </p>
      </div>
      <div class="wb-hero__tags">
        <el-tag type="warning" effect="plain">演示数据模式</el-tag>
        <el-tag type="info" effect="plain">本地模拟登录</el-tag>
      </div>
    </section>

    <LoadingState v-if="loading" text="正在加载工作台演示数据" />

    <ErrorState
      v-else-if="error"
      :description="error"
      retry-text="重新加载"
      @retry="load"
    />

    <template v-else-if="data">
      <section class="wb-stats">
        <StatCard
          v-for="card in statCards"
          :key="card.label"
          :label="card.label"
          :value="card.value"
          :tone="card.tone"
          :hint="card.hint"
        />
      </section>

      <div class="wb-columns">
        <div class="wb-column">
          <section class="ss-section">
            <header class="ss-section__header">
              <span class="ss-section__title">待办任务</span>
              <span class="ss-section__hint">演示数据 · 后续接入真实审核流程</span>
            </header>
            <ul class="wb-todo-list">
              <li v-for="todo in data.todos" :key="todo.id">
                <button class="wb-todo" type="button" @click="goModule(todo.moduleCode)">
                  <span class="wb-todo__count">{{ todo.count }}</span>
                  <span class="wb-todo__body">
                    <span class="wb-todo__label">
                      {{ todo.label }}
                      <span class="wb-todo__demo">演示</span>
                    </span>
                    <span class="wb-todo__desc">{{ todo.description }}</span>
                  </span>
                  <span class="wb-todo__action">前往 {{ todo.moduleCode }}</span>
                </button>
              </li>
            </ul>
          </section>

          <section class="ss-section">
            <header class="ss-section__header">
              <span class="ss-section__title">系统模块状态</span>
              <span class="ss-section__hint">仅表示接入情况，不伪造运行状态</span>
            </header>
            <ul class="wb-module-list">
              <li v-for="item in data.modules" :key="item.name">
                <div class="wb-module__info">
                  <span class="wb-module__name">{{ item.name }}</span>
                  <span class="wb-module__note">{{ item.note }}</span>
                </div>
                <StatusTag :status="item.status" />
              </li>
            </ul>
          </section>
        </div>

        <div class="wb-column">
          <section class="ss-section">
            <header class="ss-section__header">
              <span class="ss-section__title">最近操作记录</span>
              <span class="ss-section__hint">演示数据</span>
            </header>
            <ul class="wb-log-list">
              <li v-for="item in data.operations" :key="item.logId" class="wb-log">
                <span class="wb-log__time">{{ item.time }}</span>
                <span class="wb-log__text">
                  <strong>{{ item.operator }}</strong> {{ item.action }}
                  <span class="wb-log__target">{{ item.target }}</span>
                </span>
                <StatusTag :status="item.result" />
              </li>
            </ul>
          </section>

          <section class="ss-section">
            <header class="ss-section__header">
              <span class="ss-section__title">最近任务</span>
              <span class="ss-section__hint">占位 · 未接入执行器</span>
            </header>
            <ul class="wb-task-list">
              <li v-for="task in data.tasks" :key="task.id">
                <div>
                  <p class="wb-task__name">{{ task.name }}</p>
                  <p class="wb-task__note">{{ task.note }}</p>
                </div>
                <el-tag type="info" effect="plain" size="small">占位</el-tag>
              </li>
            </ul>
          </section>

          <section class="ss-section">
            <header class="ss-section__header">
              <span class="ss-section__title">系统版本</span>
            </header>
            <dl class="wb-version">
              <div>
                <dt>版本</dt>
                <dd>{{ data.version }}</dd>
              </div>
              <div>
                <dt>数据来源</dt>
                <dd>Mock Adapter · 演示数据</dd>
              </div>
            </dl>
            <p class="wb-version__note">{{ data.buildNote }}</p>
          </section>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.wb {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-5);
}

/* ===== 欢迎区 ===== */
.wb-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--ss-space-6);
  padding: var(--ss-space-6);
  border: 1px solid var(--ss-border);
  border-radius: var(--ss-radius-lg);
  background: var(--ss-bg-card);
  box-shadow: var(--ss-shadow-sm);
}

.wb-hero__eyebrow {
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
  letter-spacing: 0.08em;
}

.wb-hero__title {
  margin-top: var(--ss-space-2);
  font-size: var(--ss-font-size-h2);
  font-weight: var(--ss-font-weight-semibold);
}

.wb-hero__meta {
  margin-top: var(--ss-space-2);
  color: var(--ss-text-secondary);
  font-size: var(--ss-font-size-sm);
}

.wb-hero__note {
  margin-top: var(--ss-space-3);
  max-width: 76ch;
  padding: var(--ss-space-3) var(--ss-space-4);
  border-left: 3px solid var(--ss-warning);
  border-radius: var(--ss-radius-xs);
  background: var(--ss-warning-bg);
  color: var(--ss-text-secondary);
  font-size: var(--ss-font-size-sm);
}

.wb-hero__tags {
  display: flex;
  flex-shrink: 0;
  gap: var(--ss-space-2);
}

/* ===== 统计卡片 ===== */
.wb-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--ss-space-4);
}

/* ===== 双列布局 ===== */
.wb-columns {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 1fr);
  gap: var(--ss-space-5);
  align-items: start;
}

.wb-column {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-5);
  min-width: 0;
}

/* ===== 待办 ===== */
.wb-todo-list,
.wb-module-list,
.wb-log-list,
.wb-task-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.wb-todo + .wb-todo {
  margin-top: var(--ss-space-2);
}

.wb-todo {
  display: flex;
  align-items: center;
  gap: var(--ss-space-4);
  width: 100%;
  padding: var(--ss-space-3) var(--ss-space-4);
  border: 1px solid var(--ss-divider);
  border-radius: var(--ss-radius-md);
  background: var(--ss-bg-subtle);
  color: inherit;
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.18s ease, background-color 0.18s ease;
}

.wb-todo:hover {
  border-color: var(--ss-border-strong);
  background: var(--ss-bg-hover);
}

.wb-todo__count {
  display: grid;
  place-items: center;
  min-width: 40px;
  height: 40px;
  border-radius: var(--ss-radius-md);
  background: var(--ss-primary-light);
  color: var(--ss-primary);
  font-size: var(--ss-font-size-body-lg);
  font-weight: var(--ss-font-weight-semibold);
  font-variant-numeric: tabular-nums;
}

.wb-todo__body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.wb-todo__label {
  display: inline-flex;
  align-items: center;
  gap: var(--ss-space-2);
  color: var(--ss-text-primary);
  font-size: var(--ss-font-size-body);
  font-weight: var(--ss-font-weight-medium);
}

.wb-todo__demo {
  padding: 0 5px;
  border: 1px solid var(--ss-border-strong);
  border-radius: var(--ss-radius-xs);
  color: var(--ss-text-tertiary);
  font-size: 11px;
  font-weight: var(--ss-font-weight-regular);
}

.wb-todo__desc {
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
}

.wb-todo__action {
  flex-shrink: 0;
  color: var(--ss-accent);
  font-size: var(--ss-font-size-xs);
}

/* ===== 模块状态 ===== */
.wb-module-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ss-space-4);
  padding: var(--ss-space-3) 0;
  border-bottom: 1px solid var(--ss-divider);
}

.wb-module-list li:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.wb-module__info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.wb-module__name {
  color: var(--ss-text-primary);
  font-size: var(--ss-font-size-body);
}

.wb-module__note {
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
}

/* ===== 最近操作 ===== */
.wb-log-list {
  display: flex;
  flex-direction: column;
}

.wb-log {
  display: flex;
  align-items: center;
  gap: var(--ss-space-3);
  padding: var(--ss-space-3) 0;
  border-bottom: 1px solid var(--ss-divider);
}

.wb-log:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.wb-log__time {
  flex-shrink: 0;
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
  font-variant-numeric: tabular-nums;
}

.wb-log__text {
  flex: 1;
  min-width: 0;
  color: var(--ss-text-secondary);
  font-size: var(--ss-font-size-sm);
}

.wb-log__text strong {
  color: var(--ss-text-primary);
  font-weight: var(--ss-font-weight-medium);
}

.wb-log__target {
  color: var(--ss-text-tertiary);
}

/* ===== 任务占位 ===== */
.wb-task-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ss-space-3);
  padding: var(--ss-space-3) 0;
  border-bottom: 1px solid var(--ss-divider);
}

.wb-task-list li:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.wb-task__name {
  color: var(--ss-text-primary);
  font-size: var(--ss-font-size-body);
}

.wb-task__note {
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
}

/* ===== 版本 ===== */
.wb-version {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ss-space-8);
  margin: 0;
}

.wb-version dt {
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
}

.wb-version dd {
  margin: 4px 0 0;
  color: var(--ss-text-primary);
  font-size: var(--ss-font-size-sm);
}

.wb-version__note {
  padding-top: var(--ss-space-3);
  border-top: 1px solid var(--ss-divider);
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
  line-height: var(--ss-line-height-base);
}

@media (max-width: 1180px) {
  .wb-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .wb-columns {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 720px) {
  .wb-hero {
    flex-direction: column;
  }

  .wb-stats {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
