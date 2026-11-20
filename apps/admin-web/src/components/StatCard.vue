<script setup lang="ts">
import { computed } from 'vue'

/**
 * 工作台统计卡片。
 *
 * demo 为 true 时右上角固定展示"演示"标识，
 * 避免 Mock 统计数字被误认为真实运营指标。
 */
type Tone = 'primary' | 'accent' | 'safe' | 'warning' | 'danger' | 'neutral'

interface Props {
  label: string
  value: number | string
  /** 辅助说明，建议说明口径 */
  hint?: string
  tone?: Tone
  /** 是否为演示数据 */
  demo?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  hint: '',
  tone: 'primary',
  demo: true,
})

const displayValue = computed(() =>
  typeof props.value === 'number' ? props.value.toLocaleString('zh-CN') : props.value,
)
</script>

<template>
  <article class="ss-stat-card" :class="`is-${tone}`">
    <header class="ss-stat-card__header">
      <span class="ss-stat-card__label">{{ label }}</span>
      <span v-if="demo" class="ss-stat-card__demo">演示</span>
    </header>
    <p class="ss-stat-card__value">{{ displayValue }}</p>
    <p v-if="hint" class="ss-stat-card__hint">{{ hint }}</p>
  </article>
</template>

<style scoped>
.ss-stat-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-2);
  padding: var(--ss-space-5);
  border: 1px solid var(--ss-border);
  border-radius: var(--ss-radius-lg);
  background: var(--ss-bg-card);
  box-shadow: var(--ss-shadow-sm);
  overflow: hidden;
}

.ss-stat-card::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 3px;
  background: var(--ss-card-tone, var(--ss-primary));
}

.ss-stat-card.is-primary {
  --ss-card-tone: var(--ss-primary);
}

.ss-stat-card.is-accent {
  --ss-card-tone: var(--ss-accent);
}

.ss-stat-card.is-safe {
  --ss-card-tone: var(--ss-safe);
}

.ss-stat-card.is-warning {
  --ss-card-tone: var(--ss-warning);
}

.ss-stat-card.is-danger {
  --ss-card-tone: var(--ss-danger);
}

.ss-stat-card.is-neutral {
  --ss-card-tone: var(--ss-neutral);
}

.ss-stat-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ss-space-2);
}

.ss-stat-card__label {
  color: var(--ss-text-secondary);
  font-size: var(--ss-font-size-sm);
}

.ss-stat-card__demo {
  flex-shrink: 0;
  padding: 1px 6px;
  border: 1px solid var(--ss-border-strong);
  border-radius: var(--ss-radius-xs);
  color: var(--ss-text-tertiary);
  font-size: 11px;
  letter-spacing: 0.04em;
}

.ss-stat-card__value {
  color: var(--ss-text-primary);
  font-size: 30px;
  font-weight: var(--ss-font-weight-semibold);
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}

.ss-stat-card__hint {
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
}
</style>
