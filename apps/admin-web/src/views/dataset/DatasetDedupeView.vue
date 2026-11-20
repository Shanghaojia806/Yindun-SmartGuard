<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { ApiError } from '@/api/http'
import { fetchDatasetDetail, fetchDedupeOverview, resolveDedupe } from '@/api/dataset'
import { DataTable, ErrorState, LoadingState, PageHeader, StatCard, StatusTag } from '@/components'
import DatasetWorkspaceHeader from '@/components/dataset/DatasetWorkspaceHeader.vue'
import type { DatasetSummary, DedupeGroup, DedupeOverview } from '@/types/dataset'

/**
 * A04 数据去重（P10）。
 *
 * Mock 环境不计算真实文件内容哈希，仅按演示哈希分组；
 * 页面明确标注“模拟去重”，确认操作只修改本地 Mock 数据并记录操作日志。
 */

const route = useRoute()
const datasetId = computed(() => String(route.params.id ?? ''))

const dataset = ref<DatasetSummary | null>(null)
const datasetLoading = ref(false)
const datasetError = ref('')

const overview = ref<DedupeOverview | null>(null)
const overviewLoading = ref(false)
const overviewError = ref('')
const resolvingId = ref('')

interface GroupSelection {
  keep: string
  exclude: string[]
}

const selections = reactive<Record<string, GroupSelection>>({})

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

function syncSelections(data: DedupeOverview) {
  for (const group of data.groups) {
    if (!selections[group.groupId]) {
      const keep = group.keepSampleId || group.sampleIds[0] || ''
      selections[group.groupId] = {
        keep,
        exclude:
          group.excludedSampleIds.length > 0
            ? [...group.excludedSampleIds]
            : group.sampleIds.filter((item) => item !== keep),
      }
    }
  }
}

async function loadOverview() {
  if (!datasetId.value) return
  overviewLoading.value = true
  overviewError.value = ''
  try {
    overview.value = await fetchDedupeOverview(datasetId.value)
    syncSelections(overview.value)
  } catch (cause) {
    overview.value = null
    overviewError.value = resolveMessage(cause, '去重信息加载失败')
  } finally {
    overviewLoading.value = false
  }
}

async function bootstrap() {
  await loadDataset()
  if (!dataset.value) return
  await loadOverview()
}

onMounted(bootstrap)

watch(datasetId, () => {
  overview.value = null
  Object.keys(selections).forEach((key) => delete selections[key])
  void bootstrap()
})

const pendingGroups = computed(() => overview.value?.groups.filter((item) => item.status === 'pending') ?? [])
const resolvedGroups = computed(() => overview.value?.groups.filter((item) => item.status === 'resolved') ?? [])
const excludedCount = computed(() =>
  (overview.value?.groups ?? []).reduce((sum, group) => sum + group.excludedSampleIds.length, 0),
)

function sampleNameOf(group: DedupeGroup, sampleId: string): string {
  const index = group.sampleIds.indexOf(sampleId)
  return index >= 0 ? group.sampleNames[index] ?? sampleId : sampleId
}

function setKeep(groupId: string, sampleId: string) {
  const selection = selections[groupId]
  if (!selection) return
  selection.keep = sampleId
  selection.exclude = selection.exclude.filter((item) => item !== sampleId)
}

function toggleExclude(groupId: string, sampleId: string, checked: boolean) {
  const selection = selections[groupId]
  if (!selection || sampleId === selection.keep) return
  if (checked && !selection.exclude.includes(sampleId)) {
    selection.exclude = [...selection.exclude, sampleId]
  } else if (!checked) {
    selection.exclude = selection.exclude.filter((item) => item !== sampleId)
  }
}

async function submitGroup(group: DedupeGroup) {
  const selection = selections[group.groupId]
  if (!selection || !selection.keep) {
    ElMessage.warning('请先选择要保留的样本')
    return
  }
  resolvingId.value = group.groupId
  try {
    overview.value = await resolveDedupe(datasetId.value, group.groupId, selection.keep, selection.exclude)
    syncSelections(overview.value)
    ElMessage.success(
      selection.exclude.length > 0
        ? `已保留 1 条并排除 ${selection.exclude.length} 条重复样本（本地 Mock）`
        : '已人工确认保留该组全部样本（本地 Mock）',
    )
    await loadDataset()
  } catch (cause) {
    ElMessage.error(resolveMessage(cause, '去重确认失败，请稍后重试'))
  } finally {
    resolvingId.value = ''
  }
}
</script>

<template>
  <div class="dedupe-page">
    <LoadingState v-if="datasetLoading" text="正在加载去重工作台" min-height="360px" />
    <ErrorState v-else-if="datasetError" :description="datasetError" @retry="loadDataset" />

    <template v-else-if="dataset">
      <DatasetWorkspaceHeader :dataset="dataset" active="dedupe" />

      <PageHeader
        eyebrow="A04 · 数据去重"
        title="重复数据检查"
        description="按模拟文件哈希分组展示疑似重复样本，支持人工确认保留或排除。"
        :divider="false"
      >
        <template #extra>
          <el-tag type="warning" effect="plain">演示数据（Mock）</el-tag>
          <el-button :loading="overviewLoading" @click="loadOverview">刷新分组</el-button>
        </template>
      </PageHeader>

      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="模拟去重"
        :description="overview?.note ?? '模拟去重：未执行真实文件内容哈希计算。'"
      />

      <section class="dedupe-summary">
        <StatCard label="重复分组" :value="overview?.groups.length ?? 0" hint="按相同模拟哈希聚合" />
        <StatCard label="待人工确认" :value="pendingGroups.length" hint="尚未确认保留与排除" tone="warning" />
        <StatCard label="已处理分组" :value="resolvedGroups.length" hint="已记录去重操作" tone="safe" />
        <StatCard label="已排除样本" :value="excludedCount" hint="不会进入训练集" tone="danger" />
      </section>

      <el-alert v-if="overviewError" type="error" :closable="false" show-icon :title="overviewError" />
      <div v-if="overviewLoading" class="dedupe-empty">正在加载重复分组…</div>

      <template v-else-if="overview">
        <p v-if="overview.groups.length === 0" class="dedupe-empty">
          当前没有疑似重复样本。演示数据中的重复组来自固定规则，部分数据集可能不存在重复。
        </p>

        <section v-for="group in overview.groups" :key="group.groupId" class="dedupe-group">
          <header class="dedupe-group__header">
            <div>
              <h3 class="dedupe-group__title">
                {{ group.groupId }}
                <StatusTag
                  :status="group.status === 'resolved' ? 'completed' : 'pending'"
                  :label="group.status === 'resolved' ? '已处理' : '待确认'"
                />
                <el-tag v-if="group.simulated" size="small" type="warning" effect="plain">模拟</el-tag>
              </h3>
              <p class="dedupe-group__meta">
                模拟哈希 {{ group.fileHash }} · {{ group.sampleIds.length }} 条样本 · {{ group.reason }}
              </p>
            </div>
            <div v-if="group.status === 'resolved'" class="dedupe-group__resolved">
              <span>{{ group.resolvedAt || '已记录' }}</span>
              <span>{{ group.resolvedBy || '演示管理员' }}</span>
            </div>
          </header>

          <ul class="dedupe-samples">
            <li v-for="(sampleId, index) in group.sampleIds" :key="sampleId" class="dedupe-sample">
              <div class="dedupe-sample__main">
                <el-radio
                  :model-value="selections[group.groupId]?.keep ?? ''"
                  :value="sampleId"
                  :disabled="group.status === 'resolved'"
                  @change="setKeep(group.groupId, sampleId)"
                >
                  <span class="dedupe-sample__name">{{ group.sampleNames[index] ?? sampleId }}</span>
                </el-radio>
                <span class="dedupe-sample__id">{{ sampleId }}</span>
              </div>
              <div class="dedupe-sample__actions">
                <el-checkbox
                  :model-value="selections[group.groupId]?.exclude.includes(sampleId) ?? false"
                  :disabled="group.status === 'resolved' || selections[group.groupId]?.keep === sampleId"
                  @change="(value: string | number | boolean) => toggleExclude(group.groupId, sampleId, Boolean(value))"
                >
                  排除
                </el-checkbox>
                <el-tag size="small" type="info" effect="plain">疑似重复</el-tag>
              </div>
            </li>
          </ul>

          <footer class="dedupe-group__footer">
            <span class="dedupe-group__hint">
              保留 {{ sampleNameOf(group, selections[group.groupId]?.keep ?? '') }}，排除
              {{ selections[group.groupId]?.exclude.length ?? 0 }} 条（被排除样本将标记为不可训练）
            </span>
            <el-button
              v-if="group.status === 'pending'"
              type="primary"
              size="small"
              :loading="resolvingId === group.groupId"
              @click="submitGroup(group)"
            >
              确认处理
            </el-button>
            <el-tag v-else type="success" effect="plain" size="small">已锁定处理结果</el-tag>
          </footer>
        </section>

        <section class="dedupe-block">
          <header class="dedupe-block__header">
            <h3 class="dedupe-block__title">去重操作记录</h3>
            <span class="dedupe-block__hint">只保存在本地 Mock 数据中，刷新后重置</span>
          </header>
          <DataTable
            :data="overview.records"
            row-key="recordId"
            size="small"
            empty-title="暂无去重操作"
            empty-description="确认保留或排除样本后，这里会记录操作人、时间与处理结果。"
          >
            <el-table-column prop="action" label="操作" width="180" />
            <el-table-column prop="detail" label="处理详情" min-width="260" />
            <el-table-column prop="operator" label="操作人" width="120" />
            <el-table-column prop="operatedAt" label="操作时间" width="160" />
          </DataTable>
        </section>
      </template>
    </template>
  </div>
</template>

<style scoped>
.dedupe-page {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-4);
}

.dedupe-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--ss-space-3);
}

.dedupe-empty {
  padding: var(--ss-space-5);
  border: 1px dashed var(--ss-border-strong);
  border-radius: var(--ss-radius-md);
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-sm);
  text-align: center;
}

.dedupe-group {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-3);
  padding: var(--ss-space-4);
  border: 1px solid var(--ss-border);
  border-radius: var(--ss-radius-lg);
  background: var(--ss-bg-card);
  box-shadow: var(--ss-shadow-sm);
}

.dedupe-group__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--ss-space-3);
}

.dedupe-group__title {
  display: flex;
  align-items: center;
  gap: var(--ss-space-2);
  font-size: var(--ss-font-size-body);
  font-weight: var(--ss-font-weight-semibold);
}

.dedupe-group__meta {
  margin-top: var(--ss-space-1);
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
}

.dedupe-group__resolved {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
}

.dedupe-samples {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  border: 1px solid var(--ss-divider);
  border-radius: var(--ss-radius-md);
  list-style: none;
  overflow: hidden;
}

.dedupe-sample {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ss-space-3);
  padding: var(--ss-space-2) var(--ss-space-3);
  background: var(--ss-bg-subtle);
}

.dedupe-sample + .dedupe-sample {
  border-top: 1px solid var(--ss-divider);
}

.dedupe-sample__main {
  display: flex;
  align-items: center;
  gap: var(--ss-space-2);
}

.dedupe-sample__name {
  color: var(--ss-text-primary);
  font-size: var(--ss-font-size-sm);
}

.dedupe-sample__id {
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
}

.dedupe-sample__actions {
  display: flex;
  align-items: center;
  gap: var(--ss-space-3);
}

.dedupe-group__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ss-space-3);
  flex-wrap: wrap;
}

.dedupe-group__hint {
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
}

.dedupe-block {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-3);
  padding: var(--ss-space-5);
  border: 1px solid var(--ss-border);
  border-radius: var(--ss-radius-lg);
  background: var(--ss-bg-card);
  box-shadow: var(--ss-shadow-sm);
}

.dedupe-block__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.dedupe-block__title {
  font-size: var(--ss-font-size-body-lg);
  font-weight: var(--ss-font-weight-semibold);
}

.dedupe-block__hint {
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
}
</style>
