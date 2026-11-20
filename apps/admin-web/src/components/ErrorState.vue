<script setup lang="ts">
import { Connection, Lock, WarningFilled } from '@element-plus/icons-vue'
import { computed } from 'vue'

/**
 * 错误状态：覆盖加载失败、网络异常、权限不足、数据不存在。
 *
 * 供后续业务页统一复用，文案区分原因并给出下一步动作；
 * 权限不足场景不提供重试按钮，避免无效操作。
 */
type ErrorKind = 'load' | 'network' | 'permission' | 'notFound'

interface Props {
  /** 错误类型 */
  kind?: ErrorKind
  /** 标题，留空时按 kind 使用默认文案 */
  title?: string
  /** 原因说明 / 处理建议 */
  description?: string
  /** 重试按钮文案 */
  retryText?: string
}

const props = withDefaults(defineProps<Props>(), {
  kind: 'load',
  title: '',
  description: '',
  retryText: '重新加载',
})

const emit = defineEmits<{
  (e: 'retry'): void
}>()

const preset = {
  load: {
    icon: WarningFilled,
    title: '数据加载失败',
    description: '服务暂时无法返回数据，请稍后重试。',
  },
  network: {
    icon: Connection,
    title: '网络连接异常',
    description: '请检查网络连接后重试。',
  },
  permission: {
    icon: Lock,
    title: '权限不足',
    description: '当前账号无权查看该内容，请联系管理员分配权限。',
  },
  notFound: {
    icon: WarningFilled,
    title: '未找到相关数据',
    description: '该记录可能已被删除，请返回列表重新选择。',
  },
} as const

const current = computed(() => preset[props.kind])
const displayTitle = computed(() => props.title || current.value.title)
const displayDescription = computed(() => props.description || current.value.description)
</script>

<template>
  <div class="ss-error-state" role="alert">
    <div class="ss-error-state__icon" :class="`is-${kind}`" aria-hidden="true">
      <el-icon :size="32">
        <component :is="current.icon" />
      </el-icon>
    </div>
    <p class="ss-error-state__title">{{ displayTitle }}</p>
    <p class="ss-error-state__desc">{{ displayDescription }}</p>
    <div class="ss-error-state__actions">
      <el-button v-if="kind !== 'permission'" type="primary" plain @click="emit('retry')">
        {{ retryText }}
      </el-button>
      <slot name="actions" />
    </div>
  </div>
</template>

<style scoped>
.ss-error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--ss-space-2);
  padding: var(--ss-space-10) var(--ss-space-6);
  text-align: center;
}

.ss-error-state__icon {
  display: grid;
  place-items: center;
  width: 64px;
  height: 64px;
  margin-bottom: var(--ss-space-1);
  border-radius: 50%;
  background: var(--ss-danger-bg);
  color: var(--ss-danger);
}

.ss-error-state__icon.is-permission {
  background: var(--ss-warning-bg);
  color: var(--ss-warning);
}

.ss-error-state__icon.is-network {
  background: var(--ss-neutral-bg);
  color: var(--ss-neutral);
}

.ss-error-state__title {
  color: var(--ss-text-primary);
  font-size: var(--ss-font-size-body-lg);
  font-weight: var(--ss-font-weight-medium);
}

.ss-error-state__desc {
  max-width: 52ch;
  color: var(--ss-text-secondary);
  font-size: var(--ss-font-size-sm);
}

.ss-error-state__actions {
  display: flex;
  gap: var(--ss-space-2);
  margin-top: var(--ss-space-3);
}
</style>
