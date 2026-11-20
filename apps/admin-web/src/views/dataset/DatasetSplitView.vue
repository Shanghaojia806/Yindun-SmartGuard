<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ApiError } from '@/api/http'
import {
  createDatasetVersion,
  fetchDatasetDetail,
  fetchSplitPreview,
} from '@/api/dataset'
import {
  DataTable,
  ErrorState,
  LoadingState,
  PageHeader,
  StatCard,
} from '@/components'
import DatasetWorkspaceHeader from '@/components/dataset/DatasetWorkspaceHeader.vue'
import type {
  DatasetSummary,
  DatasetVersion,
  SplitConfig,
  SplitResult,
} from '@/types/dataset'

/**
 * A04 数据拆分（P10）。
 *
 * 仅对通过训练资格校验的样本进行拆分；默认按来源名称分组，
 * Mock 环境无法验证真实内容相似度，页面明确提示泄漏风险未验证。
 */

const route = useRoute()
const router = useRouter()
const datasetId = computed(() => String(route.params.id ?? ''))

const dataset = ref<DatasetSummary | null>(null)
const datasetLoading = ref(false)
const datasetError = ref('')

const config = reactive<SplitConfig>({
  trainRatio: 70,
  validationRatio: 15,
  testRatio: 15,
  seed: 42,
  strategy: 'group_by_source',
})

const result = ref<SplitResult | null>(null)
const previewLoading = ref(false)
const previewError = ref('')

const ratioTotal = computed(
  () => Number(config.trainRatio) + Number(config.validationRatio) + Number(config.testRatio),
)
const ratioValid = computed(() => Math.abs(ratioTotal.value - 100) < 0.001)
/** 任意集合比例为 0：不擅自改成非零，直接阻止创建正式版本 */
const hasZeroRatio = computed(
  () => Number(config.trainRatio) <= 0 || Number(config.validationRatio) <= 0 || Number(config.testRatio) <= 0,
)

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

onMounted(loadDataset)

watch(datasetId, () => {
  result.value = null
  void loadDataset()
})

function applyPreset() {
  config.trainRatio = 70
  config.validationRatio = 15
  config.testRatio = 15
}

async function generatePreview() {
  if (!ratioValid.value) {
    ElMessage.warning(`三个比例之和必须等于 100%，当前为 ${ratioTotal.value}%`)
    return
  }
  previewLoading.value = true
  previewError.value = ''
  try {
    result.value = await fetchSplitPreview(datasetId.value, {
      trainRatio: Number(config.trainRatio),
      validationRatio: Number(config.validationRatio),
      testRatio: Number(config.testRatio),
      seed: Number(config.seed),
      strategy: config.strategy,
    })
    ElMessage.success('拆分预览已生成（本地 Mock 规则）')
  } catch (cause) {
    result.value = null
    previewError.value = resolveMessage(cause, '拆分预览生成失败')
  } finally {
    previewLoading.value = false
  }
}

const LEAKAGE_LABELS: Record<SplitResult['leakageRisk'], string> = {
  grouped: '已按来源分组（内容相似度未验证）',
  partial_group_split: '部分来源分组被拆分以满足比例（存在泄漏风险）',
  unverified: '未验证分组隔离，存在泄漏风险',
}

function splitLabel(split: string): string {
  if (split === 'train') return '训练集'
  if (split === 'validation') return '验证集'
  return '测试集'
}

function splitTagType(split: string): 'success' | 'warning' | 'danger' {
  if (split === 'train') return 'success'
  if (split === 'validation') return 'warning'
  return 'danger'
}

function strategyLabel(strategy: string): string {
  return strategy === 'random' ? '随机拆分' : '按来源分组'
}

/* ===== 创建版本 ===== */
const versionVisible = ref(false)
const versionLoading = ref(false)
const versionDescription = ref('')
const versionChangeLog = ref('')
const createdVersion = ref<DatasetVersion | null>(null)

function openVersionDialog() {
  if (!result.value) {
    ElMessage.warning('请先生成拆分预览')
    return
  }
  if (hasZeroRatio.value) {
    ElMessage.warning(
      `训练集、验证集、测试集比例必须均大于 0%，当前为 ${config.trainRatio}/${config.validationRatio}/${config.testRatio}，无法创建正式版本`,
    )
    return
  }
  if (result.value.eligibleCount === 0) {
    ElMessage.warning('当前没有满足训练资格的样本，无法创建版本')
    return
  }
  if (result.value.eligibleCount < 3) {
    ElMessage.warning(
      `合格样本仅 ${result.value.eligibleCount} 条，不足以拆分为训练/验证/测试三个非空集合，无法创建正式版本`,
    )
    return
  }
  versionDescription.value = `基于 ${config.trainRatio}/${config.validationRatio}/${config.testRatio} 拆分的训练版本`
  versionChangeLog.value = '按最新标注与质检结果重新拆分并创建版本'
  createdVersion.value = null
  versionVisible.value = true
}

async function submitVersion() {
  versionLoading.value = true
  try {
    const version = await createDatasetVersion(datasetId.value, {
      versionDescription: versionDescription.value,
      changeLog: versionChangeLog.value,
      splitConfig: {
        trainRatio: Number(config.trainRatio),
        validationRatio: Number(config.validationRatio),
        testRatio: Number(config.testRatio),
        seed: Number(config.seed),
        strategy: config.strategy,
      },
    })
    createdVersion.value = version
    versionVisible.value = false
    ElMessage.success(`已创建版本 ${version.versionNumber}（草稿，本地 Mock）`)
    await loadDataset()
  } catch (cause) {
    ElMessage.error(resolveMessage(cause, '创建版本失败，请先完成标注与质检'))
  } finally {
    versionLoading.value = false
  }
}

function goVersions() {
  void router.push(`/admin/datasets/${datasetId.value}/versions`)
}

function goAnnotation() {
  void router.push(`/admin/datasets/${datasetId.value}/annotation`)
}
</script>

<template>
  <div class="split-page">
    <LoadingState v-if="datasetLoading" text="正在加载拆分工作台" min-height="360px" />
    <ErrorState v-else-if="datasetError" :description="datasetError" @retry="loadDataset" />

    <template v-else-if="dataset">
      <DatasetWorkspaceHeader :dataset="dataset" active="split" />

      <PageHeader
        eyebrow="A04 · 数据拆分"
        title="训练 / 验证 / 测试拆分"
        description="按训练资格筛选样本后拆分。没有许可、未通过质检或存在重复冲突的样本不会进入正式训练集。"
        :divider="false"
      >
        <template #extra>
          <el-tag type="warning" effect="plain">演示数据（Mock）</el-tag>
          <el-button @click="goVersions">查看版本</el-button>
        </template>
      </PageHeader>

      <section class="split-config">
        <header class="split-config__header">
          <h3 class="split-config__title">拆分配置</h3>
          <el-button size="small" @click="applyPreset">恢复默认 70 / 15 / 15</el-button>
        </header>

        <div class="split-config__grid">
          <div class="ss-field">
            <label class="ss-field__label" for="split-train">训练集比例（%）</label>
            <el-input-number id="split-train" v-model="config.trainRatio" :min="0" :max="100" :step="5" />
          </div>
          <div class="ss-field">
            <label class="ss-field__label" for="split-validation">验证集比例（%）</label>
            <el-input-number id="split-validation" v-model="config.validationRatio" :min="0" :max="100" :step="5" />
          </div>
          <div class="ss-field">
            <label class="ss-field__label" for="split-test">测试集比例（%）</label>
            <el-input-number id="split-test" v-model="config.testRatio" :min="0" :max="100" :step="5" />
          </div>
          <div class="ss-field">
            <label class="ss-field__label" for="split-seed">随机种子</label>
            <el-input-number id="split-seed" v-model="config.seed" :min="0" :step="1" />
          </div>
          <div class="ss-field">
            <label class="ss-field__label" for="split-strategy">拆分策略</label>
            <el-select id="split-strategy" v-model="config.strategy">
              <el-option label="按来源分组（推荐）" value="group_by_source" />
              <el-option label="随机拆分" value="random" />
            </el-select>
          </div>
        </div>

        <div class="split-config__footer">
          <span :class="['split-config__sum', ratioValid ? 'is-valid' : 'is-invalid']">
            比例合计 {{ ratioTotal }}%
            <template v-if="!ratioValid">（必须等于 100%）</template>
          </span>
          <el-button type="primary" :loading="previewLoading" :disabled="!ratioValid" @click="generatePreview">
            生成拆分预览
          </el-button>
        </div>
      </section>

      <el-alert v-if="previewError" type="error" :closable="false" show-icon :title="previewError" />

      <template v-if="result">
        <section class="split-summary">
          <StatCard label="参与拆分样本" :value="result.eligibleCount" :hint="`数据集共 ${result.totalSamples} 条，已排除 ${result.excluded.length} 条`" />
          <StatCard label="训练集" :value="result.trainCount" :hint="`${config.trainRatio}% 目标比例`" tone="safe" />
          <StatCard label="验证集" :value="result.validationCount" :hint="`${config.validationRatio}% 目标比例`" tone="warning" />
          <StatCard label="测试集" :value="result.testCount" :hint="`${config.testRatio}% 目标比例`" tone="danger" />
        </section>

        <el-alert
          :type="result.leakageRisk === 'unverified' ? 'error' : 'warning'"
          :closable="false"
          show-icon
          :title="LEAKAGE_LABELS[result.leakageRisk]"
          :description="`当前策略：${strategyLabel(result.config.strategy)}，随机种子 ${result.config.seed}。拆分数量按最大余数法分配，三者之和等于合格样本数；Mock 环境无法验证原始视频或内容的相似度，真实拆分仍需后端校验。`"
        />

        <ul class="split-warnings">
          <li v-for="warning in result.warnings" :key="warning">{{ warning }}</li>
        </ul>

        <section class="split-block">
          <header class="split-block__header">
            <h3 class="split-block__title">拆分结果预览</h3>
            <span class="split-block__hint">预览仅展示前 200 条，完整结果在创建版本时由后端生成</span>
          </header>
          <DataTable
            :data="result.preview.slice(0, 200)"
            row-key="sampleId"
            size="small"
            max-height="360"
            empty-title="没有可拆分样本"
            empty-description="请先完成标注并运行质检，使样本满足训练资格。"
          >
            <el-table-column prop="sampleName" label="样本" min-width="170" />
            <el-table-column label="来源" min-width="180">
              <template #default="{ row }">{{ row.sourceName || '来源缺失' }}</template>
            </el-table-column>
            <el-table-column label="数据集" width="110">
              <template #default="{ row }">
                <el-tag :type="splitTagType(row.split)" size="small">{{ splitLabel(row.split) }}</el-tag>
              </template>
            </el-table-column>
          </DataTable>
        </section>

        <section class="split-block">
          <header class="split-block__header">
            <h3 class="split-block__title">被排除样本（{{ result.excluded.length }}）</h3>
            <span class="split-block__hint">未满足训练资格要求的样本不会进入任何拆分</span>
          </header>
          <DataTable
            :data="result.excluded"
            row-key="sampleId"
            size="small"
            max-height="280"
            empty-title="没有被排除的样本"
            empty-description="所有样本均通过前端训练资格校验。"
          >
            <el-table-column prop="sampleName" label="样本" min-width="180" />
            <el-table-column label="阻止原因" min-width="320">
              <template #default="{ row }">
                <span class="split-reason">{{ row.reasons.join('；') }}</span>
              </template>
            </el-table-column>
          </DataTable>
        </section>

        <div class="split-actions">
          <el-button @click="goAnnotation">返回标注工作台</el-button>
          <el-button type="primary" :disabled="hasZeroRatio || result.eligibleCount < 3" @click="openVersionDialog">
            以当前拆分创建版本
          </el-button>
        </div>
        <p v-if="hasZeroRatio" class="split-block-hint">
          训练集、验证集、测试集比例必须均大于 0%，当前存在 0% 比例，无法创建正式版本（不会自动改为非零比例）。
        </p>
        <p v-else-if="result.eligibleCount < 3" class="split-block-hint">
          {{
            result.eligibleCount === 0
              ? '当前没有满足训练资格的样本，无法创建正式版本；请先完成标注并运行质检。'
              : `当前仅有 ${result.eligibleCount} 条合格样本，不足以拆分为训练/验证/测试三个非空集合，无法创建正式版本。`
          }}
        </p>
      </template>

      <el-result
        v-else-if="!previewLoading"
        icon="info"
        title="尚未生成拆分预览"
        sub-title="请设置拆分比例、随机种子与策略后点击“生成拆分预览”。只有满足训练资格的样本才会参与拆分。"
      >
        <template #extra>
          <el-button type="primary" :disabled="!ratioValid" @click="generatePreview">生成拆分预览</el-button>
        </template>
      </el-result>

      <el-alert
        v-if="createdVersion"
        type="success"
        :closable="false"
        show-icon
        :title="`已创建版本 ${createdVersion.versionNumber}（草稿）`"
        description="版本创建后处于草稿状态，可在版本管理中提交审核或发布；已发布版本不允许覆盖修改。"
      />
    </template>

    <el-dialog v-model="versionVisible" title="以当前拆分创建版本" width="500px" append-to-body>
      <el-form label-position="top">
        <el-form-item label="版本说明">
          <el-input v-model="versionDescription" type="textarea" :rows="2" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="变更说明">
          <el-input v-model="versionChangeLog" type="textarea" :rows="2" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="拆分策略">{{ strategyLabel(config.strategy) }}</el-descriptions-item>
        <el-descriptions-item label="比例">
          {{ config.trainRatio }} / {{ config.validationRatio }} / {{ config.testRatio }}
        </el-descriptions-item>
        <el-descriptions-item label="随机种子">{{ config.seed }}</el-descriptions-item>
        <el-descriptions-item label="参与样本">{{ result?.eligibleCount ?? 0 }} 条</el-descriptions-item>
      </el-descriptions>
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="版本仅创建在本地 Mock 数据中，不代表已生成真实训练数据包。"
      />
      <template #footer>
        <el-button :disabled="versionLoading" @click="versionVisible = false">取消</el-button>
        <el-button type="primary" :loading="versionLoading" @click="submitVersion">创建版本</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.split-page {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-4);
}

.split-config {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-4);
  padding: var(--ss-space-5);
  border: 1px solid var(--ss-border);
  border-radius: var(--ss-radius-lg);
  background: var(--ss-bg-card);
  box-shadow: var(--ss-shadow-sm);
}

.split-config__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.split-config__title {
  font-size: var(--ss-font-size-body-lg);
  font-weight: var(--ss-font-weight-semibold);
}

.split-config__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: var(--ss-space-3) var(--ss-space-4);
}

.split-config__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ss-space-3);
  padding-top: var(--ss-space-4);
  border-top: 1px solid var(--ss-divider);
}

.split-config__sum {
  font-size: var(--ss-font-size-sm);
}

.split-config__sum.is-valid {
  color: var(--ss-safe);
}

.split-config__sum.is-invalid {
  color: var(--ss-danger);
}

.split-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--ss-space-3);
}

.split-warnings {
  margin: 0;
  padding: var(--ss-space-3) var(--ss-space-3) var(--ss-space-3) var(--ss-space-6);
  border: 1px solid var(--ss-warning);
  border-radius: var(--ss-radius-md);
  background: var(--ss-warning-bg);
  color: var(--ss-text-secondary);
  font-size: var(--ss-font-size-sm);
  line-height: 1.8;
}

.split-block {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-3);
  padding: var(--ss-space-5);
  border: 1px solid var(--ss-border);
  border-radius: var(--ss-radius-lg);
  background: var(--ss-bg-card);
  box-shadow: var(--ss-shadow-sm);
}

.split-block__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ss-space-3);
}

.split-block__title {
  font-size: var(--ss-font-size-body);
  font-weight: var(--ss-font-weight-semibold);
}

.split-block__hint {
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
}

.split-reason {
  color: var(--ss-danger);
  font-size: var(--ss-font-size-xs);
}

.split-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--ss-space-2);
}

.split-block-hint {
  margin-top: var(--ss-space-2);
  color: var(--ss-danger);
  font-size: var(--ss-font-size-sm);
  text-align: right;
}
</style>
