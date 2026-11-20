<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'

import { ApiError } from '@/api/http'
import type { AccountStatus, AdminUser, UserDetail, UserQuery } from '@/api/models'
import { changeUserStatus, fetchUserDetail, fetchUsers } from '@/api/user'
import type { PageResult } from '@/api/types'
import {
  ConfirmDialog,
  DataTable,
  DetailDrawer,
  FilterPanel,
  PageHeader,
  Pagination,
  StatusTag,
} from '@/components'

/**
 * A02 用户管理。
 *
 * 数据全部来自 Mock Adapter：姓名、联系方式、绑定关系均为虚构，
 * 启用 / 禁用只修改内存中的演示数据，注销为占位交互。
 */

const ROLE_LABELS: Record<AdminUser['role'], string> = {
  elder: '老人',
  family: '家属',
}

const BINDING_LABELS: Record<AdminUser['bindingStatus'], string> = {
  bound: '已绑定',
  unbound: '未绑定',
  pending: '待确认',
}

/** 模板中以运行时字符串读取文案，避免对 any 行数据做类型断言 */
function roleLabel(role: string): string {
  return role === 'family' ? ROLE_LABELS.family : ROLE_LABELS.elder
}

function bindingLabel(binding: string): string {
  if (binding === 'bound') return BINDING_LABELS.bound
  if (binding === 'pending') return BINDING_LABELS.pending
  return BINDING_LABELS.unbound
}

const DEFAULT_QUERY: UserQuery = {
  keyword: '',
  userId: '',
  role: '',
  status: '',
  binding: '',
  page: 1,
  pageSize: 10,
}

const query = reactive<UserQuery>({ ...DEFAULT_QUERY })
const result = ref<PageResult<AdminUser>>({
  items: [],
  page: 1,
  pageSize: 10,
  total: 0,
})
const loading = ref(false)
const error = ref('')

function resolveMessage(cause: unknown, fallback: string): string {
  return cause instanceof ApiError ? cause.message : fallback
}

async function loadUsers() {
  loading.value = true
  error.value = ''
  try {
    result.value = await fetchUsers(query)
  } catch (cause) {
    result.value = { items: [], page: query.page, pageSize: query.pageSize, total: 0 }
    error.value = resolveMessage(cause, '用户列表加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

onMounted(loadUsers)

function handleSearch() {
  query.page = 1
  void loadUsers()
}

function handleReset() {
  Object.assign(query, { ...DEFAULT_QUERY, pageSize: query.pageSize })
  void loadUsers()
}

/* ===== 详情 ===== */
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detail = ref<UserDetail | null>(null)
const detailUserId = ref('')

async function loadDetail() {
  if (!detailUserId.value) return
  detailLoading.value = true
  detailError.value = ''
  try {
    detail.value = await fetchUserDetail(detailUserId.value)
  } catch (cause) {
    detail.value = null
    detailError.value = resolveMessage(cause, '用户详情加载失败，请稍后重试')
  } finally {
    detailLoading.value = false
  }
}

function openDetail(user: AdminUser) {
  detailUserId.value = user.userId
  detailVisible.value = true
  void loadDetail()
}

/* ===== 启用 / 禁用（演示） ===== */
const toggleVisible = ref(false)
const toggleLoading = ref(false)
const toggleTarget = ref<AdminUser | null>(null)

const nextStatus = computed<AccountStatus>(() =>
  toggleTarget.value?.status === 'active' ? 'disabled' : 'active',
)

function askToggle(user: AdminUser) {
  toggleTarget.value = user
  toggleVisible.value = true
}

async function confirmToggle() {
  const target = toggleTarget.value
  if (!target) return
  toggleLoading.value = true
  try {
    await changeUserStatus(target.userId, nextStatus.value)
    ElMessage.success(
      `已${nextStatus.value === 'disabled' ? '禁用' : '启用'}该账户（仅本地演示数据）`,
    )
    toggleVisible.value = false
    await loadUsers()
    if (detailVisible.value && detail.value?.user.userId === target.userId) {
      await loadDetail()
    }
  } catch (cause) {
    ElMessage.error(resolveMessage(cause, '账户状态更新失败，请稍后重试'))
  } finally {
    toggleLoading.value = false
  }
}

/* ===== 注销（占位） ===== */
const removeVisible = ref(false)
const removeTarget = ref<AdminUser | null>(null)

function askRemove(user: AdminUser) {
  removeTarget.value = user
  removeVisible.value = true
}

function confirmRemove() {
  removeVisible.value = false
  ElMessage.info('注销用户为占位交互：真实注销需服务端流程支持，本次未修改任何数据')
}
</script>

<template>
  <div class="user-page">
    <PageHeader
      eyebrow="A02 · 用户与设备"
      title="用户管理"
      description="查看老人与家属账户、家庭绑定关系与账户状态。以下均为虚构演示数据，联系方式已脱敏。"
    >
      <template #extra>
        <el-tag type="warning" effect="plain">演示数据（Mock）</el-tag>
      </template>
    </PageHeader>

    <FilterPanel :loading="loading" @search="handleSearch" @reset="handleReset">
      <div class="ss-field">
        <label class="ss-field__label" for="user-keyword">用户名称</label>
        <el-input
          id="user-keyword"
          v-model="query.keyword"
          placeholder="输入姓名关键字"
          clearable
          @keyup.enter="handleSearch"
        />
      </div>
      <div class="ss-field">
        <label class="ss-field__label" for="user-id">用户 ID</label>
        <el-input
          id="user-id"
          v-model="query.userId"
          placeholder="例如 U-2026-0004"
          clearable
          @keyup.enter="handleSearch"
        />
      </div>
      <div class="ss-field">
        <label class="ss-field__label" for="user-role">用户角色</label>
        <el-select id="user-role" v-model="query.role" placeholder="全部角色" clearable>
          <el-option label="老人" value="elder" />
          <el-option label="家属" value="family" />
        </el-select>
      </div>
      <div class="ss-field">
        <label class="ss-field__label" for="user-status">账户状态</label>
        <el-select id="user-status" v-model="query.status" placeholder="全部状态" clearable>
          <el-option label="正常" value="active" />
          <el-option label="已禁用" value="disabled" />
        </el-select>
      </div>
      <div class="ss-field">
        <label class="ss-field__label" for="user-binding">家庭绑定</label>
        <el-select id="user-binding" v-model="query.binding" placeholder="全部绑定状态" clearable>
          <el-option label="已绑定" value="bound" />
          <el-option label="待确认" value="pending" />
          <el-option label="未绑定" value="unbound" />
        </el-select>
      </div>
    </FilterPanel>

    <DataTable
      :data="result.items"
      :loading="loading"
      :error="error"
      row-key="userId"
      empty-title="没有匹配的用户"
      empty-description="请调整搜索或筛选条件后重试；演示数据固定为 26 条，重置筛选可恢复。"
      @row-click="openDetail"
      @retry="loadUsers"
    >
      <el-table-column prop="userId" label="用户 ID" width="140" />
      <el-table-column prop="name" label="用户名称" width="110" />
      <el-table-column label="用户角色" width="100">
        <template #default="{ row }">{{ roleLabel(row.role) }}</template>
      </el-table-column>
      <el-table-column prop="phoneMasked" label="联系方式" width="140" />
      <el-table-column label="账户状态" width="110">
        <template #default="{ row }">
          <StatusTag :status="row.status" />
        </template>
      </el-table-column>
      <el-table-column prop="registeredAt" label="注册时间" width="160" />
      <el-table-column label="家庭绑定" width="110">
        <template #default="{ row }">
          <StatusTag :status="row.bindingStatus" />
        </template>
      </el-table-column>
      <el-table-column prop="lastActiveAt" label="最近活跃" width="160" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click.stop="openDetail(row)">详情</el-button>
          <el-button
            link
            :type="row.status === 'active' ? 'warning' : 'success'"
            @click.stop="askToggle(row)"
          >
            {{ row.status === 'active' ? '禁用' : '启用' }}
          </el-button>
          <el-button link type="danger" @click.stop="askRemove(row)">注销</el-button>
        </template>
      </el-table-column>

      <template #footer>
        <Pagination
          v-model:page="query.page"
          v-model:page-size="query.pageSize"
          :total="result.total"
          @change="loadUsers"
        />
      </template>
    </DataTable>

    <DetailDrawer
      v-model="detailVisible"
      :title="detail ? `${detail.user.name} · ${detail.user.userId}` : '用户详情'"
      subtitle="演示数据 · 查看操作不会修改真实账户"
      :loading="detailLoading"
      :error="detailError"
      @retry="loadDetail"
    >
      <template v-if="detail">
        <section class="detail-block">
          <h4 class="detail-block__title">基础信息</h4>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="用户 ID">{{ detail.user.userId }}</el-descriptions-item>
            <el-descriptions-item label="用户名称">{{ detail.user.name }}</el-descriptions-item>
            <el-descriptions-item label="联系方式">
              {{ detail.user.phoneMasked }}
              <span class="detail-block__hint">已脱敏</span>
            </el-descriptions-item>
            <el-descriptions-item label="账户状态">
              <StatusTag :status="detail.user.status" />
            </el-descriptions-item>
            <el-descriptions-item label="注册时间">{{ detail.user.registeredAt }}</el-descriptions-item>
            <el-descriptions-item label="最近活跃">{{ detail.user.lastActiveAt }}</el-descriptions-item>
          </el-descriptions>
        </section>

        <section class="detail-block">
          <h4 class="detail-block__title">角色与家庭关系</h4>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="用户角色">
              {{ roleLabel(detail.user.role) }}
            </el-descriptions-item>
            <el-descriptions-item label="家庭绑定">
              <StatusTag :status="detail.user.bindingStatus" />
              <span class="detail-block__hint">
                {{ bindingLabel(detail.user.bindingStatus) }}（演示关系，不含真实家庭信息）
              </span>
            </el-descriptions-item>
          </el-descriptions>
        </section>

        <section class="detail-block">
          <h4 class="detail-block__title">操作记录（占位）</h4>
          <ul class="detail-log">
            <li v-for="log in detail.operations" :key="log.logId">
              <div>
                <p class="detail-log__action">{{ log.action }}</p>
                <p class="detail-log__meta">{{ log.operator }} · {{ log.time }}</p>
              </div>
              <StatusTag :status="log.result" />
            </li>
          </ul>
        </section>
      </template>

      <template #footer>
        <el-button @click="detail && openDetail(detail.user)">重新加载</el-button>
        <el-button
          :type="detail?.user.status === 'active' ? 'warning' : 'success'"
          @click="detail && askToggle(detail.user)"
        >
          {{ detail?.user.status === 'active' ? '禁用账户' : '启用账户' }}
        </el-button>
        <el-button type="danger" plain @click="detail && askRemove(detail.user)">
          注销用户（占位）
        </el-button>
      </template>
    </DetailDrawer>

    <ConfirmDialog
      v-model="toggleVisible"
      :title="nextStatus === 'disabled' ? '禁用用户账户' : '启用用户账户'"
      :message="`确定要${nextStatus === 'disabled' ? '禁用' : '启用'}用户「${toggleTarget?.name ?? ''}」吗？`"
      description="当前操作只修改本地 Mock 数据，不会影响真实用户账号；刷新页面后会恢复初始演示状态。"
      :type="nextStatus === 'disabled' ? 'warning' : 'primary'"
      :confirm-text="nextStatus === 'disabled' ? '确认禁用' : '确认启用'"
      :loading="toggleLoading"
      @confirm="confirmToggle"
    />

    <ConfirmDialog
      v-model="removeVisible"
      title="注销用户（占位）"
      :message="`即将注销用户「${removeTarget?.name ?? ''}」`"
      description="P09 仅提供二次确认占位：真实注销需要服务端校验与数据留存策略，本轮不会删除任何数据。"
      type="danger"
      confirm-text="确认注销（占位）"
      @confirm="confirmRemove"
    />
  </div>
</template>

<style scoped>
.user-page {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-5);
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

.detail-block__hint {
  margin-left: var(--ss-space-2);
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
}

.detail-log {
  list-style: none;
  margin: 0;
  padding: 0;
  border: 1px solid var(--ss-divider);
  border-radius: var(--ss-radius-md);
  overflow: hidden;
}

.detail-log li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ss-space-3);
  padding: var(--ss-space-3) var(--ss-space-4);
  background: var(--ss-bg-subtle);
}

.detail-log li + li {
  border-top: 1px solid var(--ss-divider);
}

.detail-log__action {
  color: var(--ss-text-primary);
  font-size: var(--ss-font-size-sm);
}

.detail-log__meta {
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
}
</style>
