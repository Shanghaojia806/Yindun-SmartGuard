<script setup lang="ts">
import { ElAlert, ElCard, ElTag } from 'element-plus'
import { computed } from 'vue'

import PageHeader from '@/components/PageHeader.vue'
import { requireModule } from '@/router/modules'

/**
 * 管理端模块占位页统一骨架（P03）。
 *
 * 本轮只建立后台框架与路由，A01–A10 的业务功能均未实现，
 * 因此各模块页面统一使用本组件，明确标注"尚未实现"，避免被误认为已完成功能。
 *
 * 每个模块仍保留独立的路由与独立的 View 文件，后续实现业务时直接替换 View 内容即可。
 */
interface Props {
  /** 模块编号，如 A01 */
  moduleCode: string
}

const props = defineProps<Props>()

const moduleInfo = computed(() => requireModule(props.moduleCode))
</script>

<template>
  <div class="module-page">
    <PageHeader
      :eyebrow="`${moduleInfo.code} · ${moduleInfo.group}`"
      :title="moduleInfo.title"
      :description="moduleInfo.description"
    >
      <template #extra>
        <el-tag type="info" effect="plain">待实现</el-tag>
      </template>
    </PageHeader>

    <el-alert
      class="module-page__alert"
      type="warning"
      :closable="false"
      show-icon
      title="当前为页面占位，业务功能尚未实现"
      description="A04—A09 在 P09 仅保留可访问的菜单入口与路由，计划在 P10—P12 或后续轮次实现。本页不发起接口请求，也不展示虚构的业务数据。"
    />

    <el-card class="module-page__card" shadow="never">
      <template #header>
        <div class="module-page__card-header">
          <span>计划能力</span>
          <span class="module-page__hint">以下能力将在后续轮次实现</span>
        </div>
      </template>

      <ul class="module-page__list">
        <li v-for="feature in moduleInfo.plannedFeatures" :key="feature">{{ feature }}</li>
      </ul>
    </el-card>

    <el-card class="module-page__card" shadow="never">
      <template #header>
        <div class="module-page__card-header">
          <span>路由信息</span>
        </div>
      </template>
      <dl class="module-page__meta">
        <div>
          <dt>页面编号</dt>
          <dd>{{ moduleInfo.code }}</dd>
        </div>
        <div>
          <dt>访问路径</dt>
          <dd><code>{{ moduleInfo.path }}</code></dd>
        </div>
        <div>
          <dt>所属分组</dt>
          <dd>{{ moduleInfo.group }}</dd>
        </div>
      </dl>
    </el-card>
  </div>
</template>

<style scoped>
.module-page {
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-5);
}

.module-page__alert {
  border-radius: var(--ss-radius-md);
}

.module-page__card {
  border-radius: var(--ss-radius-lg);
  border-color: var(--ss-border);
}

.module-page__card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ss-space-3);
  font-weight: var(--ss-font-weight-semibold);
  color: var(--ss-primary);
}

.module-page__hint {
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
  font-weight: var(--ss-font-weight-regular);
}

.module-page__list {
  margin: 0;
  padding-left: var(--ss-space-5);
  color: var(--ss-text-secondary);
  font-size: var(--ss-font-size-body);
}

.module-page__list li {
  margin: var(--ss-space-2) 0;
}

.module-page__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ss-space-8);
  margin: 0;
}

.module-page__meta dt {
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
}

.module-page__meta dd {
  margin: 4px 0 0;
  color: var(--ss-text-primary);
  font-size: var(--ss-font-size-body);
}
</style>
