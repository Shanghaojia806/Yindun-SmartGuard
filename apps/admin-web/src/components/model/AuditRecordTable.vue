<script setup lang="ts">
import type { ModelAuditRecord } from '@/types/model'

/**
 * 模型发布审计记录表（只读）。
 *
 * 审计记录不提供删除或编辑入口。
 */
interface Props {
  records: ModelAuditRecord[]
}

defineProps<Props>()

const resultType: Record<string, 'success' | 'warning' | 'danger'> = {
  success: 'success',
  failed: 'danger',
  blocked: 'warning',
}

const resultLabel: Record<string, string> = {
  success: '成功',
  failed: '失败',
  blocked: '被拦截',
}
</script>

<template>
  <el-table :data="records" size="small" border class="audit-record-table">
    <el-table-column prop="operatedAt" label="操作时间" width="140" />
    <el-table-column prop="operator" label="操作人" width="110" />
    <el-table-column prop="operationLabel" label="操作类型" width="120" />
    <el-table-column label="操作对象" min-width="170">
      <template #default="{ row }">{{ row.targetId }}（{{ row.targetLabel }}）</template>
    </el-table-column>
    <el-table-column label="结果" width="100">
      <template #default="{ row }">
        <el-tag size="small" :type="resultType[row.result]" effect="light">
          {{ resultLabel[row.result] }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="notes" label="操作说明" min-width="260" show-overflow-tooltip />
  </el-table>
</template>
