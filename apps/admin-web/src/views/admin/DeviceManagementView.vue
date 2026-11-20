<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'

import { ApiError } from '@/api/http'
import type {
  AdminDevice,
  DeviceDetail,
  DeviceFilterOptions,
  DeviceQuery,
} from '@/api/models'
import {
  fetchDeviceDetail,
  fetchDeviceFilterOptions,
  fetchDevices,
} from '@/api/device'
import type { PageResult } from '@/api/types'
import {
  DataTable,
  DetailDrawer,
  FilterPanel,
  PageHeader,
  Pagination,
  StatusTag,
} from '@/components'

/**
 * A03 设备管理。
 *
 * 本轮为只读页面：展示设备兼容性与异常记录，
 * 不提供远程控制、远程开启屏幕采集或操作用户设备的能力。
 * 所有状态均为演示数据，不代表真实设备检测结果。
 */

const DEFAULT_QUERY: DeviceQuery = {
  deviceId: '',
  model: '',
  brand: '',
  osVersion: '',
  status: '',
  appVersion: '',
  page: 1,
  pageSize: 10,
}

const query = reactive<DeviceQuery>({ ...DEFAULT_QUERY })
const result = ref<PageResult<AdminDevice>>({
  items: [],
  page: 1,
  pageSize: 10,
  total: 0,
})
const options = ref<DeviceFilterOptions>({
  brands: [],
  osVersions: [],
  appVersions: [],
})
const loading = ref(false)
const error = ref('')

function resolveMessage(cause: unknown, fallback: string): string {
  return cause instanceof ApiError ? cause.message : fallback
}

async function loadDevices() {
  loading.value = true
  error.value = ''
  try {
    result.value = await fetchDevices(query)
  } catch (cause) {
    result.value = { items: [], page: query.page, pageSize: query.pageSize, total: 0 }
    error.value = resolveMessage(cause, '设备列表加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  try {
    options.value = await fetchDeviceFilterOptions()
  } catch (cause) {
    console.warn('[device] 筛选选项加载失败，保留手动输入筛选项', cause)
  }
}

onMounted(() => {
  void loadOptions()
  void loadDevices()
})

function handleSearch() {
  query.page = 1
  void loadDevices()
}

function handleReset() {
  Object.assign(query, { ...DEFAULT_QUERY, pageSize: query.pageSize })
  void loadDevices()
}

/* ===== 详情 ===== */
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detail = ref<DeviceDetail | null>(null)
const detailDeviceId = ref('')

async function loadDetail() {
  if (!detailDeviceId.value) return
  detailLoading.value = true
  detailError.value = ''
  try {
    detail.value = await fetchDeviceDetail(detailDeviceId.value)
  } catch (cause) {
    detail.value = null
    detailError.value = resolveMessage(cause, '设备详情加载失败，请稍后重试')
  } finally {
    detailLoading.value = false
  }
}

function openDetail(device: AdminDevice) {
  detailDeviceId.value = device.deviceId
  detailVisible.value = true
  void loadDetail()
}

const LEVEL_LABELS: Record<'info' | 'warning' | 'danger', string> = {
  info: '提示',
  warning: '警告',
  danger: '严重',
}

const LEVEL_TYPES: Record<'info' | 'warning' | 'danger', 'info' | 'warning' | 'danger'> = {
  info: 'info',
  warning: 'warning',
  danger: 'danger',
}
</script>

<template>
  <div class="device-page">
    <PageHeader
      eyebrow="A03 · 用户与设备"
      title="设备管理"
      description="查看设备兼容性、系统与 App 版本、异常记录。仅为演示数据，不提供远程控制能力。"
    >
      <template #extra>
        <el-tag type="warning" effect="plain">演示数据（Mock）</el-tag>
      </template>
    </PageHeader>

    <FilterPanel :loading="loading" @search="handleSearch" @reset="handleReset">
      <div class="ss-field">
        <label class="ss-field__label" for="device-id">设备 ID</label>
        <el-input
          id="device-id"
          v-model="query.deviceId"
          placeholder="例如 DEV-2026-0003"
          clearable
          @keyup.enter="handleSearch"
        />
      </div>
      <div class="ss-field">
        <label class="ss-field__label" for="device-model">设备型号</label>
        <el-input
          id="device-model"
          v-model="query.model"
          placeholder="输入型号关键字"
          clearable
          @keyup.enter="handleSearch"
        />
      </div>
      <div class="ss-field">
        <label class="ss-field__label" for="device-brand">品牌</label>
        <el-select id="device-brand" v-model="query.brand" placeholder="全部品牌" clearable>
          <el-option v-for="brand in options.brands" :key="brand" :label="brand" :value="brand" />
        </el-select>
      </div>
      <div class="ss-field">
        <label class="ss-field__label" for="device-os">系统版本</label>
        <el-select id="device-os" v-model="query.osVersion" placeholder="全部系统版本" clearable>
          <el-option
            v-for="version in options.osVersions"
            :key="version"
            :label="version"
            :value="version"
          />
        </el-select>
      </div>
      <div class="ss-field">
        <label class="ss-field__label" for="device-status">设备状态</label>
        <el-select id="device-status" v-model="query.status" placeholder="全部状态" clearable>
          <el-option label="正常" value="normal" />
          <el-option label="异常" value="abnormal" />
        </el-select>
      </div>
      <div class="ss-field">
        <label class="ss-field__label" for="device-app">App 版本</label>
        <el-select id="device-app" v-model="query.appVersion" placeholder="全部版本" clearable>
          <el-option
            v-for="version in options.appVersions"
            :key="version"
            :label="version"
            :value="version"
          />
        </el-select>
      </div>
    </FilterPanel>

    <DataTable
      :data="result.items"
      :loading="loading"
      :error="error"
      row-key="deviceId"
      empty-title="没有匹配的设备"
      empty-description="请调整搜索或筛选条件后重试；演示数据固定为 16 条设备记录。"
      @row-click="openDetail"
      @retry="loadDevices"
    >
      <el-table-column prop="deviceId" label="设备 ID" width="150" />
      <el-table-column label="关联用户" width="130">
        <template #default="{ row }">
          <span class="device-user">{{ row.userName }}</span>
          <span class="device-user__id">{{ row.userId }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="brand" label="品牌" width="90" />
      <el-table-column prop="model" label="设备型号" width="150" />
      <el-table-column label="系统版本" width="150">
        <template #default="{ row }">{{ row.osName }} · {{ row.osVersion }}</template>
      </el-table-column>
      <el-table-column prop="appVersion" label="App 版本" width="100" />
      <el-table-column label="设备状态" width="100">
        <template #default="{ row }">
          <StatusTag :status="row.status" />
        </template>
      </el-table-column>
      <el-table-column prop="lastActiveAt" label="最近活跃" width="160" />
      <el-table-column label="异常信息" min-width="200">
        <template #default="{ row }">
          <span v-if="row.abnormalNote" class="device-abnormal">{{ row.abnormalNote }}</span>
          <span v-else class="ss-text-tertiary">无</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click.stop="openDetail(row)">详情</el-button>
        </template>
      </el-table-column>

      <template #footer>
        <Pagination
          v-model:page="query.page"
          v-model:page-size="query.pageSize"
          :total="result.total"
          @change="loadDevices"
        />
      </template>
    </DataTable>

    <DetailDrawer
      v-model="detailVisible"
      :title="detail ? `${detail.device.brand} ${detail.device.model}` : '设备详情'"
      :subtitle="detail ? `${detail.device.deviceId} · 演示数据，不代表真实设备检测结果` : '演示数据'"
      :loading="detailLoading"
      :error="detailError"
      @retry="loadDetail"
    >
      <template v-if="detail">
        <section class="detail-block">
          <h4 class="detail-block__title">基本设备信息</h4>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="设备 ID">{{ detail.device.deviceId }}</el-descriptions-item>
            <el-descriptions-item label="品牌 / 型号">
              {{ detail.device.brand }} {{ detail.device.model }}
            </el-descriptions-item>
            <el-descriptions-item label="设备状态">
              <StatusTag :status="detail.device.status" />
            </el-descriptions-item>
            <el-descriptions-item label="最近活跃">{{ detail.device.lastActiveAt }}</el-descriptions-item>
          </el-descriptions>
        </section>

        <section class="detail-block">
          <h4 class="detail-block__title">关联账户</h4>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="用户名称">{{ detail.device.userName }}</el-descriptions-item>
            <el-descriptions-item label="用户 ID">{{ detail.device.userId }}</el-descriptions-item>
          </el-descriptions>
        </section>

        <section class="detail-block">
          <h4 class="detail-block__title">版本与兼容性</h4>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="系统版本">
              {{ detail.device.osName }} · {{ detail.device.osVersion }}
            </el-descriptions-item>
            <el-descriptions-item label="App 版本">{{ detail.device.appVersion }}</el-descriptions-item>
            <el-descriptions-item label="最近连接时间">
              {{ detail.device.lastActiveAt }}
            </el-descriptions-item>
            <el-descriptions-item label="兼容性状态">
              <StatusTag :status="detail.device.compatibility" />
            </el-descriptions-item>
          </el-descriptions>
        </section>

        <section class="detail-block">
          <h4 class="detail-block__title">异常记录</h4>
          <ul v-if="detail.records.length" class="detail-records">
            <li v-for="record in detail.records" :key="record.recordId">
              <el-tag :type="LEVEL_TYPES[record.level]" size="small" effect="light">
                {{ LEVEL_LABELS[record.level] }}
              </el-tag>
              <div class="detail-records__body">
                <p class="detail-records__desc">{{ record.description }}</p>
                <p class="detail-records__time">{{ record.time }}</p>
              </div>
            </li>
          </ul>
          <p v-else class="detail-block__empty">该设备暂无异常记录（演示数据）。</p>
        </section>

        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="本轮不提供远程控制能力"
          description="管理端不能远程开启屏幕采集或操作用户设备；设备授权与采集控制始终由老人在本机完成。"
        />
      </template>

      <template #footer>
        <el-button @click="detail && openDetail(detail.device)">重新加载</el-button>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </DetailDrawer>
  </div>
</template>

<style scoped>
.device-page {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-5);
}

.device-user {
  display: block;
  color: var(--ss-text-primary);
}

.device-user__id {
  display: block;
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
}

.device-abnormal {
  color: var(--ss-danger);
  font-size: var(--ss-font-size-sm);
}

.detail-block {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-3);
}

.detail-block__title {
  color: var(--ss-text-primary);
  font-size: var(--ss-font-size-body);
  font-weight: var(--ss-font-weight-semibold);
}

.detail-block__empty {
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-sm);
}

.detail-records {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-2);
}

.detail-records li {
  display: flex;
  align-items: flex-start;
  gap: var(--ss-space-3);
  padding: var(--ss-space-3) var(--ss-space-4);
  border: 1px solid var(--ss-divider);
  border-radius: var(--ss-radius-md);
  background: var(--ss-bg-subtle);
}

.detail-records__body {
  min-width: 0;
}

.detail-records__desc {
  color: var(--ss-text-primary);
  font-size: var(--ss-font-size-sm);
}

.detail-records__time {
  margin-top: 2px;
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
}
</style>
