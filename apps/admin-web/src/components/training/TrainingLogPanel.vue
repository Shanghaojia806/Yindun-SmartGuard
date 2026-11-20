<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { DataTable, Pagination } from '@/components'
import { fetchTrainingLogs } from '@/api/training'
import type { ExecutionMode, LogLevel, TrainingLog } from '@/types/training'

/**
 * 训练日志面板（P11）。
 *
 * 日志只来自 FastAPI（真实 Worker 上报）或明确标注的 Mock 演示日志，
 * 不在浏览器中伪造实时输出。ERROR 日志高亮展示，便于排查。
 */
interface Props {
  taskId: string
}

const props = defineProps<Props>()

const logs = ref<TrainingLog[]>([])
const loading = ref(false)
const error = ref('')
const total = ref(0)
const notice = ref('')
const executionMode = ref<ExecutionMode>('mock')

const query = reactive({
  level: '' as LogLevel | '',
  keyword: '',
  page: 1,
  pageSize: 20,
})

const LEVEL_OPTIONS: Array<{ value: LogLevel | ''; label: string }> = [
  { value: '', label: '全部等级' },
  { value: 'INFO', label: 'INFO' },
  { value: 'WARNING', label: 'WARNING' },
  { value: 'ERROR', label: 'ERROR' },
  { value: 'DEBUG', label: 'DEBUG' },
]

const SOURCE_LABELS: Record<TrainingLog['source'], string> = {
  worker: '真实 Worker',
  mock_demo: '演示日志',
  api: '业务接口',
}

const emptyDescription = computed(() =>
  query.level || query.keyword
    ? '没有符合筛选条件的日志，请调整等级或关键词。'
    : '该任务暂无可展示的日志。',
)

function levelTagType(level: LogLevel): 'primary' | 'warning' | 'danger' | 'info' {
  if (level === 'ERROR') return 'danger'
  if (level === 'WARNING') return 'warning'
  if (level === 'DEBUG') return 'info'
  return 'primary'
}

function rowClassName({ row }: { row: TrainingLog }): string {
  return row.level === 'ERROR' ? 'is-error-row' : ''
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const result = await fetchTrainingLogs(props.taskId, {
      level: query.level,
      keyword: query.keyword,
      page: query.page,
      pageSize: query.pageSize,
    })
    logs.value = result.items
    total.value = result.total
    notice.value = result.notice
    executionMode.value = result.executionMode
  } catch (err) {
    error.value = err instanceof Error ? err.message : '训练日志加载失败'
  } finally {
    loading.value = false
  }
}

function search(): void {
  query.page = 1
  void load()
}

watch(
  () => props.taskId,
  () => {
    query.level = ''
    query.keyword = ''
    query.page = 1
    void load()
  },
  { immediate: true },
)
</script>

<template>
  <div class="ss-log-panel">
    <el-alert
      v-if="notice"
      :title="notice"
      :type="executionMode === 'mock' ? 'warning' : 'info'"
      :closable="false"
      show-icon
      class="ss-log-panel__notice"
    />

    <div class="ss-log-panel__toolbar">
      <el-select v-model="query.level" class="ss-log-panel__level" @change="search">
        <el-option
          v-for="item in LEVEL_OPTIONS"
          :key="item.value || 'all'"
          :label="item.label"
          :value="item.value"
        />
      </el-select>
      <el-input
        v-model="query.keyword"
        class="ss-log-panel__keyword"
        placeholder="搜索日志关键词，例如 loss、Epoch、ERROR"
        clearable
        @keyup.enter="search"
        @clear="search"
      />
      <el-button type="primary" @click="search">搜索</el-button>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <DataTable
      :data="logs"
      :loading="loading"
      :error="error"
      row-key="logId"
      :row-class-name="rowClassName"
      empty-title="暂无训练日志"
      :empty-description="emptyDescription"
      @retry="load"
    >
      <el-table-column prop="timestamp" label="时间" width="176" />
      <el-table-column label="等级" width="104">
        <template #default="{ row }">
          <el-tag :type="levelTagType(row.level)" size="small" effect="plain">
            {{ row.level }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="来源" width="116">
        <template #default="{ row }">
          <span class="ss-log-panel__source">{{ SOURCE_LABELS[row.source as TrainingLog['source']] }}</span>
        </template>
      </el-table-column>
      <el-table-column label="日志内容" min-width="420">
        <template #default="{ row }">
          <span class="ss-log-panel__message">{{ row.message }}</span>
        </template>
      </el-table-column>

      <template #footer>
        <Pagination
          v-model:page="query.page"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[20, 50, 100]"
          @change="load"
        />
      </template>
    </DataTable>
  </div>
</template>

<style scoped>
.ss-log-panel {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-3);
}

.ss-log-panel__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ss-space-2);
}

.ss-log-panel__level {
  width: 140px;
}

.ss-log-panel__keyword {
  flex: 1 1 260px;
  max-width: 420px;
}

.ss-log-panel__source {
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
}

.ss-log-panel__message {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: var(--ss-font-size-xs);
  color: var(--ss-text-secondary);
  word-break: break-word;
}

.ss-log-panel :deep(.el-table .is-error-row) {
  background: var(--ss-danger-bg);
}

.ss-log-panel :deep(.el-table .is-error-row .ss-log-panel__message) {
  color: var(--ss-danger);
  font-weight: var(--ss-font-weight-medium);
}
</style>
