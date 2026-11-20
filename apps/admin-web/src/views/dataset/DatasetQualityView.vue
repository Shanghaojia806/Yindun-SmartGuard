<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ApiError } from '@/api/http'
import {
  fetchDatasetDetail,
  fetchDatasetSamples,
  fetchQualityReport,
  runDatasetQualityCheck,
  submitSampleReview,
} from '@/api/dataset'
import {
  DataTable,
  DetailDrawer,
  ErrorState,
  LoadingState,
  PageHeader,
  StatCard,
  StatusTag,
} from '@/components'
import DatasetWorkspaceHeader from '@/components/dataset/DatasetWorkspaceHeader.vue'
import type {
  DatasetSample,
  DatasetSummary,
  QualityIssue,
  QualityIssueType,
  QualityReport,
  ReviewStatus,
} from '@/types/dataset'

/**
 * A04 数据质检（P10）。
 *
 * 质检必须由用户主动触发；未运行质检的样本保持“未质检”，
 * 不会被自动标记为通过。质检结果只写本地 Mock 数据。
 */

const route = useRoute()
const router = useRouter()
const datasetId = computed(() => String(route.params.id ?? ''))

const dataset = ref<DatasetSummary | null>(null)
const datasetLoading = ref(false)
const datasetError = ref('')

const report = ref<QualityReport | null>(null)
const reportLoading = ref(false)
const reportError = ref('')
const checking = ref(false)

const samples = ref<DatasetSample[]>([])
const sampleMap = computed(() => {
  const map = new Map<string, DatasetSample>()
  for (const sample of samples.value) map.set(sample.sampleId, sample)
  return map
})

const ISSUE_LABELS: Record<QualityIssueType, string> = {
  missing_source: '来源信息缺失',
  missing_license: '许可信息缺失',
  missing_labels: '风险类别缺失',
  missing_evidence: '证据位置缺失',
  incomplete_annotation: '标注不完整',
  duplicate: '重复样本',
  label_conflict: '标签冲突',
  format_error: '格式异常',
}

const SEVERITY_LABELS: Record<QualityIssue['severity'], string> = {
  info: '提示',
  warning: '警告',
  danger: '严重',
}

function severityType(severity: string): 'info' | 'warning' | 'danger' {
  if (severity === 'danger') return 'danger'
  if (severity === 'warning') return 'warning'
  return 'info'
}

function severityLabel(severity: string): string {
  return SEVERITY_LABELS[severity as QualityIssue['severity']] ?? severity
}

function issueLabel(type: string): string {
  return ISSUE_LABELS[type as QualityIssueType] ?? type
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

async function loadSamples() {
  if (!datasetId.value) return
  try {
    const page = await fetchDatasetSamples({
      datasetId: datasetId.value,
      keyword: '',
      sourceType: '',
      annotationStatus: '',
      reviewStatus: '',
      licenseStatus: '',
      page: 1,
      pageSize: 500,
    })
    samples.value = page.items
  } catch {
    samples.value = []
  }
}

async function loadReport() {
  if (!datasetId.value) return
  reportLoading.value = true
  reportError.value = ''
  try {
    report.value = await fetchQualityReport(datasetId.value)
  } catch (cause) {
    report.value = null
    reportError.value = resolveMessage(cause, '质检报告加载失败')
  } finally {
    reportLoading.value = false
  }
}

async function runCheck() {
  if (!datasetId.value) return
  checking.value = true
  try {
    report.value = await runDatasetQualityCheck(datasetId.value)
    ElMessage.success('质检已完成，结果已写入本地 Mock 数据')
    await Promise.all([loadDataset(), loadSamples()])
  } catch (cause) {
    ElMessage.error(resolveMessage(cause, '质检执行失败'))
  } finally {
    checking.value = false
  }
}

async function bootstrap() {
  await loadDataset()
  if (!dataset.value) return
  await Promise.all([loadSamples(), loadReport()])
}

onMounted(bootstrap)

watch(datasetId, () => {
  report.value = null
  samples.value = []
  void bootstrap()
})

/* ===== 筛选 ===== */
const issueTypeFilter = ref<QualityIssueType | ''>('')
const severityFilter = ref<QualityIssue['severity'] | ''>('')

const filteredIssues = computed(() => {
  const issues = report.value?.issues ?? []
  return issues.filter((issue) => {
    if (issueTypeFilter.value && issue.issueType !== issueTypeFilter.value) return false
    if (severityFilter.value && issue.severity !== severityFilter.value) return false
    return true
  })
})

const manualReviewSamples = computed(() =>
  samples.value.filter((sample) => sample.qualityResult === 'manual_review'),
)

/* ===== 查看问题 ===== */
const detailVisible = ref(false)
const detailSampleId = ref('')
const detailSample = computed(() => sampleMap.value.get(detailSampleId.value) ?? null)
const detailIssues = computed(() =>
  (report.value?.issues ?? []).filter((issue) => issue.sampleId === detailSampleId.value),
)

function openIssueDetail(issue: QualityIssue) {
  detailSampleId.value = issue.sampleId
  detailVisible.value = true
}

function locateSample(sampleId: string) {
  void router.push({
    path: `/admin/datasets/${datasetId.value}/annotation`,
    query: { sampleId },
  })
}

/* ===== 审核意见 ===== */
const reviewVisible = ref(false)
const reviewSampleId = ref('')
const reviewResult = ref<ReviewStatus>('approved')
const reviewNotes = ref('')
const reviewLoading = ref(false)

function openReview(sampleId: string) {
  reviewSampleId.value = sampleId
  reviewResult.value = 'approved'
  reviewNotes.value = ''
  reviewVisible.value = true
}

async function submitReview() {
  if (!reviewSampleId.value) return
  reviewLoading.value = true
  try {
    await submitSampleReview(datasetId.value, reviewSampleId.value, reviewResult.value, reviewNotes.value)
    ElMessage.success('审核意见已记录到本地 Mock 数据')
    reviewVisible.value = false
    await Promise.all([loadSamples(), loadReport(), loadDataset()])
  } catch (cause) {
    ElMessage.error(resolveMessage(cause, '审核意见提交失败'))
  } finally {
    reviewLoading.value = false
  }
}
</script>

<template>
  <div class="quality-page">
    <LoadingState v-if="datasetLoading" text="正在加载质检工作台" min-height="360px" />
    <ErrorState v-else-if="datasetError" :description="datasetError" @retry="loadDataset" />

    <template v-else-if="dataset">
      <DatasetWorkspaceHeader :dataset="dataset" active="quality" />

      <PageHeader
        eyebrow="A04 · 数据质检"
        title="数据质量检查"
        description="检查来源、许可、标签、证据、格式与重复等问题。质检需人工触发，未检查的数据不会自动标记为通过。"
        :divider="false"
      >
        <template #extra>
          <el-tag type="warning" effect="plain">演示数据（Mock）</el-tag>
          <el-button :loading="checking" type="primary" @click="runCheck">
            {{ report ? '重新质检' : '开始质检' }}
          </el-button>
        </template>
      </PageHeader>

      <el-alert
        v-if="!report && !reportLoading"
        type="info"
        :closable="false"
        show-icon
        title="尚未运行质检"
        description="当前所有样本保持“未质检”状态。点击“开始质检”后才会生成本地演示质检结果。"
      />
      <el-alert v-if="reportError" type="error" :closable="false" show-icon :title="reportError" />

      <section v-if="report" class="quality-summary">
        <StatCard label="本次检查样本" :value="report.totalChecked" :hint="`生成时间 ${report.generatedAt}`" />
        <StatCard label="通过" :value="report.passedCount" hint="无质检问题" tone="safe" />
        <StatCard label="待修正" :value="report.needsFixCount" hint="可修正后重新质检" tone="warning" />
        <StatCard label="不通过" :value="report.failedCount" hint="格式或许可阻断" tone="danger" />
        <StatCard label="待人工复核" :value="report.manualReviewCount" hint="疑似重复等待确认" tone="warning" />
      </section>

      <section class="quality-block">
        <header class="quality-block__header">
          <h3 class="quality-block__title">质检问题</h3>
          <div class="quality-block__filters">
            <el-select v-model="issueTypeFilter" size="small" placeholder="全部问题类型" clearable>
              <el-option
                v-for="(label, value) in ISSUE_LABELS"
                :key="value"
                :label="label"
                :value="value"
              />
            </el-select>
            <el-select v-model="severityFilter" size="small" placeholder="全部严重程度" clearable>
              <el-option label="严重" value="danger" />
              <el-option label="警告" value="warning" />
              <el-option label="提示" value="info" />
            </el-select>
          </div>
        </header>

        <DataTable
          v-if="report"
          :data="filteredIssues"
          :loading="reportLoading"
          row-key="issueId"
          size="small"
          empty-title="没有质检问题"
          empty-description="当前筛选条件下没有问题记录；若尚未质检，请先点击“开始质检”。"
          loading-min-height="200px"
        >
          <el-table-column label="样本" min-width="170">
            <template #default="{ row }">
              <div class="cell-title">{{ row.sampleName }}</div>
              <div class="cell-sub">{{ row.sampleId }}</div>
            </template>
          </el-table-column>
          <el-table-column label="问题类型" width="150">
            <template #default="{ row }">{{ issueLabel(row.issueType) }}</template>
          </el-table-column>
          <el-table-column prop="description" label="问题描述" min-width="240" />
          <el-table-column label="严重程度" width="110">
            <template #default="{ row }">
              <el-tag :type="severityType(row.severity)" size="small">
                {{ severityLabel(row.severity) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="260" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click.stop="openIssueDetail(row)">查看问题</el-button>
              <el-button link type="primary" @click.stop="locateSample(row.sampleId)">修正标注</el-button>
              <el-button link type="primary" @click.stop="openReview(row.sampleId)">审核意见</el-button>
            </template>
          </el-table-column>
        </DataTable>
      </section>

      <section class="quality-block">
        <header class="quality-block__header">
          <h3 class="quality-block__title">待人工复核样本</h3>
          <span class="quality-block__hint">疑似重复等无法自动判定的样本，需要人工确认</span>
        </header>
        <ul v-if="manualReviewSamples.length" class="review-list">
          <li v-for="sample in manualReviewSamples" :key="sample.sampleId" class="review-list__item">
            <div>
              <p class="cell-title">{{ sample.sampleName }}</p>
              <p class="cell-sub">{{ sample.sampleId }} · {{ sample.sourceName || '来源缺失' }}</p>
            </div>
            <div class="review-list__actions">
              <StatusTag :status="sample.duplicateStatus" />
              <el-button size="small" @click="locateSample(sample.sampleId)">定位样本</el-button>
              <el-button size="small" type="primary" plain @click="openReview(sample.sampleId)">
                记录审核意见
              </el-button>
            </div>
          </li>
        </ul>
        <p v-else class="quality-empty">暂无待人工复核样本；运行质检后此处会显示需要人工判断的样本。</p>
      </section>
    </template>

    <DetailDrawer
      v-model="detailVisible"
      :title="detailSample ? detailSample.sampleName : '样本问题详情'"
      :subtitle="detailSample ? `${detailSample.sampleId} · 质检问题定位` : ''"
      size="540px"
    >
      <template v-if="detailSample">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="来源名称">{{ detailSample.sourceName || '缺失' }}</el-descriptions-item>
          <el-descriptions-item label="许可状态">
            <StatusTag :status="detailSample.licenseStatus" />
          </el-descriptions-item>
          <el-descriptions-item label="标注状态">
            <StatusTag :status="detailSample.annotationStatus" />
          </el-descriptions-item>
          <el-descriptions-item label="审核状态">
            <StatusTag :status="detailSample.reviewStatus" />
          </el-descriptions-item>
          <el-descriptions-item label="重复状态">
            <StatusTag :status="detailSample.duplicateStatus" />
          </el-descriptions-item>
          <el-descriptions-item label="质检结果">
            <StatusTag :status="detailSample.qualityResult" />
          </el-descriptions-item>
        </el-descriptions>

        <section class="detail-block">
          <h4 class="detail-block__title">问题明细（{{ detailIssues.length }} 项）</h4>
          <ul class="issue-list">
            <li v-for="issue in detailIssues" :key="issue.issueId">
              <el-tag :type="severityType(issue.severity)" size="small" effect="plain">
                {{ issueLabel(issue.issueType) }}
              </el-tag>
              <span class="issue-list__text">{{ issue.description }}</span>
            </li>
          </ul>
          <p v-if="detailIssues.length === 0" class="quality-empty">该样本当前没有质检问题。</p>
        </section>
      </template>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button type="primary" @click="detailSample && locateSample(detailSample.sampleId)">
          去修正标注
        </el-button>
      </template>
    </DetailDrawer>

    <el-dialog v-model="reviewVisible" title="记录审核意见" width="480px" append-to-body>
      <el-form label-position="top">
        <el-form-item label="审核结果">
          <el-select v-model="reviewResult">
            <el-option label="通过" value="approved" />
            <el-option label="待修正" value="needs_fix" />
            <el-option label="驳回" value="rejected" />
          </el-select>
        </el-form-item>
        <el-form-item label="审核意见">
          <el-input
            v-model="reviewNotes"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="说明问题与后续处理要求"
          />
        </el-form-item>
      </el-form>
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="审核结果只写入本地 Mock 数据，不代表真实审核结论。"
      />
      <template #footer>
        <el-button :disabled="reviewLoading" @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" :loading="reviewLoading" @click="submitReview">提交审核意见</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.quality-page {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-4);
}

.quality-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--ss-space-3);
}

.quality-block {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-3);
  padding: var(--ss-space-5);
  border: 1px solid var(--ss-border);
  border-radius: var(--ss-radius-lg);
  background: var(--ss-bg-card);
  box-shadow: var(--ss-shadow-sm);
}

.quality-block__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ss-space-3);
  flex-wrap: wrap;
}

.quality-block__title {
  font-size: var(--ss-font-size-body-lg);
  font-weight: var(--ss-font-weight-semibold);
}

.quality-block__hint {
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
}

.quality-block__filters {
  display: flex;
  gap: var(--ss-space-2);
}

.quality-block__filters :deep(.el-select) {
  width: 170px;
}

.review-list {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.review-list__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ss-space-3);
  padding: var(--ss-space-3);
  border: 1px solid var(--ss-divider);
  border-radius: var(--ss-radius-md);
  background: var(--ss-bg-subtle);
}

.review-list__actions {
  display: flex;
  align-items: center;
  gap: var(--ss-space-2);
}

.quality-empty {
  padding: var(--ss-space-4);
  border: 1px dashed var(--ss-border-strong);
  border-radius: var(--ss-radius-md);
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-sm);
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

.detail-block {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-3);
}

.detail-block__title {
  font-size: var(--ss-font-size-body);
  font-weight: var(--ss-font-weight-semibold);
}

.issue-list {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.issue-list li {
  display: flex;
  align-items: flex-start;
  gap: var(--ss-space-2);
  padding: var(--ss-space-2) var(--ss-space-3);
  border: 1px solid var(--ss-divider);
  border-radius: var(--ss-radius-sm);
  background: var(--ss-bg-subtle);
}

.issue-list__text {
  color: var(--ss-text-secondary);
  font-size: var(--ss-font-size-sm);
}
</style>
