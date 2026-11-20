<script setup lang="ts">
import { computed } from 'vue'

import { COMPATIBILITY_STATUS_LABELS } from '@/types/model'
import type { CompatibilityStatus } from '@/types/model'

/**
 * Android 运行时兼容性状态标签。
 */
interface Props {
  status: CompatibilityStatus
}

const props = defineProps<Props>()

const typeMap: Record<CompatibilityStatus, 'success' | 'warning' | 'danger' | 'info'> = {
  untested: 'info',
  testing: 'warning',
  compatible: 'success',
  partially_compatible: 'warning',
  incompatible: 'danger',
  test_failed: 'danger',
}

const type = computed(() => typeMap[props.status])
const label = computed(() => COMPATIBILITY_STATUS_LABELS[props.status])
</script>

<template>
  <el-tag :type="type" size="small" effect="light">{{ label }}</el-tag>
</template>
