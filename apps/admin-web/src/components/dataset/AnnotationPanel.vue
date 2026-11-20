<script setup lang="ts">
import { computed, reactive, watch } from 'vue'

import { StatusTag } from '@/components'
import RiskLabelPicker from './RiskLabelPicker.vue'
import EvidenceEditor from './EvidenceEditor.vue'
import type {
  AnnotationDraft,
  AnnotationStatus,
  DatasetSample,
  DatasetType,
  EvidenceLocation,
  RiskLabel,
} from '@/types/dataset'

/**
 * 标注面板（P10）。
 *
 * 面板本身不发起请求：编辑结果通过 save 事件交给页面，
 * 由页面调用 API Service 写入 Mock Repository，保证重新进入后可读取。
 */
interface Props {
  sample: DatasetSample
  labels: RiskLabel[]
  saving?: boolean
}

const props = withDefaults(defineProps<Props>(), { saving: false })

const emit = defineEmits<{
  (e: 'save', draft: AnnotationDraft): void
  (e: 'submit', draft: AnnotationDraft): void
  (e: 'review', result: 'approved' | 'needs_fix' | 'rejected'): void
}>()

const STATUS_OPTIONS: Array<{ value: AnnotationStatus; label: string }> = [
  { value: 'in_progress', label: '标注中' },
  { value: 'submitted', label: '已提交' },
  { value: 'completed', label: '已完成' },
]

const MODALITY_LABELS: Record<DatasetType, string> = {
  screen: '屏幕对话',
  video: '视频',
  audio: '音频',
  text: '文本',
  multimodal: '多模态混合',
}

const SOURCE_LABELS: Record<DatasetSample['sourceType'], string> = {
  team_record: '团队自建',
  partner_authorized: '合作授权',
  synthetic: '合成数据',
  public_licensed: '公开许可',
}

const draft = reactive<AnnotationDraft>({
  riskCategories: [],
  evidenceLocations: [],
  annotationNotes: '',
  annotationStatus: 'in_progress',
})

function cloneEvidence(items: EvidenceLocation[]): EvidenceLocation[] {
  return items.map((item) => ({ ...item }))
}

function loadFromSample(sample: DatasetSample) {
  draft.riskCategories = [...sample.riskCategories]
  draft.evidenceLocations = cloneEvidence(sample.evidenceLocations)
  draft.annotationNotes = sample.annotationNotes ?? ''
  draft.annotationStatus =
    sample.annotationStatus === 'unannotated'
      ? 'in_progress'
      : sample.annotationStatus === 'reviewing'
        ? 'submitted'
        : sample.annotationStatus
}

watch(() => props.sample, loadFromSample, { immediate: true })

const modalityLabel = computed(() => MODALITY_LABELS[props.sample.modality])
const sourceLabel = computed(() => SOURCE_LABELS[props.sample.sourceType])

const hasLabelConflict = computed(
  () => draft.riskCategories.includes('正常内容') && draft.riskCategories.length > 1,
)

const hints = computed(() => {
  const list: string[] = []
  if (!props.sample.formatValid) list.push('文件格式异常，修正前不能进入训练集。')
  if (props.sample.licenseStatus === 'missing') list.push('使用许可缺失，该样本不得进入正式训练数据集。')
  else if (!props.sample.sourceLicense.allowTraining) list.push('当前许可未允许用于模型训练。')
  if (!props.sample.sourceName.trim()) list.push('来源信息缺失，请先在导入或来源登记中补充。')
  if (draft.riskCategories.length === 0) list.push('尚未选择风险类别，可先保存为标注中。')
  if (draft.evidenceLocations.length === 0) list.push('尚未录入证据位置。')
  if (hasLabelConflict.value) list.push('“正常内容”不能与其他风险标签同时选择。')
  return list
})

const canSubmit = computed(
  () =>
    !props.saving &&
    draft.riskCategories.length > 0 &&
    draft.evidenceLocations.length > 0 &&
    !hasLabelConflict.value,
)

function buildDraft(status: AnnotationStatus): AnnotationDraft {
  return {
    riskCategories: [...draft.riskCategories],
    evidenceLocations: cloneEvidence(draft.evidenceLocations),
    annotationNotes: draft.annotationNotes,
    annotationStatus: status,
  }
}

function handleSave() {
  emit('save', buildDraft(draft.annotationStatus))
}

function handleSubmit() {
  draft.annotationStatus = 'submitted'
  emit('submit', buildDraft('submitted'))
}
</script>

<template>
  <section class="annotation-panel">
    <header class="annotation-panel__header">
      <div>
        <h3 class="annotation-panel__title">{{ props.sample.sampleName }}</h3>
        <p class="annotation-panel__subtitle">
          {{ props.sample.sampleId }} · {{ modalityLabel }} · {{ sourceLabel }}
        </p>
      </div>
      <div class="annotation-panel__tags">
        <StatusTag :status="props.sample.annotationStatus" />
        <StatusTag :status="props.sample.reviewStatus" />
        <StatusTag :status="props.sample.licenseStatus" />
      </div>
    </header>

    <el-descriptions :column="2" border size="small">
      <el-descriptions-item label="来源名称">
        {{ props.sample.sourceName || '缺失' }}
      </el-descriptions-item>
      <el-descriptions-item label="获取时间">{{ props.sample.acquiredAt }}</el-descriptions-item>
      <el-descriptions-item label="来源说明">
        {{ props.sample.sourceNote || '未填写' }}
      </el-descriptions-item>
      <el-descriptions-item label="来源证明">
        {{ props.sample.sourceProof || '未填写' }}
      </el-descriptions-item>
      <el-descriptions-item label="许可类型">
        {{ props.sample.sourceLicense.licenseType || '缺失' }}
      </el-descriptions-item>
      <el-descriptions-item label="允许训练">
        <StatusTag
          :status="props.sample.sourceLicense.allowTraining ? 'approved' : 'blocked'"
          :label="props.sample.sourceLicense.allowTraining ? '允许' : '不允许'"
        />
      </el-descriptions-item>
      <el-descriptions-item label="重复状态">
        <StatusTag :status="props.sample.duplicateStatus" />
      </el-descriptions-item>
      <el-descriptions-item label="质检结果">
        <StatusTag :status="props.sample.qualityResult" />
      </el-descriptions-item>
    </el-descriptions>

    <el-alert
      v-if="hints.length"
      type="warning"
      :closable="false"
      show-icon
      title="当前样本存在以下待完善项"
    >
      <ul class="annotation-panel__hints">
        <li v-for="hint in hints" :key="hint">{{ hint }}</li>
      </ul>
    </el-alert>

    <section class="annotation-panel__block">
      <h4 class="annotation-panel__block-title">
        风险类别（多标签）
        <span class="annotation-panel__block-hint">可同时选择多个标签</span>
      </h4>
      <RiskLabelPicker v-model="draft.riskCategories" :labels="props.labels" :disabled="props.saving" />
    </section>

    <section class="annotation-panel__block">
      <h4 class="annotation-panel__block-title">证据位置</h4>
      <EvidenceEditor
        v-model="draft.evidenceLocations"
        :disabled="props.saving || props.sample.duplicateStatus === 'excluded'"
      />
    </section>

    <section class="annotation-panel__block">
      <h4 class="annotation-panel__block-title">标注说明</h4>
      <el-input
        v-model="draft.annotationNotes"
        type="textarea"
        :rows="3"
        maxlength="300"
        show-word-limit
        placeholder="记录判断依据、话术特征或需要复核的疑点"
        :disabled="props.saving"
      />
    </section>

    <footer class="annotation-panel__footer">
      <div class="annotation-panel__status">
        <span class="annotation-panel__status-label">标注状态</span>
        <el-select v-model="draft.annotationStatus" size="small" :disabled="props.saving">
          <el-option
            v-for="option in STATUS_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </div>
      <div class="annotation-panel__actions">
        <el-button :loading="props.saving" @click="handleSave">保存标注</el-button>
        <el-button type="primary" :disabled="!canSubmit" :loading="props.saving" @click="handleSubmit">
          提交复核
        </el-button>
      </div>
    </footer>

    <section class="annotation-panel__review">
      <span class="annotation-panel__review-label">演示复核</span>
      <el-button size="small" type="success" plain @click="emit('review', 'approved')">
        标记通过
      </el-button>
      <el-button size="small" type="warning" plain @click="emit('review', 'needs_fix')">
        退回修正
      </el-button>
      <el-button size="small" type="danger" plain @click="emit('review', 'rejected')">
        驳回
      </el-button>
      <span class="annotation-panel__review-note">复核结果同样只写入本地 Mock 数据。</span>
    </section>
  </section>
</template>

<style scoped>
.annotation-panel {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-4);
  padding: var(--ss-space-5);
  border: 1px solid var(--ss-border);
  border-radius: var(--ss-radius-lg);
  background: var(--ss-bg-card);
  box-shadow: var(--ss-shadow-sm);
}

.annotation-panel__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--ss-space-3);
}

.annotation-panel__title {
  font-size: var(--ss-font-size-body-lg);
  font-weight: var(--ss-font-weight-semibold);
}

.annotation-panel__subtitle {
  margin-top: var(--ss-space-1);
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
}

.annotation-panel__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ss-space-1);
  justify-content: flex-end;
}

.annotation-panel__hints {
  margin: var(--ss-space-1) 0 0;
  padding-left: var(--ss-space-4);
  font-size: var(--ss-font-size-xs);
  line-height: 1.8;
}

.annotation-panel__block {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-3);
  padding-top: var(--ss-space-4);
  border-top: 1px solid var(--ss-divider);
}

.annotation-panel__block-title {
  display: flex;
  align-items: baseline;
  gap: var(--ss-space-2);
  color: var(--ss-text-primary);
  font-size: var(--ss-font-size-sm);
  font-weight: var(--ss-font-weight-semibold);
}

.annotation-panel__block-hint {
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
  font-weight: normal;
}

.annotation-panel__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ss-space-3);
  padding-top: var(--ss-space-4);
  border-top: 1px solid var(--ss-divider);
  flex-wrap: wrap;
}

.annotation-panel__status {
  display: flex;
  align-items: center;
  gap: var(--ss-space-2);
}

.annotation-panel__status-label {
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
}

.annotation-panel__actions {
  display: flex;
  gap: var(--ss-space-2);
}

.annotation-panel__review {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--ss-space-2);
  padding: var(--ss-space-3);
  border: 1px dashed var(--ss-border-strong);
  border-radius: var(--ss-radius-md);
  background: var(--ss-bg-subtle);
}

.annotation-panel__review-label {
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
}

.annotation-panel__review-note {
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
}
</style>
