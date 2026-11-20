<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import {
  fetchModelArtifacts,
  fetchModelDetail,
  fetchModelOptions,
  fetchModelVersionCompare,
  fetchModelVersions,
} from '@/api/model'
import { ApiError } from '@/api/http'
import type { PageResult } from '@/api/types'
import { DataTable, FilterPanel, PageHeader, Pagination } from '@/components'
import CompatibilityTag from '@/components/model/CompatibilityTag.vue'
import ReleaseStatusTag from '@/components/model/ReleaseStatusTag.vue'
import VerificationTag from '@/components/model/VerificationTag.vue'
import { formatFileSize } from '@/api/mock/modelAdapter'
import { MODEL_FILE_DEMO_NOTE } from '@/api/mock/modelData'
import type {
  ModelArtifact,
  ModelOptions,
  ModelSummary,
  ModelVersion,
  ModelVersionCompareRow,
  ModelVersionQuery,
} from '@/types/model'
import { MODEL_FORMAT_LABELS, QUANTIZATION_FORMAT_LABELS } from '@/types/model'

/**
 * A06 模型版本列表（P12）。
 *
 * 版本记录不可覆盖修改；已发布版本通过撤回 / 回滚产生新记录。
 */

const route = useRoute()
const router = useRouter()

const modelId = computed(() => String(route.params.modelId ?? ''))

const DEFAULT_QUERY: ModelVersionQuery = {
  keyword: '',
  releaseStatus: '',
  compatibilityStatus: '',
  page: 1,
  pageSize: 10,
}

const query = reactive<ModelVersionQuery>({ ...DEFAULT_QUERY })
const result = ref<PageResult<ModelVersion>>({ items: [], page: 1, pageSize: 10, total: 0 })
const artifacts = ref<Record<string, ModelArtifact>>({})
const model = ref<ModelSummary | null>(null)
const options = ref<ModelOptions | null>(null)
const loading = ref(false)
const error = ref('')

const selected = ref<ModelVersion[]>([])
const compareVisible = ref(false)
const compareLoading = ref(false)
const compareRows = ref<ModelVersionCompareRow[]>([])

const evaluationLabel: Record<string, string> = {
  evaluated: '已评估',
  demo: '演示指标',
  not_evaluated: '未评估',
}

function resolveMessage(cause: unknown, fallback: string): string {
  return cause instanceof ApiError ? cause.message : fallback
}

function artifactOf(version: ModelVersion): ModelArtifact | undefined {
  return artifacts.value[version.artifactId]
}

function formatLabel(value: string): string {
  return MODEL_FORMAT_LABELS[value as keyof typeof MODEL_FORMAT_LABELS] ?? value
}

function quantizationSummary(version: ModelVersion): string {
  if (!version.quantization.length) return '无'
  return version.quantization
    .map(
      (item) =>
        QUANTIZATION_FORMAT_LABELS[item.format as keyof typeof QUANTIZATION_FORMAT_LABELS] ??
        item.format,
    )
    .join(' / ')
}

async function loadVersions(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [page, artifactList] = await Promise.all([
      fetchModelVersions(modelId.value, query),
      fetchModelArtifacts(modelId.value),
    ])
    result.value = page
    artifacts.value = Object.fromEntries(artifactList.map((item) => [item.artifactId, item]))
  } catch (cause) {
    result.value = { items: [], page: query.page, pageSize: query.pageSize, total: 0 }
    error.value = resolveMessage(cause, '模型版本加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

async function loadContext(): Promise<void> {
  try {
    const [detail, optionList] = await Promise.all([
      fetchModelDetail(modelId.value),
      fetchModelOptions(),
    ])
    model.value = detail
    options.value = optionList
  } catch {
    // 上下文加载失败不阻断版本列表
  }
}

function handleSearch(): void {
  query.page = 1
  void loadVersions()
}

function handleReset(): void {
  Object.assign(query, DEFAULT_QUERY)
  void loadVersions()
}

function handleSelectionChange(rows: ModelVersion[]): void {
  selected.value = rows
}

const canCompare = computed(() => selected.value.length === 2)

async function openCompare(): Promise<void> {
  if (!canCompare.value) return
  const [first, second] = selected.value
  const base = first.createdAt <= second.createdAt ? first : second
  const target = base === first ? second : first
  compareVisible.value = true
  compareLoading.value = true
  compareRows.value = []
  try {
    compareRows.value = await fetchModelVersionCompare(base.modelVersionId, target.modelVersionId)
  } catch (cause) {
    compareRows.value = []
    ElMessage.error(resolveMessage(cause, '版本对比失败'))
  } finally {
    compareLoading.value = false
  }
}

function goDetail(version: ModelVersion): void {
  void router.push({
    name: 'model-version-detail',
    params: { modelId: modelId.value, versionId: version.modelVersionId },
  })
}

onMounted(() => {
  void loadContext()
  void loadVersions()
})
</script>

<template>
  <div class="version-list">
    <PageHeader
      eyebrow="A06 · 模型版本"
      :title="model ? `${model.modelName} · 版本列表` : '模型版本列表'"
      description="每个版本保持独立记录；已发布版本不可直接修改，只能通过撤回或回滚产生新记录。"
    >
      <template #extra>
        <el-button @click="router.push({ name: 'model-detail', params: { modelId } })">模型详情</el-button>
        <el-button @click="router.push({ name: 'model-repository' })">返回仓库</el-button>
        <el-button type="primary" :disabled="!canCompare" @click="openCompare">
          版本对比（{{ selected.length }}/2）
        </el-button>
      </template>
    </PageHeader>

    <el-alert type="warning" :closable="false" show-icon :title="MODEL_FILE_DEMO_NOTE" />

    <FilterPanel :loading="loading" @search="handleSearch" @reset="handleReset">
      <div class="version-list__filters">
        <el-input
          v-model="query.keyword"
          placeholder="版本号 / 训练任务 ID"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
        />
        <el-select v-model="query.releaseStatus" placeholder="发布状态" clearable style="width: 150px">
          <el-option
            v-for="item in options?.releaseStatuses ?? []"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-select
          v-model="query.compatibilityStatus"
          placeholder="兼容性状态"
          clearable
          style="width: 150px"
        >
          <el-option
            v-for="item in options?.compatibilityStatuses ?? []"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </div>
    </FilterPanel>

    <DataTable
      :data="result.items"
      :loading="loading"
      :error="error"
      row-key="modelVersionId"
      empty-description="当前模型没有符合条件的版本记录。"
      @retry="loadVersions"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="46" :selectable="() => true" />
      <el-table-column label="版本" width="110">
        <template #default="{ row }">
          <span class="version-list__version">{{ row.modelVersion }}</span>
          <span class="version-list__id">{{ row.modelVersionId }}</span>
        </template>
      </el-table-column>
      <el-table-column label="训练任务" width="120">
        <template #default="{ row }">{{ row.trainingTaskId || '未关联' }}</template>
      </el-table-column>
      <el-table-column label="数据集版本" min-width="180">
        <template #default="{ row }">
          <div>{{ row.datasetVersion }}</div>
          <div class="version-list__id">{{ row.datasetVersionId }}</div>
        </template>
      </el-table-column>
      <el-table-column label="格式" width="110">
        <template #default="{ row }">{{ formatLabel(row.modelFormat) }}</template>
      </el-table-column>
      <el-table-column label="量化" width="110">
        <template #default="{ row }">
          {{ quantizationSummary(row) }}
        </template>
      </el-table-column>
      <el-table-column label="文件大小" width="100">
        <template #default="{ row }">
          {{ formatFileSize(artifactOf(row)?.fileSizeBytes ?? 0) }}
        </template>
      </el-table-column>
      <el-table-column label="文件哈希" width="150">
        <template #default="{ row }">
          <VerificationTag :status="artifactOf(row)?.fileHashStatus ?? 'not_available'" />
        </template>
      </el-table-column>
      <el-table-column label="数字签名" width="150">
        <template #default="{ row }">
          <VerificationTag :status="artifactOf(row)?.signatureStatus ?? 'not_available'" />
        </template>
      </el-table-column>
      <el-table-column label="兼容性" width="110">
        <template #default="{ row }">
          <CompatibilityTag :status="row.runtimeCompatibility[0]?.compatibilityStatus ?? 'untested'" />
        </template>
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
      <el-table-column label="发布状态" width="110">
        <template #default="{ row }">
          <ReleaseStatusTag :status="row.releaseStatus" size="small" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="goDetail(row)">详情</el-button>
        </template>
      </el-table-column>

      <template #footer>
        <Pagination
          v-model:page="query.page"
          v-model:page-size="query.pageSize"
          :total="result.total"
          :page-sizes="[10, 20, 50]"
          @change="loadVersions"
        />
      </template>
    </DataTable>

    <el-dialog v-model="compareVisible" title="版本对比" width="760px">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="对比结果仅反映版本配置与状态，不代表模型效果优劣。"
        class="version-list__compare-note"
      />
      <div v-loading="compareLoading">
        <el-table :data="compareRows" size="small" border>
          <el-table-column prop="label" label="对比项" width="140" />
          <el-table-column prop="baseValue" label="较早版本" min-width="200" />
          <el-table-column prop="targetValue" label="较新版本" min-width="200">
            <template #default="{ row }">
              <span :class="{ 'is-changed': row.changed }">{{ row.targetValue }}</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="compareVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.version-list {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-4);
}

.version-list__filters {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ss-space-2);
}

.version-list__version {
  display: block;
  color: var(--ss-text-primary);
  font-weight: var(--ss-font-weight-medium);
}

.version-list__id {
  display: block;
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
}

.version-list__compare-note {
  margin-bottom: var(--ss-space-3);
}

.is-changed {
  color: var(--ss-accent);
  font-weight: 500;
}
</style>
