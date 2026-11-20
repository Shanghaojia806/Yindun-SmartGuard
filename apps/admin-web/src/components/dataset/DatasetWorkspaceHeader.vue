<script setup lang="ts">
import { computed } from 'vue'

import { StatusTag } from '@/components'
import type { DatasetSummary, DatasetType } from '@/types/dataset'

/**
 * 数据集工作台头部：数据集基本信息 + 子工作台导航。
 * 供标注、质检、去重、拆分、版本页面复用。
 */
type WorkspaceKey = 'annotation' | 'quality' | 'dedupe' | 'split' | 'versions'

interface Props {
  dataset: DatasetSummary
  active: WorkspaceKey
}

const props = defineProps<Props>()

const TYPE_LABELS: Record<DatasetType, string> = {
  screen: '屏幕对话',
  video: '视频',
  audio: '音频',
  text: '文本',
  multimodal: '多模态混合',
}

const NAV_ITEMS: Array<{ key: WorkspaceKey; label: string; segment: string }> = [
  { key: 'annotation', label: '标注工作台', segment: 'annotation' },
  { key: 'quality', label: '数据质检', segment: 'quality' },
  { key: 'dedupe', label: '数据去重', segment: 'dedupe' },
  { key: 'split', label: '数据拆分', segment: 'split' },
  { key: 'versions', label: '版本管理', segment: 'versions' },
]

const navItems = computed(() =>
  NAV_ITEMS.map((item) => ({
    ...item,
    path: `/admin/datasets/${props.dataset.datasetId}/${item.segment}`,
  })),
)
</script>

<template>
  <section class="ws-header">
    <div class="ws-header__top">
      <div class="ws-header__info">
        <router-link class="ws-header__back" to="/admin/datasets">← 返回数据集列表</router-link>
        <div class="ws-header__title-row">
          <h2 class="ws-header__title">{{ dataset.datasetName }}</h2>
          <StatusTag :status="dataset.datasetStatus" />
          <el-tag type="warning" effect="plain" size="small">演示数据</el-tag>
        </div>
        <p class="ws-header__desc">{{ dataset.description || '暂无描述' }}</p>
        <div class="ws-header__meta">
          <span>{{ dataset.datasetId }}</span>
          <span>{{ TYPE_LABELS[dataset.datasetType] }}</span>
          <span>当前版本 {{ dataset.datasetVersion }}</span>
          <span>样本 {{ dataset.totalSamples }} 条</span>
          <span>标注完成率 {{ dataset.annotationRate }}%</span>
          <span>可训练率 {{ dataset.eligibilityRate }}%</span>
        </div>
      </div>
    </div>

    <nav class="ws-nav">
      <router-link
        v-for="item in navItems"
        :key="item.key"
        :to="item.path"
        class="ws-nav__item"
        :class="{ 'is-active': active === item.key }"
      >
        {{ item.label }}
      </router-link>
    </nav>
  </section>
</template>

<style scoped>
.ws-header {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-4);
  padding: var(--ss-space-5);
  border: 1px solid var(--ss-border);
  border-radius: var(--ss-radius-lg);
  background: var(--ss-bg-card);
  box-shadow: var(--ss-shadow-sm);
}

.ws-header__back {
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
}

.ws-header__back:hover {
  color: var(--ss-accent);
}

.ws-header__title-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--ss-space-2);
  margin-top: var(--ss-space-2);
}

.ws-header__title {
  font-size: var(--ss-font-size-h3);
  font-weight: var(--ss-font-weight-semibold);
}

.ws-header__desc {
  margin-top: var(--ss-space-2);
  color: var(--ss-text-secondary);
  font-size: var(--ss-font-size-sm);
}

.ws-header__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ss-space-4);
  margin-top: var(--ss-space-3);
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
  font-variant-numeric: tabular-nums;
}

.ws-nav {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ss-space-1);
  padding: var(--ss-space-1);
  border: 1px solid var(--ss-divider);
  border-radius: var(--ss-radius-md);
  background: var(--ss-bg-subtle);
}

.ws-nav__item {
  padding: 6px 14px;
  border-radius: var(--ss-radius-sm);
  color: var(--ss-text-secondary);
  font-size: var(--ss-font-size-sm);
}

.ws-nav__item:hover {
  color: var(--ss-primary);
}

.ws-nav__item.is-active {
  background: var(--ss-bg-card);
  color: var(--ss-primary);
  font-weight: var(--ss-font-weight-semibold);
  box-shadow: var(--ss-shadow-sm);
}
</style>
