<script setup lang="ts">
import type { ReleaseRecord } from '@/types/model'

import ReleaseStatusTag from './ReleaseStatusTag.vue'

/**
 * 模型发布记录表：保留每次发布 / 撤回 / 回滚的独立记录。
 */
interface Props {
  records: ReleaseRecord[]
}

defineProps<Props>()

const channelLabels: Record<string, string> = {
  internal_test: '内部测试（演示）',
  canary: '灰度发布',
  production: '正式发布',
}
</script>

<template>
  <el-table :data="records" size="small" border class="release-record-table">
    <el-table-column prop="releaseId" label="发布单号" width="130" />
    <el-table-column prop="modelVersion" label="版本" width="100" />
    <el-table-column label="渠道" width="140">
      <template #default="{ row }">{{ channelLabels[row.releaseChannel] }}</template>
    </el-table-column>
    <el-table-column label="状态" width="110">
      <template #default="{ row }">
        <ReleaseStatusTag :status="row.releaseStatus" size="small" />
      </template>
    </el-table-column>
    <el-table-column prop="operator" label="操作人" width="110" />
    <el-table-column prop="reviewedBy" label="审核人" width="110" />
    <el-table-column label="提交时间" width="140">
      <template #default="{ row }">{{ row.submittedAt || '—' }}</template>
    </el-table-column>
    <el-table-column label="发布时间" width="140">
      <template #default="{ row }">{{ row.publishedAt || '—' }}</template>
    </el-table-column>
    <el-table-column label="撤回时间" width="140">
      <template #default="{ row }">{{ row.withdrawnAt || '—' }}</template>
    </el-table-column>
    <el-table-column label="回滚" width="150">
      <template #default="{ row }">
        <span v-if="row.rollbackFrom || row.rollbackTo">
          {{ row.rollbackFrom || '—' }} → {{ row.rollbackTo || '—' }}
        </span>
        <span v-else>—</span>
      </template>
    </el-table-column>
    <el-table-column prop="operationNotes" label="说明" min-width="240" show-overflow-tooltip />
  </el-table>
</template>
