<script setup lang="ts">
import { Loading } from '@element-plus/icons-vue'

/**
 * 加载状态：提供明确的加载反馈。
 *
 * 两种形态：
 * - block（默认）：占位区块，用于页面或卡片级加载
 * - inline：行内小尺寸，用于列表局部刷新
 */
interface Props {
  /** 加载提示文案 */
  text?: string
  /** 形态 */
  variant?: 'block' | 'inline'
  /** 区块最小高度 */
  minHeight?: string
}

withDefaults(defineProps<Props>(), {
  text: '正在加载数据',
  variant: 'block',
  minHeight: '200px',
})
</script>

<template>
  <div
    v-if="variant === 'block'"
    class="ss-loading"
    :style="{ minHeight }"
    role="status"
    aria-live="polite"
  >
    <el-icon class="ss-loading__spinner" :size="28"><Loading /></el-icon>
    <p class="ss-loading__text">{{ text }}</p>
    <slot />
  </div>

  <span v-else class="ss-loading-inline" role="status" aria-live="polite">
    <el-icon class="ss-loading__spinner" :size="14"><Loading /></el-icon>
    <span>{{ text }}</span>
  </span>
</template>

<style scoped>
.ss-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--ss-space-3);
  padding: var(--ss-space-8);
  color: var(--ss-text-secondary);
}

.ss-loading__spinner {
  color: var(--ss-accent);
  animation: ss-spin 1s linear infinite;
}

.ss-loading__text {
  font-size: var(--ss-font-size-sm);
}

.ss-loading-inline {
  display: inline-flex;
  align-items: center;
  gap: var(--ss-space-2);
  color: var(--ss-text-secondary);
  font-size: var(--ss-font-size-sm);
}

@keyframes ss-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .ss-loading__spinner {
    animation-duration: 3s;
  }
}
</style>
