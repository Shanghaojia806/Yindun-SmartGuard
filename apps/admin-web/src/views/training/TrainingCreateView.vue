<script setup lang="ts">
import { ArrowLeft, CircleCheckFilled, CircleCloseFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { ApiError } from '@/api/http'
import { IS_MOCK } from '@/api/request'
import {
  createTrainingTask,
  fetchTrainingDatasetChecks,
  fetchTrainingOptions,
} from '@/api/training'
import { fetchDedupeOverview, resolveDedupe, runDatasetQualityCheck } from '@/api/dataset'
import { PageHeader } from '@/components'
import {
  DEFAULT_TRAINING_PARAMETERS,
  TRAINING_PARAMETER_LIMITS,
  validateTrainingParameters,
  type ModelType,
  type TrainingDatasetCheck,
  type TrainingModelOption,
  type TrainingOptions,
  type TrainingParameters,
  type TrainingTaskType,
} from '@/types/training'

/**
 * A05 创建训练任务向导（P11）。
 *
 * 四步：基本信息 → 选择数据集版本 → 训练参数 → 确认提交。
 * 数据集资格检查只在前端做交互层提示，提交时 Mock 服务端（以及未来 FastAPI）
 * 会再次校验；不满足条件的版本无法创建任务。
 */

const router = useRouter()

const options = ref<TrainingOptions>({
  modelTypes: [],
  optimizers: [],
  devices: [],
  statuses: [],
  executionModes: [],
  taskTypes: [],
  datasets: [],
})
const optionsLoading = ref(false)
const optionsError = ref('')

const step = ref(0)

const form = reactive({
  taskName: '',
  taskDescription: '',
  modelType: 'text_risk_classifier' as ModelType,
  taskType: 'risk_binary' as TrainingTaskType,
  datasetId: '',
  datasetVersionId: '',
  trainingParameters: { ...DEFAULT_TRAINING_PARAMETERS } as TrainingParameters,
})

const errors = ref<Record<string, string>>({})
const paramErrors = ref<Record<string, string>>({})

const checks = ref<TrainingDatasetCheck[]>([])
const checksLoading = ref(false)
const checksError = ref('')
const prepareLoading = ref(false)
const submitting = ref(false)

const selectedModel = computed<TrainingModelOption | undefined>(() =>
  options.value.modelTypes.find((item) => item.value === form.modelType),
)

const selectedDataset = computed(() =>
  options.value.datasets.find((item) => item.datasetId === form.datasetId),
)

const selectedCheck = computed(() =>
  checks.value.find((item) => item.versionId === form.datasetVersionId),
)

const optimizerLabel = computed(
  () => options.value.optimizers.find((item) => item.value === form.trainingParameters.optimizer)?.label ?? '—',
)

const deviceLabel = computed(
  () => options.value.devices.find((item) => item.value === form.trainingParameters.device)?.label ?? '—',
)

const taskTypeLabel = computed(
  () => options.value.taskTypes.find((item) => item.value === form.taskType)?.label ?? form.taskType,
)

// 阻止原因属于数据集级前置条件，未选中版本时也必须能执行演示准备，否则会形成死循环
const canPrepareDemo = computed(() =>
  checks.value.some(
    (check) =>
      !check.eligible &&
      check.blockedReasons.some((item) => item.includes('质检') || item.includes('重复')),
  ),
)

function resolveMessage(cause: unknown, fallback: string): string {
  return cause instanceof ApiError ? cause.message : fallback
}

async function loadOptions(): Promise<void> {
  optionsLoading.value = true
  optionsError.value = ''
  try {
    options.value = await fetchTrainingOptions()
    const first = options.value.modelTypes[0]
    if (first) {
      form.modelType = first.value
      form.taskType = first.taskTypes[0]
    }
  } catch (cause) {
    optionsError.value = resolveMessage(cause, '训练配置项加载失败')
  } finally {
    optionsLoading.value = false
  }
}

onMounted(() => {
  void loadOptions()
})

watch(
  () => form.modelType,
  () => {
    const model = selectedModel.value
    if (model && !model.taskTypes.includes(form.taskType)) {
      form.taskType = model.taskTypes[0]
    }
  },
)

async function loadChecks(): Promise<void> {
  if (!form.datasetId) {
    checks.value = []
    return
  }
  checksLoading.value = true
  checksError.value = ''
  try {
    checks.value = await fetchTrainingDatasetChecks(form.datasetId)
  } catch (cause) {
    checks.value = []
    checksError.value = resolveMessage(cause, '数据集资格校验失败')
  } finally {
    checksLoading.value = false
  }
}

watch(
  () => form.datasetId,
  () => {
    form.datasetVersionId = ''
    void loadChecks()
  },
)

watch(
  () => form.datasetVersionId,
  () => {
    const check = selectedCheck.value
    if (check && !check.eligible) {
      form.datasetVersionId = ''
    }
  },
)

function validateBasic(): boolean {
  const next: Record<string, string> = {}
  const name = form.taskName.trim()
  if (!name) next.taskName = '请填写任务名称'
  else if (name.length > 50) next.taskName = '任务名称不能超过 50 个字符'
  if (form.taskDescription.trim().length > 200) next.taskDescription = '任务描述不能超过 200 个字符'
  const model = selectedModel.value
  if (!model) next.modelType = '请选择模型类型'
  else if (!model.taskTypes.includes(form.taskType)) next.taskType = '当前模型类型不支持所选训练任务类型'
  errors.value = next
  return Object.keys(next).length === 0
}

function validateDataset(): boolean {
  if (!form.datasetId) {
    ElMessage.warning('请选择数据集')
    return false
  }
  if (!form.datasetVersionId) {
    ElMessage.warning('请选择通过训练资格校验的数据集版本')
    return false
  }
  if (!selectedCheck.value?.eligible) {
    ElMessage.error('所选数据集版本未通过训练资格校验，请先处理阻止原因')
    return false
  }
  return true
}

function validateParams(): boolean {
  paramErrors.value = validateTrainingParameters(form.trainingParameters)
  if (Object.keys(paramErrors.value).length > 0) {
    ElMessage.warning('请修正训练参数后再继续')
    return false
  }
  return true
}

function handleNext(): void {
  if (step.value === 0 && !validateBasic()) return
  if (step.value === 1 && !validateDataset()) return
  if (step.value === 2 && !validateParams()) return
  step.value += 1
}

function handlePrev(): void {
  if (step.value > 0) step.value -= 1
}

async function prepareDemoData(): Promise<void> {
  if (!form.datasetId) return
  prepareLoading.value = true
  try {
    await runDatasetQualityCheck(form.datasetId)
    const overview = await fetchDedupeOverview(form.datasetId)
    const pending = overview.groups.filter((item) => item.status === 'pending')
    for (const group of pending) {
      const exclude = group.sampleIds.filter((item) => item !== group.keepSampleId)
      await resolveDedupe(form.datasetId, group.groupId, group.keepSampleId, exclude)
    }
    await loadChecks()
    ElMessage.success(
      `已执行 A04 演示质检并处理 ${pending.length} 组疑似重复（仅修改本地 Mock 数据，不代表真实数据治理完成）`,
    )
  } catch (cause) {
    ElMessage.error(resolveMessage(cause, '演示数据准备失败'))
  } finally {
    prepareLoading.value = false
  }
}

async function submit(): Promise<void> {
  if (!validateBasic() || !validateDataset() || !validateParams()) return
  submitting.value = true
  try {
    const created = await createTrainingTask({
      taskName: form.taskName,
      taskDescription: form.taskDescription,
      modelType: form.modelType,
      taskType: form.taskType,
      datasetId: form.datasetId,
      datasetVersionId: form.datasetVersionId,
      trainingParameters: { ...form.trainingParameters },
    })
    ElMessage.success(
      IS_MOCK
        ? `已创建任务 ${created.taskId}，当前状态为等待执行（模拟任务，不会执行真实训练）`
        : `已创建任务 ${created.taskId}，状态为等待执行，训练执行器领取后将开始真实 CPU 训练`,
    )
    void router.push(`/admin/training/${created.taskId}`)
  } catch (cause) {
    ElMessage.error(resolveMessage(cause, '创建训练任务失败，请检查数据集资格'))
  } finally {
    submitting.value = false
  }
}

function goBack(): void {
  void router.push('/admin/training')
}
</script>

<template>
  <div class="training-create">
    <PageHeader
      eyebrow="A05 · 训练管理"
      title="创建训练任务"
      description="按步骤完成基本信息、数据集版本、训练参数与最终确认。任务由独立 Python Worker 执行，浏览器不参与训练。"
    >
      <template #extra>
        <el-tag :type="IS_MOCK ? 'warning' : 'success'" effect="plain">
          {{ IS_MOCK ? '模拟任务' : '真实 Worker' }}
        </el-tag>
        <el-button :icon="ArrowLeft" @click="goBack">返回列表</el-button>
      </template>
    </PageHeader>

    <el-alert
      v-if="IS_MOCK"
      type="warning"
      :closable="false"
      show-icon
      title="训练由独立 Python Worker 执行，浏览器不参与训练；当前未连接真实 Worker，创建的任务不会真正开始训练。"
    />
    <el-alert
      v-else
      type="success"
      :closable="false"
      show-icon
      title="已连接真实训练执行器：创建后由 Python Worker 领取任务并执行 CPU Smoke Test，页面展示的日志与指标来自 Worker 上报。"
    />

    <el-steps :active="step" align-center finish-status="success" class="training-create__steps">
      <el-step title="基本信息" description="名称、模型与任务类型" />
      <el-step title="选择数据集" description="版本与训练资格校验" />
      <el-step title="训练参数" description="Epochs、批次与优化器" />
      <el-step title="确认任务" description="核对后提交" />
    </el-steps>

    <el-card shadow="never" class="training-create__card">
      <el-alert
        v-if="optionsError"
        type="error"
        :closable="false"
        show-icon
        :title="optionsError"
        class="training-create__block"
      />
      <el-skeleton v-if="optionsLoading" :rows="6" animated />

      <template v-else>
        <!-- 第一步：基本信息 -->
        <div v-show="step === 0" class="training-create__section">
          <h2 class="training-create__title">基本信息</h2>
          <el-form label-position="top">
            <el-form-item label="任务名称" required :error="errors.taskName">
              <el-input
                v-model="form.taskName"
                placeholder="例如：文本反诈分类-第三轮训练"
                maxlength="50"
                show-word-limit
              />
            </el-form-item>
            <el-form-item label="任务描述" :error="errors.taskDescription">
              <el-input
                v-model="form.taskDescription"
                type="textarea"
                :rows="3"
                maxlength="200"
                show-word-limit
                placeholder="说明训练目的、数据范围与预期验证内容（不填写也不影响创建）"
              />
            </el-form-item>
            <el-form-item label="模型类型" required :error="errors.modelType">
              <el-radio-group v-model="form.modelType" class="training-create__model-group">
                <el-radio-button
                  v-for="model in options.modelTypes"
                  :key="model.value"
                  :value="model.value"
                >
                  {{ model.label }}
                </el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-alert
              v-if="selectedModel"
              type="info"
              :closable="false"
              show-icon
              :title="selectedModel.description"
              :description="`实现状态：${selectedModel.implementationStatus === 'not_implemented' ? '尚未实现（仅配置项）' : selectedModel.implementationStatus === 'planned' ? '计划中' : 'Worker 已注册'}；${selectedModel.implementationNote}`"
              class="training-create__block"
            />
            <el-form-item label="训练任务类型" required :error="errors.taskType">
              <el-select v-model="form.taskType" class="training-create__select">
                <el-option
                  v-for="item in options.taskTypes.filter((option) => selectedModel?.taskTypes.includes(option.value))"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-form>
        </div>

        <!-- 第二步：选择数据集 -->
        <div v-show="step === 1" class="training-create__section">
          <h2 class="training-create__title">选择数据集版本</h2>
          <p class="training-create__hint">
            训练数据必须来自 A04 已发布且通过资格校验的数据集版本；校验在提交时由服务端再次执行。
          </p>

          <el-form label-position="top">
            <el-form-item label="数据集">
              <el-select
                v-model="form.datasetId"
                placeholder="选择数据集"
                class="training-create__select"
                filterable
              >
                <el-option
                  v-for="item in options.datasets"
                  :key="item.datasetId"
                  :label="`${item.datasetName}（${item.datasetId}）`"
                  :value="item.datasetId"
                />
              </el-select>
            </el-form-item>
          </el-form>

          <el-skeleton v-if="checksLoading" :rows="4" animated />
          <el-alert
            v-else-if="checksError"
            type="error"
            :closable="false"
            show-icon
            :title="checksError"
          />
          <el-empty
            v-else-if="form.datasetId && checks.length === 0"
            description="该数据集暂无版本记录，请先在 A04 创建并发布版本"
            :image-size="64"
          />
          <el-empty
            v-else-if="!form.datasetId"
            description="请选择数据集后查看版本训练资格"
            :image-size="64"
          />

          <template v-else>
            <el-radio-group v-model="form.datasetVersionId" class="training-create__versions">
              <el-radio
                v-for="check in checks"
                :key="check.versionId"
                :value="check.versionId"
                :disabled="!check.eligible"
                class="training-create__version"
                border
              >
                <div class="training-create__version-main">
                  <span class="training-create__version-name">
                    {{ check.versionNumber }}
                    <el-tag
                      :type="check.versionStatus === 'published' ? 'success' : 'info'"
                      size="small"
                      effect="plain"
                    >
                      {{ check.versionStatus === 'published' ? '已发布' : check.versionStatus === 'reviewing' ? '审核中' : '草稿' }}
                    </el-tag>
                  </span>
                  <span class="training-create__version-meta">
                    样本 {{ check.sampleCount }} · 训练 {{ check.trainCount }} / 验证 {{ check.validationCount }} / 测试 {{ check.testCount }}
                  </span>
                  <span
                    class="training-create__version-state"
                    :class="check.eligible ? 'is-pass' : 'is-blocked'"
                  >
                    {{ check.eligible ? '通过资格校验' : `存在 ${check.blockedReasons.length} 项阻止原因` }}
                  </span>
                  <ul v-if="!check.eligible" class="training-create__version-reasons">
                    <li v-for="reason in check.blockedReasons" :key="reason">{{ reason }}</li>
                  </ul>
                </div>
              </el-radio>
            </el-radio-group>

            <div v-if="form.datasetId && selectedDataset" class="training-create__actions-row">
              <el-button
                v-if="canPrepareDemo"
                type="warning"
                plain
                :loading="prepareLoading"
                @click="prepareDemoData"
              >
                执行 A04 演示质检与去重准备
              </el-button>
              <el-button link type="primary" @click="router.push(`/admin/datasets/${form.datasetId}/versions`)">
                前往数据集版本管理
              </el-button>
            </div>

            <section v-if="selectedCheck" class="training-create__checks">
              <header class="training-create__checks-header">
                <span>资格检查结果（{{ selectedCheck.versionNumber }}）</span>
                <span class="training-create__checks-time">校验时间：{{ selectedCheck.checkedAt }}</span>
              </header>
              <ul class="training-create__check-list">
                <li
                  v-for="item in selectedCheck.checks"
                  :key="item.key"
                  class="training-create__check"
                  :class="item.passed ? 'is-pass' : 'is-blocked'"
                >
                  <el-icon :size="16">
                    <CircleCheckFilled v-if="item.passed" />
                    <CircleCloseFilled v-else />
                  </el-icon>
                  <div>
                    <p class="training-create__check-label">{{ item.label }}</p>
                    <p class="training-create__check-detail">{{ item.detail }}</p>
                  </div>
                </li>
              </ul>
              <p class="training-create__hint">{{ selectedCheck.note }}</p>
            </section>
          </template>
        </div>

        <!-- 第三步：训练参数 -->
        <div v-show="step === 2" class="training-create__section">
          <h2 class="training-create__title">训练参数</h2>
          <p class="training-create__hint">
            参数仅作为训练任务配置保存；当前没有真实 Worker，参数不会被执行。
          </p>

          <el-form label-position="top" class="training-create__params">
            <el-form-item label="Epochs（训练轮数）" :error="paramErrors.epochs">
              <el-input-number
                v-model="form.trainingParameters.epochs"
                :min="TRAINING_PARAMETER_LIMITS.epochs.min"
                :max="TRAINING_PARAMETER_LIMITS.epochs.max"
                :step="1"
                step-strictly
              />
              <span class="training-create__range">范围 1–200</span>
            </el-form-item>
            <el-form-item label="Batch Size（批次大小）" :error="paramErrors.batchSize">
              <el-input-number
                v-model="form.trainingParameters.batchSize"
                :min="TRAINING_PARAMETER_LIMITS.batchSize.min"
                :max="TRAINING_PARAMETER_LIMITS.batchSize.max"
                :step="1"
                step-strictly
              />
              <span class="training-create__range">范围 1–512</span>
            </el-form-item>
            <el-form-item label="Learning Rate（学习率）" :error="paramErrors.learningRate">
              <el-input-number
                v-model="form.trainingParameters.learningRate"
                :min="TRAINING_PARAMETER_LIMITS.learningRate.min"
                :max="TRAINING_PARAMETER_LIMITS.learningRate.max"
                :step="0.0001"
                :precision="5"
              />
              <span class="training-create__range">范围 0.00001–0.1</span>
            </el-form-item>
            <el-form-item label="Optimizer（优化器）" :error="paramErrors.optimizer">
              <el-select v-model="form.trainingParameters.optimizer">
                <el-option
                  v-for="item in options.optimizers"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
              <span class="training-create__range">
                {{ options.optimizers.find((item) => item.value === form.trainingParameters.optimizer)?.note }}
              </span>
            </el-form-item>
            <el-form-item label="Random Seed（随机种子）" :error="paramErrors.randomSeed">
              <el-input-number
                v-model="form.trainingParameters.randomSeed"
                :min="TRAINING_PARAMETER_LIMITS.randomSeed.min"
                :max="TRAINING_PARAMETER_LIMITS.randomSeed.max"
                :step="1"
                step-strictly
              />
              <span class="training-create__range">范围 0–4294967295</span>
            </el-form-item>
            <el-form-item label="Device（训练设备）" :error="paramErrors.device">
              <el-select v-model="form.trainingParameters.device">
                <el-option
                  v-for="item in options.devices"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
              <span class="training-create__range">
                {{ options.devices.find((item) => item.value === form.trainingParameters.device)?.note }}
              </span>
            </el-form-item>
            <el-form-item label="Validation Split（验证集比例）" :error="paramErrors.validationSplit">
              <el-input-number
                v-model="form.trainingParameters.validationSplit"
                :min="TRAINING_PARAMETER_LIMITS.validationSplit.min"
                :max="TRAINING_PARAMETER_LIMITS.validationSplit.max"
                :step="1"
                step-strictly
              />
              <span class="training-create__range">范围 5–50（%）</span>
            </el-form-item>
            <el-form-item label="Checkpoint Interval（检查点间隔）" :error="paramErrors.checkpointInterval">
              <el-input-number
                v-model="form.trainingParameters.checkpointInterval"
                :min="TRAINING_PARAMETER_LIMITS.checkpointInterval.min"
                :max="TRAINING_PARAMETER_LIMITS.checkpointInterval.max"
                :step="1"
                step-strictly
              />
              <span class="training-create__range">范围 1–50（Epoch），不能大于训练轮数</span>
            </el-form-item>
          </el-form>

          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="训练集/验证集指标必须分开统计；Accuracy 不能单独作为反诈能力结论，需同时关注 Precision、Recall 与 F1。"
          />
        </div>

        <!-- 第四步：确认 -->
        <div v-show="step === 3" class="training-create__section">
          <h2 class="training-create__title">确认任务</h2>
          <el-descriptions :column="2" border size="small" class="training-create__review">
            <el-descriptions-item label="任务名称">{{ form.taskName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="模型类型">{{ selectedModel?.label ?? '—' }}</el-descriptions-item>
            <el-descriptions-item label="训练任务类型">{{ taskTypeLabel }}</el-descriptions-item>
            <el-descriptions-item label="执行模式">
              <el-tag :type="IS_MOCK ? 'warning' : 'success'" effect="plain" size="small">
                {{ IS_MOCK ? '模拟任务' : '真实 Worker' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="数据集">{{ selectedDataset?.datasetName ?? '—' }}</el-descriptions-item>
            <el-descriptions-item label="数据集版本">{{ selectedCheck?.versionNumber ?? '—' }}</el-descriptions-item>
            <el-descriptions-item label="数据拆分">
              训练 {{ selectedCheck?.trainCount ?? 0 }} / 验证 {{ selectedCheck?.validationCount ?? 0 }} / 测试 {{ selectedCheck?.testCount ?? 0 }}
            </el-descriptions-item>
            <el-descriptions-item label="任务描述">{{ form.taskDescription || '暂无描述' }}</el-descriptions-item>
          </el-descriptions>

          <el-descriptions :column="2" border size="small" class="training-create__review">
            <el-descriptions-item label="Epochs">{{ form.trainingParameters.epochs }}</el-descriptions-item>
            <el-descriptions-item label="Batch Size">{{ form.trainingParameters.batchSize }}</el-descriptions-item>
            <el-descriptions-item label="Learning Rate">{{ form.trainingParameters.learningRate }}</el-descriptions-item>
            <el-descriptions-item label="Optimizer">{{ optimizerLabel }}</el-descriptions-item>
            <el-descriptions-item label="Random Seed">{{ form.trainingParameters.randomSeed }}</el-descriptions-item>
            <el-descriptions-item label="Device">{{ deviceLabel }}</el-descriptions-item>
            <el-descriptions-item label="Validation Split">{{ form.trainingParameters.validationSplit }}%</el-descriptions-item>
            <el-descriptions-item label="Checkpoint Interval">{{ form.trainingParameters.checkpointInterval }} Epoch</el-descriptions-item>
          </el-descriptions>

          <section v-if="selectedCheck" class="training-create__checks">
            <header class="training-create__checks-header">
              <span>资格检查结果</span>
              <el-tag :type="selectedCheck.eligible ? 'success' : 'danger'" size="small">
                {{ selectedCheck.eligible ? '通过' : '未通过' }}
              </el-tag>
            </header>
            <ul class="training-create__check-list">
              <li
                v-for="item in selectedCheck.checks"
                :key="item.key"
                class="training-create__check"
                :class="item.passed ? 'is-pass' : 'is-blocked'"
              >
                <el-icon :size="16">
                  <CircleCheckFilled v-if="item.passed" />
                  <CircleCloseFilled v-else />
                </el-icon>
                <div>
                  <p class="training-create__check-label">{{ item.label }}</p>
                  <p class="training-create__check-detail">{{ item.detail }}</p>
                </div>
              </li>
            </ul>
          </section>

          <el-alert
            type="warning"
            :closable="false"
            show-icon
            title="提交后任务状态为「等待执行」，不会直接标记为训练成功；成功结果必须由真实执行器上报。"
            class="training-create__block"
          />
        </div>
      </template>
    </el-card>

    <div class="training-create__footer">
      <el-button :disabled="step === 0 || submitting" @click="handlePrev">上一步</el-button>
      <el-button v-if="step < 3" type="primary" @click="handleNext">下一步</el-button>
      <el-button v-else type="primary" :loading="submitting" @click="submit">提交任务</el-button>
    </div>
  </div>
</template>

<style scoped>
.training-create {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-5);
}

.training-create__steps {
  padding: var(--ss-space-4);
  border: 1px solid var(--ss-border);
  border-radius: var(--ss-radius-lg);
  background: var(--ss-bg-card);
}

.training-create__card {
  border: 1px solid var(--ss-border);
  border-radius: var(--ss-radius-lg);
  box-shadow: var(--ss-shadow-sm);
}

.training-create__section {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-4);
}

.training-create__title {
  color: var(--ss-text-primary);
  font-size: var(--ss-font-size-body-lg);
  font-weight: var(--ss-font-weight-semibold);
}

.training-create__hint {
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
  line-height: 1.7;
}

.training-create__block {
  margin-top: var(--ss-space-3);
}

.training-create__select {
  width: 100%;
  max-width: 420px;
}

.training-create__model-group {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ss-space-2);
}

.training-create__versions {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-2);
  width: 100%;
}

.training-create__version {
  width: 100%;
  height: auto;
  margin-right: 0;
  padding: var(--ss-space-3) var(--ss-space-4);
  align-items: flex-start;
}

.training-create__version-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
  white-space: normal;
}

.training-create__version-name {
  display: inline-flex;
  align-items: center;
  gap: var(--ss-space-2);
  color: var(--ss-text-primary);
  font-weight: var(--ss-font-weight-medium);
}

.training-create__version-meta {
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
}

.training-create__version-state {
  font-size: var(--ss-font-size-xs);
}

.training-create__version-state.is-pass {
  color: var(--ss-safe);
}

.training-create__version-state.is-blocked {
  color: var(--ss-danger);
}

.training-create__version-reasons {
  margin: 2px 0 0;
  padding-left: 18px;
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
  line-height: 1.6;
}

.training-create__actions-row {
  display: flex;
  align-items: center;
  gap: var(--ss-space-3);
  flex-wrap: wrap;
}

.training-create__checks {
  padding: var(--ss-space-4);
  border: 1px solid var(--ss-border);
  border-radius: var(--ss-radius-md);
  background: var(--ss-bg-subtle);
}

.training-create__checks-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ss-space-3);
  margin-bottom: var(--ss-space-3);
  color: var(--ss-text-primary);
  font-size: var(--ss-font-size-sm);
  font-weight: var(--ss-font-weight-medium);
}

.training-create__checks-time {
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
  font-weight: normal;
}

.training-create__check-list {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.training-create__check {
  display: flex;
  align-items: flex-start;
  gap: var(--ss-space-2);
  color: var(--ss-text-secondary);
  font-size: var(--ss-font-size-sm);
}

.training-create__check.is-pass :deep(.el-icon) {
  color: var(--ss-safe);
}

.training-create__check.is-blocked :deep(.el-icon) {
  color: var(--ss-danger);
}

.training-create__check-label {
  color: var(--ss-text-primary);
  font-size: var(--ss-font-size-sm);
}

.training-create__check-detail {
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
  line-height: 1.6;
}

.training-create__params {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: var(--ss-space-2) var(--ss-space-5);
}

.training-create__range {
  display: block;
  margin-top: 2px;
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
}

.training-create__review {
  margin-top: var(--ss-space-2);
}

.training-create__footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--ss-space-2);
}
</style>
