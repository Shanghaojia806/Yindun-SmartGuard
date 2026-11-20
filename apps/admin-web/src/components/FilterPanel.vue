<script setup lang="ts">
/**
 * 筛选面板：统一搜索 / 筛选区域。
 *
 * 默认插槽放置筛选项，actions 插槽放置「查询 / 重置」等操作；
 * 未提供 actions 插槽且 showActions 为真时，渲染内置查询与重置按钮。
 * 折叠仅在窄屏生效，避免把常用筛选能力隐藏起来。
 */
interface Props {
  /** 面板标题，留空则不显示标题行 */
  title?: string
  /** 查询按钮是否处于加载状态 */
  loading?: boolean
  /** 是否渲染操作按钮区 */
  showActions?: boolean
  /** 查询按钮文案 */
  searchText?: string
  /** 重置按钮文案 */
  resetText?: string
}

const props = withDefaults(defineProps<Props>(), {
  title: '筛选条件',
  loading: false,
  showActions: true,
  searchText: '查询',
  resetText: '重置',
})

const emit = defineEmits<{
  (e: 'search'): void
  (e: 'reset'): void
}>()
</script>

<template>
  <section class="ss-filter-panel">
    <div v-if="props.title" class="ss-filter-panel__header">
      <span class="ss-filter-panel__title">{{ props.title }}</span>
      <slot name="header-extra" />
    </div>

    <div class="ss-filter-panel__body">
      <slot />
    </div>

    <div v-if="props.showActions || $slots.actions" class="ss-filter-panel__actions">
      <slot name="actions">
        <el-button :disabled="props.loading" @click="emit('reset')">
          {{ props.resetText }}
        </el-button>
        <el-button type="primary" :loading="props.loading" @click="emit('search')">
          {{ props.loading ? '查询中' : props.searchText }}
        </el-button>
      </slot>
    </div>
  </section>
</template>

<style scoped>
.ss-filter-panel {
  padding: var(--ss-space-5);
  border: 1px solid var(--ss-border);
  border-radius: var(--ss-radius-lg);
  background: var(--ss-bg-card);
  box-shadow: var(--ss-shadow-sm);
}

.ss-filter-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ss-space-3);
  margin-bottom: var(--ss-space-4);
}

.ss-filter-panel__title {
  color: var(--ss-text-primary);
  font-size: var(--ss-font-size-body);
  font-weight: var(--ss-font-weight-semibold);
}

.ss-filter-panel__body {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: var(--ss-space-3) var(--ss-space-4);
}

.ss-filter-panel__body :deep(> *) {
  min-width: 200px;
}

.ss-filter-panel__actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--ss-space-2);
  margin-top: var(--ss-space-4);
  padding-top: var(--ss-space-4);
  border-top: 1px solid var(--ss-divider);
}

@media (max-width: 720px) {
  .ss-filter-panel__body :deep(> *) {
    min-width: 100%;
  }

  .ss-filter-panel__actions {
    justify-content: stretch;
  }
}
</style>
