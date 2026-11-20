<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { ApiError } from '@/api/http'
import {
  changeVersionStatus,
  createDatasetVersion,
  fetchDatasetDetail,
  fetchDatasetVersion,
  fetchDatasetVersions,
  fetchVersionCompare,
  reviewDatasetVersion,
} from '@/api/dataset'
import {
  ConfirmDialog,
  DataTable,
  DetailDrawer,
  ErrorState,
  LoadingState,
  PageHeader,
  StatusTag,
} from '@/components'
import DatasetWorkspaceHeader from '@/components/dataset/DatasetWorkspaceHeader.vue'
import type {
  DatasetSummary,
  DatasetVersion,
  VersionCompareRow,
  VersionStatus,
} from '@/types/dataset'

/**
 * A04 数据集版本管理（P10）。
 *
 * 已发布版本在 Mock 规则层同样禁止修改状态或覆盖内容；
 * 任何数据变更都应创建新版本记录。
 */

const route = useRoute()
const datasetId = computed(() => String(route.params.id ?? ''))

const dataset = ref<DatasetSummary | null>(null)
const datasetLoading = ref(false)
const datasetError = ref('')

const versions = ref<DatasetVersion[]>([])
const versionsLoading = ref(false)
const versionsError = ref('')

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

async function loadVersions() {
  if (!datasetId.value) return
  versionsLoading.value = true
  versionsError.value = ''
  try {
    versions.value = await fetchDatasetVersions(datasetId.value)
  } catch (cause) {
    versions.value = []
    versionsError.value = resolveMessage(cause, '版本列表加载失败')
  } finally {
    versionsLoading.value = false
  }
}

async function bootstrap() {
  await loadDataset()
  if (!dataset.value) return
  await loadVersions()
}

onMounted(bootstrap)

watch(datasetId, () => {
  versions.value = []
  void bootstrap()
})

/* ===== 创建版本 ===== */
const createVisible = ref(false)
const createLoading = ref(false)
const createDescription = ref('')
const createChangeLog = ref('')

function openCreate() {
  createDescription.value = ''
  createChangeLog.value = ''
  createVisible.value = true
}

async function submitCreate() {
  createLoading.value = true
  try {
    const created = await createDatasetVersion(datasetId.value, {
      versionDescription: createDescription.value,
      changeLog: createChangeLog.value,
    })
    ElMessage.success(`已创建版本 ${created.versionNumber}（草稿，本地 Mock）`)
    createVisible.value = false
    await Promise.all([loadDataset(), loadVersions()])
  } catch (cause) {
    ElMessage.error(resolveMessage(cause, '创建版本失败，请先完成标注与质检'))
  } finally {
    createLoading.value = false
  }
}

/* ===== 详情与差异 ===== */
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detailVersion = ref<DatasetVersion | null>(null)
const baseVersionId = ref('')
const compareRows = ref<VersionCompareRow[]>([])
const compareLoading = ref(false)

const otherVersions = computed(() =>
  versions.value.filter((item) => item.versionId !== detailVersion.value?.versionId),
)

async function loadDetail(versionId: string) {
  detailLoading.value = true
  detailError.value = ''
  try {
    detailVersion.value = await fetchDatasetVersion(versionId)
  } catch (cause) {
    detailVersion.value = null
    detailError.value = resolveMessage(cause, '版本详情加载失败')
  } finally {
    detailLoading.value = false
  }
}

async function loadCompare() {
  if (!detailVersion.value || !baseVersionId.value) {
    compareRows.value = []
    return
  }
  compareLoading.value = true
  try {
    compareRows.value = await fetchVersionCompare(detailVersion.value.versionId, baseVersionId.value)
  } catch (cause) {
    compareRows.value = []
    ElMessage.error(resolveMessage(cause, '版本差异加载失败'))
  } finally {
    compareLoading.value = false
  }
}

async function openDetail(version: DatasetVersion) {
  detailVisible.value = true
  baseVersionId.value = ''
  compareRows.value = []
  await loadDetail(version.versionId)
  const base = versions.value.find((item) => item.versionId !== version.versionId)
  if (base) {
    baseVersionId.value = base.versionId
    await loadCompare()
  }
}

/* ===== 状态变更 ===== */
const confirmVisible = ref(false)
const confirmLoading = ref(false)
const pendingVersion = ref<DatasetVersion | null>(null)
const pendingStatus = ref<VersionStatus>('reviewing')

const STATUS_TITLE: Record<VersionStatus, string> = {
  draft: '退回草稿',
  reviewing: '提交版本审核',
  published: '发布版本',
}

const STATUS_MESSAGE: Record<VersionStatus, string> = {
  draft: '退回草稿后仍可继续修改，请确认已完成必要的数据处理。',
  reviewing: '提交审核后版本进入审核中状态，可继续等待发布。',
  published: '发布后该版本将锁定，不允许直接覆盖修改；后续数据变更必须创建新版本。',
}

function askStatus(version: DatasetVersion, status: VersionStatus) {
  if (status === 'published' && version.manualReviewStatus === 'pending') {
    ElMessage.warning('该版本存在同源分组拆分泄漏风险，请先完成人工复核再发布')
    return
  }
  pendingVersion.value = version
  pendingStatus.value = status
  confirmVisible.value = true
}

async function confirmStatus() {
  if (!pendingVersion.value) return
  confirmLoading.value = true
  try {
    const updated = await changeVersionStatus(pendingVersion.value.versionId, pendingStatus.value)
    ElMessage.success(`版本 ${updated.versionNumber} 状态已更新为 ${STATUS_TITLE[pendingStatus.value]}`)
    confirmVisible.value = false
    await Promise.all([loadDataset(), loadVersions()])
    if (detailVersion.value?.versionId === updated.versionId) {
      await loadDetail(updated.versionId)
    }
  } catch (cause) {
    ElMessage.error(resolveMessage(cause, '版本状态更新失败'))
  } finally {
    confirmLoading.value = false
  }
}

function versionStatusLabel(status: string): string {
  if (status === 'draft') return '草稿'
  if (status === 'reviewing') return '审核中'
  return '已发布'
}

/* ===== 同源拆分人工复核 ===== */
const reviewVisible = ref(false)
const reviewLoading = ref(false)
const reviewNotes = ref('')
const pendingReviewVersion = ref<DatasetVersion | null>(null)

const LEAKAGE_LABEL: Record<string, string> = {
  grouped: '按来源分组（相似度未验证）',
  partial_group_split: '部分来源分组被拆分（存在泄漏风险）',
  unverified: '未验证分组隔离',
}

const MANUAL_REVIEW_LABEL: Record<string, string> = {
  not_required: '无需复核',
  pending: '待人工复核',
  approved: '已人工复核',
}

function openManualReview(version: DatasetVersion) {
  pendingReviewVersion.value = version
  reviewNotes.value = ''
  reviewVisible.value = true
}

async function submitManualReview() {
  if (!pendingReviewVersion.value) return
  reviewLoading.value = true
  try {
    const updated = await reviewDatasetVersion(pendingReviewVersion.value.versionId, reviewNotes.value)
    ElMessage.success(`版本 ${updated.versionNumber} 已完成人工复核，可继续提交审核与发布`)
    reviewVisible.value = false
    await Promise.all([loadDataset(), loadVersions()])
    if (detailVersion.value?.versionId === updated.versionId) {
      await loadDetail(updated.versionId)
    }
  } catch (cause) {
    ElMessage.error(resolveMessage(cause, '人工复核记录失败'))
  } finally {
    reviewLoading.value = false
  }
}
</script>

<template>
  <div class="versions-page">
    <LoadingState v-if="datasetLoading" text="正在加载版本管理" min-height="360px" />
    <ErrorState v-else-if="datasetError" :description="datasetError" @retry="loadDataset" />

    <template v-else-if="dataset">
      <DatasetWorkspaceHeader :dataset="dataset" active="versions" />

      <PageHeader
        eyebrow="A04 · 版本管理"
        title="数据集版本"
        description="版本记录固定样本数量与拆分结果。已发布版本不允许直接覆盖修改，任何数据变更都应产生新版本。"
        :divider="false"
      >
        <template #extra>
          <el-tag type="warning" effect="plain">演示数据（Mock）</el-tag>
          <el-button @click="loadVersions">刷新版本</el-button>
          <el-button type="primary" @click="openCreate">创建版本</el-button>
        </template>
      </PageHeader>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="版本规则"
        description="创建版本要求存在满足训练资格的样本；发布后的版本在本地 Mock 规则中同样被锁定。"
      />

      <el-alert v-if="versionsError" type="error" :closable="false" show-icon :title="versionsError" />

      <DataTable
        :data="versions"
        :loading="versionsLoading"
        :error="versionsError"
        row-key="versionId"
        empty-title="暂无版本记录"
        empty-description="创建数据集时会自动生成初始版本，也可以点击“创建版本”补充新版本。"
        @retry="loadVersions"
        @row-click="openDetail"
      >
        <el-table-column label="版本" width="120">
          <template #default="{ row }">
            <span class="version-number">{{ row.versionNumber }}</span>
            <div class="cell-sub">{{ row.versionId }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="150">
          <template #default="{ row }">
            <StatusTag :status="row.versionStatus" />
            <el-tag
              v-if="row.manualReviewStatus === 'pending'"
              type="warning"
              size="small"
              effect="plain"
              class="review-tag"
            >
              同源待复核
            </el-tag>
            <el-tag
              v-else-if="row.manualReviewStatus === 'approved'"
              type="success"
              size="small"
              effect="plain"
              class="review-tag"
            >
              同源已复核
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="样本数量" width="100" align="right">
          <template #default="{ row }">{{ row.sampleCount }}</template>
        </el-table-column>
        <el-table-column label="训练 / 验证 / 测试" width="180">
          <template #default="{ row }">
            {{ row.trainCount }} / {{ row.validationCount }} / {{ row.testCount }}
          </template>
        </el-table-column>
        <el-table-column prop="versionDescription" label="版本说明" min-width="220" />
        <el-table-column prop="createdBy" label="创建人" width="100" />
        <el-table-column prop="createdAt" label="创建时间" width="160" />
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openDetail(row)">详情</el-button>
            <el-button
              v-if="row.manualReviewStatus === 'pending'"
              link
              type="warning"
              @click.stop="openManualReview(row)"
            >
              人工复核
            </el-button>
            <el-button
              v-if="row.versionStatus === 'draft'"
              link
              type="primary"
              @click.stop="askStatus(row, 'reviewing')"
            >
              提交审核
            </el-button>
            <el-button
              v-else-if="row.versionStatus === 'reviewing'"
              link
              type="primary"
              :disabled="row.manualReviewStatus === 'pending'"
              @click.stop="askStatus(row, 'published')"
            >
              发布
            </el-button>
            <el-button
              v-else
              link
              disabled
            >
              已锁定
            </el-button>
            <el-button
              v-if="row.versionStatus === 'reviewing'"
              link
              type="warning"
              @click.stop="askStatus(row, 'draft')"
            >
              退回草稿
            </el-button>
          </template>
        </el-table-column>
      </DataTable>
    </template>

    <DetailDrawer
      v-model="detailVisible"
      :title="detailVersion ? `版本 ${detailVersion.versionNumber}` : '版本详情'"
      :subtitle="detailVersion ? `${detailVersion.versionId} · 本地 Mock 版本记录` : ''"
      size="620px"
      :loading="detailLoading"
      :error="detailError"
      @retry="detailVersion && loadDetail(detailVersion.versionId)"
    >
      <template v-if="detailVersion">
        <el-alert
          v-if="detailVersion.versionStatus === 'published'"
          type="success"
          :closable="false"
          show-icon
          title="该版本已发布并锁定，不允许覆盖修改；如需调整数据请创建新版本。"
        />

        <el-alert
          v-if="detailVersion.manualReviewStatus === 'pending'"
          type="warning"
          :closable="false"
          show-icon
          title="该版本未完成同源拆分人工复核，暂不允许发布为正式可训练版本。"
          description="创建版本时检测到同一来源分组被拆分到多个集合，存在同源数据泄漏风险。请核实后记录复核结论。"
        />
        <el-alert
          v-else-if="detailVersion.manualReviewStatus === 'approved'"
          type="success"
          :closable="false"
          show-icon
          title="该版本的同源拆分风险已完成人工复核。"
          :description="`复核人：${detailVersion.manualReviewedBy ?? '演示管理员'} · ${detailVersion.manualReviewedAt ?? ''}`"
        />

        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="版本号">{{ detailVersion.versionNumber }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <StatusTag :status="detailVersion.versionStatus" />
          </el-descriptions-item>
          <el-descriptions-item label="样本数量">{{ detailVersion.sampleCount }}</el-descriptions-item>
          <el-descriptions-item label="训练集">{{ detailVersion.trainCount }}</el-descriptions-item>
          <el-descriptions-item label="验证集">{{ detailVersion.validationCount }}</el-descriptions-item>
          <el-descriptions-item label="测试集">{{ detailVersion.testCount }}</el-descriptions-item>
          <el-descriptions-item label="创建人">{{ detailVersion.createdBy }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detailVersion.createdAt }}</el-descriptions-item>
          <el-descriptions-item label="版本说明" :span="2">
            {{ detailVersion.versionDescription }}
          </el-descriptions-item>
          <el-descriptions-item label="数据泄漏风险" :span="2">
            {{ LEAKAGE_LABEL[detailVersion.leakageRisk ?? 'unverified'] ?? '未记录' }}
          </el-descriptions-item>
          <el-descriptions-item label="同源复核">
            {{ MANUAL_REVIEW_LABEL[detailVersion.manualReviewStatus] ?? '无需复核' }}
          </el-descriptions-item>
          <el-descriptions-item label="复核人">
            {{ detailVersion.manualReviewedBy ?? '—' }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detailVersion.manualReviewNotes" label="复核意见" :span="2">
            {{ detailVersion.manualReviewNotes }}
          </el-descriptions-item>
        </el-descriptions>

        <section class="detail-block">
          <h4 class="detail-block__title">版本变更记录</h4>
          <ul class="change-log">
            <li v-for="(item, index) in detailVersion.changeLog" :key="`${item}-${index}`">
              {{ item }}
            </li>
          </ul>
        </section>

        <section class="detail-block">
          <h4 class="detail-block__title">版本差异对比</h4>
          <div class="compare-controls">
            <span class="compare-controls__label">对比基线</span>
            <el-select v-model="baseVersionId" size="small" placeholder="选择基线版本" @change="loadCompare">
              <el-option
                v-for="item in otherVersions"
                :key="item.versionId"
                :label="`${item.versionNumber}（${versionStatusLabel(item.versionStatus)}）`"
                :value="item.versionId"
              />
            </el-select>
          </div>
          <el-table v-loading="compareLoading" :data="compareRows" size="small" border>
            <el-table-column prop="label" label="对比项" width="130" />
            <el-table-column prop="baseValue" label="基线" min-width="120" />
            <el-table-column prop="targetValue" label="当前版本" min-width="120" />
            <el-table-column label="是否变化" width="100">
              <template #default="{ row }">
                <el-tag :type="row.changed ? 'warning' : 'info'" size="small">
                  {{ row.changed ? '已变化' : '一致' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
          <p v-if="compareRows.length === 0 && !compareLoading" class="compare-empty">
            没有可对比的基线版本。
          </p>
        </section>
      </template>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button
          v-if="detailVersion?.manualReviewStatus === 'pending'"
          type="warning"
          @click="detailVersion && openManualReview(detailVersion)"
        >
          人工复核
        </el-button>
        <el-button
          v-if="detailVersion?.versionStatus === 'draft'"
          type="primary"
          @click="detailVersion && askStatus(detailVersion, 'reviewing')"
        >
          提交审核
        </el-button>
        <el-button
          v-else-if="detailVersion?.versionStatus === 'reviewing'"
          type="primary"
          :disabled="detailVersion?.manualReviewStatus === 'pending'"
          @click="detailVersion && askStatus(detailVersion, 'published')"
        >
          发布版本
        </el-button>
      </template>
    </DetailDrawer>

    <el-dialog v-model="reviewVisible" title="同源拆分人工复核" width="520px" append-to-body>
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="请确认已人工核实该版本的同源数据泄漏风险。"
        description="复核通过前，该版本不允许发布为正式可训练版本；复核记录会写入版本变更记录。"
      />
      <p v-if="pendingReviewVersion" class="review-version">
        版本：{{ pendingReviewVersion.versionNumber }} · 训练 / 验证 / 测试
        {{ pendingReviewVersion.trainCount }} / {{ pendingReviewVersion.validationCount }} /
        {{ pendingReviewVersion.testCount }}
      </p>
      <el-input
        v-model="reviewNotes"
        type="textarea"
        :rows="3"
        maxlength="200"
        show-word-limit
        placeholder="填写复核结论，例如：已核对同源组，确认无同一原始数据跨集合，风险可接受"
      />
      <template #footer>
        <el-button :disabled="reviewLoading" @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" :loading="reviewLoading" @click="submitManualReview">
          确认复核通过
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="createVisible" title="创建数据集版本" width="500px" append-to-body>
      <el-form label-position="top">
        <el-form-item label="版本说明">
          <el-input
            v-model="createDescription"
            type="textarea"
            :rows="2"
            maxlength="200"
            show-word-limit
            placeholder="说明本次版本的数据范围与用途"
          />
        </el-form-item>
        <el-form-item label="变更说明">
          <el-input
            v-model="createChangeLog"
            type="textarea"
            :rows="2"
            maxlength="200"
            show-word-limit
            placeholder="例如：补充复核通过的样本并重新拆分"
          />
        </el-form-item>
      </el-form>
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="创建版本会按当前训练资格与默认拆分规则（70/15/15）生成快照，仅保存在本地 Mock 数据中。"
      />
      <template #footer>
        <el-button :disabled="createLoading" @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createLoading" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <ConfirmDialog
      v-model="confirmVisible"
      :title="STATUS_TITLE[pendingStatus]"
      :message="`确认将版本 ${pendingVersion?.versionNumber ?? ''} 的状态变更为「${STATUS_TITLE[pendingStatus]}」吗？`"
      :description="STATUS_MESSAGE[pendingStatus]"
      :type="pendingStatus === 'published' ? 'primary' : 'warning'"
      :confirm-text="STATUS_TITLE[pendingStatus]"
      :loading="confirmLoading"
      @confirm="confirmStatus"
    />
  </div>
</template>

<style scoped>
.versions-page {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-4);
}

.version-number {
  color: var(--ss-text-primary);
  font-weight: var(--ss-font-weight-semibold);
}

.cell-sub {
  margin-top: 2px;
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
}

.review-tag {
  margin-left: var(--ss-space-1);
}

.review-version {
  margin: var(--ss-space-3) 0;
  color: var(--ss-text-secondary);
  font-size: var(--ss-font-size-sm);
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

.change-log {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-2);
  margin: 0;
  padding: var(--ss-space-3) var(--ss-space-3) var(--ss-space-3) var(--ss-space-6);
  border: 1px solid var(--ss-divider);
  border-radius: var(--ss-radius-md);
  background: var(--ss-bg-subtle);
  color: var(--ss-text-secondary);
  font-size: var(--ss-font-size-sm);
  line-height: 1.7;
}

.compare-controls {
  display: flex;
  align-items: center;
  gap: var(--ss-space-2);
}

.compare-controls__label {
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
}

.compare-empty {
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-sm);
}
</style>
