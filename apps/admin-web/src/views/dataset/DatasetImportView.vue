<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ApiError } from '@/api/http'
import {
  confirmDatasetImport,
  createDataset,
  fetchDatasets,
  fetchImportDemoRows,
  fetchImportPreview,
} from '@/api/dataset'
import { DataTable, PageHeader, StatCard, StatusTag } from '@/components'
import type {
  DatasetSummary,
  DatasetType,
  ImportLicenseDraft,
  ImportResult,
  ImportRow,
  ImportSourceDraft,
  ImportSummary,
  SourceType,
} from '@/types/dataset'

/**
 * A04 数据导入向导（P10）。
 *
 * 五步：选择数据集 → 导入数据 → 来源登记 → 许可登记 → 导入确认。
 * 文件选择只在浏览器本地读取元信息与内容做格式校验，不上传服务器；
 * 演示数据包与导入结果均写入本地 Mock Repository。
 */

const route = useRoute()
const router = useRouter()

const TYPE_LABELS: Record<DatasetType, string> = {
  screen: '屏幕对话',
  video: '视频',
  audio: '音频',
  text: '文本',
  multimodal: '多模态混合',
}

const SOURCE_LABELS: Record<SourceType, string> = {
  team_record: '团队自建素材',
  partner_authorized: '合作机构授权',
  synthetic: '合成数据',
  public_licensed: '公开许可素材',
}

const steps = ['选择数据集', '导入数据', '来源登记', '许可登记', '导入确认']
const step = ref(0)

function resolveMessage(cause: unknown, fallback: string): string {
  return cause instanceof ApiError ? cause.message : fallback
}

function today(): string {
  const now = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}

/* ===== 第一步：数据集 ===== */

const datasets = ref<DatasetSummary[]>([])
const datasetsLoading = ref(false)
const selectedDatasetId = ref('')
const createMode = ref(false)
const createLoading = ref(false)
const newDataset = reactive({
  datasetName: '',
  datasetType: 'text' as DatasetType,
  description: '',
})

const selectedDataset = computed(
  () => datasets.value.find((item) => item.datasetId === selectedDatasetId.value) ?? null,
)

async function loadDatasets() {
  datasetsLoading.value = true
  try {
    const page = await fetchDatasets({
      keyword: '',
      datasetType: '',
      status: '',
      createdFrom: '',
      createdTo: '',
      annotationProgress: '',
      page: 1,
      pageSize: 100,
    })
    datasets.value = page.items
    const preset = typeof route.query.datasetId === 'string' ? route.query.datasetId : ''
    if (preset && datasets.value.some((item) => item.datasetId === preset)) {
      selectedDatasetId.value = preset
    } else if (!selectedDatasetId.value && datasets.value.length > 0) {
      selectedDatasetId.value = datasets.value[0].datasetId
    }
  } catch (cause) {
    ElMessage.error(resolveMessage(cause, '数据集列表加载失败'))
  } finally {
    datasetsLoading.value = false
  }
}

async function submitCreateDataset() {
  if (!newDataset.datasetName.trim()) {
    ElMessage.warning('请填写新数据集名称')
    return
  }
  createLoading.value = true
  try {
    const created = await createDataset({
      datasetName: newDataset.datasetName,
      datasetType: newDataset.datasetType,
      description: newDataset.description,
    })
    datasets.value = [created, ...datasets.value]
    selectedDatasetId.value = created.datasetId
    createMode.value = false
    ElMessage.success(`已创建并选中数据集「${created.datasetName}」（本地 Mock）`)
  } catch (cause) {
    ElMessage.error(resolveMessage(cause, '创建数据集失败'))
  } finally {
    createLoading.value = false
  }
}

/* ===== 第二步：导入数据 ===== */

const rows = ref<ImportRow[]>([])
const fileInput = ref<HTMLInputElement | null>(null)
const reading = ref(false)
const rowSource = ref<'local_file' | 'demo_pack' | ''>('')

const allowedExtensions = ['.json', '.csv', '.txt']

function buildRow(
  fileName: string,
  fileHash: string,
  formatValid: boolean,
  formatIssue: string,
  datasetType: DatasetType,
): ImportRow {
  return {
    rowId: `ROW-LOCAL-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`,
    fileName,
    sampleName: fileName,
    modality: datasetType,
    formatValid,
    formatIssue,
    sourceType: 'team_record',
    sourceName: '',
    sourceNote: '',
    acquiredAt: today(),
    sourceProof: '',
    licenseType: '',
    licenseScope: '',
    allowTraining: false,
    allowModify: false,
    validUntil: '',
    licenseProof: '',
    riskCategory: '信息不足',
    evidenceText: '',
    fileHash,
  }
}

async function handleFiles(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  if (files.length === 0) return
  if (!selectedDataset.value) {
    ElMessage.warning('请先选择数据集')
    return
  }
  reading.value = true
  rowSource.value = 'local_file'
  const datasetType = selectedDataset.value.datasetType
  const built: ImportRow[] = []

  for (const file of files) {
    const lower = file.name.toLowerCase()
    const ext = allowedExtensions.find((item) => lower.endsWith(item))
    const hash = `local-${file.size}-${file.name}`
    if (!ext) {
      built.push(buildRow(file.name, hash, false, '不支持的文件类型，仅支持 JSON / CSV / TXT', datasetType))
      continue
    }
    if (file.size > 5 * 1024 * 1024) {
      built.push(buildRow(file.name, hash, false, '文件超过 5MB，演示环境不接受大文件', datasetType))
      continue
    }
    try {
      const text = await file.text()
      if (!text.trim()) {
        built.push(buildRow(file.name, hash, false, '文件内容为空', datasetType))
        continue
      }
      let issue = ''
      if (ext === '.json') {
        try {
          JSON.parse(text)
        } catch {
          issue = 'JSON 结构无法解析'
        }
      } else if (ext === '.csv') {
        const lines = text.split(/\r?\n/).filter((line) => line.trim().length > 0)
        if (lines.length < 2) issue = 'CSV 至少需要表头与一行数据'
        else if (!lines[0].includes(',')) issue = 'CSV 表头缺少分隔符'
      }
      built.push(buildRow(file.name, hash, issue === '', issue, datasetType))
    } catch {
      built.push(buildRow(file.name, hash, false, '文件读取失败', datasetType))
    }
  }

  rows.value = built
  reading.value = false
  input.value = ''
  ElMessage.success(`已在本地读取 ${built.length} 个文件，未上传到服务器`)
}

function triggerFilePicker() {
  fileInput.value?.click()
}

async function loadDemoPack(mode: 'normal' | 'format-error' | 'duplicate') {
  if (!selectedDataset.value) {
    ElMessage.warning('请先选择数据集')
    return
  }
  try {
    rows.value = await fetchImportDemoRows(mode)
    rowSource.value = 'demo_pack'
    ElMessage.success('已载入演示导入包（本地 Mock 数据）')
  } catch (cause) {
    ElMessage.error(resolveMessage(cause, '演示导入包加载失败'))
  }
}

function clearRows() {
  rows.value = []
  rowSource.value = ''
}

/* ===== 第三步：来源登记 ===== */

const source = reactive<ImportSourceDraft>({
  sourceType: 'team_record',
  sourceName: '团队自建反诈演示素材',
  sourceNote: '演示导入批次，素材由团队自行制作或已获授权',
  acquiredAt: today(),
  sourceProof: 'SRC-DEMO-2026',
})

function clearSource() {
  source.sourceName = ''
  source.sourceNote = ''
  source.sourceProof = ''
  ElMessage.warning('已清空来源信息，用于验证“来源缺失”校验')
}

const sourceMissing = computed(() => !source.sourceName.trim() || !source.acquiredAt)

/* ===== 第四步：许可登记 ===== */

const license = reactive<ImportLicenseDraft>({
  licenseType: '团队自建样本授权',
  licenseScope: '仅用于本项目反诈模型训练与评测',
  allowTraining: true,
  allowModify: true,
  validUntil: '2027-12-31',
  licenseProof: 'LIC-DEMO-2026',
  reviewStatus: 'verified',
})

function clearLicense() {
  license.licenseType = ''
  license.licenseScope = ''
  license.licenseProof = ''
  license.allowTraining = false
  license.reviewStatus = 'missing'
  ElMessage.warning('已清空许可信息，用于验证“许可缺失”校验')
}

const licenseMissing = computed(
  () => !license.licenseType.trim() || !license.licenseScope.trim() || !license.allowTraining,
)

/* ===== 第五步：导入确认 ===== */

const preview = ref<ImportSummary | null>(null)
const previewLoading = ref(false)
const previewError = ref('')
const selectedRowIds = ref<string[]>([])
const importing = ref(false)
const importResult = ref<ImportResult | null>(null)

async function loadPreview() {
  if (!selectedDataset.value) return
  previewLoading.value = true
  previewError.value = ''
  try {
    const summary = await fetchImportPreview(
      selectedDataset.value.datasetId,
      rows.value,
      source,
      license,
    )
    preview.value = summary
    selectedRowIds.value = summary.candidates
      .filter((item) => item.importable)
      .map((item) => item.rowId)
  } catch (cause) {
    preview.value = null
    previewError.value = resolveMessage(cause, '导入预览生成失败')
  } finally {
    previewLoading.value = false
  }
}

function toggleRow(rowId: string, checked: boolean) {
  if (checked) {
    if (!selectedRowIds.value.includes(rowId)) selectedRowIds.value = [...selectedRowIds.value, rowId]
  } else {
    selectedRowIds.value = selectedRowIds.value.filter((item) => item !== rowId)
  }
}

async function confirmImport() {
  if (!selectedDataset.value || !preview.value) return
  if (selectedRowIds.value.length === 0) {
    ElMessage.warning('请至少选择一个可导入样本')
    return
  }
  importing.value = true
  try {
    importResult.value = await confirmDatasetImport(
      selectedDataset.value.datasetId,
      rows.value,
      source,
      license,
      selectedRowIds.value,
    )
    ElMessage.success(
      `已导入 ${importResult.value.importedCount} 条样本到本地 Mock 数据集`,
    )
  } catch (cause) {
    ElMessage.error(resolveMessage(cause, '导入失败，请稍后重试'))
  } finally {
    importing.value = false
  }
}

/* ===== 步骤控制 ===== */

function next() {
  if (step.value === 0) {
    if (!selectedDataset.value) {
      ElMessage.warning('请先选择或创建数据集')
      return
    }
  }
  if (step.value === 1 && rows.value.length === 0) {
    ElMessage.warning('请选择本地文件或载入演示导入包')
    return
  }
  if (step.value === 2 && sourceMissing.value) {
    ElMessage.warning('来源信息不完整：可以继续，但所有样本都会因来源缺失而不可导入')
  }
  if (step.value === 3 && licenseMissing.value) {
    ElMessage.warning('许可信息不完整：可以继续，但样本不会进入可训练数据')
  }
  step.value = Math.min(step.value + 1, steps.length - 1)
  if (step.value === 4) void loadPreview()
}

function previous() {
  step.value = Math.max(step.value - 1, 0)
}

function goAnnotation() {
  if (!selectedDataset.value) return
  void router.push(`/admin/datasets/${selectedDataset.value.datasetId}/annotation`)
}

function backToList() {
  void router.push('/admin/datasets')
}

onMounted(loadDatasets)
</script>

<template>
  <div class="import-page">
    <PageHeader
      eyebrow="A04 · 数据集管理"
      title="数据导入向导"
      description="导入流程包含来源与许可登记。文件仅在浏览器本地读取，不会上传服务器；演示包与导入结果只写入本地 Mock 数据。"
    >
      <template #extra>
        <el-tag type="warning" effect="plain">演示数据（Mock）</el-tag>
        <el-button @click="backToList">返回数据集列表</el-button>
      </template>
    </PageHeader>

    <el-steps :active="step" finish-status="success" align-center class="import-steps">
      <el-step v-for="(title, index) in steps" :key="title" :title="title" :description="`第 ${index + 1} 步`" />
    </el-steps>

    <section v-if="step === 0" class="import-card">
      <h3 class="import-card__title">选择目标数据集</h3>
      <p class="import-card__desc">导入的样本会追加到所选数据集，不会修改其他数据集。</p>

      <div class="import-mode">
        <el-radio-group v-model="createMode">
          <el-radio-button :value="false">选择已有数据集</el-radio-button>
          <el-radio-button :value="true">创建新数据集</el-radio-button>
        </el-radio-group>
      </div>

      <template v-if="!createMode">
        <el-select
          v-model="selectedDatasetId"
          placeholder="请选择数据集"
          filterable
          :loading="datasetsLoading"
          class="import-select"
        >
          <el-option
            v-for="item in datasets"
            :key="item.datasetId"
            :label="`${item.datasetName}（${item.datasetId} · ${TYPE_LABELS[item.datasetType]} · ${item.totalSamples} 条）`"
            :value="item.datasetId"
          />
        </el-select>
        <p v-if="selectedDataset" class="import-card__hint">
          当前选择：{{ selectedDataset.datasetName }} · 状态
          {{ selectedDataset.datasetStatus }} · 当前版本 {{ selectedDataset.datasetVersion }}
        </p>
      </template>

      <template v-else>
        <el-form label-position="top" class="import-form">
          <el-form-item label="数据集名称" required>
            <el-input v-model="newDataset.datasetName" placeholder="例如：短视频反诈样本集" maxlength="40" show-word-limit />
          </el-form-item>
          <el-form-item label="数据集类型">
            <el-select v-model="newDataset.datasetType">
              <el-option
                v-for="(label, value) in TYPE_LABELS"
                :key="value"
                :label="label"
                :value="value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="数据集描述">
            <el-input v-model="newDataset.description" type="textarea" :rows="2" maxlength="200" show-word-limit />
          </el-form-item>
          <el-button type="primary" plain :loading="createLoading" @click="submitCreateDataset">
            创建并选中
          </el-button>
        </el-form>
      </template>
    </section>

    <section v-if="step === 1" class="import-card">
      <h3 class="import-card__title">导入数据</h3>
      <p class="import-card__desc">
        支持 JSON / CSV / TXT。浏览器只读取文件名、大小与内容结构用于格式校验，不会上传文件；
        演示环境不包含任何私人录屏或个人隐私内容。
      </p>

      <div class="import-actions">
        <input
          ref="fileInput"
          type="file"
          multiple
          accept=".json,.csv,.txt"
          class="import-file-input"
          @change="handleFiles"
        />
        <el-button type="primary" :loading="reading" @click="triggerFilePicker">选择本地文件</el-button>
        <el-button @click="loadDemoPack('normal')">载入正常演示包（8 条）</el-button>
        <el-button @click="loadDemoPack('format-error')">载入格式错误演示包</el-button>
        <el-button @click="loadDemoPack('duplicate')">载入重复样本演示包</el-button>
        <el-button v-if="rows.length" type="danger" plain @click="clearRows">清空</el-button>
      </div>

      <el-alert
        v-if="rowSource === 'local_file'"
        type="info"
        :closable="false"
        show-icon
        title="本地文件已解析：未上传服务器，文件哈希为模拟值。"
      />

      <DataTable
        v-if="rows.length"
        :data="rows"
        row-key="rowId"
        empty-title="尚未选择文件"
        empty-description="可点击“选择本地文件”，或载入演示导入包。"
      >
        <el-table-column prop="fileName" label="文件" min-width="200" />
        <el-table-column label="格式校验" width="160">
          <template #default="{ row }">
            <StatusTag :status="row.formatValid ? 'passed' : 'failed'" :label="row.formatValid ? '格式正确' : '格式错误'" />
          </template>
        </el-table-column>
        <el-table-column prop="formatIssue" label="格式问题" min-width="200">
          <template #default="{ row }">{{ row.formatIssue || '—' }}</template>
        </el-table-column>
        <el-table-column prop="fileHash" label="模拟哈希" min-width="160" />
      </DataTable>
      <p v-else class="import-empty">尚未选择文件或载入演示数据包。</p>
    </section>

    <section v-if="step === 2" class="import-card">
      <h3 class="import-card__title">来源登记</h3>
      <p class="import-card__desc">来源登记会应用到本批次所有样本。没有明确来源的样本不得进入正式训练数据集。</p>

      <el-form label-position="top" class="import-form">
        <div class="import-form__grid">
          <el-form-item label="数据来源类型" required>
            <el-select v-model="source.sourceType">
              <el-option
                v-for="(label, value) in SOURCE_LABELS"
                :key="value"
                :label="label"
                :value="value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="来源名称" required>
            <el-input v-model="source.sourceName" placeholder="例如：团队自建反诈演示素材" />
          </el-form-item>
          <el-form-item label="获取时间" required>
            <el-date-picker v-model="source.acquiredAt" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" />
          </el-form-item>
          <el-form-item label="来源证明 / 参考信息">
            <el-input v-model="source.sourceProof" placeholder="登记编号或说明" />
          </el-form-item>
        </div>
        <el-form-item label="来源说明">
          <el-input v-model="source.sourceNote" type="textarea" :rows="2" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>

      <el-alert
        v-if="sourceMissing"
        type="warning"
        :closable="false"
        show-icon
        title="来源信息不完整，本批次样本将因来源缺失而不可导入。"
      />
      <div class="import-actions">
        <el-button type="danger" plain @click="clearSource">清空来源信息（演示缺失场景）</el-button>
      </div>
    </section>

    <section v-if="step === 3" class="import-card">
      <h3 class="import-card__title">使用许可登记</h3>
      <p class="import-card__desc">
        没有明确许可、或许可不允许训练的样本，不能进入正式训练数据集；本轮仅前端规则校验。
      </p>

      <el-form label-position="top" class="import-form">
        <div class="import-form__grid">
          <el-form-item label="许可类型" required>
            <el-input v-model="license.licenseType" placeholder="例如：团队自建样本授权" />
          </el-form-item>
          <el-form-item label="许可范围" required>
            <el-input v-model="license.licenseScope" placeholder="例如：仅用于本项目模型训练与评测" />
          </el-form-item>
          <el-form-item label="许可有效期">
            <el-date-picker v-model="license.validUntil" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" />
          </el-form-item>
          <el-form-item label="许可证明材料">
            <el-input v-model="license.licenseProof" placeholder="许可证明编号" />
          </el-form-item>
          <el-form-item label="是否允许用于模型训练">
            <el-switch v-model="license.allowTraining" active-text="允许" inactive-text="不允许" />
          </el-form-item>
          <el-form-item label="是否允许修改或标注">
            <el-switch v-model="license.allowModify" active-text="允许" inactive-text="不允许" />
          </el-form-item>
          <el-form-item label="审核状态">
            <el-select v-model="license.reviewStatus">
              <el-option label="已核验" value="verified" />
              <el-option label="待核验" value="pending" />
              <el-option label="缺失" value="missing" />
            </el-select>
          </el-form-item>
        </div>
      </el-form>

      <el-alert
        v-if="licenseMissing"
        type="error"
        :closable="false"
        show-icon
        title="许可信息不完整或未允许训练，本批次样本不会进入可训练数据。"
      />
      <div class="import-actions">
        <el-button type="danger" plain @click="clearLicense">清空许可信息（演示缺失场景）</el-button>
      </div>
    </section>

    <section v-if="step === 4" class="import-card">
      <h3 class="import-card__title">导入确认</h3>
      <p class="import-card__desc">
        目标数据集：{{ selectedDataset?.datasetName ?? '未选择' }}。仅有满足格式、来源与许可要求的样本可以导入。
      </p>

      <div v-if="importResult" class="import-result">
        <el-result
          icon="success"
          title="导入完成（本地 Mock）"
          :sub-title="`成功导入 ${importResult.importedCount} 条，跳过 ${importResult.skippedCount} 条。数据仅保存在本次浏览器会话内存中。`"
        >
          <template #extra>
            <el-button @click="backToList">返回数据集列表</el-button>
            <el-button type="primary" @click="goAnnotation">进入标注工作台</el-button>
          </template>
        </el-result>
      </div>

      <template v-else>
        <div class="import-summary">
          <StatCard label="待导入总数" :value="preview?.total ?? 0" hint="本次提交的文件数量" />
          <StatCard label="格式错误" :value="preview?.formatErrorCount ?? 0" hint="结构无法解析" tone="danger" />
          <StatCard label="来源缺失" :value="preview?.missingSourceCount ?? 0" hint="来源名称或获取时间缺失" tone="warning" />
          <StatCard label="许可缺失" :value="preview?.missingLicenseCount ?? 0" hint="未登记许可或不允许训练" tone="warning" />
          <StatCard label="重复样本" :value="preview?.duplicateCount ?? 0" hint="模拟哈希重复" tone="warning" />
          <StatCard label="可导入" :value="preview?.importableCount ?? 0" hint="通过全部必要条件" tone="safe" />
        </div>

        <el-alert
          v-if="previewError"
          type="error"
          :closable="false"
          show-icon
          :title="previewError"
        />

        <div v-if="previewLoading" class="import-empty">正在生成导入预览…</div>

        <DataTable
          v-else-if="preview && preview.candidates.length"
          :data="preview.candidates"
          row-key="rowId"
        >
          <el-table-column label="选择" width="70">
            <template #default="{ row }">
              <el-checkbox
                :model-value="selectedRowIds.includes(row.rowId)"
                :disabled="!row.importable"
                @change="(value: string | number | boolean) => toggleRow(row.rowId, Boolean(value))"
              />
            </template>
          </el-table-column>
          <el-table-column prop="fileName" label="文件" min-width="180" />
          <el-table-column label="格式" width="100">
            <template #default="{ row }">
              <StatusTag :status="row.formatValid ? 'passed' : 'failed'" :label="row.formatValid ? '正常' : '错误'" />
            </template>
          </el-table-column>
          <el-table-column label="来源" width="100">
            <template #default="{ row }">
              <StatusTag :status="row.hasSource ? 'verified' : 'missing'" :label="row.hasSource ? '完整' : '缺失'" />
            </template>
          </el-table-column>
          <el-table-column label="许可" width="100">
            <template #default="{ row }">
              <StatusTag :status="row.hasLicense ? 'verified' : 'missing'" :label="row.hasLicense ? '完整' : '缺失'" />
            </template>
          </el-table-column>
          <el-table-column label="重复" width="120">
            <template #default="{ row }">
              <StatusTag :status="row.duplicate ? 'suspected' : 'unique'" :label="row.duplicate ? row.duplicateOf || '重复' : '无重复'" />
            </template>
          </el-table-column>
          <el-table-column label="不可导入原因" min-width="220">
            <template #default="{ row }">
              <span v-if="row.reasons.length === 0" class="import-ok">满足导入条件</span>
              <span v-else class="import-block">{{ row.reasons.join('；') }}</span>
            </template>
          </el-table-column>
        </DataTable>

        <p v-else-if="preview" class="import-empty">没有可预览的样本。</p>

        <el-alert
          type="warning"
          :closable="false"
          show-icon
          title="导入后的样本初始为“未标注”，需要进入标注工作台完成标注并通过质检后才可能具备训练资格。"
        />

        <div class="import-actions">
          <el-button :disabled="previewLoading" @click="loadPreview">重新生成预览</el-button>
          <el-button
            type="primary"
            :loading="importing"
            :disabled="previewLoading || selectedRowIds.length === 0"
            @click="confirmImport"
          >
            确认导入 {{ selectedRowIds.length }} 条
          </el-button>
        </div>
      </template>
    </section>

    <footer class="import-footer">
      <el-button :disabled="step === 0" @click="previous">上一步</el-button>
      <el-button v-if="step < steps.length - 1" type="primary" @click="next">下一步</el-button>
      <span class="import-footer__note">来源、许可、质检与版本规则均为本地演示逻辑，真实训练前仍需后端复核。</span>
    </footer>
  </div>
</template>

<style scoped>
.import-page {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-5);
}

.import-steps {
  padding: var(--ss-space-4) var(--ss-space-5);
  border: 1px solid var(--ss-border);
  border-radius: var(--ss-radius-lg);
  background: var(--ss-bg-card);
}

.import-card {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-4);
  padding: var(--ss-space-5);
  border: 1px solid var(--ss-border);
  border-radius: var(--ss-radius-lg);
  background: var(--ss-bg-card);
  box-shadow: var(--ss-shadow-sm);
}

.import-card__title {
  font-size: var(--ss-font-size-body-lg);
  font-weight: var(--ss-font-weight-semibold);
}

.import-card__desc {
  color: var(--ss-text-secondary);
  font-size: var(--ss-font-size-sm);
  line-height: 1.7;
}

.import-card__hint {
  margin-top: var(--ss-space-2);
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
}

.import-mode {
  display: flex;
  gap: var(--ss-space-3);
}

.import-select {
  width: 100%;
  max-width: 560px;
}

.import-form__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 0 var(--ss-space-4);
}

.import-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ss-space-2);
}

.import-file-input {
  display: none;
}

.import-empty {
  padding: var(--ss-space-6);
  border: 1px dashed var(--ss-border-strong);
  border-radius: var(--ss-radius-md);
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-sm);
  text-align: center;
}

.import-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: var(--ss-space-3);
}

.import-ok {
  color: var(--ss-safe);
}

.import-block {
  color: var(--ss-danger);
}

.import-result {
  padding: var(--ss-space-3);
  border: 1px solid var(--ss-border);
  border-radius: var(--ss-radius-md);
  background: var(--ss-bg-subtle);
}

.import-footer {
  display: flex;
  align-items: center;
  gap: var(--ss-space-3);
  padding: var(--ss-space-4) var(--ss-space-5);
  border: 1px solid var(--ss-border);
  border-radius: var(--ss-radius-lg);
  background: var(--ss-bg-card);
}

.import-footer__note {
  margin-left: auto;
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
}
</style>
