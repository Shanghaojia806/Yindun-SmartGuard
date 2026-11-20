<script setup lang="ts">
import { computed } from 'vue'

import type {
  AccountStatus,
  AuditResult,
  BindingStatus,
  CompatibilityStatus,
  DeviceStatus,
  ModuleHealth,
} from '@/api/models'

/**
 * 统一状态标签。
 *
 * 业务页面不直接写 el-tag 的文案与颜色映射，
 * 新增状态时只需维护本文件的 PRESET。
 */
type StatusValue =
  | AccountStatus
  | BindingStatus
  | DeviceStatus
  | CompatibilityStatus
  | AuditResult
  | ModuleHealth
  | 'draft'
  | 'importing'
  | 'annotating'
  | 'reviewing'
  | 'quality_blocked'
  | 'ready'
  | 'archived'
  | 'unannotated'
  | 'in_progress'
  | 'submitted'
  | 'completed'
  | 'approved'
  | 'rejected'
  | 'needs_fix'
  | 'unique'
  | 'suspected'
  | 'confirmed_duplicate'
  | 'excluded'
  | 'verified'
  | 'missing'
  | 'passed'
  | 'failed'
  | 'manual_review'
  | 'published'
  | 'blocked'
  | 'unchecked'
  | 'succeeded'
  | 'canceled'
  | 'canceling'

type TagType = 'success' | 'warning' | 'danger' | 'info' | 'primary'

interface StatusPreset {
  label: string
  type: TagType
}

interface Props {
  /** 状态枚举值 */
  status: StatusValue
  /** 覆盖默认文案 */
  label?: string
  size?: 'small' | 'default' | 'large'
  effect?: 'light' | 'plain' | 'dark'
}

const props = withDefaults(defineProps<Props>(), {
  label: '',
  size: 'small',
  effect: 'light',
})

const PRESET: Record<StatusValue, StatusPreset> = {
  active: { label: '正常', type: 'success' },
  disabled: { label: '已禁用', type: 'danger' },
  bound: { label: '已绑定', type: 'success' },
  unbound: { label: '未绑定', type: 'info' },
  pending: { label: '待确认', type: 'warning' },
  normal: { label: '正常', type: 'success' },
  abnormal: { label: '异常', type: 'danger' },
  compatible: { label: '兼容', type: 'success' },
  limited: { label: '受限', type: 'warning' },
  unknown: { label: '未检测', type: 'info' },
  success: { label: '成功', type: 'success' },
  failure: { label: '失败', type: 'danger' },
  running: { label: '运行中', type: 'success' },
  degraded: { label: '降级', type: 'warning' },
  offline: { label: '未接入', type: 'info' },
  draft: { label: '草稿', type: 'info' },
  importing: { label: '导入中', type: 'warning' },
  annotating: { label: '标注中', type: 'warning' },
  reviewing: { label: '审核中', type: 'primary' },
  quality_blocked: { label: '质检阻塞', type: 'danger' },
  ready: { label: '可训练', type: 'success' },
  archived: { label: '已归档', type: 'info' },
  unannotated: { label: '未标注', type: 'info' },
  in_progress: { label: '标注中', type: 'warning' },
  submitted: { label: '已提交', type: 'primary' },
  completed: { label: '已完成', type: 'success' },
  approved: { label: '已通过', type: 'success' },
  rejected: { label: '已驳回', type: 'danger' },
  needs_fix: { label: '待修正', type: 'warning' },
  unique: { label: '无重复', type: 'success' },
  suspected: { label: '疑似重复', type: 'warning' },
  confirmed_duplicate: { label: '确认重复', type: 'danger' },
  excluded: { label: '已排除', type: 'info' },
  verified: { label: '已核验', type: 'success' },
  missing: { label: '缺失', type: 'danger' },
  passed: { label: '通过', type: 'success' },
  failed: { label: '不通过', type: 'danger' },
  manual_review: { label: '待人工复核', type: 'warning' },
  published: { label: '已发布', type: 'success' },
  blocked: { label: '已阻止', type: 'danger' },
  unchecked: { label: '未质检', type: 'info' },
  succeeded: { label: '训练成功', type: 'success' },
  canceled: { label: '已取消', type: 'info' },
  canceling: { label: '正在取消', type: 'warning' },
}

const preset = computed<StatusPreset>(() => PRESET[props.status])
const text = computed(() => props.label || preset.value.label)
</script>

<template>
  <el-tag :type="preset.type" :size="size" :effect="effect" disable-transitions>
    {{ text }}
  </el-tag>
</template>
