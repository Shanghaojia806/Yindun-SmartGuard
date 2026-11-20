<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { ApiError } from '@/api/http'
import {
  evaluateSampleEligibility,
  fetchDatasetDetail,
  fetchDatasetLabels,
  fetchDatasetSample,
  fetchDatasetSamples,
  saveSampleAnnotation,
  submitSampleReview,
} from '@/api/dataset'
import { DataTable, ErrorState, LoadingState, Pagination, StatusTag } from '@/components'
import AnnotationPanel from '@/components/dataset/AnnotationPanel.vue'
import DatasetWorkspaceHeader from '@/components/dataset/DatasetWorkspaceHeader.vue'
import type {
  AnnotationDraft,
  DatasetSample,
  DatasetSummary,
  ReviewStatus,
  RiskLabel,
  SampleQuery,
  SourceType,
  TrainingEligibility,
} from '@/types/dataset'

/**
 * A04 标注工作台（P10）。
 *
 * 左侧样本列表 + 右侧标注面板。保存时调用 API Service 写入 Mock Repository，
 * 随后重新读取样本，验证标注结果在重新加载后仍然存在。
 */

const route = useRoute()
const datasetId = computed(() => String(route.params.id ?? ''))

const dataset = ref<DatasetSummary | null>(null)
const datasetLoading = ref(false)
const datasetError = ref('')
const labels = ref<RiskLabel[]>([])

const DEFAULT_QUERY: SampleQuery = {
  datasetId: '',
  keyword: '',
  sourceType: '',
  annotationStatus: '',
  reviewStatus: '',
  licenseStatus: '',
  page: 1,
  pageSize: 10,
}

const query = reactive<SampleQuery>({ ...DEFAULT_QUERY })
const rows = ref<DatasetSample[]>([])
const total = ref(0)
const listLoading = ref(false)
const listError = ref('')

const selected = ref<DatasetSample | null>(null)
const selectedLoading = ref(false)
const eligibility = ref<TrainingEligibility | null>(null)
const saving = ref(false)

const SOURCE_LABELS: Record<SourceType, string> = {
  team_record: '团队自建',
  partner_authorized: '合作授权',
  synthetic: '合成数据',
  public_licensed: '公开许可',
}

function sourceLabel(type: string): string {
  return SOURCE_LABELS[type as SourceType] ?? type
}

function resolveMessage(cause: unknown, fallback: string): string {
  return cause instanceof ApiError ? cause.message : fallback
}

async function loadDataset() {
  if (!datasetId.value) return
  datasetLoading.value = true
  datasetError.value = ''
  try {
    dataset.value = await fetchDatasetDetail(datasetId.value)
  } catch (cause) {
    dataset.value = null
    datasetError.value = resolveMessage(cause, '数据集信息加载失败')
  } finally {
    datasetLoading.value = false
  }
}

async function loadLabels() {
  try {
    labels.value = await fetchDatasetLabels()
  } catch {
    labels.value = []
  }
}

async function loadSamples() {
  if (!datasetId.value) return
  query.datasetId = datasetId.value
  listLoading.value = true
  listError.value = ''
  try {
    const page = await fetchDatasetSamples(query)
    rows.value = page.items
    total.value = page.total
    if (page.items.length === 0) {
      selected.value = null
      eligibility.value = null
    }
  } catch (cause) {
    rows.value = []
    total.value = 0
    listError.value = resolveMessage(cause, '样本列表加载失败')
  } finally {
    listLoading.value = false
  }
}

async function selectSample(sampleId: string) {
  selectedLoading.value = true
  try {
    const sample = await fetchDatasetSample(datasetId.value, sampleId)
    selected.value = sample
    eligibility.value = await evaluateSampleEligibility(datasetId.value, sampleId)
  } catch (cause) {
    selected.value = null
    eligibility.value = null
    ElMessage.error(resolveMessage(cause, '样本详情加载失败'))
  } finally {
    selectedLoading.value = false
  }
}

async function bootstrap() {
  await loadDataset()
  if (!dataset.value) return
  await loadLabels()
  await loadSamples()
  const querySampleId = typeof route.query.sampleId === 'string' ? route.query.sampleId : ''
  const target = querySampleId || rows.value[0]?.sampleId
  if (target) await selectSample(target)
}

onMounted(bootstrap)

watch(datasetId, () => {
  selected.value = null
  eligibility.value = null
  rows.value = []
  total.value = 0
  Object.assign(query, { ...DEFAULT_QUERY, datasetId: datasetId.value })
  void bootstrap()
})

function handleSearch() {
  query.page = 1
  void loadSamples()
}

function handleReset() {
  Object.assign(query, { ...DEFAULT_QUERY, datasetId: datasetId.value, pageSize: query.pageSize })
  void loadSamples()
}

async function refreshAfterWrite(sampleId: string) {
  await Promise.all([loadDataset(), loadSamples()])
  await selectSample(sampleId)
}

async function handleSave(draft: AnnotationDraft) {
  if (!selected.value) return
  saving.value = true
  const sampleId = selected.value.sampleId
  try {
    await saveSampleAnnotation(datasetId.value, sampleId, draft)
    ElMessage.success('标注已保存到本地 Mock Repository，重新进入页面仍可读取')
    await refreshAfterWrite(sampleId)
  } catch (cause) {
    ElMessage.error(resolveMessage(cause, '标注保存失败，请稍后重试'))
  } finally {
    saving.value = false
  }
}

async function handleSubmit(draft: AnnotationDraft) {
  if (!selected.value) return
  saving.value = true
  const sampleId = selected.value.sampleId
  try {
    await saveSampleAnnotation(datasetId.value, sampleId, { ...draft, annotationStatus: 'submitted' })
    ElMessage.success('已提交复核（本地 Mock）')
    await refreshAfterWrite(sampleId)
  } catch (cause) {
    ElMessage.error(resolveMessage(cause, '提交复核失败，请稍后重试'))
  } finally {
    saving.value = false
  }
}

async function handleReview(result: ReviewStatus) {
  if (!selected.value) return
  saving.value = true
  const sampleId = selected.value.sampleId
  try {
    await submitSampleReview(datasetId.value, sampleId, result, '演示复核意见：由标注工作台直接记录')
    ElMessage.success('复核结果已写入本地 Mock 数据')
    await refreshAfterWrite(sampleId)
  } catch (cause) {
    ElMessage.error(resolveMessage(cause, '复核操作失败'))
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="annotation-page">
    <LoadingState v-if="datasetLoading" text="正在加载数据集工作台" min-height="360px" />
    <ErrorState v-else-if="datasetError" :description="datasetError" @retry="loadDataset" />

    <template v-else-if="dataset">
      <DatasetWorkspaceHeader :dataset="dataset" active="annotation" />

      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="标注仅写入本地 Mock Repository；刷新浏览器会恢复到初始演示数据，真实标注服务尚未接入。"
      />

      <div class="annotation-layout">
        <section class="annotation-list">
          <div class="annotation-filters">
            <el-input
              v-model="query.keyword"
              size="small"
              placeholder="搜索样本文件名"
              clearable
              class="annotation-filters__keyword"
              @keyup.enter="handleSearch"
            />
            <el-select v-model="query.annotationStatus" size="small" placeholder="标注状态" clearable>
              <el-option label="未标注" value="unannotated" />
              <el-option label="标注中" value="in_progress" />
              <el-option label="已提交" value="submitted" />
              <el-option label="待复核" value="reviewing" />
              <el-option label="已完成" value="completed" />
            </el-select>
            <el-select v-model="query.reviewStatus" size="small" placeholder="审核状态" clearable>
              <el-option label="待审核" value="pending" />
              <el-option label="已通过" value="approved" />
              <el-option label="待修正" value="needs_fix" />
              <el-option label="已驳回" value="rejected" />
            </el-select>
            <el-select v-model="query.licenseStatus" size="small" placeholder="许可状态" clearable>
              <el-option label="已核验" value="verified" />
              <el-option label="待核验" value="pending" />
              <el-option label="缺失" value="missing" />
            </el-select>
            <div class="annotation-filters__actions">
              <el-button size="small" @click="handleReset">重置</el-button>
              <el-button size="small" type="primary" @click="handleSearch">查询</el-button>
            </div>
          </div>

          <DataTable
            :data="rows"
            :loading="listLoading"
            :error="listError"
            row-key="sampleId"
            size="small"
            empty-title="没有匹配的样本"
            empty-description="请调整筛选条件；演示数据集在重置筛选后可恢复全部样本。"
            loading-min-height="220px"
            @row-click="(row: DatasetSample) => selectSample(row.sampleId)"
            @retry="loadSamples"
          >
            <el-table-column label="样本" min-width="170">
              <template #default="{ row }">
                <div class="cell-title">{{ row.sampleName }}</div>
                <div class="cell-sub">{{ row.sampleId }}</div>
              </template>
            </el-table-column>
            <el-table-column label="来源" min-width="120">
              <template #default="{ row }">
                <span v-if="row.sourceName" class="cell-text">{{ sourceLabel(row.sourceType) }}</span>
                <StatusTag v-else status="missing" label="来源缺失" />
              </template>
            </el-table-column>
            <el-table-column label="风险类别" min-width="180">
              <template #default="{ row }">
                <div class="cell-tags">
                  <el-tag v-for="label in row.riskCategories" :key="label" size="small" effect="plain">
                    {{ label }}
                  </el-tag>
                  <span v-if="row.riskCategories.length === 0" class="cell-sub">未标注</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="标注" width="96">
              <template #default="{ row }">
                <StatusTag :status="row.annotationStatus" />
              </template>
            </el-table-column>
            <el-table-column label="审核" width="96">
              <template #default="{ row }">
                <StatusTag :status="row.reviewStatus" />
              </template>
            </el-table-column>
            <el-table-column label="许可" width="90">
              <template #default="{ row }">
                <StatusTag :status="row.licenseStatus" />
              </template>
            </el-table-column>
            <el-table-column label="重复" width="100">
              <template #default="{ row }">
                <StatusTag :status="row.duplicateStatus" />
              </template>
            </el-table-column>

            <template #footer>
              <Pagination
                v-model:page="query.page"
                v-model:page-size="query.pageSize"
                :total="total"
                :page-sizes="[10, 20, 50]"
                @change="loadSamples"
              />
            </template>
          </DataTable>
        </section>

        <section class="annotation-detail">
          <div v-if="selectedLoading" class="annotation-detail__placeholder">
            <LoadingState text="正在加载样本标注" min-height="260px" />
          </div>
          <template v-else-if="selected">
            <el-alert
              v-if="eligibility && !eligibility.eligible"
              type="warning"
              :closable="false"
              show-icon
              title="该样本暂不具备训练资格"
            >
              <ul class="eligibility-list">
                <li v-for="reason in eligibility.reasons" :key="reason">{{ reason }}</li>
              </ul>
            </el-alert>
            <el-alert
              v-else-if="eligibility"
              type="success"
              :closable="false"
              show-icon
              title="该样本已满足前端训练资格规则（真实训练前仍需后端复核）"
            />

            <AnnotationPanel
              :sample="selected"
              :labels="labels"
              :saving="saving"
              @save="handleSave"
              @submit="handleSubmit"
              @review="handleReview"
            />
          </template>
          <el-empty
            v-else
            description="请选择左侧样本开始标注"
            :image-size="120"
          />
        </section>
      </div>
    </template>
  </div>
</template>

<style scoped>
.annotation-page {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-4);
}

.annotation-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr);
  gap: var(--ss-space-4);
  align-items: start;
}

.annotation-list {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-3);
  min-width: 0;
}

.annotation-filters {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ss-space-2);
  padding: var(--ss-space-3);
  border: 1px solid var(--ss-border);
  border-radius: var(--ss-radius-lg);
  background: var(--ss-bg-card);
}

.annotation-filters__keyword {
  flex: 1;
  min-width: 160px;
}

.annotation-filters__actions {
  display: flex;
  gap: var(--ss-space-1);
  margin-left: auto;
}

.annotation-detail {
  position: sticky;
  top: calc(var(--ss-header-height) + var(--ss-space-4));
  min-width: 0;
}

.annotation-detail__placeholder {
  border: 1px solid var(--ss-border);
  border-radius: var(--ss-radius-lg);
  background: var(--ss-bg-card);
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

.cell-text {
  color: var(--ss-text-secondary);
  font-size: var(--ss-font-size-sm);
}

.cell-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.eligibility-list {
  margin: var(--ss-space-1) 0 0;
  padding-left: var(--ss-space-4);
  font-size: var(--ss-font-size-xs);
  line-height: 1.8;
}

@media (max-width: 1180px) {
  .annotation-layout {
    grid-template-columns: 1fr;
  }

  .annotation-detail {
    position: static;
  }
}
</style>
