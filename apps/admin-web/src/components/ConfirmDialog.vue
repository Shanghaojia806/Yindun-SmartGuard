<script setup lang="ts">
import { CircleCloseFilled, InfoFilled, WarningFilled } from '@element-plus/icons-vue'
import { computed } from 'vue'

/**
 * 通用确认弹窗：用于删除、撤销、停用等不可撤销或高风险操作。
 *
 * 使用方式：v-model 控制显隐，confirm 事件内执行业务请求，
 * 请求期间将 loading 置为 true 防止重复提交。
 */
type ConfirmType = 'danger' | 'warning' | 'primary'

interface Props {
  /** 显隐状态（v-model） */
  modelValue: boolean
  /** 弹窗标题 */
  title?: string
  /** 主提示文案 */
  message?: string
  /** 影响范围或后果说明 */
  description?: string
  /** 风险类型，决定图标与确认按钮配色 */
  type?: ConfirmType
  /** 确认按钮文案，建议使用明确动词 */
  confirmText?: string
  /** 取消按钮文案 */
  cancelText?: string
  /** 确认操作进行中 */
  loading?: boolean
  /** 弹窗宽度 */
  width?: string
}

const props = withDefaults(defineProps<Props>(), {
  title: '确认操作',
  message: '',
  description: '',
  type: 'warning',
  confirmText: '确认',
  cancelText: '取消',
  loading: false,
  width: '420px',
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'confirm'): void
  (e: 'cancel'): void
}>()

const iconMap = {
  danger: CircleCloseFilled,
  warning: WarningFilled,
  primary: InfoFilled,
} as const

const icon = computed(() => iconMap[props.type])

function close() {
  if (props.loading) return
  emit('update:modelValue', false)
  emit('cancel')
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :width="width"
    :close-on-click-modal="!loading"
    :close-on-press-escape="!loading"
    :show-close="!loading"
    align-center
    append-to-body
    class="ss-confirm-dialog"
    @update:model-value="(value: boolean) => emit('update:modelValue', value)"
    @close="close"
  >
    <template #header>
      <span class="ss-confirm-dialog__header">
        <el-icon class="ss-confirm-dialog__icon" :class="`is-${type}`" :size="20">
          <component :is="icon" />
        </el-icon>
        {{ title }}
      </span>
    </template>

    <p v-if="message" class="ss-confirm-dialog__message">{{ message }}</p>
    <p v-if="description" class="ss-confirm-dialog__desc">{{ description }}</p>
    <slot />

    <template #footer>
      <el-button :disabled="loading" @click="close">{{ cancelText }}</el-button>
      <el-button :type="type === 'primary' ? 'primary' : type" :loading="loading" @click="emit('confirm')">
        {{ loading ? '处理中' : confirmText }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.ss-confirm-dialog__header {
  display: inline-flex;
  align-items: center;
  gap: var(--ss-space-2);
  color: var(--ss-text-primary);
  font-size: var(--ss-font-size-body-lg);
  font-weight: var(--ss-font-weight-semibold);
}

.ss-confirm-dialog__icon.is-danger {
  color: var(--ss-danger);
}

.ss-confirm-dialog__icon.is-warning {
  color: var(--ss-warning);
}

.ss-confirm-dialog__icon.is-primary {
  color: var(--ss-accent);
}

.ss-confirm-dialog__message {
  color: var(--ss-text-primary);
  font-size: var(--ss-font-size-body);
}

.ss-confirm-dialog__desc {
  margin-top: var(--ss-space-2);
  padding: var(--ss-space-3) var(--ss-space-4);
  border-left: 3px solid var(--ss-border-strong);
  border-radius: var(--ss-radius-xs);
  background: var(--ss-bg-subtle);
  color: var(--ss-text-secondary);
  font-size: var(--ss-font-size-sm);
}
</style>
