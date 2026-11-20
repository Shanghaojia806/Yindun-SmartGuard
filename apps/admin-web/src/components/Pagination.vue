<script setup lang="ts">
/**
 * 统一分页组件。
 *
 * 使用 v-model:page / v-model:pageSize 双向绑定，
 * 页码或每页条数变化时触发 change，页面据此重新取数。
 */
interface Props {
  page: number
  pageSize: number
  total: number
  pageSizes?: number[]
}

const props = withDefaults(defineProps<Props>(), {
  pageSizes: () => [10, 20, 50],
})

const emit = defineEmits<{
  (e: 'update:page', value: number): void
  (e: 'update:pageSize', value: number): void
  (e: 'change'): void
}>()

function handlePageChange(value: number) {
  emit('update:page', value)
  emit('change')
}

function handleSizeChange(value: number) {
  emit('update:pageSize', value)
  emit('update:page', 1)
  emit('change')
}
</script>

<template>
  <div class="ss-pagination">
    <span class="ss-pagination__total">共 {{ props.total }} 条记录</span>
    <el-pagination
      background
      :current-page="props.page"
      :page-size="props.pageSize"
      :page-sizes="props.pageSizes"
      :total="props.total"
      layout="sizes, prev, pager, next, jumper"
      @current-change="handlePageChange"
      @size-change="handleSizeChange"
    />
  </div>
</template>

<style scoped>
.ss-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ss-space-4);
  flex-wrap: wrap;
}

.ss-pagination__total {
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-sm);
}

@media (max-width: 720px) {
  .ss-pagination {
    justify-content: center;
  }
}
</style>
