<script setup lang="ts">
import ErrorState from './ErrorState.vue'
import LoadingState from './LoadingState.vue'

/**
 * 统一详情抽屉。
 *
 * 提供标题 / 副标题 / 加载态 / 错误态 / 底部操作区，
 * 业务页面只负责内容与底部按钮。
 */
interface Props {
  modelValue: boolean
  title: string
  subtitle?: string
  /** 抽屉宽度 */
  size?: string
  loading?: boolean
  error?: string
}

const props = withDefaults(defineProps<Props>(), {
  subtitle: '',
  size: '520px',
  loading: false,
  error: '',
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'retry'): void
}>()

function handleClose() {
  emit('update:modelValue', false)
}
</script>

<template>
  <el-drawer
    :model-value="props.modelValue"
    :size="props.size"
    append-to-body
    destroy-on-close
    class="ss-detail-drawer"
    @update:model-value="(value: boolean) => emit('update:modelValue', value)"
    @close="handleClose"
  >
    <template #header>
      <div class="ss-detail-drawer__header">
        <h3 class="ss-detail-drawer__title">{{ props.title }}</h3>
        <p v-if="props.subtitle" class="ss-detail-drawer__subtitle">{{ props.subtitle }}</p>
      </div>
    </template>

    <LoadingState v-if="props.loading" text="正在加载详情" min-height="240px" />

    <ErrorState v-else-if="props.error" :description="props.error" @retry="emit('retry')" />

    <div v-else class="ss-detail-drawer__body">
      <slot />
    </div>

    <template v-if="$slots.footer && !props.loading && !props.error" #footer>
      <div class="ss-detail-drawer__footer">
        <slot name="footer" />
      </div>
    </template>
  </el-drawer>
</template>

<style scoped>
.ss-detail-drawer__header {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-1);
}

.ss-detail-drawer__title {
  color: var(--ss-text-primary);
  font-size: var(--ss-font-size-body-lg);
  font-weight: var(--ss-font-weight-semibold);
}

.ss-detail-drawer__subtitle {
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
}

.ss-detail-drawer__body {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-5);
}

.ss-detail-drawer__footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--ss-space-2);
}
</style>
