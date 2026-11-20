<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { reactive } from 'vue'

import type { EvidenceLocation, EvidenceModality } from '@/types/dataset'

/**
 * 证据位置编辑器（P10）。
 *
 * 支持屏幕文本、视频时间、音频时间与页面区域四类证据：
 * - 所有修改都通过 update:modelValue 回传新数组，不直接改动父级数据；
 * - Mock 阶段只记录证据描述，不包含真实的视频帧或音频波形解析。
 */
interface Props {
  modelValue: EvidenceLocation[]
  disabled?: boolean
}

const props = withDefaults(defineProps<Props>(), { disabled: false })

const emit = defineEmits<{
  (e: 'update:modelValue', value: EvidenceLocation[]): void
}>()

const MODALITY_OPTIONS: Array<{ value: EvidenceModality; label: string }> = [
  { value: 'screen', label: '屏幕文本位置' },
  { value: 'video', label: '视频时间位置' },
  { value: 'audio', label: '音频时间位置' },
  { value: 'region', label: '页面区域' },
]

const MODALITY_LABELS: Record<EvidenceModality, string> = {
  screen: '屏幕文本',
  video: '视频时间',
  audio: '音频时间',
  region: '页面区域',
}

const form = reactive({
  modality: 'screen' as EvidenceModality,
  screenPage: '',
  timeStart: 0,
  timeEnd: 0,
  region: '',
  textSnippet: '',
})

function modalityLabel(modality: string): string {
  return MODALITY_LABELS[modality as EvidenceModality] ?? modality
}

let manualSeq = 0

function updateItem(index: number, patch: Partial<EvidenceLocation>) {
  const next = props.modelValue.map((item, position) =>
    position === index ? { ...item, ...patch } : item,
  )
  emit('update:modelValue', next)
}

function removeItem(index: number) {
  emit(
    'update:modelValue',
    props.modelValue.filter((_, position) => position !== index),
  )
}

function resetForm() {
  form.screenPage = ''
  form.timeStart = 0
  form.timeEnd = 0
  form.region = ''
  form.textSnippet = ''
}

function addItem() {
  const snippet = form.textSnippet.trim()
  if (!snippet) {
    ElMessage.warning('请填写证据文字摘要，便于复核时定位')
    return
  }
  if (form.modality === 'video' || form.modality === 'audio') {
    if (form.timeEnd <= form.timeStart) {
      ElMessage.warning('结束时间必须大于开始时间')
      return
    }
  }
  manualSeq += 1
  const item: EvidenceLocation = {
    evidenceId: `EV-MANUAL-${Date.now()}-${manualSeq}`,
    modality: form.modality,
    textSnippet: snippet,
  }
  if (form.modality === 'screen') {
    item.screenPage = form.screenPage.trim()
    item.region = form.region.trim()
  }
  if (form.modality === 'video' || form.modality === 'audio') {
    item.timeStart = form.timeStart
    item.timeEnd = form.timeEnd
  }
  if (form.modality === 'region') {
    item.region = form.region.trim()
  }
  emit('update:modelValue', [...props.modelValue, item])
  resetForm()
}
</script>

<template>
  <div class="evidence-editor">
    <ul v-if="props.modelValue.length" class="evidence-list">
      <li v-for="(item, index) in props.modelValue" :key="item.evidenceId" class="evidence-item">
        <header class="evidence-item__header">
          <el-tag size="small" effect="plain">{{ modalityLabel(item.modality) }}</el-tag>
          <el-button
            v-if="!props.disabled"
            link
            type="danger"
            size="small"
            @click="removeItem(index)"
          >
            删除
          </el-button>
        </header>

        <div class="evidence-item__fields">
          <el-input
            v-if="item.modality === 'screen'"
            :model-value="item.screenPage ?? ''"
            size="small"
            placeholder="屏幕页面位置，例如：第 2 个对话页面"
            :disabled="props.disabled"
            @update:model-value="(value: string) => updateItem(index, { screenPage: value })"
          />
          <el-input
            v-if="item.modality === 'screen' || item.modality === 'region'"
            :model-value="item.region ?? ''"
            size="small"
            placeholder="页面区域，例如：页面中部气泡区域"
            :disabled="props.disabled"
            @update:model-value="(value: string) => updateItem(index, { region: value })"
          />
          <div v-if="item.modality === 'video' || item.modality === 'audio'" class="evidence-item__time">
            <span class="evidence-item__time-label">时间（秒）</span>
            <el-input-number
              :model-value="item.timeStart ?? 0"
              :min="0"
              :precision="1"
              :controls="false"
              size="small"
              :disabled="props.disabled"
              @update:model-value="(value: number | undefined) => updateItem(index, { timeStart: value ?? 0 })"
            />
            <span class="evidence-item__time-sep">至</span>
            <el-input-number
              :model-value="item.timeEnd ?? 0"
              :min="0"
              :precision="1"
              :controls="false"
              size="small"
              :disabled="props.disabled"
              @update:model-value="(value: number | undefined) => updateItem(index, { timeEnd: value ?? 0 })"
            />
          </div>
          <el-input
            :model-value="item.textSnippet"
            size="small"
            type="textarea"
            :rows="2"
            placeholder="证据文字摘要（必填）"
            :disabled="props.disabled"
            @update:model-value="(value: string) => updateItem(index, { textSnippet: value })"
          />
        </div>
      </li>
    </ul>
    <p v-else class="evidence-empty">尚未记录证据位置，至少补充一条才能通过质检。</p>

    <section v-if="!props.disabled" class="evidence-add">
      <div class="evidence-add__row">
        <el-select v-model="form.modality" size="small" class="evidence-add__modality">
          <el-option
            v-for="option in MODALITY_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-input
          v-if="form.modality === 'screen'"
          v-model="form.screenPage"
          size="small"
          placeholder="屏幕页面位置"
          class="evidence-add__input"
        />
        <el-input
          v-if="form.modality === 'screen' || form.modality === 'region'"
          v-model="form.region"
          size="small"
          placeholder="页面区域"
          class="evidence-add__input"
        />
        <template v-if="form.modality === 'video' || form.modality === 'audio'">
          <el-input-number v-model="form.timeStart" :min="0" :precision="1" :controls="false" size="small" />
          <span class="evidence-add__sep">至</span>
          <el-input-number v-model="form.timeEnd" :min="0" :precision="1" :controls="false" size="small" />
        </template>
      </div>
      <div class="evidence-add__row">
        <el-input
          v-model="form.textSnippet"
          size="small"
          type="textarea"
          :rows="2"
          placeholder="证据文字摘要（必填），例如：对方要求先转账 3000 元再返利"
        />
        <el-button type="primary" plain size="small" @click="addItem">添加证据</el-button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.evidence-editor {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-3);
}

.evidence-list {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.evidence-item {
  padding: var(--ss-space-3);
  border: 1px solid var(--ss-border);
  border-radius: var(--ss-radius-md);
  background: var(--ss-bg-subtle);
}

.evidence-item__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--ss-space-2);
}

.evidence-item__fields {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-2);
}

.evidence-item__time {
  display: flex;
  align-items: center;
  gap: var(--ss-space-2);
}

.evidence-item__time-label,
.evidence-item__time-sep {
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
}

.evidence-empty {
  padding: var(--ss-space-3);
  border: 1px dashed var(--ss-border-strong);
  border-radius: var(--ss-radius-md);
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-sm);
}

.evidence-add {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-2);
  padding-top: var(--ss-space-3);
  border-top: 1px dashed var(--ss-divider);
}

.evidence-add__row {
  display: flex;
  align-items: flex-start;
  gap: var(--ss-space-2);
}

.evidence-add__modality {
  width: 140px;
  flex-shrink: 0;
}

.evidence-add__input {
  flex: 1;
}

.evidence-add__sep {
  padding-top: 5px;
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
}

.evidence-add__row :deep(.el-textarea) {
  flex: 1;
}
</style>
