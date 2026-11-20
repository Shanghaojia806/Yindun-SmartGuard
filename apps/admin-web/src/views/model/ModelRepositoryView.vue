<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { fetchModelOptions, fetchModels, fetchModelStats, importModelVersion } from '@/api/model'
import { IS_MOCK } from '@/api/request'
import { ApiError } from '@/api/http'
import type { PageResult } from '@/api/types'
import { DataTable, FilterPanel, PageHeader, Pagination, StatCard } from '@/components'
import CompatibilityTag from '@/components/model/CompatibilityTag.vue'
import ReleaseStatusTag from '@/components/model/ReleaseStatusTag.vue'
import type {
  ModelListQuery,
  ModelOptions,
  ModelStats,
  ModelSummary,
} from '@/types/model'
import { MODEL_TYPE_LABELS } from '@/types/model'
import { MODEL_DEMO_NOTE } from '@/api/mock/modelData'

/**
 * A06 模型仓库（P12）。
 *
 * 真实模式下数据来自 FastAPI：模型版本必须关联真实 TrainingTask，
 * 文件哈希、签名与发布资格全部由服务端重新校验，前端不自行判定。
 */

const MODEL_REAL_NOTE =
  '真实后端模式：模型来自 P11-R 真实训练产物，SHA-256 与 Ed25519 签名由服务端校验；Smoke Test / 合成数据产物仅限受限内部测试。'

const router = useRouter()

const DEFAULT_QUERY: ModelListQuery = {
  keyword: '',
  modelType: '',
  framework: '',
  releaseStatus: '',
  compatibilityStatus: '',
  publishedFrom: '',
  publishedTo: '',
  page: 1,
  pageSize: 10,
}

const query = reactive<ModelListQuery>({ ...DEFAULT_QUERY })
const result = ref<PageResult<ModelSummary>>({ items: [], page: 1, pageSize: 10, total: 0 })
const loading = ref(false)
const error = ref('')

const stats = ref<ModelStats>({
  totalModels: 0,
  totalVersions: 0,
  pendingReview: 0,
  published: 0,
  withdrawn: 0,
  compatibilityUntested: 0,
})

const options = ref<ModelOptions>({
  modelTypes: [],
  frameworks: [],
  modelFormats: [],
  releaseStatuses: [],
  compatibilityStatuses: [],
  channels: [],
  quantizationFormats: [],
  datasetVersions: [],
  trainingTasks: [],
})

const totalText = computed(() => `${result.value.total} 个模型`)

const importVisible = ref(false)
const importSubmitting = ref(false)
const importError = ref('')
const importForm = reactive({
  trainingTaskId: '',
  modelName: '',
  description: '',
  operator: 'P12验收员',
  roleLabel: '系统管理员',
})
const importableTasks = computed(() =>
  options.value.trainingTasks.filter(
    (task) => task.taskStatus === 'succeeded' && task.executionMode === 'worker',
  ),
)

function openImport(): void {
  importError.value = ''
  importVisible.value = true
}

function handleImportTaskChange(taskId: string): void {
  const task = options.value.trainingTasks.find((item) => item.taskId === taskId)
  if (task && !importForm.modelName) {
    importForm.modelName = task.taskName
  }
}

async function submitImport(): Promise<void> {
  if (!importForm.trainingTaskId) {
    ElMessage.warning('请选择训练任务')
    return
  }
  importSubmitting.value = true
  importError.value = ''
  try {
    const version = await importModelVersion({
      trainingTaskId: importForm.trainingTaskId,
      modelName: importForm.modelName,
      description: importForm.description,
      operator: importForm.operator,
      roleLabel: importForm.roleLabel,
    })
    importVisible.value = false
    ElMessage.success(`已导入 ${version.modelVersion}，文件哈希与签名已由服务端校验`)
    await refreshAll()
    void router.push({
      name: 'model-version-detail',
      params: { modelId: version.modelId, versionId: version.modelVersionId },
    })
  } catch (cause) {
    importError.value = resolveMessage(cause, '导入失败，请检查训练任务产物')
    ElMessage.error(importError.value)
  } finally {
    importSubmitting.value = false
  }
}

function resolveMessage(cause: unknown, fallback: string): string {
  return cause instanceof ApiError ? cause.message : fallback
}

function modelTypeLabel(value: string): string {
  return MODEL_TYPE_LABELS[value as keyof typeof MODEL_TYPE_LABELS] ?? value
}

async function loadStats(): Promise<void> {
  try {
    stats.value = await fetchModelStats()
  } catch {
    stats.value = {
      totalModels: 0,
      totalVersions: 0,
      pendingReview: 0,
      published: 0,
      withdrawn: 0,
      compatibilityUntested: 0,
    }
  }
}

async function loadOptions(): Promise<void> {
  try {
    options.value = await fetchModelOptions()
  } catch {
    // 筛选项失败不阻断列表
  }
}

async function loadModels(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    result.value = await fetchModels(query)
  } catch (cause) {
    result.value = { items: [], page: query.page, pageSize: query.pageSize, total: 0 }
    error.value = resolveMessage(cause, '模型仓库加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

async function refreshAll(): Promise<void> {
  await Promise.all([loadStats(), loadModels()])
}

function handleSearch(): void {
  query.page = 1
  void loadModels()
}

function handleReset(): void {
  Object.assign(query, DEFAULT_QUERY)
  void loadModels()
}

function handlePageChange(): void {
  void loadModels()
}

function goDetail(model: ModelSummary, tab = 'overview'): void {
  void router.push({
    name: 'model-detail',
    params: { modelId: model.modelId },
    query: tab === 'overview' ? undefined : { tab },
  })
}

function goVersions(model: ModelSummary): void {
  void router.push({ name: 'model-versions', params: { modelId: model.modelId } })
}

function goLatestVersion(model: ModelSummary): void {
  if (!model.latestVersionId) {
    void goVersions(model)
    return
  }
  void router.push({
    name: 'model-version-detail',
    params: { modelId: model.modelId, versionId: model.latestVersionId },
  })
}

onMounted(() => {
  void loadOptions()
  void refreshAll()
})
</script>

<template>
  <div class="model-repo">
    <PageHeader
      eyebrow="A06 · 模型版本"
      title="模型仓库"
      description="管理模型版本、量化信息、运行时兼容性、审核发布与回滚记录。"
    >
      <template #extra>
        <el-button v-if="!IS_MOCK" type="primary" @click="openImport">从训练任务导入</el-button>
        <el-button @click="refreshAll">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert
      :type="IS_MOCK ? 'warning' : 'info'"
      :closable="false"
      show-icon
      class="model-repo__notice"
      :title="IS_MOCK ? MODEL_DEMO_NOTE : MODEL_REAL_NOTE"
    />

    <section class="model-repo__stats">
      <StatCard label="模型总数" :value="stats.totalModels" :demo="IS_MOCK" hint="仓库中的模型条目" tone="primary" />
      <StatCard label="模型版本总数" :value="stats.totalVersions" :demo="IS_MOCK" hint="含草稿与历史版本" tone="accent" />
      <StatCard label="待审核版本" :value="stats.pendingReview" :demo="IS_MOCK" hint="等待审核决定" tone="warning" />
      <StatCard label="已发布版本" :value="stats.published" :demo="IS_MOCK" :hint="IS_MOCK ? '含演示发布记录' : '服务端真实发布记录'" tone="safe" />
      <StatCard label="已撤回版本" :value="stats.withdrawn" :demo="IS_MOCK" hint="停止分发，非设备端卸载" tone="danger" />
      <StatCard
        label="兼容性待测试"
        :value="stats.compatibilityUntested"
        :demo="IS_MOCK"
        hint="存在未测试的运行时记录"
        tone="neutral"
      />
    </section>

    <FilterPanel :loading="loading" @search="handleSearch" @reset="handleReset">
      <div class="model-repo__filters">
        <el-input
          v-model="query.keyword"
          placeholder="模型名称 / 模型 ID / 版本"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
        />
        <el-select v-model="query.modelType" placeholder="模型类型" clearable style="width: 160px">
          <el-option
            v-for="item in options.modelTypes"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-select v-model="query.framework" placeholder="模型框架" clearable style="width: 130px">
          <el-option
            v-for="item in options.frameworks"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-select
          v-model="query.releaseStatus"
          placeholder="版本状态"
          clearable
          style="width: 140px"
        >
          <el-option
            v-for="item in options.releaseStatuses"
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
            v-for="item in options.compatibilityStatuses"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-date-picker
          v-model="query.publishedFrom"
          type="date"
          placeholder="发布时间起"
          value-format="YYYY-MM-DD"
          style="width: 150px"
        />
        <el-date-picker
          v-model="query.publishedTo"
          type="date"
          placeholder="发布时间止"
          value-format="YYYY-MM-DD"
          style="width: 150px"
        />
      </div>
    </FilterPanel>

    <DataTable
      :data="result.items"
      :loading="loading"
      :error="error"
      row-key="modelId"
      :empty-description="`当前筛选条件下没有模型（${totalText}）。`"
      @retry="loadModels"
    >
      <el-table-column label="模型" min-width="240">
        <template #default="{ row }">
          <div class="model-repo__name">{{ row.modelName }}</div>
          <div class="model-repo__sub">{{ row.modelId }} · {{ row.versionCount }} 个版本</div>
        </template>
      </el-table-column>
      <el-table-column label="模型类型" width="140">
        <template #default="{ row }">{{ modelTypeLabel(row.modelType) }}</template>
      </el-table-column>
      <el-table-column label="框架" width="100">
        <template #default="{ row }">{{ row.framework === 'pytorch' ? 'PyTorch' : row.framework }}</template>
      </el-table-column>
      <el-table-column label="最新版本" width="130">
        <template #default="{ row }">
          <div>{{ row.latestVersion }}</div>
          <ReleaseStatusTag :status="row.releaseStatus" size="small" />
        </template>
      </el-table-column>
      <el-table-column label="当前发布版本" width="130">
        <template #default="{ row }">
          <span v-if="row.publishedVersion">{{ row.publishedVersion }}</span>
          <span v-else class="model-repo__muted">暂无</span>
        </template>
      </el-table-column>
      <el-table-column prop="trainingSourceLabel" label="训练来源" min-width="200" show-overflow-tooltip />
      <el-table-column label="兼容性" width="110">
        <template #default="{ row }">
          <CompatibilityTag :status="row.compatibilityStatus" />
        </template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="最近更新" width="140" />
      <el-table-column label="操作" width="250" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="goDetail(row)">模型详情</el-button>
          <el-button link type="primary" @click="goVersions(row)">版本</el-button>
          <el-button link type="primary" @click="goLatestVersion(row)">评估</el-button>
          <el-button link type="primary" @click="goDetail(row, 'releases')">发布记录</el-button>
        </template>
      </el-table-column>

      <template #footer>
        <Pagination
          v-model:page="query.page"
          v-model:page-size="query.pageSize"
          :total="result.total"
          :page-sizes="[10, 20, 50]"
          @change="handlePageChange"
        />
      </template>
    </DataTable>

    <el-dialog v-model="importVisible" title="从训练任务导入真实模型产物" width="560px">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="导入时服务端会重新读取产物清单、计算 SHA-256 并验签；Smoke Test 产物仅允许受限内部测试，不能作为正式反诈模型发布。"
      />
      <el-form label-position="top" class="model-repo__import-form">
        <el-form-item label="训练任务（仅成功且由真实 Worker 执行）">
          <el-select
            v-model="importForm.trainingTaskId"
            placeholder="选择训练任务"
            filterable
            style="width: 100%"
            @change="handleImportTaskChange"
          >
            <el-option
              v-for="task in importableTasks"
              :key="task.taskId"
              :label="`${task.taskName} · ${task.taskId}`"
              :value="task.taskId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="模型名称（同名模型将追加版本）">
          <el-input v-model="importForm.modelName" placeholder="例如：文本反诈分类模型（Smoke Test）" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="importForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item v-if="importError">
          <span class="model-repo__import-error">{{ importError }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" :loading="importSubmitting" @click="submitImport">导入并校验</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.model-repo {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-4);
}

.model-repo__notice {
  margin-top: calc(var(--ss-space-1) * -1);
}

.model-repo__stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: var(--ss-space-3);
}

.model-repo__filters {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ss-space-2);
}

.model-repo__name {
  color: var(--ss-text-primary);
  font-weight: var(--ss-font-weight-medium);
}

.model-repo__sub {
  margin-top: 2px;
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
}

.model-repo__muted {
  color: var(--ss-text-tertiary);
}

.model-repo__import-form {
  margin-top: var(--ss-space-3);
}

.model-repo__import-error {
  color: var(--ss-danger, #f56c6c);
  font-size: var(--ss-font-size-xs);
}
</style>
