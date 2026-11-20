<script setup lang="ts">
import { DocumentRemove } from '@element-plus/icons-vue'
import type { Component } from 'vue'

/**
 * 数据表格空状态：用于 el-table 的 #empty 插槽或独立空态区块。
 *
 * 提供可选操作入口（actions 插槽），例如「新建数据集」；
 * 不放置与当前场景无关的装饰性按钮。
 */
interface Props {
  /** 主文案 */
  title?: string
  /** 补充说明，建议说明为什么为空、下一步做什么 */
  description?: string
  /** 图标组件，默认使用统一的文档图标 */
  icon?: Component
  /** 紧凑模式，用于表格内嵌空态 */
  compact?: boolean
}

withDefaults(defineProps<Props>(), {
  title: '暂无数据',
  description: '当前没有可展示的记录，请调整筛选条件后重试。',
  icon: () => DocumentRemove,
  compact: false,
})
</script>

<template>
  <div class="ss-table-empty" :class="{ 'is-compact': compact }">
    <div class="ss-table-empty__icon" aria-hidden="true">
      <el-icon :size="compact ? 28 : 36">
        <component :is="icon" />
      </el-icon>
    </div>
    <p class="ss-table-empty__title">{{ title }}</p>
    <p v-if="description" class="ss-table-empty__desc">{{ description }}</p>
    <div v-if="$slots.actions" class="ss-table-empty__actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<style scoped>
.ss-table-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--ss-space-2);
  padding: var(--ss-space-10) var(--ss-space-6);
  text-align: center;
}

.ss-table-empty.is-compact {
  padding: var(--ss-space-6) var(--ss-space-4);
}

.ss-table-empty__icon {
  display: grid;
  place-items: center;
  width: 64px;
  height: 64px;
  margin-bottom: var(--ss-space-1);
  border-radius: var(--ss-radius-xl);
  background: var(--ss-bg-subtle);
  color: var(--ss-text-tertiary);
}

.ss-table-empty.is-compact .ss-table-empty__icon {
  width: 48px;
  height: 48px;
  border-radius: var(--ss-radius-lg);
}

.ss-table-empty__title {
  color: var(--ss-text-primary);
  font-size: var(--ss-font-size-body-lg);
  font-weight: var(--ss-font-weight-medium);
}

.ss-table-empty__desc {
  max-width: 46ch;
  color: var(--ss-text-secondary);
  font-size: var(--ss-font-size-sm);
}

.ss-table-empty__actions {
  margin-top: var(--ss-space-2);
}
</style>
