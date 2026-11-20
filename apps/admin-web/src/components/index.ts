/**
 * 通用组件统一出口。
 *
 * 业务页面统一从 '@/components' 导入，避免各处散落相对路径。
 */
export { default as PageHeader } from './PageHeader.vue'
export { default as FilterPanel } from './FilterPanel.vue'
export { default as DataTable } from './DataTable.vue'
export { default as DataTableEmpty } from './DataTableEmpty.vue'
export { default as Pagination } from './Pagination.vue'
export { default as StatusTag } from './StatusTag.vue'
export { default as StatCard } from './StatCard.vue'
export { default as DetailDrawer } from './DetailDrawer.vue'
export { default as ConfirmDialog } from './ConfirmDialog.vue'
export { default as LoadingState } from './LoadingState.vue'
export { default as ErrorState } from './ErrorState.vue'
