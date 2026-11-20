<script setup lang="ts">
import type { RiskLabel } from '@/types/dataset'

/**
 * 多标签选择器。
 *
 * 标签集来自服务端（Mock 阶段为 DATASET_LABELS），
 * 页面只维护选中的标签名称数组，便于后续扩展标签体系。
 */
interface Props {
  modelValue: string[]
  labels: RiskLabel[]
  disabled?: boolean
}

const props = withDefaults(defineProps<Props>(), { disabled: false })

const emit = defineEmits<{
  (e: 'update:modelValue', value: string[]): void
}>()

function update(value: Array<string | number | boolean>) {
  emit(
    'update:modelValue',
    value.map((item) => String(item)),
  )
}

function groupLabel(group: RiskLabel['group']): string {
  if (group === 'normal') return '正常'
  if (group === 'unknown') return '信息不足'
  return '风险'
}
</script>

<template>
  <el-checkbox-group
    :model-value="props.modelValue"
    :disabled="props.disabled"
    class="label-picker"
    @update:model-value="update"
  >
    <el-checkbox
      v-for="label in props.labels"
      :key="label.labelId"
      :value="label.labelName"
      class="label-picker__item"
      :class="[`is-${label.group}`, { 'is-checked': props.modelValue.includes(label.labelName) }]"
    >
      <span class="label-picker__name">{{ label.labelName }}</span>
      <span class="label-picker__group">{{ groupLabel(label.group) }}</span>
    </el-checkbox>
  </el-checkbox-group>
</template>

<style scoped>
.label-picker {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ss-space-2);
}

.label-picker__item {
  margin: 0;
  padding: 7px 10px;
  border: 1px solid var(--ss-border);
  border-radius: var(--ss-radius-md);
  background: var(--ss-bg-card);
  transition: border-color 0.15s ease, background-color 0.15s ease;
}

.label-picker__item:hover {
  border-color: var(--ss-border-strong);
}

.label-picker__item.is-checked {
  border-color: var(--ss-accent);
  background: var(--ss-accent-light);
}

.label-picker__item.is-checked.is-normal {
  border-color: var(--ss-safe);
  background: var(--ss-safe-bg);
}

.label-picker__item.is-checked.is-unknown {
  border-color: var(--ss-warning);
  background: var(--ss-warning-bg);
}

.label-picker__item :deep(.el-checkbox__input) {
  display: none;
}

.label-picker__item :deep(.el-checkbox__label) {
  display: inline-flex;
  align-items: center;
  gap: var(--ss-space-2);
  padding: 0;
  color: var(--ss-text-primary);
  font-size: var(--ss-font-size-sm);
}

.label-picker__group {
  padding: 0 5px;
  border-radius: var(--ss-radius-xs);
  background: var(--ss-bg-subtle);
  color: var(--ss-text-tertiary);
  font-size: 11px;
}
</style>
