<script setup lang="ts">
/**
 * 页面标题区：统一标题、说明与右侧操作区。
 * 业务页面应使用本组件，避免各页面自建标题样式。
 */
interface Props {
  /** 页面主标题 */
  title: string
  /** 标题上方的小标签，用于标注所属模块 */
  eyebrow?: string
  /** 页面说明，建议一句话描述用途 */
  description?: string
  /** 是否显示标题区底部分割线 */
  divider?: boolean
}

withDefaults(defineProps<Props>(), {
  eyebrow: '',
  description: '',
  divider: true,
})
</script>

<template>
  <header class="ss-page-header" :class="{ 'is-divider': divider }">
    <div class="ss-page-header__main">
      <p v-if="eyebrow" class="ss-page-header__eyebrow">{{ eyebrow }}</p>
      <h1 class="ss-page-header__title">{{ title }}</h1>
      <p v-if="description" class="ss-page-header__desc">{{ description }}</p>
      <slot name="description" />
    </div>
    <div v-if="$slots.extra" class="ss-page-header__extra">
      <slot name="extra" />
    </div>
  </header>
</template>

<style scoped>
.ss-page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--ss-space-6);
  padding-bottom: var(--ss-space-4);
}

.ss-page-header.is-divider {
  border-bottom: 1px solid var(--ss-divider);
}

.ss-page-header__main {
  min-width: 0;
}

.ss-page-header__eyebrow {
  margin-bottom: var(--ss-space-1);
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
  letter-spacing: 0.08em;
}

.ss-page-header__title {
  font-size: var(--ss-font-size-h1);
  font-weight: var(--ss-font-weight-semibold);
  letter-spacing: 0.01em;
}

.ss-page-header__desc {
  margin-top: var(--ss-space-2);
  max-width: 72ch;
  color: var(--ss-text-secondary);
  font-size: var(--ss-font-size-body);
}

.ss-page-header__extra {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: var(--ss-space-2);
  flex-wrap: wrap;
  justify-content: flex-end;
}

@media (max-width: 960px) {
  .ss-page-header {
    flex-direction: column;
    gap: var(--ss-space-3);
  }

  .ss-page-header__extra {
    justify-content: flex-start;
  }
}
</style>
