<script setup lang="ts">
import { computed } from 'vue'

import { RELEASE_STATUS_LABELS } from '@/types/model'
import type { ModelReleaseStatus } from '@/types/model'

/**
 * 模型版本发布状态标签。
 */
interface Props {
  status: ModelReleaseStatus
  size?: 'large' | 'default' | 'small'
}

const props = withDefaults(defineProps<Props>(), { size: 'default' })

const typeMap: Record<ModelReleaseStatus, 'primary' | 'success' | 'warning' | 'danger' | 'info'> = {
  draft: 'info',
  pending_review: 'warning',
  approved: 'primary',
  rejected: 'danger',
  published: 'success',
  withdrawn: 'info',
  rolled_back: 'warning',
}

const type = computed(() => typeMap[props.status])
const label = computed(() => RELEASE_STATUS_LABELS[props.status])
</script>

<template>
  <el-tag :type="type" :size="size" effect="light" round>{{ label }}</el-tag>
</template>
