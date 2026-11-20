<script setup lang="ts" generic="T extends object">
import DataTableEmpty from './DataTableEmpty.vue'
import ErrorState from './ErrorState.vue'
import LoadingState from './LoadingState.vue'

/**
 * 统一数据表格。
 *
 * 负责三态（Loading / Error / Empty）与表格外壳，
 * 业务页面只通过默认插槽声明列、通过 footer 插槽放分页，
 * 避免各页面重复编写加载与空态逻辑。
 */
interface Props {
  data: T[]
  loading?: boolean
  /** 错误文案；非空时展示错误态而不是表格 */
  error?: string
  rowKey?: string
  emptyTitle?: string
  emptyDescription?: string
  /** 加载态最小高度 */
  loadingMinHeight?: string
}

withDefaults(defineProps<Props>(), {
  loading: false,
  error: '',
  rowKey: '',
  emptyTitle: '暂无数据',
  emptyDescription: '当前没有可展示的记录，请调整筛选条件后重试。',
  loadingMinHeight: '240px',
})

defineOptions({ inheritAttrs: false })

const emit = defineEmits<{
  (e: 'retry'): void
  (e: 'row-click', row: T): void
}>()

function handleRowClick(row: T) {
  emit('row-click', row)
}
</script>

<template>
  <section class="ss-data-table">
    <LoadingState
      v-if="loading"
      text="正在加载数据"
      :min-height="loadingMinHeight"
    />

    <ErrorState v-else-if="error" :description="error" @retry="emit('retry')" />

    <template v-else>
      <el-table
        v-bind="$attrs"
        :data="data"
        :row-key="rowKey || undefined"
        class="ss-data-table__table"
        @row-click="handleRowClick"
      >
        <slot />
        <template #empty>
          <DataTableEmpty compact :title="emptyTitle" :description="emptyDescription">
            <template v-if="$slots['empty-actions']" #actions>
              <slot name="empty-actions" />
            </template>
          </DataTableEmpty>
        </template>
      </el-table>

      <div v-if="$slots.footer" class="ss-data-table__footer">
        <slot name="footer" />
      </div>
    </template>
  </section>
</template>

<style scoped>
.ss-data-table {
  border: 1px solid var(--ss-border);
  border-radius: var(--ss-radius-lg);
  background: var(--ss-bg-card);
  box-shadow: var(--ss-shadow-sm);
  overflow: hidden;
}

.ss-data-table__table {
  width: 100%;
}

.ss-data-table__footer {
  padding: var(--ss-space-3) var(--ss-space-4);
  border-top: 1px solid var(--ss-divider);
}

.ss-data-table :deep(.el-table__row) {
  cursor: pointer;
}
</style>
