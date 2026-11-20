<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { ApiError } from '@/api/http'
import type { PageResult } from '@/api/types'
import {
  createDataset,
  fetchDatasetDetail,
  fetchDatasetOptions,
  fetchDatasets,
  fetchDatasetStats,
} from '@/api/dataset'
import {
  DataTable,
  DetailDrawer,
  FilterPanel,
  PageHeader,
  Pagination,
  StatCard,
  StatusTag,
} from '@/components'
import type {
  CreateDatasetInput,
  DatasetListQuery,
  DatasetOptions,
  DatasetOverviewStats,
  DatasetSummary,
  DatasetType,
} from '@/types/dataset'

/**
 * A04 数据集列表（P10）。
 *
 * 统计与列表均由 Mock Repository 实时计算，页面不写死任何数字；
 * 所有数据明确标记为演示数据，未接入真实数据存储服务。
 */

const router = useRouter()

const TYPE_LABELS: Record<DatasetType, string> = {
  screen: '屏幕对话',
  video: '视频',
  audio: '音频',
  text: '文本',
  multimodal: '多模态混合',
}

function typeLabel(type: string): string {
  return TYPE_LABELS[type as DatasetType] ?? type
}

const DEFAULT_QUERY: DatasetListQuery = {
  keyword: '',
  datasetType: '',
  status: '',
  createdFrom: '',
  createdTo: '',
  annotationProgress: '',
  page: 1,
  pageSize: 10,
}

const query = reactive<DatasetListQuery>({ ...DEFAULT_QUERY })
const result = ref<PageResult<DatasetSummary>>({ items: [], page: 1, pageSize: 10, total: 0 })
const loading = ref(false)
const error = ref('')

const stats = ref<DatasetOverviewStats>({
  datasetTotal: 0,
  sampleTotal: 0,
  annotatedTotal: 0,
  pendingReviewTotal: 0,
  qualityPassedTotal: 0,
  blockedTotal: 0,
})

const options = ref<DatasetOptions>({ types: [], statuses: [] })

function resolveMessage(cause: unknown, fallback: string): string {
  return cause instanceof ApiError ? cause.message : fallback
}

async function loadStats() {
  try {
    stats.value = await fetchDatasetStats()
  } catch {
    stats.value = {
      datasetTotal: 0,
      sampleTotal: 0,
      annotatedTotal: 0,
      pendingReviewTotal: 0,
      qualityPassedTotal: 0,
      blockedTotal: 0,
    }
  }
}

async function loadOptions() {
  try {
    options.value = await fetchDatasetOptions()
  } catch {
    options.value = { types: [], statuses: [] }
  }
}

async function loadDatasets() {
  loading.value = true
  error.value = ''
  try {
    result.value = await fetchDatasets(query)
  } catch (cause) {
    result.value = { items: [], page: query.page, pageSize: query.pageSize, total: 0 }
    error.value = resolveMessage(cause, '数据集列表加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadOptions()
  void loadStats()
  void loadDatasets()
})

function handleSearch() {
  query.page = 1
  void loadDatasets()
}

function handleReset() {
  Object.assign(query, { ...DEFAULT_QUERY, pageSize: query.pageSize })
  void loadDatasets()
}

async function refreshAll() {
  await Promise.all([loadStats(), loadDatasets()])
}

/* ===== 详情抽屉 ===== */
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detail = ref<DatasetSummary | null>(null)
const detailId = ref('')

async function loadDetail() {
  if (!detailId.value) return
  detailLoading.value = true
  detailError.value = ''
  try {
    detail.value = await fetchDatasetDetail(detailId.value)
  } catch (cause) {
    detail.value = null
    detailError.value = resolveMessage(cause, '数据集详情加载失败')
  } finally {
    detailLoading.value = false
  }
}

function openDetail(row: DatasetSummary) {
  detailId.value = row.datasetId
  detail.value = row
  detailVisible.value = true
  void loadDetail()
}

/* ===== 创建数据集 ===== */
const createVisible = ref(false)
const createLoading = ref(false)
const createForm = reactive<CreateDatasetInput>({
  datasetName: '',
  datasetType: 'screen',
  description: '',
})

function openCreate() {
  createForm.datasetName = ''
  createForm.datasetType = 'screen'
  createForm.description = ''
  createVisible.value = true
}

async function submitCreate() {
  if (!createForm.datasetName.trim()) {
    ElMessage.warning('请填写数据集名称')
    return
  }
  createLoading.value = true
  try {
    const created = await createDataset({
      datasetName: createForm.datasetName,
      datasetType: createForm.datasetType,
      description: createForm.description,
    })
    ElMessage.success(`已创建演示数据集「${created.datasetName}」（仅保存在本地 Mock 数据中）`)
    createVisible.value = false
    await refreshAll()
  } catch (cause) {
    ElMessage.error(resolveMessage(cause, '创建数据集失败，请稍后重试'))
  } finally {
    createLoading.value = false
  }
}

/* ===== 跳转 ===== */
function goAnnotation(row: DatasetSummary) {
  void router.push(`/admin/datasets/${row.datasetId}/annotation`)
}

function goVersions(row: DatasetSummary) {
  void router.push(`/admin/datasets/${row.datasetId}/versions`)
}

function goImport(row?: DatasetSummary) {
  void router.push({
    path: '/admin/datasets/import',
    query: row ? { datasetId: row.datasetId } : {},
  })
}

const statsCards = computed(() => [
  { label: '数据集总数', value: stats.value.datasetTotal, hint: '当前演示数据集数量', tone: 'primary' as const },
  { label: '样本总数', value: stats.value.sampleTotal, hint: '含未标注与受阻样本', tone: 'accent' as const },
  { label: '已完成标注', value: stats.value.annotatedTotal, hint: '已完成或已提交的样本', tone: 'safe' as const },
  { label: '待审核样本', value: stats.value.pendingReviewTotal, hint: '提交后等待复核', tone: 'warning' as const },
  { label: '已通过质检', value: stats.value.qualityPassedTotal, hint: '需主动运行质检后更新', tone: 'safe' as const },
  { label: '不可用于训练', value: stats.value.blockedTotal, hint: '来源/许可/格式受阻', tone: 'danger' as const },
])
</script>

<template>
  <div class="dataset-page">
    <PageHeader
      eyebrow="A04 · 数据与模型"
      title="数据集管理"
      description="管理反诈多模态训练数据的来源许可、标注、质检、去重、拆分与版本。以下均为团队自建演示数据，未接入真实数据存储服务。"
    >
      <template #extra>
        <el-tag type="warning" effect="plain">演示数据（Mock）</el-tag>
        <el-button @click="goImport()">导入数据</el-button>
        <el-button type="primary" @click="openCreate">创建数据集</el-button>
      </template>
    </PageHeader>

    <section class="stats-grid">
      <StatCard
        v-for="card in statsCards"
        :key="card.label"
        :label="card.label"
        :value="card.value"
        :hint="card.hint"
        :tone="card.tone"
      />
    </section>

    <FilterPanel :loading="loading" @search="handleSearch" @reset="handleReset">
      <div class="ss-field">
        <label class="ss-field__label" for="dataset-keyword">数据集名称</label>
        <el-input
          id="dataset-keyword"
          v-model="query.keyword"
          placeholder="输入名称关键字"
          clearable
          @keyup.enter="handleSearch"
        />
      </div>
      <div class="ss-field">
        <label class="ss-field__label" for="dataset-type">数据集类型</label>
        <el-select id="dataset-type" v-model="query.datasetType" placeholder="全部类型" clearable>
          <el-option
            v-for="option in options.types"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </div>
      <div class="ss-field">
        <label class="ss-field__label" for="dataset-status">数据集状态</label>
        <el-select id="dataset-status" v-model="query.status" placeholder="全部状态" clearable>
          <el-option
            v-for="option in options.statuses"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </div>
      <div class="ss-field">
        <label class="ss-field__label" for="dataset-created-from">创建时间起</label>
        <el-date-picker
          id="dataset-created-from"
          v-model="query.createdFrom"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="起始日期"
          clearable
        />
      </div>
      <div class="ss-field">
        <label class="ss-field__label" for="dataset-created-to">创建时间止</label>
        <el-date-picker
          id="dataset-created-to"
          v-model="query.createdTo"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="结束日期"
          clearable
        />
      </div>
      <div class="ss-field">
        <label class="ss-field__label" for="dataset-progress">标注进度</label>
        <el-select id="dataset-progress" v-model="query.annotationProgress" placeholder="全部进度" clearable>
          <el-option label="未开始" value="not_started" />
          <el-option label="标注中" value="in_progress" />
          <el-option label="全部完成" value="completed" />
        </el-select>
      </div>
    </FilterPanel>

    <DataTable
      :data="result.items"
      :loading="loading"
      :error="error"
      row-key="datasetId"
      empty-title="没有匹配的数据集"
      empty-description="请调整搜索或筛选条件后重试；演示数据共 6 个数据集，重置筛选可恢复。"
      @row-click="openDetail"
      @retry="loadDatasets"
    >
      <el-table-column label="数据集名称" min-width="220">
        <template #default="{ row }">
          <div class="cell-title">{{ row.datasetName }}</div>
          <div class="cell-sub">{{ row.datasetId }} · {{ typeLabel(row.datasetType) }}</div>
        </template>
      </el-table-column>
      <el-table-column label="样本数量" width="100" align="right">
        <template #default="{ row }">{{ row.totalSamples }}</template>
      </el-table-column>
      <el-table-column label="标注完成率" width="150">
        <template #default="{ row }">
          <el-progress :percentage="row.annotationRate" :stroke-width="8" />
        </template>
      </el-table-column>
      <el-table-column label="审核进度" width="150">
        <template #default="{ row }">
          <div class="cell-progress">
            <el-progress :percentage="row.reviewRate" :stroke-width="8" status="success" />
            <span class="cell-sub">待审核 {{ row.pendingReviewSamples }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="当前版本" width="110">
        <template #default="{ row }">{{ row.datasetVersion }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <StatusTag :status="row.datasetStatus" />
        </template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="最近更新" width="150" />
      <el-table-column label="操作" width="250" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click.stop="openDetail(row)">详情</el-button>
          <el-button link type="primary" @click.stop="goAnnotation(row)">标注</el-button>
          <el-button link type="primary" @click.stop="goVersions(row)">版本</el-button>
          <el-button link type="primary" @click.stop="goImport(row)">导入</el-button>
        </template>
      </el-table-column>

      <template #footer>
        <Pagination
          v-model:page="query.page"
          v-model:page-size="query.pageSize"
          :total="result.total"
          @change="loadDatasets"
        />
      </template>
    </DataTable>

    <DetailDrawer
      v-model="detailVisible"
      :title="detail ? detail.datasetName : '数据集详情'"
      :subtitle="detail ? `${detail.datasetId} · 演示数据 · 查看不会修改真实数据` : ''"
      size="560px"
      :loading="detailLoading"
      :error="detailError"
      @retry="loadDetail"
    >
      <template v-if="detail">
        <section class="detail-block">
          <h4 class="detail-block__title">基础信息</h4>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="数据集 ID">{{ detail.datasetId }}</el-descriptions-item>
            <el-descriptions-item label="数据集类型">{{ typeLabel(detail.datasetType) }}</el-descriptions-item>
            <el-descriptions-item label="当前状态">
              <StatusTag :status="detail.datasetStatus" />
            </el-descriptions-item>
            <el-descriptions-item label="当前版本">{{ detail.datasetVersion }}</el-descriptions-item>
            <el-descriptions-item label="创建人">{{ detail.createdBy }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ detail.createdAt }}</el-descriptions-item>
            <el-descriptions-item label="最近更新">{{ detail.updatedAt }}</el-descriptions-item>
            <el-descriptions-item label="描述">{{ detail.description || '暂无描述' }}</el-descriptions-item>
          </el-descriptions>
        </section>

        <section class="detail-block">
          <h4 class="detail-block__title">数据进度</h4>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="样本总数">{{ detail.totalSamples }}</el-descriptions-item>
            <el-descriptions-item label="已标注">{{ detail.annotatedSamples }}</el-descriptions-item>
            <el-descriptions-item label="标注完成率">{{ detail.annotationRate }}%</el-descriptions-item>
            <el-descriptions-item label="审核通过">{{ detail.approvedSamples }}</el-descriptions-item>
            <el-descriptions-item label="待审核">{{ detail.pendingReviewSamples }}</el-descriptions-item>
            <el-descriptions-item label="质检通过">{{ detail.qualityPassedSamples }}</el-descriptions-item>
            <el-descriptions-item label="不可训练">{{ detail.blockedSamples }}</el-descriptions-item>
            <el-descriptions-item label="可训练率">{{ detail.eligibilityRate }}%</el-descriptions-item>
          </el-descriptions>
          <p class="detail-hint">
            “质检通过”仅在主动运行质检后更新；“可训练率”按来源、许可、标注、质检与重复状态综合计算。
          </p>
        </section>
      </template>

      <template #footer>
        <el-button @click="detail && goImport(detail)">导入数据</el-button>
        <el-button @click="detail && goVersions(detail)">查看版本</el-button>
        <el-button type="primary" @click="detail && goAnnotation(detail)">进入标注工作台</el-button>
      </template>
    </DetailDrawer>

    <el-dialog v-model="createVisible" title="创建数据集" width="480px" append-to-body>
      <el-form label-width="88px" label-position="top">
        <el-form-item label="数据集名称" required>
          <el-input v-model="createForm.datasetName" placeholder="例如：反诈短信文本样本集" maxlength="40" show-word-limit />
        </el-form-item>
        <el-form-item label="数据集类型">
          <el-select v-model="createForm.datasetType" class="create-type">
            <el-option
              v-for="option in options.types"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="数据集描述">
          <el-input
            v-model="createForm.description"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="说明数据用途、来源范围与许可情况"
          />
        </el-form-item>
      </el-form>
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="仅创建本地演示数据集，不会写入任何真实数据服务。"
      />
      <template #footer>
        <el-button :disabled="createLoading" @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createLoading" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.dataset-page {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-5);
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: var(--ss-space-4);
}

.cell-title {
  color: var(--ss-text-primary);
  font-weight: var(--ss-font-weight-medium);
}

.cell-sub {
  margin-top: 2px;
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
}

.cell-progress {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.detail-block {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-3);
}

.detail-block__title {
  color: var(--ss-text-primary);
  font-size: var(--ss-font-size-body);
  font-weight: var(--ss-font-weight-semibold);
}

.detail-hint {
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
  line-height: 1.7;
}

.create-type {
  width: 100%;
}
</style>
