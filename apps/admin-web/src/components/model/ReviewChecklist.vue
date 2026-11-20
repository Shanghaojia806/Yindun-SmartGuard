<script setup lang="ts">
import { computed } from 'vue'

import { RELEASE_CHANNEL_LABELS } from '@/types/model'
import type { ModelReviewResult } from '@/types/model'

/**
 * 模型审核清单（11 项）。
 *
 * 仅用于“服务端”审核结果的展示；发布动作仍由适配器 / 后端重新校验。
 */
interface Props {
  result: ModelReviewResult | null
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), { loading: false })

const statusLabels: Record<string, string> = {
  passed: '通过',
  failed: '不通过',
  unverified: '未验证',
  not_applicable: '不适用',
}

const statusTypes: Record<string, 'success' | 'danger' | 'warning' | 'info'> = {
  passed: 'success',
  failed: 'danger',
  unverified: 'warning',
  not_applicable: 'info',
}

const channelLabel = computed(() =>
  props.result ? RELEASE_CHANNEL_LABELS[props.result.channel] : '',
)
const passedCount = computed(
  () => props.result?.checks.filter((item) => item.status === 'passed').length ?? 0,
)
</script>

<template>
  <div v-loading="loading" class="review-checklist">
    <template v-if="result">
      <div class="review-checklist__head">
        <span class="review-checklist__title">审核清单</span>
        <el-tag size="small" effect="plain">{{ channelLabel }}</el-tag>
        <el-tag
          size="small"
          :type="result.eligible ? 'success' : 'danger'"
          effect="light"
        >
          {{ result.eligible ? '满足当前渠道放行条件' : '存在阻断项' }}
        </el-tag>
        <span class="review-checklist__count">
          通过 {{ passedCount }} / {{ result.checks.length }}
        </span>
      </div>

      <el-alert
        :type="result.eligible ? 'success' : 'error'"
        :closable="false"
        show-icon
        :title="result.eligible ? '审核条件已满足（演示环境）' : '以下关键条件未满足'"
      >
        <template v-if="!result.eligible">
          <ul class="review-checklist__reasons">
            <li v-for="reason in result.blockedReasons" :key="reason">{{ reason }}</li>
          </ul>
        </template>
        <span v-else>{{ result.note }}</span>
      </el-alert>

      <el-table :data="result.checks" size="small" border class="review-checklist__table">
        <el-table-column prop="label" label="审核项" min-width="170" />
        <el-table-column label="结果" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTypes[row.status]" effect="light">
              {{ statusLabels[row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="detail" label="说明" min-width="260" />
        <el-table-column label="阻断" width="80">
          <template #default="{ row }">
            <span :class="row.blocking ? 'is-blocking' : 'is-tolerant'">
              {{ row.blocking ? '是' : '否' }}
            </span>
          </template>
        </el-table-column>
      </el-table>

      <p class="review-checklist__note">{{ result.note }}</p>
    </template>

    <el-empty v-else description="选择发布渠道后生成审核清单" />
  </div>
</template>

<style scoped>
.review-checklist__head {
  display: flex;
  align-items: center;
  gap: var(--ss-space-2);
  margin-bottom: var(--ss-space-3);
  flex-wrap: wrap;
}

.review-checklist__title {
  color: var(--ss-text-primary);
  font-weight: var(--ss-font-weight-medium);
}

.review-checklist__count {
  margin-left: auto;
  color: var(--ss-text-secondary);
  font-size: var(--ss-font-size-sm);
}

.review-checklist__reasons {
  margin: 0;
  padding-left: 18px;
  line-height: 1.7;
}

.review-checklist__table {
  margin-top: var(--ss-space-3);
}

.review-checklist__note {
  margin-top: var(--ss-space-3);
  color: var(--ss-text-secondary);
  font-size: var(--ss-font-size-xs, 12px);
  line-height: 1.6;
}

.is-blocking {
  color: var(--ss-danger);
  font-weight: 500;
}

.is-tolerant {
  color: var(--ss-text-tertiary);
}
</style>
