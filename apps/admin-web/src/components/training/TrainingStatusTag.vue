<script setup lang="ts">
import { computed } from 'vue'
import { StatusTag } from '@/components'
import type { TrainingTaskStatus } from '@/types/training'

/**
 * 训练任务状态标签。
 *
 * RUNNING 且已提交取消请求时展示「正在取消」，避免在 Worker 确认前宣称任务已停止。
 */
interface Props {
  status: TrainingTaskStatus
  cancelRequested?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  cancelRequested: false,
})

const LABELS: Record<TrainingTaskStatus, string> = {
  pending: '等待执行',
  running: '运行中',
  succeeded: '训练成功',
  failed: '训练失败',
  canceled: '已取消',
}

const displayStatus = computed(() =>
  props.status === 'running' && props.cancelRequested ? 'canceling' : props.status,
)

const displayLabel = computed(() => {
  if (props.status === 'running' && props.cancelRequested) return '正在取消'
  return LABELS[props.status]
})
</script>

<template>
  <StatusTag :status="displayStatus" :label="displayLabel" />
</template>
