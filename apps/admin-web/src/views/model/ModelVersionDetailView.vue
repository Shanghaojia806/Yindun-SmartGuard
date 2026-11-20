<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import {
  approveModelReview,
  evaluateModelVersion,
  fetchModelArtifact,
  fetchModelDatasetSnapshot,
  fetchModelDetail,
  fetchModelOptions,
  fetchModelVersion,
  fetchModelVersions,
  fetchModelAudits,
  fetchReleaseRecords,
  publishModelVersion,
  rejectModelReview,
  rollbackModelVersion,
  submitModelReview,
  withdrawModelVersion,
} from '@/api/model'
import { fetchDatasetDetail, fetchDatasetVersion } from '@/api/dataset'
import { fetchTrainingTask } from '@/api/training'
import { ApiError } from '@/api/http'
import { IS_MOCK } from '@/api/request'
import { useSessionStore } from '@/stores/session'
import { PageHeader } from '@/components'
import AuditRecordTable from '@/components/model/AuditRecordTable.vue'
import CompatibilityTag from '@/components/model/CompatibilityTag.vue'
import ReleaseRecordTable from '@/components/model/ReleaseRecordTable.vue'
import ReleaseStatusTag from '@/components/model/ReleaseStatusTag.vue'
import ReviewChecklist from '@/components/model/ReviewChecklist.vue'
import VerificationTag from '@/components/model/VerificationTag.vue'
import { formatFileSize } from '@/api/mock/modelAdapter'
import { MODEL_DEMO_NOTE, MODEL_FILE_DEMO_NOTE, MODEL_RELEASE_DEMO_NOTE } from '@/api/mock/modelData'
import type { DatasetSummary, DatasetVersion } from '@/types/dataset'
import type {
  ModelArtifact,
  ModelAuditRecord,
  ModelDatasetSnapshot,
  ModelOptions,
  ModelReviewResult,
  ModelSummary,
  ModelVersion,
  ReleaseChannel,
  ReleaseRecord,
  RuntimeCompatibility,
} from '@/types/model'
import {
  MODEL_FORMAT_LABELS,
  QUANTIZATION_FORMAT_LABELS,
  QUANTIZATION_STATUS_LABELS,
  canManageModelRelease,
} from '@/types/model'
import type { TrainingTask } from '@/types/training'

/**
 * A06 模型版本详情（P12）。
 *
 * 数据来源：
 * - 模型版本 / 文件记录：A06 Repository；
 * - 数据集版本与资格：A04 数据集模块（只读）；
 * - 训练来源：P11 训练任务（模拟执行）。
 *
 * 浏览器不读取模型文件、不执行量化或推理；所有发布操作均由适配器 / 后端二次校验。
 */

const route = useRoute()
const router = useRouter()
const session = useSessionStore()

const modelId = computed(() => String(route.params.modelId ?? ''))
const versionId = computed(() => String(route.params.versionId ?? ''))
const roleLabel = computed(() => session.roleLabel)
const operator = computed(() => session.displayName)

const model = ref<ModelSummary | null>(null)
const version = ref<ModelVersion | null>(null)
const artifact = ref<ModelArtifact | null>(null)
const datasetVersion = ref<DatasetVersion | null>(null)
const dataset = ref<DatasetSummary | null>(null)
/** 真实模式：服务端数据集快照（P13 统一数据来源） */
const datasetSnapshot = ref<ModelDatasetSnapshot | null>(null)
const trainingTask = ref<TrainingTask | null>(null)
const options = ref<ModelOptions | null>(null)
const releases = ref<ReleaseRecord[]>([])
const audits = ref<ModelAuditRecord[]>([])
const siblingVersions = ref<ModelVersion[]>([])
const loading = ref(false)
const error = ref('')
const activeTab = ref(String(route.query.tab ?? 'overview'))

const canRelease = computed(() => canManageModelRelease(roleLabel.value))
const isDraft = computed(() =>
  ['draft', 'rejected', 'pending_review'].includes(version.value?.releaseStatus ?? ''),
)
const isPendingReview = computed(() => version.value?.releaseStatus === 'pending_review')
const isApproved = computed(() => version.value?.releaseStatus === 'approved')
const isPublished = computed(() => version.value?.releaseStatus === 'published')

/** 真实产物：来自 P11-R Python Worker，哈希与签名由服务端校验 */
const isRealModel = computed(() => artifact.value?.artifactSource === 'training_worker')
/** Smoke Test 产物：只允许受限内部测试 */
const isSmokeProfile = computed(() => version.value?.trainingProfile === 'smoke_test')
const sourceNotice = computed(() =>
  isRealModel.value
    ? '真实后端模式：模型文件、SHA-256 与 Ed25519 签名均由服务端校验；Smoke Test / 合成数据产物不能作为正式反诈模型发布。'
    : MODEL_DEMO_NOTE,
)
const fileNotice = computed(() =>
  isRealModel.value
    ? '文件信息来自后端产物清单与真实文件；哈希由服务端重新计算，签名使用公钥验证。'
    : MODEL_FILE_DEMO_NOTE,
)
const releaseNotice = computed(() =>
  isRealModel.value
    ? '发布资质由 FastAPI 服务端重新校验：未签名、开发密钥、Smoke Test、合成数据、无真实测试集评估或缺少设备测试记录时，灰度与正式发布会被阻断。'
    : MODEL_RELEASE_DEMO_NOTE,
)
const publishedStateText = computed(() => {
  if (version.value?.releaseStatus !== 'published') return '按状态流转规则操作'
  if (!isRealModel.value) return '已发布（演示）'
  return isSmokeProfile.value ? '已发布（受限内部测试）' : '已发布'
})
const evaluationSourceLabel = computed(() =>
  isRealModel.value
    ? '训练任务（真实 Worker）'
    : evaluationSourceLabels[version.value?.evaluationSummary.source ?? ''] ??
      version.value?.evaluationSummary.source,
)

const trainingStatusLabels: Record<string, string> = {
  pending: '等待执行',
  running: '正在运行',
  succeeded: '训练成功',
  failed: '训练失败',
  canceled: '已取消',
}
const datasetVersionStatusLabels: Record<string, string> = {
  draft: '草稿',
  reviewing: '审核中',
  published: '已发布',
  revoked: '已撤销',
  frozen: '已冻结',
}
const manualReviewLabels: Record<string, string> = {
  not_required: '无需复核',
  pending: '待人工复核',
  approved: '已复核通过',
}
const sourceStatusLabels: Record<string, string> = {
  clear: '来源清晰',
  unclear: '来源不清晰',
  unverified: '来源未验证',
}
const licenseStatusLabels: Record<string, string> = {
  allowed: '允许训练',
  forbidden: '禁止训练',
  unknown: '许可未知',
}
const qualityStatusLabels: Record<string, string> = {
  passed: '质检通过',
  quality_blocked: '质检阻断',
  pending: '待质检',
}
const duplicateStatusLabels: Record<string, string> = {
  resolved: '已处理',
  unresolved: '未处理',
  pending: '待处理',
}
const leakageRiskLabels: Record<string, string> = {
  none: '无泄漏风险',
  partial_group_split: '同源拆分存在风险',
  unverified: '未验证',
}
const snapshotSourceLabels: Record<string, string> = {
  synthetic_demo: '合成演示数据',
  synthetic: '合成数据',
  real: '真实采集数据',
}
const evaluationSourceLabels: Record<string, string> = {
  training_task: '训练任务（真实 Worker）',
  mock_demo: 'Mock 演示数据',
}
const evaluationStatusLabels: Record<string, string> = {
  evaluated: '已完成评估',
  demo: '演示指标',
  not_evaluated: '未评估',
}

const datasetRiskReasons = computed(() => {
  // 真实模式只采用服务端快照的阻断原因，不拼装 Mock 统计
  if (datasetSnapshot.value) return datasetSnapshot.value.blockedReasons
  const reasons: string[] = []
  if (datasetVersion.value && datasetVersion.value.versionStatus !== 'published') {
    reasons.push(`数据集版本状态为 ${datasetVersionStatusLabels[datasetVersion.value.versionStatus] ?? datasetVersion.value.versionStatus}，未发布`)
  }
  if (datasetVersion.value?.manualReviewStatus === 'pending') {
    reasons.push('数据集存在未完成的同源拆分人工复核')
  }
  if (dataset.value?.datasetStatus === 'quality_blocked') {
    reasons.push('数据集质检未通过（quality_blocked）')
  }
  if (dataset.value && dataset.value.blockedSamples > 0) {
    reasons.push(`数据集中存在 ${dataset.value.blockedSamples} 条被阻断样本`)
  }
  return reasons
})

/* ===== 审核 / 发布对话框 ===== */

type ReviewMode = 'submit' | 'approve'

const reviewVisible = ref(false)
const reviewMode = ref<ReviewMode>('submit')
const reviewChannel = ref<ReleaseChannel>('internal_test')
const reviewNotes = ref('')
const reviewResult = ref<ModelReviewResult | null>(null)
const reviewLoading = ref(false)
const reviewSubmitting = ref(false)

const publishVisible = ref(false)
const publishForm = reactive({ channel: 'internal_test' as ReleaseChannel, deviceScope: '', notes: '' })
const publishResult = ref<ModelReviewResult | null>(null)
const publishLoading = ref(false)
const publishSubmitting = ref(false)

const rejectVisible = ref(false)
const rejectReason = ref('')
const rejectSubmitting = ref(false)

const withdrawVisible = ref(false)
const withdrawReason = ref('')
const withdrawImpact = ref('已发布版本将停止分发；不会远程卸载设备上的模型')
const withdrawSubmitting = ref(false)

const rollbackVisible = ref(false)
const rollbackTarget = ref('')
const rollbackNotes = ref('')
const rollbackSubmitting = ref(false)

const rollbackTargets = computed(() =>
  siblingVersions.value.filter(
    (item) =>
      item.modelVersionId !== version.value?.modelVersionId &&
      ['approved', 'published'].includes(item.releaseStatus),
  ),
)

function resolveMessage(cause: unknown, fallback: string): string {
  return cause instanceof ApiError ? cause.message : fallback
}

async function loadAll(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [detail, versionData, optionList, releaseList, auditList, versionPage] = await Promise.all([
      fetchModelDetail(modelId.value),
      fetchModelVersion(modelId.value, versionId.value),
      fetchModelOptions(),
      fetchReleaseRecords(modelId.value, versionId.value),
      fetchModelAudits(modelId.value, versionId.value),
      fetchModelVersions(modelId.value, {
        keyword: '',
        releaseStatus: '',
        compatibilityStatus: '',
        page: 1,
        pageSize: 100,
      }),
    ])
    model.value = detail
    version.value = versionData
    options.value = optionList
    releases.value = releaseList
    audits.value = auditList
    siblingVersions.value = versionPage.items
    artifact.value = await fetchModelArtifact(versionData.artifactId)

    if (IS_MOCK) {
      const [datasetVersionData, datasetDetail] = await Promise.all([
        fetchDatasetVersion(versionData.datasetVersionId).catch(() => null),
        fetchDatasetDetail(versionData.datasetId).catch(() => null),
      ])
      datasetVersion.value = datasetVersionData
      dataset.value = datasetDetail
      datasetSnapshot.value = null
    } else {
      // 真实模式：数据集卡片只读服务端快照，避免把 P10 Mock 数据当作真实来源
      datasetSnapshot.value = await fetchModelDatasetSnapshot(
        versionData.modelVersionId,
      ).catch(() => null)
      datasetVersion.value = null
      dataset.value = null
    }

    if (versionData.trainingTaskId) {
      trainingTask.value = await fetchTrainingTask(versionData.trainingTaskId).catch(() => null)
    } else {
      trainingTask.value = null
    }
  } catch (cause) {
    error.value = resolveMessage(cause, '模型版本详情加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function goBack(): void {
  void router.push({ name: 'model-versions', params: { modelId: modelId.value } })
}

/* ===== 审核清单 ===== */

async function refreshReviewResult(channel: ReleaseChannel): Promise<void> {
  if (!version.value) return
  reviewLoading.value = true
  try {
    reviewResult.value = await evaluateModelVersion(version.value.modelVersionId, channel, roleLabel.value)
  } catch (cause) {
    reviewResult.value = null
    ElMessage.error(resolveMessage(cause, '审核清单生成失败'))
  } finally {
    reviewLoading.value = false
  }
}

async function refreshPublishResult(channel: ReleaseChannel): Promise<void> {
  if (!version.value) return
  publishLoading.value = true
  try {
    publishResult.value = await evaluateModelVersion(version.value.modelVersionId, channel, roleLabel.value)
  } catch (cause) {
    publishResult.value = null
    ElMessage.error(resolveMessage(cause, '审核清单生成失败'))
  } finally {
    publishLoading.value = false
  }
}

function openReview(mode: ReviewMode): void {
  reviewMode.value = mode
  reviewChannel.value = 'internal_test'
  reviewNotes.value = ''
  reviewVisible.value = true
  void refreshReviewResult(reviewChannel.value)
}

function handleReviewChannelChange(value: ReleaseChannel): void {
  void refreshReviewResult(value)
}

async function handleReviewSubmit(): Promise<void> {
  if (!version.value) return
  reviewSubmitting.value = true
  try {
    if (reviewMode.value === 'submit') {
      await submitModelReview(version.value.modelVersionId, {
        channel: reviewChannel.value,
        notes: reviewNotes.value,
        operator: operator.value,
        roleLabel: roleLabel.value,
      })
      ElMessage.success(IS_MOCK ? '已提交审核（演示记录）' : '已提交审核')
    } else {
      await approveModelReview(version.value.modelVersionId, {
        channel: reviewChannel.value,
        notes: reviewNotes.value,
        operator: operator.value,
        roleLabel: roleLabel.value,
      })
      ElMessage.success(IS_MOCK ? '审核通过（演示环境）' : '审核通过')
    }
    reviewVisible.value = false
    await loadAll()
  } catch (cause) {
    ElMessage.error(resolveMessage(cause, '审核操作失败'))
  } finally {
    reviewSubmitting.value = false
  }
}

/* ===== 审核拒绝 ===== */

function openReject(): void {
  rejectReason.value = ''
  rejectVisible.value = true
}

async function handleReject(): Promise<void> {
  if (!version.value) return
  if (!rejectReason.value.trim()) {
    ElMessage.warning('请填写拒绝原因')
    return
  }
  rejectSubmitting.value = true
  try {
    await rejectModelReview(
      version.value.modelVersionId,
      operator.value,
      roleLabel.value,
      rejectReason.value.trim(),
    )
    ElMessage.success(IS_MOCK ? '已提交审核拒绝（演示记录）' : '已提交审核拒绝')
    rejectVisible.value = false
    await loadAll()
  } catch (cause) {
    ElMessage.error(resolveMessage(cause, '审核拒绝失败'))
  } finally {
    rejectSubmitting.value = false
  }
}

/* ===== 发布 ===== */

function openPublish(): void {
  publishForm.channel = 'internal_test'
  publishForm.deviceScope = '演示设备范围（不向真实设备下发）'
  publishForm.notes = ''
  publishVisible.value = true
  void refreshPublishResult(publishForm.channel)
}

function handlePublishChannelChange(value: ReleaseChannel): void {
  void refreshPublishResult(value)
}

async function handlePublish(): Promise<void> {
  if (!version.value) return
  if (!canRelease.value) {
    ElMessage.error('当前角色不具备模型发布权限')
    return
  }
  publishSubmitting.value = true
  try {
    await publishModelVersion(version.value.modelVersionId, {
      channel: publishForm.channel,
      deviceScope: publishForm.deviceScope,
      notes: publishForm.notes,
      operator: operator.value,
      roleLabel: roleLabel.value,
    })
    ElMessage.success(
      IS_MOCK
        ? '发布完成（演示发布，不产生真实可用模型）'
        : publishForm.channel === 'internal_test'
          ? '发布完成（受限内部测试；Smoke Test 产物不得正式发布）'
          : '发布完成',
    )
    publishVisible.value = false
    await loadAll()
  } catch (cause) {
    ElMessage.error(resolveMessage(cause, '发布失败'))
  } finally {
    publishSubmitting.value = false
  }
}

/* ===== 撤回 ===== */

function openWithdraw(): void {
  withdrawReason.value = ''
  withdrawImpact.value = '已发布版本将停止分发；不会远程卸载设备上的模型'
  withdrawVisible.value = true
}

async function handleWithdraw(): Promise<void> {
  if (!version.value) return
  if (!withdrawReason.value.trim()) {
    ElMessage.warning('请填写撤回原因')
    return
  }
  withdrawSubmitting.value = true
  try {
    await withdrawModelVersion(version.value.modelVersionId, {
      reason: withdrawReason.value.trim(),
      impactScope: withdrawImpact.value,
      operator: operator.value,
      roleLabel: roleLabel.value,
    })
    ElMessage.success('已撤回（仅停止分发，不涉及设备端卸载）')
    withdrawVisible.value = false
    await loadAll()
  } catch (cause) {
    ElMessage.error(resolveMessage(cause, '撤回失败'))
  } finally {
    withdrawSubmitting.value = false
  }
}

/* ===== 回滚 ===== */

function openRollback(): void {
  rollbackTarget.value = rollbackTargets.value[0]?.modelVersionId ?? ''
  rollbackNotes.value = ''
  rollbackVisible.value = true
}

async function handleRollback(): Promise<void> {
  if (!version.value || !rollbackTarget.value) {
    ElMessage.warning('请选择回滚目标版本')
    return
  }
  rollbackSubmitting.value = true
  try {
    await rollbackModelVersion(modelId.value, {
      fromVersionId: version.value.modelVersionId,
      toVersionId: rollbackTarget.value,
      operator: operator.value,
      roleLabel: roleLabel.value,
      notes: rollbackNotes.value,
    })
    ElMessage.success(IS_MOCK ? '回滚完成（演示记录，未影响真实设备）' : '回滚完成，已写入审计记录')
    rollbackVisible.value = false
    await loadAll()
  } catch (cause) {
    ElMessage.error(resolveMessage(cause, '回滚失败'))
  } finally {
    rollbackSubmitting.value = false
  }
}

/* ===== 展示辅助 ===== */

function formatMetric(value: number | null, kind: 'ratio' | 'loss' = 'ratio'): string {
  if (value === null) return '—'
  return kind === 'ratio' ? `${(value * 100).toFixed(1)}%` : value.toFixed(3)
}

function splitRatio(count: number, total: number): string {
  if (!total) return '—'
  return `${((count / total) * 100).toFixed(1)}%`
}

function quantizationFormatLabel(value: string): string {
  return QUANTIZATION_FORMAT_LABELS[value as keyof typeof QUANTIZATION_FORMAT_LABELS] ?? value
}

function quantizationStatusLabel(value: string): string {
  return QUANTIZATION_STATUS_LABELS[value as keyof typeof QUANTIZATION_STATUS_LABELS] ?? value
}

function acceleratorText(row: RuntimeCompatibility): string {
  if (!row.acceleratorSupport.length) return '无'
  return row.acceleratorSupport.map((item) => item.toUpperCase()).join(' / ')
}

watch(activeTab, (value) => {
  if (value === 'review' && !reviewResult.value && version.value) {
    void refreshReviewResult('internal_test')
  }
})

onMounted(() => {
  void loadAll()
})
</script>

<template>
  <div class="version-detail">
    <PageHeader
      eyebrow="A06 · 模型版本"
      :title="version ? `${model?.modelName ?? '模型'} · ${version.modelVersion}` : '模型版本详情'"
      description="模型文件、评估、量化与兼容性信息均为只读记录；发布操作需通过审核与权限校验。"
    >
      <template #extra>
        <el-button @click="goBack">版本列表</el-button>
        <el-button @click="router.push({ name: 'model-detail', params: { modelId } })">模型详情</el-button>
        <el-button type="primary" :loading="loading" @click="loadAll">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert type="warning" :closable="false" show-icon :title="sourceNotice" />
    <el-alert
      v-if="isSmokeProfile"
      type="error"
      :closable="false"
      show-icon
      title="trainingProfile=smoke_test：该版本仅允许受限内部测试，灰度与正式发布会被 FastAPI 服务端阻断。"
      class="version-detail__smoke-alert"
    />

    <el-skeleton v-if="loading && !version" :rows="8" animated />

    <el-alert v-else-if="error" type="error" :closable="false" show-icon :title="error">
      <el-button size="small" @click="loadAll">重新加载</el-button>
    </el-alert>

    <template v-else-if="version">
      <section class="version-detail__toolbar">
        <ReleaseStatusTag :status="version.releaseStatus" />
        <span class="version-detail__state-hint">
          当前状态：{{ publishedStateText }}
        </span>
        <div class="version-detail__actions">
          <el-button v-if="isDraft" @click="openReview('submit')">提交审核</el-button>
          <el-button v-if="isPendingReview" type="primary" @click="openReview('approve')">审核通过</el-button>
          <el-button v-if="isPendingReview" type="danger" plain @click="openReject">审核拒绝</el-button>
          <el-button v-if="isApproved" type="primary" @click="openPublish">执行发布</el-button>
          <el-button v-if="isPublished" type="warning" plain @click="openWithdraw">撤回</el-button>
          <el-button v-if="isPublished" @click="openRollback">回滚</el-button>
        </div>
      </section>

      <el-alert
        v-if="!canRelease"
        type="error"
        :closable="false"
        show-icon
        title="当前账号不具备模型审核 / 发布权限，相关按钮点击后会被服务端拦截。"
        class="version-detail__permission"
      />

      <el-tabs v-model="activeTab">
        <el-tab-pane label="版本概况" name="overview">
          <div class="version-detail__grid">
            <el-card shadow="never">
              <template #header>基础信息</template>
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="模型名称">{{ version.modelId }} · {{ model?.modelName }}</el-descriptions-item>
                <el-descriptions-item label="模型类型">{{ model?.modelType }}</el-descriptions-item>
                <el-descriptions-item label="模型版本">{{ version.modelVersion }}</el-descriptions-item>
                <el-descriptions-item label="模型框架">PyTorch（训练框架）</el-descriptions-item>
                <el-descriptions-item label="创建时间">{{ version.createdAt }}</el-descriptions-item>
                <el-descriptions-item label="创建人">{{ version.createdBy }}</el-descriptions-item>
                <el-descriptions-item label="关联训练任务" :span="2">
                  {{ version.trainingTaskId || '未关联训练任务' }}
                </el-descriptions-item>
              </el-descriptions>
            </el-card>

            <el-card shadow="never">
              <template #header>数据集来源</template>
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="数据集">
                  {{ datasetSnapshot?.datasetName ?? version.datasetName }}
                </el-descriptions-item>
                <el-descriptions-item label="数据集版本">
                  {{ version.datasetVersion }}（{{ version.datasetVersionId }}）
                </el-descriptions-item>
                <el-descriptions-item label="版本状态">
                  {{ datasetVersionStatusLabels[datasetSnapshot?.versionStatus ?? datasetVersion?.versionStatus ?? ''] ?? '未知' }}
                </el-descriptions-item>
                <el-descriptions-item label="人工复核">
                  {{ manualReviewLabels[datasetSnapshot?.manualReviewStatus ?? datasetVersion?.manualReviewStatus ?? ''] ?? '未知' }}
                </el-descriptions-item>
                <el-descriptions-item label="数据拆分" :span="2">
                  <template v-if="datasetSnapshot">
                    训练 {{ datasetSnapshot.trainCount }}（{{ splitRatio(datasetSnapshot.trainCount, datasetSnapshot.sampleCount) }}）
                    / 验证 {{ datasetSnapshot.validationCount }}（{{ splitRatio(datasetSnapshot.validationCount, datasetSnapshot.sampleCount) }}）
                    / 测试 {{ datasetSnapshot.testCount }}（{{ splitRatio(datasetSnapshot.testCount, datasetSnapshot.sampleCount) }}）
                    · 共 {{ datasetSnapshot.sampleCount }} 条
                  </template>
                  <template v-else-if="datasetVersion">
                    训练 {{ datasetVersion.trainCount }}（{{ splitRatio(datasetVersion.trainCount, datasetVersion.sampleCount) }}）
                    / 验证 {{ datasetVersion.validationCount }}（{{ splitRatio(datasetVersion.validationCount, datasetVersion.sampleCount) }}）
                    / 测试 {{ datasetVersion.testCount }}（{{ splitRatio(datasetVersion.testCount, datasetVersion.sampleCount) }}）
                  </template>
                  <span v-else>数据集版本记录不可用</span>
                </el-descriptions-item>
                <el-descriptions-item label="来源与许可" :span="2">
                  <template v-if="datasetSnapshot">
                    来源 {{ sourceStatusLabels[datasetSnapshot.sourceStatus] ?? datasetSnapshot.sourceStatus }}；
                    许可 {{ licenseStatusLabels[datasetSnapshot.licenseStatus] ?? datasetSnapshot.licenseStatus }}；
                    质检 {{ qualityStatusLabels[datasetSnapshot.qualityStatus] ?? datasetSnapshot.qualityStatus }}；
                    重复数据 {{ duplicateStatusLabels[datasetSnapshot.duplicateStatus] ?? datasetSnapshot.duplicateStatus }}；
                    泄漏风险 {{ leakageRiskLabels[datasetSnapshot.leakageRisk] ?? datasetSnapshot.leakageRisk }}
                    <br />
                    快照来源：{{ snapshotSourceLabels[datasetSnapshot.snapshotSource] ?? datasetSnapshot.snapshotSource }}
                    · 校验时间 {{ datasetSnapshot.checkedAt }}
                    <el-tag
                      :type="datasetSnapshot.eligible ? 'success' : 'danger'"
                      size="small"
                      class="version-detail__dataset-tag"
                    >
                      {{ datasetSnapshot.eligible ? '服务端资格通过' : '服务端资格阻断' }}
                    </el-tag>
                  </template>
                  <template v-else-if="dataset">
                    质检通过 {{ dataset.qualityPassedSamples }} 条；阻断 {{ dataset.blockedSamples }} 条；
                    训练资格率 {{ dataset.eligibilityRate }}%（演示统计，真实许可由 A04 后端逐条复核）
                  </template>
                  <span v-else>数据集详情不可用</span>
                </el-descriptions-item>
              </el-descriptions>
              <p v-if="datasetSnapshot" class="version-detail__dataset-source">
                数据来源：FastAPI 服务端数据集快照（dataset_version_snapshots），非 Mock 数据。
              </p>
              <el-alert
                v-else-if="IS_MOCK"
                type="warning"
                :closable="false"
                show-icon
                class="version-detail__dataset-source"
                title="演示模式：数据集统计来自 P10 Mock，不代表服务端真实快照。"
              />
              <el-alert
                v-if="datasetRiskReasons.length"
                type="error"
                :closable="false"
                show-icon
                title="数据集风险"
                class="version-detail__risk"
              >
                <ul class="version-detail__risk-list">
                  <li v-for="reason in datasetRiskReasons" :key="reason">{{ reason }}</li>
                </ul>
              </el-alert>
            </el-card>

            <el-card shadow="never">
              <template #header>模型文件信息</template>
              <el-alert
                type="info"
                :closable="false"
                show-icon
                :title="fileNotice"
                class="version-detail__card-alert"
              />
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="文件路径" :span="2">
                  {{ artifact?.artifactPath || '—' }}
                </el-descriptions-item>
                <el-descriptions-item label="文件格式">
                  {{ MODEL_FORMAT_LABELS[artifact?.artifactFormat ?? version.modelFormat] }}
                </el-descriptions-item>
                <el-descriptions-item label="文件大小">
                  {{ formatFileSize(artifact?.fileSizeBytes ?? 0) }}
                </el-descriptions-item>
                <el-descriptions-item label="文件哈希" :span="2">
                  {{ artifact?.fileHash || (IS_MOCK ? '未提供（演示记录）' : '未提供') }}
                  <VerificationTag :status="artifact?.fileHashStatus ?? 'not_available'" />
                </el-descriptions-item>
                <el-descriptions-item label="数字签名" :span="2">
                  {{ artifact?.signature || (IS_MOCK ? '未提供（演示记录）' : '未提供') }}
                  <VerificationTag :status="artifact?.signatureStatus ?? 'not_available'" />
                </el-descriptions-item>
                <el-descriptions-item label="产物来源">{{ artifact?.artifactSource === 'demo_record' ? '演示记录' : '真实训练 Worker' }}</el-descriptions-item>
                <el-descriptions-item label="记录时间">{{ artifact?.createdAt || '—' }}</el-descriptions-item>
              </el-descriptions>
            </el-card>

            <el-card shadow="never">
              <template #header>训练来源（P11）</template>
              <el-alert
                type="info"
                :closable="false"
                show-icon
                :title="
                  trainingTask?.executionMode === 'worker'
                    ? '该版本关联 P11-R 真实 Python Worker 训练任务，参数、日志与指标均为后端持久化记录。'
                    : '关联训练任务不是真实 Worker 执行，不能作为真实模型发布依据。'
                "
                class="version-detail__card-alert"
              />
              <el-descriptions v-if="trainingTask" :column="2" border size="small">
                <el-descriptions-item label="训练任务">{{ trainingTask.taskId }}</el-descriptions-item>
                <el-descriptions-item label="任务名称">{{ trainingTask.taskName }}</el-descriptions-item>
                <el-descriptions-item label="执行模式">
                  {{ trainingTask.executionMode === 'mock' ? '模拟任务' : '真实 Worker' }}
                </el-descriptions-item>
                <el-descriptions-item label="完成状态">
                  {{ trainingStatusLabels[trainingTask.taskStatus] ?? trainingTask.taskStatus }}
                </el-descriptions-item>
                <el-descriptions-item label="训练数据集版本" :span="2">
                  {{ trainingTask.datasetVersion }}（{{ trainingTask.datasetVersionId }}）
                </el-descriptions-item>
                <el-descriptions-item label="Epochs">{{ trainingTask.trainingParameters.epochs }}</el-descriptions-item>
                <el-descriptions-item label="Batch Size">{{ trainingTask.trainingParameters.batchSize }}</el-descriptions-item>
                <el-descriptions-item label="学习率">{{ trainingTask.trainingParameters.learningRate }}</el-descriptions-item>
                <el-descriptions-item label="优化器">{{ trainingTask.trainingParameters.optimizer }}</el-descriptions-item>
                <el-descriptions-item label="设备">{{ trainingTask.trainingParameters.device }}</el-descriptions-item>
                <el-descriptions-item label="完成时间">{{ trainingTask.completedAt || '—' }}</el-descriptions-item>
              </el-descriptions>
              <el-empty v-else description="该版本未关联训练任务，不能作为发布依据" :image-size="72" />
            </el-card>

            <el-card shadow="never" class="version-detail__wide">
              <template #header>评估结果</template>
              <el-alert
                type="warning"
                :closable="false"
                show-icon
                title="评估指标基于指定测试集版本；单一 Accuracy 不足以证明反诈能力，必须结合 Precision / Recall / F1 判断。"
                class="version-detail__card-alert"
              />
              <template v-if="version.evaluationSummary.evaluationStatus !== 'not_evaluated'">
                <div class="version-detail__metrics">
                  <div class="version-detail__metric">
                    <span>Accuracy</span>
                    <strong>{{ formatMetric(version.evaluationSummary.accuracy) }}</strong>
                  </div>
                  <div class="version-detail__metric">
                    <span>Precision</span>
                    <strong>{{ formatMetric(version.evaluationSummary.precision) }}</strong>
                  </div>
                  <div class="version-detail__metric">
                    <span>Recall</span>
                    <strong>{{ formatMetric(version.evaluationSummary.recall) }}</strong>
                  </div>
                  <div class="version-detail__metric">
                    <span>F1</span>
                    <strong>{{ formatMetric(version.evaluationSummary.f1Score) }}</strong>
                  </div>
                  <div class="version-detail__metric">
                    <span>Validation Loss</span>
                    <strong>{{ formatMetric(version.evaluationSummary.validationLoss, 'loss') }}</strong>
                  </div>
                </div>
                <el-descriptions :column="2" border size="small">
                  <el-descriptions-item label="测试集版本">
                    {{ version.evaluationSummary.testDatasetVersion || '未记录' }}
                  </el-descriptions-item>
                  <el-descriptions-item label="评估时间">
                    {{ version.evaluationSummary.evaluatedAt || '—' }}
                  </el-descriptions-item>
                  <el-descriptions-item label="评估方法" :span="2">
                    {{ version.evaluationSummary.evaluationMethod || '—' }}
                  </el-descriptions-item>
                  <el-descriptions-item label="评估状态">
                    {{ evaluationStatusLabels[version.evaluationSummary.evaluationStatus] }}
                  </el-descriptions-item>
                  <el-descriptions-item label="数据来源">
                    <el-tag size="small" type="warning" effect="plain">
                      {{ evaluationSourceLabel }}
                    </el-tag>
                  </el-descriptions-item>
                </el-descriptions>
              </template>
              <el-empty v-else description="尚未完成模型评估，不能作为发布依据" :image-size="72" />
            </el-card>

            <el-card shadow="never" class="version-detail__wide">
              <template #header>量化信息</template>
              <el-alert
                type="info"
                :closable="false"
                show-icon
                title="浏览器不执行量化；没有真实量化产物时，仅展示量化配置或演示记录，不伪造精度提升。"
                class="version-detail__card-alert"
              />
              <el-table :data="version.quantization" size="small" border>
                <el-table-column label="格式" width="100">
                  <template #default="{ row }">{{ quantizationFormatLabel(row.format) }}</template>
                </el-table-column>
                <el-table-column label="原始大小" width="110">
                  <template #default="{ row }">{{ formatFileSize(row.originalSizeBytes) }}</template>
                </el-table-column>
                <el-table-column label="量化后大小" width="110">
                  <template #default="{ row }">
                    {{ row.quantizedSizeBytes === null ? '—' : formatFileSize(row.quantizedSizeBytes) }}
                  </template>
                </el-table-column>
                <el-table-column prop="method" label="量化方法" min-width="200" />
                <el-table-column label="量化时间" width="140">
                  <template #default="{ row }">{{ row.quantizedAt || '—' }}</template>
                </el-table-column>
                <el-table-column label="精度变化" width="100">
                  <template #default="{ row }">
                    {{ row.accuracyDeltaPercent === null ? '未实测' : `${row.accuracyDeltaPercent}%` }}
                  </template>
                </el-table-column>
                <el-table-column label="状态" width="110">
                  <template #default="{ row }">
                    <el-tag size="small" effect="plain">{{ quantizationStatusLabel(row.status) }}</el-tag>
                  </template>
                </el-table-column>
                <template #empty>
                  <el-empty description="暂无量化记录" :image-size="64" />
                </template>
              </el-table>
            </el-card>

            <el-card shadow="never" class="version-detail__wide">
              <template #header>运行时兼容性（Android）</template>
              <el-alert
                type="info"
                :closable="false"
                show-icon
                title="兼容性状态来自设备测试记录；理论支持不等于实际兼容，未测试记录不代表可用。"
                class="version-detail__card-alert"
              />
              <el-table :data="version.runtimeCompatibility" size="small" border>
                <el-table-column label="系统版本" width="120">
                  <template #default="{ row }">Android {{ row.minOsVersion }}–{{ row.maxOsVersion }}</template>
                </el-table-column>
                <el-table-column prop="architecture" label="架构" width="110" />
                <el-table-column label="运行时" min-width="150">
                  <template #default="{ row }">{{ row.inferenceRuntime }} {{ row.runtimeVersion }}</template>
                </el-table-column>
                <el-table-column label="设备" min-width="180">
                  <template #default="{ row }">
                    {{ row.supportedDevices.length ? row.supportedDevices.join('、') : '未登记' }}
                  </template>
                </el-table-column>
                <el-table-column label="内存" width="90">
                  <template #default="{ row }">{{ row.memoryRequirementMb }} MB</template>
                </el-table-column>
                <el-table-column label="加速" width="90">
                  <template #default="{ row }">{{ acceleratorText(row) }}</template>
                </el-table-column>
                <el-table-column label="状态" width="110">
                  <template #default="{ row }">
                    <CompatibilityTag :status="row.compatibilityStatus" />
                  </template>
                </el-table-column>
                <el-table-column label="测试时间" width="140">
                  <template #default="{ row }">{{ row.testedAt || '未测试' }}</template>
                </el-table-column>
                <template #empty>
                  <el-empty description="暂无兼容性记录" :image-size="64" />
                </template>
              </el-table>
            </el-card>
          </div>
        </el-tab-pane>

        <el-tab-pane label="审核与发布" name="review">
          <el-card shadow="never">
            <template #header>审核清单（预览）</template>
            <p class="version-detail__hint">
              清单由当前操作角色与发布渠道动态生成；提交审核或发布时，后端必须重新生成并校验。
              当前账号：{{ operator }}（{{ roleLabel }}）。
            </p>
            <el-radio-group
              :model-value="'internal_test'"
              class="version-detail__channel-hint"
              disabled
            >
              <el-radio-button value="internal_test">受限内部测试</el-radio-button>
            </el-radio-group>
            <ReviewChecklist :result="reviewResult" :loading="reviewLoading" />
            <div class="version-detail__inline-actions">
              <el-button :loading="reviewLoading" @click="refreshReviewResult('internal_test')">
                刷新内部测试清单
              </el-button>
              <el-button :loading="reviewLoading" @click="refreshReviewResult('production')">
                查看正式发布清单（应被阻断）
              </el-button>
            </div>
          </el-card>

          <el-card shadow="never" class="version-detail__section">
            <template #header>发布安全检查说明</template>
            <el-alert type="error" :closable="false" show-icon :title="releaseNotice" />
            <ul class="version-detail__safety">
              <li>发布必须同时满足：操作权限、审核通过、文件完整性、数字签名、数据集合法、评估有效、兼容性达标。</li>
              <li>服务端会重新计算文件哈希、验签并检查数据集资格；Smoke Test / 合成数据 / 开发密钥 / 无真实测试集评估均禁止灰度与正式发布。</li>
              <li>前端按钮禁用不代表服务端放行；发布接口会重新执行全部校验。</li>
            </ul>
          </el-card>
        </el-tab-pane>

        <el-tab-pane label="发布与审计记录" name="records">
          <el-card shadow="never">
            <template #header>发布记录</template>
            <ReleaseRecordTable v-if="releases.length" :records="releases" />
            <el-empty v-else description="该版本暂无发布记录" />
          </el-card>
          <el-card shadow="never" class="version-detail__section">
            <template #header>审计记录</template>
            <AuditRecordTable v-if="audits.length" :records="audits" />
            <el-empty v-else description="该版本暂无审计记录" />
          </el-card>
        </el-tab-pane>
      </el-tabs>
    </template>

    <!-- 提交审核 / 审核通过 -->
    <el-dialog
      v-model="reviewVisible"
      :title="reviewMode === 'submit' ? '提交审核' : '审核通过'"
      width="820px"
      destroy-on-close
    >
      <el-form label-width="100px">
        <el-form-item label="发布渠道">
          <el-select v-model="reviewChannel" style="width: 260px" @change="handleReviewChannelChange">
            <el-option
              v-for="item in options?.channels ?? []"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
          <p class="version-detail__hint">
            {{ options?.channels.find((item) => item.value === reviewChannel)?.note }}
          </p>
        </el-form-item>
        <el-form-item label="审核说明">
          <el-input v-model="reviewNotes" type="textarea" :rows="2" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>

      <ReviewChecklist :result="reviewResult" :loading="reviewLoading" />

      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="reviewSubmitting"
          :disabled="reviewMode === 'approve' && !reviewResult?.eligible"
          @click="handleReviewSubmit"
        >
          {{ reviewMode === 'submit' ? '提交' : '确认审核通过' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 审核拒绝 -->
    <el-dialog v-model="rejectVisible" title="审核拒绝" width="520px">
      <el-input v-model="rejectReason" type="textarea" :rows="4" maxlength="200" show-word-limit placeholder="请填写拒绝原因" />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="rejectSubmitting" @click="handleReject">确认拒绝</el-button>
      </template>
    </el-dialog>

    <!-- 发布 -->
    <el-dialog v-model="publishVisible" title="执行发布" width="860px" destroy-on-close>
      <el-alert type="error" :closable="false" show-icon :title="releaseNotice" />
      <el-form label-width="100px" class="version-detail__publish-form">
        <el-form-item label="发布渠道">
          <el-select v-model="publishForm.channel" style="width: 260px" @change="handlePublishChannelChange">
            <el-option
              v-for="item in options?.channels ?? []"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
          <p class="version-detail__hint">
            {{ options?.channels.find((item) => item.value === publishForm.channel)?.note }}
          </p>
        </el-form-item>
        <el-form-item label="适配设备范围">
          <el-input v-model="publishForm.deviceScope" maxlength="100" />
        </el-form-item>
        <el-form-item label="发布说明">
          <el-input v-model="publishForm.notes" type="textarea" :rows="2" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>

      <ReviewChecklist :result="publishResult" :loading="publishLoading" />

      <template #footer>
        <el-button @click="publishVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="publishSubmitting"
          :disabled="!publishResult?.eligible || !canRelease"
          @click="handlePublish"
        >
          确认发布
        </el-button>
      </template>
    </el-dialog>

    <!-- 撤回 -->
    <el-dialog v-model="withdrawVisible" title="撤回模型版本" width="560px">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="撤回只代表停止分发；不会远程卸载已经安装到设备上的模型。"
        class="version-detail__dialog-alert"
      />
      <el-form label-width="90px">
        <el-form-item label="撤回原因" required>
          <el-input v-model="withdrawReason" type="textarea" :rows="3" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="影响范围">
          <el-input v-model="withdrawImpact" type="textarea" :rows="2" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="withdrawVisible = false">取消</el-button>
        <el-button type="warning" :loading="withdrawSubmitting" @click="handleWithdraw">确认撤回</el-button>
      </template>
    </el-dialog>

    <!-- 回滚 -->
    <el-dialog v-model="rollbackVisible" title="版本回滚" width="600px">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="回滚会产生新的发布记录，不会覆盖或删除历史版本。"
        class="version-detail__dialog-alert"
      />
      <el-form label-width="110px">
        <el-form-item label="当前发布版本">
          <span>{{ version?.modelVersion }}（{{ version?.modelVersionId }}）</span>
        </el-form-item>
        <el-form-item label="回滚目标" required>
          <el-select v-model="rollbackTarget" placeholder="请选择已审核通过的版本" style="width: 100%">
            <el-option
              v-for="item in rollbackTargets"
              :key="item.modelVersionId"
              :label="`${item.modelVersion} · ${item.releaseStatus === 'published' ? '已发布' : '审核通过'}`"
              :value="item.modelVersionId"
            />
          </el-select>
          <p v-if="!rollbackTargets.length" class="version-detail__hint is-danger">
            当前模型没有已审核通过的其他版本，无法执行回滚。
          </p>
        </el-form-item>
        <el-form-item label="回滚说明">
          <el-input v-model="rollbackNotes" type="textarea" :rows="2" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rollbackVisible = false">取消</el-button>
        <el-button
          type="warning"
          :loading="rollbackSubmitting"
          :disabled="!rollbackTarget"
          @click="handleRollback"
        >
          确认回滚
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.version-detail {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-4);
}

.version-detail__toolbar {
  display: flex;
  align-items: center;
  gap: var(--ss-space-3);
  flex-wrap: wrap;
  padding: var(--ss-space-3) var(--ss-space-4);
  border: 1px solid var(--ss-divider);
  border-radius: var(--ss-radius-md, 8px);
  background: var(--ss-surface, #fff);
}

.version-detail__state-hint {
  color: var(--ss-text-secondary);
  font-size: var(--ss-font-size-sm);
}

.version-detail__smoke-alert {
  margin-top: var(--ss-space-2);
}

.version-detail__actions {
  display: flex;
  gap: var(--ss-space-2);
  margin-left: auto;
  flex-wrap: wrap;
}

.version-detail__permission {
  margin-top: calc(var(--ss-space-1) * -1);
}

.version-detail__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(420px, 1fr));
  gap: var(--ss-space-3);
}

.version-detail__wide {
  grid-column: 1 / -1;
}

.version-detail__card-alert {
  margin-bottom: var(--ss-space-3);
}

.version-detail__risk {
  margin-top: var(--ss-space-3);
}

.version-detail__dataset-source {
  margin-top: var(--ss-space-2);
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
}

.version-detail__dataset-tag {
  margin-left: var(--ss-space-2);
}

.version-detail__risk-list {
  margin: 0;
  padding-left: 18px;
  line-height: 1.7;
}

.version-detail__metrics {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: var(--ss-space-2);
  margin-bottom: var(--ss-space-3);
}

.version-detail__metric {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: var(--ss-space-3);
  border: 1px solid var(--ss-divider);
  border-radius: var(--ss-radius-md, 8px);
  background: var(--ss-neutral-bg);
}

.version-detail__metric span {
  color: var(--ss-text-secondary);
  font-size: var(--ss-font-size-xs);
}

.version-detail__metric strong {
  color: var(--ss-text-primary);
  font-size: 20px;
  font-weight: var(--ss-font-weight-semibold);
}

.version-detail__hint {
  margin: var(--ss-space-1) 0 0;
  color: var(--ss-text-secondary);
  font-size: var(--ss-font-size-sm);
  line-height: 1.6;
}

.version-detail__hint.is-danger {
  color: var(--ss-danger);
}

.version-detail__inline-actions {
  display: flex;
  gap: var(--ss-space-2);
  margin-top: var(--ss-space-3);
}

.version-detail__section {
  margin-top: var(--ss-space-3);
}

.version-detail__safety {
  margin: var(--ss-space-3) 0 0;
  padding-left: 18px;
  color: var(--ss-text-secondary);
  line-height: 1.8;
}

.version-detail__channel-hint {
  margin-bottom: var(--ss-space-2);
}

.version-detail__publish-form {
  margin-top: var(--ss-space-3);
}

.version-detail__dialog-alert {
  margin-bottom: var(--ss-space-3);
}
</style>
