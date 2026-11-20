<script setup lang="ts">
import { computed } from 'vue'

import { VERIFICATION_STATUS_LABELS } from '@/types/model'
import type { VerificationStatus } from '@/types/model'

/**
 * 文件哈希 / 数字签名验证状态标签。
 *
 * 演示记录不显示“已验证”，避免把演示值当成真实校验结果。
 */
interface Props {
  status: VerificationStatus
}

const props = defineProps<Props>()

const typeMap: Record<VerificationStatus, 'success' | 'warning' | 'danger' | 'info'> = {
  verified: 'success',
  not_verified: 'warning',
  failed: 'danger',
  demo_record: 'info',
  not_available: 'info',
}

const type = computed(() => typeMap[props.status])
const label = computed(() => VERIFICATION_STATUS_LABELS[props.status])
</script>

<template>
  <el-tag :type="type" size="small" effect="plain">{{ label }}</el-tag>
</template>
