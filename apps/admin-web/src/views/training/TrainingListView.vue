<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { ApiError } from '@/api/http'
import { IS_MOCK } from '@/api/request'
import type { PageResult } from '@/api/types'
import {
  cancelTrainingTask,
  confirmSimulatedExecutorStop,
  fetchTrainingOptions,
  fetchTrainingStats,
  fetchTrainingTasks,
  retryTrainingTask,
} from '@/api/training'
import { ConfirmDialog, DataTable, FilterPanel, PageHeader, Pagination, StatCard } from '@/components'
import TrainingStatusTag from '@/components/training/TrainingStatusTag.vue'
import type {
  TrainingOptions,
  TrainingOverviewStats,
  TrainingTask,
  TrainingTaskQuery,
} from '@/types/training'

/**
 * A05 训练任务列表（P11 / P11-R）。
 *
 * 统计与列表由业务接口实时返回；执行模式（mock / worker）以任务自身字段为准，
 * 页面不再无条件宣称所有任务都是模拟任务。
 */

const router = useRouter()

const DEFAULT_QUERY: TrainingTaskQuery = {
  keyword: '',
  modelType: '',
  datasetId: '',
  taskStatus: '',
  executionMode: '',
  createdFrom: '',
  createdTo: '',
  page: 1,
  pageSize: 10,
}

const query = reactive<TrainingTaskQuery>({ ...DEFAULT_QUERY })
const result = ref<PageResult<TrainingTask>>({ items: [], page: 1, pageSize: 10, total: 0 })
const loading = ref(false)
const error = ref('')

const stats = ref<TrainingOverviewStats>({
  total: 0,
  pending: 0,
  running: 0,
  succeeded: 0,
  failed: 0,
  canceled: 0,
})

const options = ref<TrainingOptions>({
  modelTypes: [],
  optimizers: [],
  devices: [],
  statuses: [],
  executionModes: [],
  taskTypes: [],
  datasets: [],
})

const DEVICE_LABELS: Record<string, string> = {
  cpu: 'CPU',
  cuda: 'CUDA',
  mps: 'MPS',
}

function resolveMessage(cause: unknown, fallback: string): string {
  return cause instanceof ApiError ? cause.message : fallback
}

function modelLabel(value: string): string {
  return options.value.modelTypes.find((item) => item.value === value)?.label ?? value
}

async function loadStats(): Promise<void> {
  try {
    stats.value = await fetchTrainingStats()
  } catch {
    stats.value = { total: 0, pending: 0, running: 0, succeeded: 0, failed: 0, canceled: 0 }
  }
}

async function loadOptions(): Promise<void> {
  try {
    options.value = await fetchTrainingOptions()
  } catch {
    // 筛选项加载失败不阻断主列表，筛选下拉会退化为空
  }
}

async function loadTasks(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    result.value = await fetchTrainingTasks(query)
  } catch (cause) {
    result.value = { items: [], page: query.page, pageSize: query.pageSize, total: 0 }
    error.value = resolveMessage(cause, '训练任务加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

async function refreshAll(): Promise<void> {
  await Promise.all([loadStats(), loadTasks()])
}

onMounted(() => {
  void loadOptions()
  void refreshAll()
})

function handleSearch(): void {
  query.page = 1
  void loadTasks()
}

function handleReset(): void {
  Object.assign(query, { ...DEFAULT_QUERY, pageSize: query.pageSize })
  void loadTasks()
}

const statsCards = computed(() => [
  { label: '任务总数', value: stats.value.total, hint: '全部训练任务记录', tone: 'primary' as const },
  { label: '等待执行', value: stats.value.pending, hint: '尚未被执行器领取', tone: 'warning' as const },
  { label: '正在运行', value: stats.value.running, hint: IS_MOCK ? '仅模拟执行器演示状态' : '由训练执行器实时上报', tone: 'accent' as const },
  { label: '已成功', value: stats.value.succeeded, hint: IS_MOCK ? '模拟结果，非真实训练' : '真实 Worker 执行完成', tone: 'safe' as const },
  { label: '失败任务', value: stats.value.failed, hint: '保留失败原因，可重试', tone: 'danger' as const },
  { label: '已取消', value: stats.value.canceled, hint: '执行器确认后才会取消', tone: 'neutral' as const },
])

/* ===== 跳转 ===== */

function goDetail(row: TrainingTask): void {
  void router.push(`/admin/training/${row.taskId}`)
}

function goLogs(row: TrainingTask): void {
  void router.push({ path: `/admin/training/${row.taskId}`, query: { tab: 'logs' } })
}

function goMetrics(row: TrainingTask): void {
  void router.push({ path: `/admin/training/${row.taskId}`, query: { tab: 'metrics' } })
}

function goCreate(): void {
  void router.push('/admin/training/new')
}

/* ===== 重试 ===== */

const retryVisible = ref(false)
const retryLoading = ref(false)
const retryTarget = ref<TrainingTask | null>(null)

function openRetry(row: TrainingTask): void {
  retryTarget.value = row
  retryVisible.value = true
}

async function submitRetry(): Promise<void> {
  if (!retryTarget.value) return
  retryLoading.value = true
  try {
    const created = await retryTrainingTask(retryTarget.value.taskId)
    ElMessage.success(`已创建重试任务 ${created.taskId}，原始失败记录与日志已保留`)
    retryVisible.value = false
    await refreshAll()
    void router.push(`/admin/training/${created.taskId}`)
  } catch (cause) {
    ElMessage.error(resolveMessage(cause, '重试失败，请检查数据集资格后重试'))
  } finally {
    retryLoading.value = false
  }
}

/* ===== 取消 ===== */

const cancelVisible = ref(false)
const cancelLoading = ref(false)
const cancelTarget = ref<TrainingTask | null>(null)

const cancelMessage = computed(() => {
  if (!cancelTarget.value) return ''
  return cancelTarget.value.taskStatus === 'pending'
    ? '该任务尚未开始执行，取消后会直接标记为已取消。'
    : '任务正在运行，取消请求需要训练执行器确认停止后才会生效。'
})

function openCancel(row: TrainingTask): void {
  cancelTarget.value = row
  cancelVisible.value = true
}

async function submitCancel(): Promise<void> {
  if (!cancelTarget.value) return
  cancelLoading.value = true
  try {
    const updated = await cancelTrainingTask(cancelTarget.value.taskId)
    const isMockTask = updated.executionMode === 'mock'
    if (updated.taskStatus === 'running' && updated.cancelRequestedAt) {
      ElMessage.warning(
        isMockTask
          ? '已发送取消请求，正在等待执行器确认停止（当前为模拟任务）'
          : '已发送取消请求，正在等待训练执行器安全停止，任务不会立即变为已取消',
      )
    } else {
      ElMessage.success(isMockTask ? '任务已取消（模拟任务，未执行真实训练）' : '任务已取消')
    }
    cancelVisible.value = false
    await refreshAll()
  } catch (cause) {
    ElMessage.error(resolveMessage(cause, '取消失败，请稍后重试'))
  } finally {
    cancelLoading.value = false
  }
}

async function confirmStop(row: TrainingTask): Promise<void> {
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
    await confirmSimulatedExecutorStop(row.taskId)
    ElMessage.success(`任务 ${row.taskId} 已取消（模拟确认）`)
    await refreshAll()
  } catch (cause) {
    ElMessage.error(resolveMessage(cause, '确认停止失败'))
  }
}

function canCancel(row: TrainingTask): boolean {
  return row.taskStatus === 'pending' || row.taskStatus === 'running'
}

function canConfirmStop(row: TrainingTask): boolean {
  return row.taskStatus === 'running' && Boolean(row.cancelRequestedAt) && row.executionMode === 'mock'
}
</script>

<template>
  <div class="training-page">
    <PageHeader
      eyebrow="A05 · 数据与模型"
      title="训练管理"
      description="配置训练任务、跟踪执行状态、查看日志与指标。训练由独立 Python Worker 执行，浏览器不参与模型训练。"
    >
      <template #extra>
        <el-tag v-if="IS_MOCK" type="warning" effect="plain">模拟任务</el-tag>
        <el-tag v-else type="success" effect="plain">真实 Worker</el-tag>
        <el-button :loading="loading" @click="refreshAll">刷新</el-button>
        <el-button type="primary" @click="goCreate">新建训练任务</el-button>
      </template>
    </PageHeader>

    <el-alert
      v-if="IS_MOCK"
      type="warning"
      :closable="false"
      show-icon
      title="当前未连接真实 Python 训练执行器：以下任务、日志与指标均为 Mock 演示数据，不代表真实 PyTorch 训练结果。"
    />
    <el-alert
      v-else
      type="success"
      :closable="false"
      show-icon
      title="已连接真实训练执行器：任务由独立 Python Worker 执行，页面仅展示服务端与 Worker 上报的数据。"
    />

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
        <label class="ss-field__label" for="training-keyword">任务名称 / 任务 ID</label>
        <el-input
          id="training-keyword"
          v-model="query.keyword"
          placeholder="输入任务名称或 T-2026-xxx"
          clearable
          @keyup.enter="handleSearch"
        />
      </div>
      <div class="ss-field">
        <label class="ss-field__label" for="training-model">模型类型</label>
        <el-select id="training-model" v-model="query.modelType" placeholder="全部模型" clearable>
          <el-option
            v-for="item in options.modelTypes"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </div>
      <div class="ss-field">
        <label class="ss-field__label" for="training-dataset">数据集</label>
        <el-select id="training-dataset" v-model="query.datasetId" placeholder="全部数据集" clearable>
          <el-option
            v-for="item in options.datasets"
            :key="item.datasetId"
            :label="item.datasetName"
            :value="item.datasetId"
          />
        </el-select>
      </div>
      <div class="ss-field">
        <label class="ss-field__label" for="training-status">任务状态</label>
        <el-select id="training-status" v-model="query.taskStatus" placeholder="全部状态" clearable>
          <el-option
            v-for="item in options.statuses"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </div>
      <div class="ss-field">
        <label class="ss-field__label" for="training-mode">执行模式</label>
        <el-select id="training-mode" v-model="query.executionMode" placeholder="全部模式" clearable>
          <el-option
            v-for="item in options.executionModes"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </div>
      <div class="ss-field">
        <label class="ss-field__label" for="training-from">创建时间起</label>
        <el-date-picker
          id="training-from"
          v-model="query.createdFrom"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="起始日期"
          clearable
        />
      </div>
      <div class="ss-field">
        <label class="ss-field__label" for="training-to">创建时间止</label>
        <el-date-picker
          id="training-to"
          v-model="query.createdTo"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="结束日期"
          clearable
        />
      </div>
    </FilterPanel>

    <DataTable
      :data="result.items"
      :loading="loading"
      :error="error"
      row-key="taskId"
      empty-title="没有匹配的训练任务"
      empty-description="请调整搜索或筛选条件后重试；演示任务共 12 条，重置筛选可恢复。"
      @row-click="goDetail"
      @retry="loadTasks"
    >
      <el-table-column label="任务名称" min-width="220">
        <template #default="{ row }">
          <div class="cell-title">{{ row.taskName }}</div>
          <div class="cell-sub">{{ row.taskId }}</div>
        </template>
      </el-table-column>
      <el-table-column label="模型类型" min-width="170">
        <template #default="{ row }">{{ modelLabel(row.modelType) }}</template>
      </el-table-column>
      <el-table-column label="数据集 / 版本" min-width="200">
        <template #default="{ row }">
          <div class="cell-title">{{ row.datasetName }}</div>
          <div class="cell-sub">{{ row.datasetId }} · {{ row.datasetVersion }}</div>
        </template>
      </el-table-column>
      <el-table-column label="训练设备" width="100">
        <template #default="{ row }">
          {{ DEVICE_LABELS[row.trainingParameters.device] ?? row.trainingParameters.device }}
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="158" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <TrainingStatusTag :status="row.taskStatus" :cancel-requested="Boolean(row.cancelRequestedAt)" />
        </template>
      </el-table-column>
      <el-table-column label="执行模式" width="110">
        <template #default="{ row }">
          <el-tag
            :type="row.executionMode === 'mock' ? 'warning' : 'success'"
            effect="plain"
            size="small"
          >
            {{ row.executionMode === 'mock' ? '模拟任务' : '真实 Worker' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click.stop="goDetail(row)">详情</el-button>
          <el-button link type="primary" @click.stop="goLogs(row)">日志</el-button>
          <el-button link type="primary" @click.stop="goMetrics(row)">指标</el-button>
          <el-button
            v-if="row.taskStatus === 'failed'"
            link
            type="warning"
            @click.stop="openRetry(row)"
          >
            重试
          </el-button>
          <el-button v-if="canConfirmStop(row)" link type="warning" @click.stop="confirmStop(row)">
            模拟确认停止
          </el-button>
          <el-button v-else-if="canCancel(row)" link type="danger" @click.stop="openCancel(row)">
            取消
          </el-button>
        </template>
      </el-table-column>

      <template #footer>
        <Pagination
          v-model:page="query.page"
          v-model:page-size="query.pageSize"
          :total="result.total"
          @change="loadTasks"
        />
      </template>
    </DataTable>

    <ConfirmDialog
      v-model="retryVisible"
      type="primary"
      title="重试失败任务"
      :message="retryTarget ? `将基于任务 ${retryTarget.taskId} 的配置创建新的执行记录。` : ''"
      description="原始失败任务与失败日志不会被覆盖；新任务仍需重新通过数据集训练资格校验，创建后状态为等待执行。"
      confirm-text="创建重试任务"
      :loading="retryLoading"
      @confirm="submitRetry"
    />

    <ConfirmDialog
      v-model="cancelVisible"
      type="warning"
      title="取消训练任务"
      :message="cancelMessage"
      description="该操作会向训练执行器发送取消请求。真实 Worker 未确认停止前，任务不会被标记为已取消；Mock 模式仅演示该流程。"
      confirm-text="确认取消"
      :loading="cancelLoading"
      @confirm="submitCancel"
    />
  </div>
</template>

<style scoped>
.training-page {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-5);
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
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
</style>
