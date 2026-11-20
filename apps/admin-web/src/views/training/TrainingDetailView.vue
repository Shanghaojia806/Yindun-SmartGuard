<script setup lang="ts">
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ApiError } from '@/api/http'
import {
  cancelTrainingTask,
  confirmSimulatedExecutorStop,
  fetchTrainingMetrics,
  fetchTrainingResult,
  fetchTrainingTask,
  retryTrainingTask,
} from '@/api/training'
import { ConfirmDialog, LoadingState, ErrorState, PageHeader } from '@/components'
import TrainingLogPanel from '@/components/training/TrainingLogPanel.vue'
import TrainingStatusTag from '@/components/training/TrainingStatusTag.vue'
import MetricChart from '@/components/training/MetricChart.vue'
import type {
  TrainingMetricSeries,
  TrainingResult,
  TrainingTask,
} from '@/types/training'

/**
 * A05 训练任务详情（P11）。
 *
 * 展示基本信息、参数、执行进度、结果、日志与指标。
 * Mock 任务的进度/指标/结果全部明确标识为演示数据，不伪造真实训练成功。
 */

const route = useRoute()
const router = useRouter()

const taskId = computed(() => String(route.params.taskId ?? ''))
const validTabs = ['overview', 'logs', 'metrics']
const activeTab = ref(
  validTabs.includes(String(route.query.tab)) ? String(route.query.tab) : 'overview',
)

const task = ref<TrainingTask | null>(null)
const loading = ref(false)
const error = ref('')

const result = ref<TrainingResult | null>(null)
const resultLoading = ref(false)
const metrics = ref<TrainingMetricSeries | null>(null)
const metricsLoading = ref(false)
const metricsError = ref('')

const cancelVisible = ref(false)
const cancelLoading = ref(false)
const retryVisible = ref(false)
const retryLoading = ref(false)

const EVALUATION_LABELS: Record<TrainingResult['evaluationStatus'], string> = {
  not_evaluated: '未评估',
  pending: '评估中',
  evaluated: '已评估',
}

const OPTIMIZER_LABELS: Record<string, string> = {
  adam: 'Adam',
  adamw: 'AdamW',
  sgd: 'SGD',
}

const DEVICE_LABELS: Record<string, string> = {
  cpu: 'CPU',
  cuda: 'CUDA',
  mps: 'MPS',
}

function resolveMessage(cause: unknown, fallback: string): string {
  return cause instanceof ApiError ? cause.message : fallback
}

function percentText(value: number | undefined): string {
  return value === undefined ? '—' : `${(value * 100).toFixed(1)}%`
}

function lossText(value: number | undefined): string {
  return value === undefined ? '—' : value.toFixed(3)
}

function durationText(item: TrainingTask): string {
  if (!item.startedAt || !item.completedAt) return '—'
  const start = new Date(item.startedAt.replace(' ', 'T')).getTime()
  const end = new Date(item.completedAt.replace(' ', 'T')).getTime()
  if (!Number.isFinite(start) || !Number.isFinite(end) || end < start) return '—'
  const minutes = Math.round((end - start) / 60000)
  return minutes >= 60 ? `${Math.floor(minutes / 60)} 小时 ${minutes % 60} 分钟` : `${minutes} 分钟`
}

const epochPercent = computed(() => {
  if (!task.value || task.value.progress.totalEpochs <= 0) return 0
  return Math.min(
    100,
    Math.round((task.value.progress.currentEpoch / task.value.progress.totalEpochs) * 100),
  )
})

const epochs = computed(() => metrics.value?.metrics.map((item) => item.epoch) ?? [])
const hasMetrics = computed(() => (metrics.value?.metrics.length ?? 0) > 0)
const metricSourceLabel = computed(() =>
  metrics.value?.source === 'worker' ? '真实 Worker 指标' : '演示指标',
)

const lossSeries = computed(() => {
  const list = metrics.value?.metrics ?? []
  return [
    { name: '训练集 Loss', data: list.map((item) => item.trainLoss), color: '#174A71' },
    { name: '验证集 Loss', data: list.map((item) => item.validationLoss), color: '#176497', dashed: true },
  ]
})

const accuracySeries = computed(() => {
  const list = metrics.value?.metrics ?? []
  return [
    { name: '训练集 Accuracy', data: list.map((item) => item.trainAccuracy), color: '#25845E' },
    { name: '验证集 Accuracy', data: list.map((item) => item.validationAccuracy), color: '#D8A03A', dashed: true },
  ]
})

const prfSeries = computed(() => {
  const list = metrics.value?.metrics ?? []
  return [
    { name: '验证集 Precision', data: list.map((item) => item.validationPrecision), color: '#176497' },
    { name: '验证集 Recall', data: list.map((item) => item.validationRecall), color: '#D8A03A' },
    { name: '验证集 F1', data: list.map((item) => item.validationF1), color: '#C83C3C' },
  ]
})

async function loadTask(): Promise<void> {
  if (!taskId.value) return
  loading.value = true
  error.value = ''
  try {
    task.value = await fetchTrainingTask(taskId.value)
  } catch (cause) {
    task.value = null
    error.value = resolveMessage(cause, '训练任务加载失败')
  } finally {
    loading.value = false
  }
}

async function loadResult(): Promise<void> {
  if (!taskId.value) return
  resultLoading.value = true
  try {
    result.value = await fetchTrainingResult(taskId.value)
  } catch {
    result.value = null
  } finally {
    resultLoading.value = false
  }
}

async function loadMetrics(): Promise<void> {
  if (!taskId.value) return
  metricsLoading.value = true
  metricsError.value = ''
  try {
    metrics.value = await fetchTrainingMetrics(taskId.value)
  } catch (cause) {
    metrics.value = null
    metricsError.value = resolveMessage(cause, '训练指标加载失败')
  } finally {
    metricsLoading.value = false
  }
}

async function loadAll(forceMetrics = false): Promise<void> {
  await Promise.all([loadTask(), loadResult()])
  if (forceMetrics || activeTab.value === 'metrics') {
    await loadMetrics()
  }
}

onMounted(() => {
  void loadAll()
})

watch(taskId, () => {
  result.value = null
  metrics.value = null
  void loadAll()
})

watch(activeTab, (tab) => {
  void router.replace({ query: { ...route.query, tab } })
  if (tab === 'metrics' && metrics.value === null) {
    void loadMetrics()
  }
})

function goBack(): void {
  void router.push('/admin/training')
}

const cancelMessage = computed(() => {
  if (!task.value) return ''
  return task.value.taskStatus === 'pending'
    ? '该任务尚未开始执行，取消后会直接标记为已取消。'
    : '任务正在运行，取消请求需要训练执行器确认停止后才会生效。'
})

async function submitCancel(): Promise<void> {
  if (!task.value) return
  cancelLoading.value = true
  try {
    const updated = await cancelTrainingTask(task.value.taskId)
    task.value = updated
    if (updated.taskStatus === 'running' && updated.cancelRequestedAt) {
      ElMessage.warning(
        updated.executionMode === 'mock'
          ? '已发送取消请求，正在等待执行器确认停止（当前为模拟任务）'
          : '已发送取消请求，正在等待训练执行器安全停止，任务不会立即变为已取消',
      )
    } else {
      ElMessage.success(
        updated.executionMode === 'mock' ? '任务已取消（模拟任务，未执行真实训练）' : '任务已取消',
      )
    }
    cancelVisible.value = false
    await loadAll()
  } catch (cause) {
    ElMessage.error(resolveMessage(cause, '取消失败，请稍后重试'))
  } finally {
    cancelLoading.value = false
  }
}

async function confirmStop(): Promise<void> {
  if (!task.value) return
  try {
    await ElMessageBox.confirm(
      '仅模拟执行器会立即确认停止；真实 Worker 必须自行上报取消结果，前端不能代替确认。',
      '确认模拟执行器已停止？',
      { type: 'warning', confirmButtonText: '模拟确认停止', cancelButtonText: '再等等' },
    )
  } catch {
    return
  }
  try {
    task.value = await confirmSimulatedExecutorStop(task.value.taskId)
    ElMessage.success('任务已取消（模拟确认）')
    await loadAll()
  } catch (cause) {
    ElMessage.error(resolveMessage(cause, '确认停止失败'))
  }
}

async function submitRetry(): Promise<void> {
  if (!task.value) return
  retryLoading.value = true
  try {
    const created = await retryTrainingTask(task.value.taskId)
    ElMessage.success(`已创建重试任务 ${created.taskId}，原始失败记录与日志已保留`)
    retryVisible.value = false
    void router.push(`/admin/training/${created.taskId}`)
  } catch (cause) {
    ElMessage.error(resolveMessage(cause, '重试失败，请检查数据集资格'))
  } finally {
    retryLoading.value = false
  }
}
</script>

<template>
  <div class="training-detail">
    <PageHeader
      eyebrow="A05 · 训练管理"
      :title="task ? task.taskName : '训练任务详情'"
      :description="task ? `${task.taskId} · ${task.modelConfig.modelFamily} · ${task.datasetName} ${task.datasetVersion}` : '查看训练任务配置、执行进度、日志与指标'"
    >
      <template #extra>
        <el-button :icon="ArrowLeft" @click="goBack">返回列表</el-button>
        <el-button :loading="loading" @click="loadAll(true)">刷新</el-button>
        <el-button v-if="task?.taskStatus === 'failed'" type="warning" @click="retryVisible = true">
          重试任务
        </el-button>
        <el-button
          v-if="task && (task.taskStatus === 'pending' || task.taskStatus === 'running') && !task.cancelRequestedAt"
          type="danger"
          plain
          @click="cancelVisible = true"
        >
          取消任务
        </el-button>
        <el-button
          v-if="task && task.taskStatus === 'running' && task.cancelRequestedAt && task.executionMode === 'mock'"
          type="warning"
          @click="confirmStop"
        >
          模拟确认停止
        </el-button>
      </template>
    </PageHeader>

    <LoadingState v-if="loading && !task" text="正在加载训练任务…" />
    <ErrorState v-else-if="error" :description="error" @retry="loadAll" />

    <template v-else-if="task">
      <el-alert
        v-if="task.executionMode === 'mock'"
        type="warning"
        :closable="false"
        show-icon
        title="模拟任务：当前未连接真实 Python 训练执行器，进度、日志、指标与结果均为 Mock 演示数据，不代表真实 PyTorch 训练效果。"
      />

      <el-tabs v-model="activeTab" class="training-detail__tabs">
        <el-tab-pane label="任务概况" name="overview">
          <div class="training-detail__grid">
            <section class="training-detail__card">
              <h3 class="training-detail__card-title">基本信息</h3>
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="任务 ID">{{ task.taskId }}</el-descriptions-item>
                <el-descriptions-item label="任务名称">{{ task.taskName }}</el-descriptions-item>
                <el-descriptions-item label="模型类型">{{ task.modelConfig.modelFamily }}</el-descriptions-item>
                <el-descriptions-item label="训练任务类型">
                  {{ task.modelConfig.taskType === 'risk_binary' ? '风险二分类' : '风险多标签分类' }}
                </el-descriptions-item>
                <el-descriptions-item label="数据集">{{ task.datasetName }}（{{ task.datasetId }}）</el-descriptions-item>
                <el-descriptions-item label="数据集版本">{{ task.datasetVersion }}</el-descriptions-item>
                <el-descriptions-item label="创建人">{{ task.createdBy }}</el-descriptions-item>
                <el-descriptions-item label="创建时间">{{ task.createdAt }}</el-descriptions-item>
                <el-descriptions-item label="当前状态">
                  <TrainingStatusTag :status="task.taskStatus" :cancel-requested="Boolean(task.cancelRequestedAt)" />
                </el-descriptions-item>
                <el-descriptions-item label="执行模式">
                  <el-tag :type="task.executionMode === 'mock' ? 'warning' : 'success'" effect="plain" size="small">
                    {{ task.executionMode === 'mock' ? '模拟任务' : '真实 Worker' }}
                  </el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="执行次数">第 {{ task.attempt }} 次</el-descriptions-item>
                <el-descriptions-item label="重试来源">
                  <router-link v-if="task.retryOfTaskId" :to="`/admin/training/${task.retryOfTaskId}`">
                    {{ task.retryOfTaskId }}
                  </router-link>
                  <span v-else>—</span>
                </el-descriptions-item>
                <el-descriptions-item label="任务描述" :span="2">
                  {{ task.taskDescription || '暂无描述' }}
                </el-descriptions-item>
                <el-descriptions-item v-if="task.errorMessage" label="失败原因" :span="2">
                  <span class="training-detail__danger">{{ task.errorMessage }}</span>
                </el-descriptions-item>
                <el-descriptions-item v-if="task.retriedByTaskId" label="后续重试任务" :span="2">
                  <router-link :to="`/admin/training/${task.retriedByTaskId}`">
                    {{ task.retriedByTaskId }}
                  </router-link>
                </el-descriptions-item>
              </el-descriptions>
            </section>

            <section class="training-detail__card">
              <h3 class="training-detail__card-title">参数配置</h3>
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="Epochs">{{ task.trainingParameters.epochs }}</el-descriptions-item>
                <el-descriptions-item label="Batch Size">{{ task.trainingParameters.batchSize }}</el-descriptions-item>
                <el-descriptions-item label="Learning Rate">{{ task.trainingParameters.learningRate }}</el-descriptions-item>
                <el-descriptions-item label="Optimizer">
                  {{ OPTIMIZER_LABELS[task.trainingParameters.optimizer] ?? task.trainingParameters.optimizer }}
                </el-descriptions-item>
                <el-descriptions-item label="Random Seed">{{ task.trainingParameters.randomSeed }}</el-descriptions-item>
                <el-descriptions-item label="Device">
                  {{ DEVICE_LABELS[task.trainingParameters.device] ?? task.trainingParameters.device }}
                </el-descriptions-item>
                <el-descriptions-item label="Validation Split">{{ task.trainingParameters.validationSplit }}%</el-descriptions-item>
                <el-descriptions-item label="Checkpoint Interval">{{ task.trainingParameters.checkpointInterval }} Epoch</el-descriptions-item>
                <el-descriptions-item label="数据拆分" :span="2">
                  训练 {{ task.datasetSplit.trainCount }} / 验证 {{ task.datasetSplit.validationCount }} / 测试 {{ task.datasetSplit.testCount }}
                </el-descriptions-item>
              </el-descriptions>
              <p class="training-detail__hint">
                参数仅保存为任务配置；当前没有真实 Worker，参数不会被执行。模型结构实现状态：{{
                  task.modelConfig.implementationStatus === 'not_implemented'
                    ? '尚未实现（仅配置项）'
                    : task.modelConfig.implementationStatus === 'planned'
                      ? '计划中'
                      : 'Worker 已注册'
                }}。
              </p>
            </section>

            <section class="training-detail__card">
              <header class="training-detail__card-header">
                <h3 class="training-detail__card-title">执行进度</h3>
                <el-tag v-if="task.executionMode === 'mock'" type="warning" size="small" effect="plain">
                  模拟进度
                </el-tag>
              </header>
              <el-progress
                :percentage="epochPercent"
                :stroke-width="10"
                :status="task.taskStatus === 'failed' ? 'exception' : task.taskStatus === 'succeeded' ? 'success' : undefined"
              />
              <el-descriptions :column="2" border size="small" class="training-detail__block">
                <el-descriptions-item label="当前 Epoch">
                  {{ task.progress.currentEpoch }} / {{ task.progress.totalEpochs }}
                </el-descriptions-item>
                <el-descriptions-item label="当前阶段">{{ task.progress.stage }}</el-descriptions-item>
                <el-descriptions-item label="开始时间">{{ task.startedAt || '—' }}</el-descriptions-item>
                <el-descriptions-item label="最近更新">{{ task.progress.updatedAt }}</el-descriptions-item>
              </el-descriptions>
              <el-alert
                v-if="task.executionMode === 'mock'"
                type="info"
                :closable="false"
                show-icon
                title="只有真实 Worker 上报的进度才是真实运行进度；当前展示的是 Mock 模拟状态。"
                class="training-detail__block"
              />
            </section>

            <section class="training-detail__card">
              <header class="training-detail__card-header">
                <h3 class="training-detail__card-title">任务结果</h3>
                <el-tag v-if="result?.simulated" type="warning" size="small" effect="plain">模拟结果</el-tag>
              </header>

              <el-skeleton v-if="resultLoading" :rows="3" animated />
              <template v-else-if="result">
                <el-alert
                  v-if="result.simulated"
                  type="warning"
                  :closable="false"
                  show-icon
                  title="模拟结果：未执行真实 PyTorch 训练与推理，以下指标不能作为模型反诈能力结论。"
                  class="training-detail__block"
                />
                <el-descriptions :column="2" border size="small">
                  <el-descriptions-item label="最终验证 Accuracy">
                    {{ percentText(result.finalMetrics.validationAccuracy) }}
                  </el-descriptions-item>
                  <el-descriptions-item label="最终验证 F1">
                    {{ percentText(result.finalMetrics.validationF1) }}
                  </el-descriptions-item>
                  <el-descriptions-item label="最终验证 Precision">
                    {{ percentText(result.finalMetrics.validationPrecision) }}
                  </el-descriptions-item>
                  <el-descriptions-item label="最终验证 Recall">
                    {{ percentText(result.finalMetrics.validationRecall) }}
                  </el-descriptions-item>
                  <el-descriptions-item label="最佳检查点">
                    Epoch {{ result.bestCheckpoint.epoch }}（validation_loss {{ lossText(result.bestCheckpoint.validationLoss) }}）
                  </el-descriptions-item>
                  <el-descriptions-item label="训练耗时">{{ durationText(task) }}</el-descriptions-item>
                  <el-descriptions-item label="检查点路径" :span="2">
                    <code class="training-detail__code">{{ result.bestCheckpoint.checkpointPath }}</code>
                  </el-descriptions-item>
                  <el-descriptions-item label="模型文件路径" :span="2">
                    <code class="training-detail__code">{{ result.modelArtifactPath }}</code>
                  </el-descriptions-item>
                  <el-descriptions-item label="数据集版本">{{ result.datasetVersion }}</el-descriptions-item>
                  <el-descriptions-item label="评估状态">
                    {{ EVALUATION_LABELS[result.evaluationStatus] }}
                  </el-descriptions-item>
                  <el-descriptions-item label="评估说明" :span="2">
                    {{ result.evaluationSummary }}
                  </el-descriptions-item>
                </el-descriptions>
              </template>
              <el-empty
                v-else
                :description="
                  task.taskStatus === 'succeeded'
                    ? '任务已标记成功，但执行器未返回结果记录'
                    : '任务尚未成功完成，没有可展示的训练结果；系统不会生成模拟成功结果。'
                "
                :image-size="64"
              />
            </section>
          </div>
        </el-tab-pane>

        <el-tab-pane label="训练日志" name="logs">
          <TrainingLogPanel :task-id="task.taskId" />
        </el-tab-pane>

        <el-tab-pane label="训练指标" name="metrics">
          <div class="training-detail__metrics">
            <el-alert
              v-if="metricsError"
              type="error"
              :closable="false"
              show-icon
              :title="metricsError"
            />
            <el-alert
              v-else-if="metrics"
              :type="metrics.source === 'mock' ? 'warning' : 'info'"
              :closable="false"
              show-icon
              :title="metrics.notice"
            />

            <section class="training-detail__card">
              <h3 class="training-detail__card-title">Epoch 训练进度</h3>
              <el-progress
                :percentage="epochPercent"
                :stroke-width="12"
                :status="task.taskStatus === 'failed' ? 'exception' : task.taskStatus === 'succeeded' ? 'success' : undefined"
              />
              <p class="training-detail__hint">
                当前 {{ task.progress.currentEpoch }} / {{ task.progress.totalEpochs }} 轮 · {{ task.progress.stage }}
              </p>
            </section>

            <el-skeleton v-if="metricsLoading" :rows="6" animated />
            <el-empty
              v-else-if="!hasMetrics"
              description="暂无训练指标：指标必须由训练执行器上报，Mock 环境不会伪造新的训练曲线。"
              :image-size="72"
            />
            <template v-else>
              <MetricChart
                title="Train Loss / Validation Loss"
                subtitle="训练集与验证集损失曲线，横轴为 Epoch"
                :epochs="epochs"
                :series="lossSeries"
                value-type="loss"
                :source-label="metricSourceLabel"
              />
              <MetricChart
                title="Accuracy 曲线"
                subtitle="训练集与验证集准确率必须分开查看，Accuracy 不能单独证明反诈能力"
                :epochs="epochs"
                :series="accuracySeries"
                value-type="percent"
                :source-label="metricSourceLabel"
              />
              <MetricChart
                title="Precision / Recall / F1"
                subtitle="验证集指标对比，类别不平衡场景必须同时关注召回与 F1"
                :epochs="epochs"
                :series="prfSeries"
                value-type="percent"
                :source-label="metricSourceLabel"
              />
            </template>
          </div>
        </el-tab-pane>
      </el-tabs>
    </template>

    <ConfirmDialog
      v-model="cancelVisible"
      type="warning"
      title="取消训练任务"
      :message="cancelMessage"
      description="取消请求必须由训练执行器确认后才算停止；Mock 模式仅演示该流程。"
      confirm-text="确认取消"
      :loading="cancelLoading"
      @confirm="submitCancel"
    />

    <ConfirmDialog
      v-model="retryVisible"
      type="primary"
      title="重试失败任务"
      :message="task ? `将基于任务 ${task.taskId} 的配置创建新的执行记录。` : ''"
      description="原始失败任务与失败日志不会被覆盖；新任务需重新通过数据集资格校验，创建后状态为等待执行。"
      confirm-text="创建重试任务"
      :loading="retryLoading"
      @confirm="submitRetry"
    />
  </div>
</template>

<style scoped>
.training-detail {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-4);
}

.training-detail__tabs {
  border-radius: var(--ss-radius-lg);
}

.training-detail__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(420px, 1fr));
  gap: var(--ss-space-4);
}

.training-detail__card {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-3);
  padding: var(--ss-space-4);
  border: 1px solid var(--ss-border);
  border-radius: var(--ss-radius-lg);
  background: var(--ss-bg-card);
  box-shadow: var(--ss-shadow-sm);
}

.training-detail__card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.training-detail__card-title {
  color: var(--ss-text-primary);
  font-size: var(--ss-font-size-body);
  font-weight: var(--ss-font-weight-semibold);
}

.training-detail__block {
  margin-top: var(--ss-space-2);
}

.training-detail__hint {
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
  line-height: 1.7;
}

.training-detail__danger {
  color: var(--ss-danger);
}

.training-detail__code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: var(--ss-font-size-xs);
  color: var(--ss-text-secondary);
  word-break: break-all;
}

.training-detail__metrics {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-4);
}
</style>
