<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { computed } from 'vue'

import { useAppStore } from '@/stores/app'

const appStore = useAppStore()
const { buildStage, fullTitle } = storeToRefs(appStore)

const stack = [
  { label: '框架', value: 'Vue 3 + TypeScript' },
  { label: '构建', value: 'Vite' },
  { label: '组件库', value: 'Element Plus' },
  { label: '状态管理', value: 'Pinia' },
  { label: '路由', value: 'Vue Router' },
  { label: '图表', value: 'ECharts（P11 启用）' },
]

const envLabel = computed(() => (import.meta.env.PROD ? '生产构建' : '开发模式'))

// 开发专用入口：设计规范预览页仅在开发环境可访问。
const isDev = import.meta.env.DEV
</script>

<template>
  <div class="startup-page">
    <header class="startup-header">
      <div class="brand">
        <div class="brand-mark" aria-hidden="true">盾</div>
        <div class="brand-text">
          <h1>{{ fullTitle }}</h1>
          <p>数据集 · 训练任务 · 模型版本与发布管理</p>
        </div>
      </div>
      <div class="header-actions">
        <router-link v-if="isDev" class="dev-entry" to="/dev/design-system">
          设计规范预览
        </router-link>
        <el-tag class="stage-tag" effect="dark" type="info">{{ buildStage }}</el-tag>
      </div>
    </header>

    <main class="startup-main">
      <el-card class="startup-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>工程基线</span>
            <el-tag size="small" effect="plain" type="success">{{ envLabel }}</el-tag>
          </div>
        </template>

        <el-descriptions :column="2" border>
          <el-descriptions-item v-for="item in stack" :key="item.label" :label="item.label">
            {{ item.value }}
          </el-descriptions-item>
        </el-descriptions>

        <el-alert
          class="startup-alert"
          type="info"
          :closable="false"
          show-icon
          title="本轮仅建立工程基线"
          description="业务页面、统计图表与接口数据将在后续阶段接入；当前页面不展示任何虚构运营指标。"
        />
      </el-card>

      <el-card class="startup-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>后续扩展位置</span>
          </div>
        </template>

        <ul class="startup-list">
          <li><code>src/views/</code> 管理端业务页面（A01–A10）</li>
          <li><code>src/api/</code> 统一请求层，接口契约见 <code>docs/api/CONTRACT_DRAFT.md</code></li>
          <li><code>src/router/</code> 路由与一级菜单</li>
          <li><code>src/stores/</code> Pinia 状态模块</li>
        </ul>
      </el-card>
    </main>
  </div>
</template>

<style scoped>
.startup-page {
  min-height: 100%;
  display: flex;
  flex-direction: column;
}

.startup-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 28px 40px;
  background: var(--ss-primary);
  color: var(--ss-text-inverse);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.dev-entry {
  padding: 6px 14px;
  border: 1px solid rgba(255, 255, 255, 0.4);
  border-radius: var(--ss-radius-pill);
  color: var(--ss-text-inverse);
  font-size: var(--ss-font-size-sm);
  transition: background-color 0.2s ease;
}

.dev-entry:hover {
  background: rgba(255, 255, 255, 0.14);
  color: var(--ss-text-inverse);
}

.brand {
  display: flex;
  align-items: center;
  gap: 16px;
}

.brand-mark {
  width: 52px;
  height: 52px;
  border-radius: var(--ss-radius-lg);
  display: grid;
  place-items: center;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.32);
  font-size: var(--ss-font-size-h2);
  font-weight: var(--ss-font-weight-semibold);
  letter-spacing: 2px;
}

.brand-text h1 {
  margin: 0;
  font-size: var(--ss-font-size-h2);
  font-weight: var(--ss-font-weight-semibold);
  letter-spacing: 1px;
}

.brand-text p {
  margin: var(--ss-space-1) 0 0;
  font-size: var(--ss-font-size-sm);
  color: rgba(255, 255, 255, 0.78);
}

.stage-tag {
  background: rgba(255, 255, 255, 0.18);
  border-color: rgba(255, 255, 255, 0.4);
  color: var(--ss-text-inverse);
}

.startup-main {
  flex: 1;
  width: 100%;
  max-width: 1080px;
  margin: 0 auto;
  padding: var(--ss-space-8) var(--ss-space-6) 56px;
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-5);
}

.startup-card {
  border-radius: var(--ss-radius-lg);
  border-color: var(--ss-border);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  color: var(--ss-primary);
}

.startup-alert {
  margin-top: 20px;
}

.startup-list {
  margin: 0;
  padding-left: 20px;
  color: var(--ss-text-secondary);
}

.startup-list li {
  margin: 6px 0;
}

@media (max-width: 720px) {
  .startup-header {
    flex-direction: column;
    align-items: flex-start;
    padding: 24px;
  }

  .startup-main {
    padding: 20px 16px 40px;
  }
}
</style>
