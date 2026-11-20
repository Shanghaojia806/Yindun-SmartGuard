<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  fetchModelAudits,
  fetchModelDetail,
  fetchModelVersions,
  fetchReleaseRecords,
} from '@/api/model'
import { ApiError } from '@/api/http'
import { useSessionStore } from '@/stores/session'
import { PageHeader } from '@/components'
import AuditRecordTable from '@/components/model/AuditRecordTable.vue'
import CompatibilityTag from '@/components/model/CompatibilityTag.vue'
import ReleaseRecordTable from '@/components/model/ReleaseRecordTable.vue'
import ReleaseStatusTag from '@/components/model/ReleaseStatusTag.vue'
import type { ModelAuditRecord, ModelSummary, ModelVersion, ReleaseRecord } from '@/types/model'
import { MODEL_TYPE_LABELS } from '@/types/model'
import { IS_MOCK } from '@/api/request'
import { MODEL_DEMO_NOTE } from '@/api/mock/modelData'

/**
 * A06 模型详情（P12）。
 *
 * 展示模型基础信息、版本概览、发布记录与审计记录；
 * 发布、撤回与回滚操作在模型版本详情中完成。
 */

const route = useRoute()
const router = useRouter()
const session = useSessionStore()

const modelId = computed(() => String(route.params.modelId ?? ''))
const activeTab = ref(String(route.query.tab ?? 'overview'))

const model = ref<ModelSummary | null>(null)
const versions = ref<ModelVersion[]>([])
const releases = ref<ReleaseRecord[]>([])
const audits = ref<ModelAuditRecord[]>([])
const loading = ref(false)
const error = ref('')

const evaluationLabel: Record<string, string> = {
  evaluated: '已评估',
  demo: '演示指标',
  not_evaluated: '未评估',
}

function resolveMessage(cause: unknown, fallback: string): string {
  return cause instanceof ApiError ? cause.message : fallback
}

async function loadAll(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [detail, versionPage, releaseList, auditList] = await Promise.all([
      fetchModelDetail(modelId.value),
      fetchModelVersions(modelId.value, {
        keyword: '',
        releaseStatus: '',
        compatibilityStatus: '',
        page: 1,
        pageSize: 100,
      }),
      fetchReleaseRecords(modelId.value),
      fetchModelAudits(modelId.value),
    ])
    model.value = detail
    versions.value = versionPage.items
    releases.value = releaseList
    audits.value = auditList
  } catch (cause) {
    error.value = resolveMessage(cause, '模型详情加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function goVersions(): void {
  void router.push({ name: 'model-versions', params: { modelId: modelId.value } })
}

function goVersion(version: ModelVersion): void {
  void router.push({
    name: 'model-version-detail',
    params: { modelId: modelId.value, versionId: version.modelVersionId },
  })
}

watch(activeTab, (value) => {
  void router.replace({ query: { ...route.query, tab: value } })
})

onMounted(() => {
  void loadAll()
})
</script>

<template>
  <div class="model-detail">
    <PageHeader
      eyebrow="A06 · 模型版本"
      :title="model ? model.modelName : '模型详情'"
      :description="model ? model.description : '加载模型信息中'"
    >
      <template #extra>
        <el-button @click="router.push({ name: 'model-repository' })">返回仓库</el-button>
        <el-button @click="goVersions">版本列表</el-button>
        <el-button type="primary" :loading="loading" @click="loadAll">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert
      :type="IS_MOCK ? 'warning' : 'info'"
      :closable="false"
      show-icon
      :title="
        IS_MOCK
          ? MODEL_DEMO_NOTE
          : '真实后端模式：版本、哈希、签名与发布记录均来自 FastAPI 与真实训练产物；Smoke Test 产物仅限受限内部测试。'
      "
    />

    <el-skeleton v-if="loading && !model" :rows="6" animated />

    <el-alert
      v-else-if="error"
      type="error"
      :closable="false"
      show-icon
      :title="error"
      class="model-detail__error"
    >
      <el-button size="small" @click="loadAll">重新加载</el-button>
    </el-alert>

    <template v-else-if="model">
      <el-card shadow="never">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="模型 ID">{{ model.modelId }}</el-descriptions-item>
          <el-descriptions-item label="模型类型">{{ MODEL_TYPE_LABELS[model.modelType] }}</el-descriptions-item>
          <el-descriptions-item label="模型框架">PyTorch（训练框架）</el-descriptions-item>
          <el-descriptions-item label="最新版本">
            {{ model.latestVersion }}
            <ReleaseStatusTag :status="model.releaseStatus" size="small" />
          </el-descriptions-item>
          <el-descriptions-item label="当前发布版本">
            {{ model.publishedVersion || '暂无' }}
          </el-descriptions-item>
          <el-descriptions-item label="版本数量">{{ model.versionCount }}</el-descriptions-item>
          <el-descriptions-item label="训练来源" :span="2">{{ model.trainingSourceLabel }}</el-descriptions-item>
          <el-descriptions-item label="最近更新">{{ model.updatedAt }}</el-descriptions-item>
          <el-descriptions-item label="当前账号">{{ session.roleLabel || '未知' }}</el-descriptions-item>
        </el-descriptions>
        <el-alert
          type="info"
          :closable="false"
          class="model-detail__worker"
          title="训练来源为 P11 训练任务；P11 未接入真实 Python Worker，关联任务均为模拟执行。"
        />
      </el-card>

      <el-tabs v-model="activeTab" class="model-detail__tabs">
        <el-tab-pane label="版本概览" name="overview">
          <el-table :data="versions" size="small" border>
            <el-table-column prop="modelVersion" label="版本" width="120" />
            <el-table-column label="发布状态" width="110">
              <template #default="{ row }">
                <ReleaseStatusTag :status="row.releaseStatus" size="small" />
              </template>
            </el-table-column>
            <el-table-column prop="trainingTaskId" label="训练任务" width="130">
              <template #default="{ row }">{{ row.trainingTaskId || '未关联' }}</template>
            </el-table-column>
            <el-table-column label="数据集版本" min-width="190">
              <template #default="{ row }">{{ row.datasetVersion }}（{{ row.datasetVersionId }}）</template>
            </el-table-column>
            <el-table-column label="评估" width="100">
              <template #default="{ row }">
                <el-tag
                  size="small"
                  :type="row.evaluationSummary.evaluationStatus === 'evaluated' ? 'success' : row.evaluationSummary.evaluationStatus === 'demo' ? 'warning' : 'info'"
                  effect="plain"
                >
                  {{ evaluationLabel[row.evaluationSummary.evaluationStatus] }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="兼容性" width="110">
              <template #default="{ row }">
                <CompatibilityTag :status="row.runtimeCompatibility[0]?.compatibilityStatus ?? 'untested'" />
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" width="140" />
            <el-table-column label="操作" width="110" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="goVersion(row)">查看详情</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="该模型暂无版本记录" />
            </template>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="发布记录" name="releases">
          <ReleaseRecordTable v-if="releases.length" :records="releases" />
          <el-empty v-else description="暂无发布记录" />
        </el-tab-pane>

        <el-tab-pane label="审计记录" name="audits">
          <el-alert
            type="info"
            :closable="false"
            show-icon
            :title="
              IS_MOCK
                ? '发布审计记录只读，页面不提供删除入口；当前记录均为演示数据。'
                : '发布审计记录由服务端只追加写入，页面不提供删除入口；撤回与回滚同样保留完整记录。'
            "
            class="model-detail__audit-note"
          />
          <AuditRecordTable v-if="audits.length" :records="audits" />
          <el-empty v-else description="暂无审计记录" />
        </el-tab-pane>
      </el-tabs>
    </template>
  </div>
</template>

<style scoped>
.model-detail {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-4);
}

.model-detail__error :deep(.el-alert__content) {
  display: flex;
  align-items: center;
  gap: var(--ss-space-3);
}

.model-detail__worker,
.model-detail__audit-note {
  margin-top: var(--ss-space-3);
}

.model-detail__tabs {
  margin-top: var(--ss-space-1);
}
</style>
