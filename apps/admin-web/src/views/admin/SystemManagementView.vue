<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'

import { ApiError } from '@/api/http'
import type { AuditLog, AuditQuery, SystemOverviewData } from '@/api/models'
import { fetchAuditLogs, fetchSystemOverview } from '@/api/system'
import type { PageResult } from '@/api/types'
import {
  DataTable,
  ErrorState,
  FilterPanel,
  LoadingState,
  PageHeader,
  Pagination,
  StatusTag,
} from '@/components'

/**
 * A10 系统管理（P09 前端骨架）。
 *
 * 管理员、角色权限、审计日志、系统配置均为演示数据；
 * 本页不执行真实 RBAC 校验、不修改系统配置、不触发数据备份。
 */

const activeTab = ref('admins')

const overviewLoading = ref(false)
const overviewError = ref('')
const overview = ref<SystemOverviewData | null>(null)

const auditQuery = reactive<AuditQuery>({ keyword: '', page: 1, pageSize: 10 })
const auditResult = ref<PageResult<AuditLog>>({
  items: [],
  page: 1,
  pageSize: 10,
  total: 0,
})
const auditLoading = ref(false)
const auditError = ref('')

function resolveMessage(cause: unknown, fallback: string): string {
  return cause instanceof ApiError ? cause.message : fallback
}

async function loadOverview() {
  overviewLoading.value = true
  overviewError.value = ''
  try {
    overview.value = await fetchSystemOverview()
  } catch (cause) {
    overview.value = null
    overviewError.value = resolveMessage(cause, '系统管理数据加载失败，请稍后重试')
  } finally {
    overviewLoading.value = false
  }
}

async function loadAudit() {
  auditLoading.value = true
  auditError.value = ''
  try {
    auditResult.value = await fetchAuditLogs(auditQuery)
  } catch (cause) {
    auditResult.value = {
      items: [],
      page: auditQuery.page,
      pageSize: auditQuery.pageSize,
      total: 0,
    }
    auditError.value = resolveMessage(cause, '审计日志加载失败，请稍后重试')
  } finally {
    auditLoading.value = false
  }
}

onMounted(() => {
  void loadOverview()
  void loadAudit()
})

function handleAuditSearch() {
  auditQuery.page = 1
  void loadAudit()
}

function handleAuditReset() {
  auditQuery.keyword = ''
  auditQuery.page = 1
  void loadAudit()
}
</script>

<template>
  <div class="system-page">
    <PageHeader
      eyebrow="A10 · 系统"
      title="系统管理"
      description="管理员账户、角色权限、操作审计与系统配置的前端骨架。当前未接入真实 RBAC 与配置服务。"
    >
      <template #extra>
        <el-tag type="warning" effect="plain">演示数据（Mock）</el-tag>
      </template>
    </PageHeader>

    <el-alert
      type="warning"
      :closable="false"
      show-icon
      title="本轮不启用真实权限与系统配置"
      description="角色与权限仅作前端展示，不代表服务端 RBAC 已生效；系统配置项不可修改，也不会执行备份。"
    />

    <LoadingState v-if="overviewLoading" text="正在加载系统管理数据" />

    <ErrorState
      v-else-if="overviewError"
      :description="overviewError"
      @retry="loadOverview"
    />

    <el-tabs v-else-if="overview" v-model="activeTab" class="system-tabs">
      <el-tab-pane label="管理员管理" name="admins">
        <div class="system-pane">
          <div class="system-pane__head">
            <p class="system-pane__title">管理员账户</p>
            <span class="ss-section__hint">演示数据 · 最近登录时间不代表真实会话</span>
          </div>

          <DataTable
            :data="overview.admins"
            row-key="adminId"
            empty-title="暂无管理员记录"
            empty-description="演示数据为空时展示此状态。"
          >
            <el-table-column prop="adminId" label="管理员 ID" width="140" />
            <el-table-column prop="name" label="管理员名称" width="140" />
            <el-table-column prop="role" label="角色" min-width="140" />
            <el-table-column label="账户状态" width="120">
              <template #default="{ row }">
                <StatusTag :status="row.status" />
              </template>
            </el-table-column>
            <el-table-column prop="lastLoginAt" label="最近登录时间" width="180" />
          </DataTable>
        </div>
      </el-tab-pane>

      <el-tab-pane label="角色权限" name="roles">
        <div class="system-pane">
          <div class="system-pane__head">
            <p class="system-pane__title">角色与功能权限对照</p>
            <span class="ss-section__hint">仅前端展示，未启用真实 RBAC</span>
          </div>

          <DataTable
            :data="overview.roles"
            row-key="roleId"
            empty-title="暂无角色配置"
            empty-description="演示数据为空时展示此状态。"
          >
            <el-table-column prop="roleName" label="角色" width="140" />
            <el-table-column prop="description" label="职责说明" min-width="240" />
            <el-table-column label="成员数" width="100">
              <template #default="{ row }">{{ row.memberCount }} 人</template>
            </el-table-column>
            <el-table-column label="功能权限" min-width="320">
              <template #default="{ row }">
                <div class="role-tags">
                  <el-tag
                    v-for="permission in row.permissions"
                    :key="permission"
                    size="small"
                    effect="plain"
                    type="info"
                  >
                    {{ permission }}
                  </el-tag>
                </div>
              </template>
            </el-table-column>
          </DataTable>
        </div>
      </el-tab-pane>

      <el-tab-pane label="操作审计" name="audit">
        <div class="system-pane">
          <FilterPanel
            :loading="auditLoading"
            title="审计日志筛选"
            @search="handleAuditSearch"
            @reset="handleAuditReset"
          >
            <div class="ss-field">
              <label class="ss-field__label" for="audit-keyword">关键字</label>
              <el-input
                id="audit-keyword"
                v-model="auditQuery.keyword"
                placeholder="操作人 / 操作类型 / 操作对象"
                clearable
                @keyup.enter="handleAuditSearch"
              />
            </div>
          </FilterPanel>

          <DataTable
            :data="auditResult.items"
            :loading="auditLoading"
            :error="auditError"
            row-key="logId"
            empty-title="没有匹配的审计记录"
            empty-description="请调整关键字后重试；审计日志为演示数据，不代表真实操作留痕。"
            @retry="loadAudit"
          >
            <el-table-column prop="operator" label="操作人" width="120" />
            <el-table-column prop="actionType" label="操作类型" width="160" />
            <el-table-column prop="time" label="操作时间" width="170" />
            <el-table-column prop="target" label="操作对象" min-width="220" />
            <el-table-column label="操作结果" width="110">
              <template #default="{ row }">
                <StatusTag :status="row.result" />
              </template>
            </el-table-column>

            <template #footer>
              <Pagination
                v-model:page="auditQuery.page"
                v-model:page-size="auditQuery.pageSize"
                :total="auditResult.total"
                @change="loadAudit"
              />
            </template>
          </DataTable>
        </div>
      </el-tab-pane>

      <el-tab-pane label="系统配置" name="config">
        <div class="system-pane">
          <div class="system-pane__head">
            <p class="system-pane__title">系统配置（占位）</p>
            <span class="ss-section__hint">本轮不执行真实配置变更与数据备份</span>
          </div>

          <div class="config-grid">
            <article v-for="item in overview.configs" :key="item.configId" class="config-card">
              <header class="config-card__header">
                <span class="config-card__label">{{ item.label }}</span>
                <el-tag type="info" size="small" effect="plain">演示</el-tag>
              </header>
              <p class="config-card__value">{{ item.value }}</p>
              <p class="config-card__desc">{{ item.description }}</p>
              <el-tooltip content="P09 仅做结构占位，接入服务端后开放修改" placement="top">
                <span class="config-card__action-wrap">
                  <el-button size="small" disabled>修改（P10+）</el-button>
                </span>
              </el-tooltip>
            </article>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.system-page {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-5);
}

.system-tabs {
  padding: var(--ss-space-2) var(--ss-space-5) var(--ss-space-5);
  border: 1px solid var(--ss-border);
  border-radius: var(--ss-radius-lg);
  background: var(--ss-bg-card);
  box-shadow: var(--ss-shadow-sm);
}

.system-pane {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-4);
  padding-top: var(--ss-space-2);
}

.system-pane__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--ss-space-3);
}

.system-pane__title {
  color: var(--ss-text-primary);
  font-size: var(--ss-font-size-body);
  font-weight: var(--ss-font-weight-semibold);
}

.role-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ss-space-1);
}

.config-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--ss-space-4);
}

.config-card {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-2);
  padding: var(--ss-space-4);
  border: 1px solid var(--ss-border);
  border-radius: var(--ss-radius-md);
  background: var(--ss-bg-subtle);
}

.config-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ss-space-2);
}

.config-card__label {
  color: var(--ss-text-secondary);
  font-size: var(--ss-font-size-sm);
}

.config-card__value {
  color: var(--ss-text-primary);
  font-size: var(--ss-font-size-body-lg);
  font-weight: var(--ss-font-weight-medium);
}

.config-card__desc {
  flex: 1;
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
  line-height: var(--ss-line-height-base);
}

.config-card__action-wrap {
  align-self: flex-start;
}

@media (max-width: 1180px) {
  .config-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .config-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
