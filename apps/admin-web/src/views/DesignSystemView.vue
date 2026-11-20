<script setup lang="ts">
import { ArrowLeft } from '@element-plus/icons-vue'
import { ref } from 'vue'

import {
  ConfirmDialog,
  DataTableEmpty,
  ErrorState,
  FilterPanel,
  LoadingState,
  PageHeader,
} from '@/components'

/**
 * 设计规范预览页（仅开发环境可访问）
 *
 * 用途：集中检查品牌色、字体层级、按钮状态、筛选区、表格空态、
 * 确认弹窗、加载与错误状态在三类桌面宽度下的表现。
 * 本页不包含任何业务数据，也不代表业务功能已完成。
 */

const confirmVisible = ref(false)
const confirmType = ref<'danger' | 'warning'>('danger')
const confirmLoading = ref(false)

const lastAction = ref('尚未执行筛选操作')

const brandColors = [
  { name: '品牌主色', token: '--ss-primary', value: '#174A71' },
  { name: '品牌深色', token: '--ss-primary-dark', value: '#0F3550' },
  { name: '品牌浅色', token: '--ss-primary-light', value: '#D6E6F2' },
  { name: '交互主色', token: '--ss-accent', value: '#176497' },
  { name: '交互浅色', token: '--ss-accent-light', value: '#E6F1F9' },
  { name: '页面背景', token: '--ss-bg-page', value: '#F4F7FA' },
]

const semanticColors = [
  { name: '安全', token: '--ss-safe', value: '#25845E', bg: '--ss-safe-bg' },
  { name: '警示', token: '--ss-warning', value: '#D8A03A', bg: '--ss-warning-bg' },
  { name: '危险', token: '--ss-danger', value: '#C83C3C', bg: '--ss-danger-bg' },
  { name: '中性', token: '--ss-neutral', value: '#64798A', bg: '--ss-neutral-bg' },
]

const typeScale = [
  { token: '--ss-font-size-display', size: '34px', label: '展示级标题', sample: '数据中心总览' },
  { token: '--ss-font-size-h1', size: '28px', label: '页面标题', sample: '数据集管理' },
  { token: '--ss-font-size-h2', size: '24px', label: '区块标题', sample: '训练任务列表' },
  { token: '--ss-font-size-h3', size: '20px', label: '卡片标题', sample: '模型版本信息' },
  { token: '--ss-font-size-body-lg', size: '16px', label: '强调正文', sample: '重点关注样本分布与标注质量。' },
  { token: '--ss-font-size-body', size: '15px', label: '正文', sample: '默认正文用于常规说明与表格内容。' },
  { token: '--ss-font-size-sm', size: '13px', label: '辅助文字', sample: '辅助说明与表单提示文字。' },
  { token: '--ss-font-size-xs', size: '12px', label: '说明文字', sample: '最小字号仅用于时间、计数等次要信息。' },
]

const spaceTokens = [
  { name: '4px', value: '--ss-space-1', width: 4 },
  { name: '8px', value: '--ss-space-2', width: 8 },
  { name: '12px', value: '--ss-space-3', width: 12 },
  { name: '16px', value: '--ss-space-4', width: 16 },
  { name: '20px', value: '--ss-space-5', width: 20 },
  { name: '24px', value: '--ss-space-6', width: 24 },
  { name: '32px', value: '--ss-space-8', width: 32 },
  { name: '40px', value: '--ss-space-10', width: 40 },
]

const radiusTokens = [
  { name: '4px', value: '--ss-radius-xs', radius: 4 },
  { name: '6px', value: '--ss-radius-sm', radius: 6 },
  { name: '8px', value: '--ss-radius-md', radius: 8 },
  { name: '12px', value: '--ss-radius-lg', radius: 12 },
  { name: '16px', value: '--ss-radius-xl', radius: 16 },
]

const shadowTokens = [
  { name: '一级阴影', value: '--ss-shadow-sm', desc: '卡片、面板默认' },
  { name: '二级阴影', value: '--ss-shadow-md', desc: '浮层、下拉容器' },
  { name: '三级阴影', value: '--ss-shadow-lg', desc: '弹窗、抽屉' },
]

const filterKeyword = ref('')
const filterStatus = ref('')
const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '已发布', value: 'published' },
  { label: '训练中', value: 'training' },
  { label: '已停用', value: 'disabled' },
]

function handleSearch() {
  const parts = [
    filterKeyword.value ? `关键词「${filterKeyword.value}」` : '无关键词',
    filterStatus.value ? `状态「${statusOptions.find((o) => o.value === filterStatus.value)?.label}」` : '全部状态',
  ]
  lastAction.value = `已按 ${parts.join('、')} 执行筛选（演示，未请求接口）`
}

function handleReset() {
  filterKeyword.value = ''
  filterStatus.value = ''
  lastAction.value = '已清空筛选条件（演示，未请求接口）'
}

function openConfirm(type: 'danger' | 'warning') {
  confirmType.value = type
  confirmVisible.value = true
}

function handleConfirm() {
  confirmLoading.value = true
  window.setTimeout(() => {
    confirmLoading.value = false
    confirmVisible.value = false
  }, 800)
}
</script>

<template>
  <div class="design-page">
    <header class="design-page__topbar">
      <router-link class="design-page__back" to="/">
        <el-icon><ArrowLeft /></el-icon>
        返回工程基线页
      </router-link>
      <el-tag type="warning" effect="plain" size="small">开发预览 · 生产环境不可访问</el-tag>
    </header>

    <main class="design-page__main">
      <PageHeader
        eyebrow="设计规范"
        title="银盾智鉴管理端设计系统"
        description="本页集中展示管理端设计变量与通用组件，用于开发阶段自查。所有内容均为静态示例，不代表业务功能已完成。"
      />

      <!-- 1. 品牌与语义色 -->
      <section class="design-section">
        <h2 class="design-section__title">01 · 品牌与语义色</h2>
        <p class="design-section__desc">品牌主色用于导航与主要按钮，交互主色用于链接与焦点态，语义色仅表达状态含义。</p>

        <div class="color-grid">
          <div v-for="color in brandColors" :key="color.token" class="color-card">
            <span class="color-card__swatch" :style="{ background: `var(${color.token})` }" />
            <div class="color-card__meta">
              <p class="color-card__name">{{ color.name }}</p>
              <p class="color-card__value">{{ color.value }}</p>
              <code>{{ color.token }}</code>
            </div>
          </div>
        </div>

        <div class="color-grid color-grid--semantic">
          <div v-for="color in semanticColors" :key="color.token" class="color-card">
            <span class="color-card__swatch" :style="{ background: `var(${color.token})` }" />
            <div class="color-card__meta">
              <p class="color-card__name">{{ color.name }}</p>
              <p class="color-card__value">{{ color.value }}</p>
              <span class="color-card__chip" :style="{ background: `var(${color.bg})`, color: `var(${color.token})` }">
                {{ color.name }}状态
              </span>
            </div>
          </div>
        </div>
      </section>

      <!-- 2. 字体层级 -->
      <section class="design-section">
        <h2 class="design-section__title">02 · 字号层级</h2>
        <p class="design-section__desc">字号跨度用于建立信息层级，正文以 15px 为基准，表格内不缩小到 12px 以下。</p>

        <div class="type-list">
          <div v-for="item in typeScale" :key="item.token" class="type-row">
            <div class="type-row__meta">
              <span class="type-row__label">{{ item.label }}</span>
              <code>{{ item.token }}</code>
              <span class="type-row__size">{{ item.size }}</span>
            </div>
            <p class="type-row__sample" :style="{ fontSize: `var(${item.token})` }">{{ item.sample }}</p>
          </div>
        </div>
      </section>

      <!-- 3. 按钮状态 -->
      <section class="design-section">
        <h2 class="design-section__title">03 · 按钮与交互状态</h2>
        <p class="design-section__desc">同一操作在正常、加载、禁用状态下必须可区分；悬浮与按下状态可直接用鼠标验证。</p>

        <el-table :data="[
          { state: '正常', desc: '可点击，主按钮使用品牌主色' },
          { state: '悬浮 / 按下', desc: '背景加深，不改变文字含义' },
          { state: '加载', desc: '显示进度并禁止重复提交' },
          { state: '禁用', desc: '降低对比度，光标不可点击' },
        ]" border>
          <el-table-column prop="state" label="状态" width="140" />
          <el-table-column prop="desc" label="规范" />
          <el-table-column label="主按钮" width="180">
            <template #default>
              <el-button type="primary">新建数据集</el-button>
            </template>
          </el-table-column>
          <el-table-column label="次按钮" width="180">
            <template #default>
              <el-button>导出记录</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="state-row">
          <div class="state-row__group">
            <span class="state-row__label">加载态</span>
            <el-button type="primary" loading>保存中</el-button>
            <el-button loading>同步中</el-button>
          </div>
          <div class="state-row__group">
            <span class="state-row__label">禁用态</span>
            <el-button type="primary" disabled>新建数据集</el-button>
            <el-button disabled>导出记录</el-button>
          </div>
          <div class="state-row__group">
            <span class="state-row__label">语义按钮</span>
            <el-button type="danger" plain>删除数据集</el-button>
            <el-button type="success" plain>标记为安全</el-button>
            <el-button type="warning" plain>加入观察</el-button>
          </div>
        </div>
      </section>

      <!-- 4. 筛选面板 -->
      <section class="design-section">
        <h2 class="design-section__title">04 · 筛选面板</h2>
        <p class="design-section__desc">筛选项统一放入 FilterPanel，操作按钮固定在面板右下角，避免各页面自行排布。</p>

        <FilterPanel :loading="false" @search="handleSearch" @reset="handleReset">
          <div class="field">
            <label class="field__label" for="filter-keyword">关键词</label>
            <el-input id="filter-keyword" v-model="filterKeyword" placeholder="输入数据集名称" clearable />
          </div>
          <div class="field">
            <label class="field__label" for="filter-status">发布状态</label>
            <el-select id="filter-status" v-model="filterStatus" placeholder="全部状态">
              <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </div>
          <div class="field field--wide">
            <span class="field__label">最近操作</span>
            <p class="field__hint">{{ lastAction }}</p>
          </div>
        </FilterPanel>
      </section>

      <!-- 5. 表格空状态 -->
      <section class="design-section">
        <h2 class="design-section__title">05 · 表格与空状态</h2>
        <p class="design-section__desc">表格无数据时使用统一空态，说明为空原因并可提供唯一的下一步操作。</p>

        <el-table :data="[]" border>
          <el-table-column prop="name" label="数据集名称" min-width="200" />
          <el-table-column prop="samples" label="样本数" width="120" />
          <el-table-column prop="status" label="状态" width="120" />
          <el-table-column prop="updatedAt" label="更新时间" width="180" />
          <template #empty>
            <DataTableEmpty
              title="暂无数据集"
              description="当前筛选条件下没有匹配的数据集，可调整条件或新建数据集。"
            >
              <template #actions>
                <el-button type="primary">新建数据集</el-button>
              </template>
            </DataTableEmpty>
          </template>
        </el-table>

        <div class="empty-compact-demo">
          <p class="empty-compact-demo__title">紧凑空态（用于卡片内的小型列表）</p>
          <DataTableEmpty
            compact
            title="暂无处理记录"
            description="该风险事件尚未产生处理动作。"
          />
        </div>
      </section>

      <!-- 6. 确认弹窗 -->
      <section class="design-section">
        <h2 class="design-section__title">06 · 确认弹窗</h2>
        <p class="design-section__desc">删除、撤销、停用等操作必须二次确认，说明影响范围，确认按钮使用明确动词。</p>

        <div class="state-row">
          <el-button type="danger" plain @click="openConfirm('danger')">打开删除确认</el-button>
          <el-button type="warning" plain @click="openConfirm('warning')">打开停用确认</el-button>
        </div>

        <ConfirmDialog
          v-model="confirmVisible"
          :type="confirmType"
          :loading="confirmLoading"
          :title="confirmType === 'danger' ? '删除数据集' : '停用模型版本'"
          :message="confirmType === 'danger'
            ? '删除后该数据集及其标注记录将无法恢复。'
            : '停用后该模型版本不再参与线上识别，可再次启用。'"
          description="示例弹窗仅演示交互状态，不会调用任何接口，也不会修改真实数据。"
          :confirm-text="confirmType === 'danger' ? '确认删除' : '确认停用'"
          @confirm="handleConfirm"
        />
      </section>

      <!-- 7. 加载与错误状态 -->
      <section class="design-section">
        <h2 class="design-section__title">07 · 加载与错误状态</h2>
        <p class="design-section__desc">加载与错误必须给出明确原因；权限不足不提供重试，避免无效操作。</p>

        <div class="state-grid">
          <div class="state-card">
            <p class="state-card__title">加载状态</p>
            <LoadingState text="正在加载数据集列表" min-height="160px" />
            <LoadingState variant="inline" text="正在同步" />
          </div>
          <div class="state-card">
            <p class="state-card__title">加载失败</p>
            <ErrorState kind="load" @retry="() => {}" />
          </div>
          <div class="state-card">
            <p class="state-card__title">网络异常</p>
            <ErrorState kind="network" @retry="() => {}" />
          </div>
          <div class="state-card">
            <p class="state-card__title">权限不足</p>
            <ErrorState kind="permission" />
          </div>
        </div>
      </section>

      <!-- 8. 间距、圆角与阴影 -->
      <section class="design-section">
        <h2 class="design-section__title">08 · 间距、圆角与阴影</h2>
        <p class="design-section__desc">间距基于 4 的倍数；阴影仅用于区分层级，不使用装饰性投影。</p>

        <div class="token-block">
          <h3 class="token-block__title">间距</h3>
          <div v-for="item in spaceTokens" :key="item.value" class="space-row">
            <code>{{ item.value }}</code>
            <span class="space-row__bar" :style="{ width: `${item.width * 3}px` }" />
            <span class="space-row__label">{{ item.name }}</span>
          </div>
        </div>

        <div class="token-block">
          <h3 class="token-block__title">圆角</h3>
          <div class="radius-row">
            <div v-for="item in radiusTokens" :key="item.value" class="radius-item">
              <span class="radius-item__box" :style="{ borderRadius: `${item.radius}px` }" />
              <code>{{ item.value }}</code>
              <span class="radius-item__label">{{ item.name }}</span>
            </div>
          </div>
        </div>

        <div class="token-block">
          <h3 class="token-block__title">阴影</h3>
          <div class="shadow-row">
            <div v-for="item in shadowTokens" :key="item.value" class="shadow-item" :style="{ boxShadow: `var(${item.value})` }">
              <p class="shadow-item__name">{{ item.name }}</p>
              <p class="shadow-item__desc">{{ item.desc }}</p>
              <code>{{ item.value }}</code>
            </div>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped>
.design-page {
  min-height: 100%;
  background: var(--ss-bg-page);
}

.design-page__topbar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ss-space-4);
  height: var(--ss-header-height);
  padding: 0 var(--ss-page-padding);
  border-bottom: 1px solid var(--ss-border);
  background: var(--ss-bg-card);
}

.design-page__back {
  display: inline-flex;
  align-items: center;
  gap: var(--ss-space-2);
  color: var(--ss-text-secondary);
  font-size: var(--ss-font-size-sm);
}

.design-page__back:hover {
  color: var(--ss-primary);
}

.design-page__main {
  max-width: 1200px;
  margin: 0 auto;
  padding: var(--ss-space-6) var(--ss-page-padding) var(--ss-space-10);
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-6);
}

.design-section {
  padding: var(--ss-space-6);
  border: 1px solid var(--ss-border);
  border-radius: var(--ss-radius-lg);
  background: var(--ss-bg-card);
  box-shadow: var(--ss-shadow-sm);
}

.design-section__title {
  font-size: var(--ss-font-size-h3);
}

.design-section__desc {
  margin-top: var(--ss-space-1);
  margin-bottom: var(--ss-space-5);
  color: var(--ss-text-secondary);
  font-size: var(--ss-font-size-sm);
}

/* 色板 */
.color-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: var(--ss-space-4);
}

.color-grid--semantic {
  margin-top: var(--ss-space-4);
}

.color-card {
  display: flex;
  gap: var(--ss-space-3);
  padding: var(--ss-space-3);
  border: 1px solid var(--ss-border);
  border-radius: var(--ss-radius-md);
}

.color-card__swatch {
  flex-shrink: 0;
  width: 48px;
  height: 48px;
  border-radius: var(--ss-radius-sm);
  border: 1px solid var(--ss-divider);
}

.color-card__meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  font-size: var(--ss-font-size-sm);
}

.color-card__name {
  color: var(--ss-text-primary);
  font-weight: var(--ss-font-weight-medium);
}

.color-card__value {
  color: var(--ss-text-secondary);
  font-size: var(--ss-font-size-xs);
}

.color-card__chip {
  align-self: flex-start;
  margin-top: 2px;
  padding: 1px 8px;
  border-radius: var(--ss-radius-pill);
  font-size: var(--ss-font-size-xs);
}

/* 字号 */
.type-list {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-3);
}

.type-row {
  display: grid;
  grid-template-columns: 220px 1fr;
  gap: var(--ss-space-4);
  align-items: baseline;
  padding-bottom: var(--ss-space-3);
  border-bottom: 1px dashed var(--ss-divider);
}

.type-row:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.type-row__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--ss-space-2);
  color: var(--ss-text-secondary);
  font-size: var(--ss-font-size-xs);
}

.type-row__label {
  color: var(--ss-text-primary);
  font-size: var(--ss-font-size-sm);
  font-weight: var(--ss-font-weight-medium);
}

.type-row__sample {
  color: var(--ss-text-primary);
  line-height: var(--ss-line-height-tight);
}

/* 按钮状态 */
.state-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ss-space-6);
  margin-top: var(--ss-space-4);
}

.state-row__group {
  display: flex;
  align-items: center;
  gap: var(--ss-space-2);
  padding: var(--ss-space-3) var(--ss-space-4);
  border: 1px solid var(--ss-divider);
  border-radius: var(--ss-radius-md);
  background: var(--ss-bg-subtle);
}

.state-row__label {
  color: var(--ss-text-secondary);
  font-size: var(--ss-font-size-xs);
}

/* 筛选演示 */
.field {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-1);
  min-width: 220px;
}

.field--wide {
  flex: 1;
  min-width: 260px;
}

.field__label {
  color: var(--ss-text-secondary);
  font-size: var(--ss-font-size-sm);
}

.field__hint {
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-sm);
  line-height: var(--ss-line-height-base);
}

/* 空态 */
.empty-compact-demo {
  margin-top: var(--ss-space-5);
  padding: var(--ss-space-4);
  border: 1px dashed var(--ss-border);
  border-radius: var(--ss-radius-md);
  background: var(--ss-bg-subtle);
}

.empty-compact-demo__title {
  color: var(--ss-text-secondary);
  font-size: var(--ss-font-size-sm);
}

/* 加载与错误 */
.state-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: var(--ss-space-4);
}

.state-card {
  padding: var(--ss-space-4);
  border: 1px solid var(--ss-border);
  border-radius: var(--ss-radius-md);
  background: var(--ss-bg-card);
}

.state-card__title {
  margin-bottom: var(--ss-space-2);
  color: var(--ss-text-secondary);
  font-size: var(--ss-font-size-sm);
}

/* 令牌 */
.token-block + .token-block {
  margin-top: var(--ss-space-6);
}

.token-block__title {
  margin-bottom: var(--ss-space-3);
  font-size: var(--ss-font-size-body-lg);
}

.space-row {
  display: flex;
  align-items: center;
  gap: var(--ss-space-3);
  padding: 3px 0;
  font-size: var(--ss-font-size-xs);
  color: var(--ss-text-secondary);
}

.space-row__bar {
  height: 10px;
  border-radius: 2px;
  background: var(--ss-primary-light);
}

.space-row__label {
  color: var(--ss-text-tertiary);
}

.radius-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ss-space-4);
}

.radius-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  font-size: var(--ss-font-size-xs);
  color: var(--ss-text-secondary);
}

.radius-item__box {
  width: 64px;
  height: 64px;
  border: 1px solid var(--ss-primary);
  background: var(--ss-primary-light);
}

.radius-item__label {
  color: var(--ss-text-tertiary);
}

.shadow-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: var(--ss-space-4);
}

.shadow-item {
  padding: var(--ss-space-4);
  border-radius: var(--ss-radius-md);
  background: var(--ss-bg-card);
}

.shadow-item__name {
  color: var(--ss-text-primary);
  font-size: var(--ss-font-size-sm);
  font-weight: var(--ss-font-weight-medium);
}

.shadow-item__desc {
  margin: 2px 0 6px;
  color: var(--ss-text-secondary);
  font-size: var(--ss-font-size-xs);
}

@media (max-width: 960px) {
  .type-row {
    grid-template-columns: 1fr;
    gap: var(--ss-space-2);
  }
}

@media (max-width: 720px) {
  .design-page__main {
    padding: var(--ss-space-4) var(--ss-space-4) var(--ss-space-8);
  }

  .design-section {
    padding: var(--ss-space-4);
  }

  .state-row {
    gap: var(--ss-space-3);
  }
}
</style>
