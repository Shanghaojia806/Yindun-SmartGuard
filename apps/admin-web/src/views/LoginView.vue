<script setup lang="ts">
import { Lock, User } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ADMIN_HOME_PATH } from '@/router/modules'
import { useSessionStore } from '@/stores/session'

/**
 * 管理后台登录占位页（P03）。
 *
 * 【重要】当前未接入真实身份认证：
 * - 不请求登录接口，不校验密码，输入内容仅在本地用于界面展示；
 * - 登录成功只是在本地记录一个开发用会话，刷新后保持、关闭标签页失效；
 * - 不得将本页面描述为真实安全认证。
 */

const router = useRouter()
const route = useRoute()
const sessionStore = useSessionStore()

const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive({
  username: '',
  password: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

const trustPoints = [
  '风险识别在用户设备本机完成，管理端不接入原始视频帧',
  '风险反馈仅保留脱敏后的判定结果与类型标签',
  '模型与规则的每次发布均保留版本与操作记录',
]

function resolveRedirect(): string {
  const redirect = route.query.redirect
  return typeof redirect === 'string' && redirect.startsWith('/') ? redirect : ADMIN_HOME_PATH
}

function enterAdmin(displayName: string) {
  sessionStore.signIn(displayName)
  ElMessage({
    type: 'success',
    message: '已进入管理后台（本地模拟登录，非真实身份认证）',
  })
  void router.replace(resolveRedirect())
}

async function handleSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  // 真实实现应在此调用认证接口；当前仅做本地模拟。
  enterAdmin(form.username)
  submitting.value = false
}

/** 开发测试入口：不校验任何凭据，直接进入后台。 */
function handleDevEntry() {
  enterAdmin('演示管理员')
}

const isDev = import.meta.env.DEV
</script>

<template>
  <div class="login-page">
    <section class="login-intro">
      <div class="login-intro__brand">
        <div class="login-intro__mark" aria-hidden="true">盾</div>
        <div>
          <p class="login-intro__name">银盾智鉴</p>
          <p class="login-intro__tagline">管理后台 · 风险识别与模型运营</p>
        </div>
      </div>

      <div class="login-intro__body">
        <h1 class="login-intro__title">用可解释的风险判定<br />守护家人的每一次观看</h1>
        <p class="login-intro__desc">
          管理后台负责数据集、训练任务、模型版本、风险规则与知识库的统一管理，
          并为家属端提供脱敏后的风险反馈依据。
        </p>

        <ul class="login-intro__points">
          <li v-for="point in trustPoints" :key="point">{{ point }}</li>
        </ul>
      </div>

      <p class="login-intro__footer">V1.3 架构 · P09 阶段仍为本地模拟登录</p>
    </section>

    <section class="login-panel">
      <div class="login-card">
        <header class="login-card__header">
          <h2 class="login-card__title">登录管理后台</h2>
          <p class="login-card__desc">请使用管理员账号登录，或通过下方开发测试入口进入。</p>
        </header>

        <el-alert
          class="login-card__notice"
          type="warning"
          :closable="false"
          show-icon
          title="当前尚未接入真实身份认证"
          description="本页为开发阶段的登录占位页：不连接任何认证服务，不校验账号密码，也不产生真实会话凭证。输入内容仅用于界面展示。"
        />

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          size="large"
          @submit.prevent="handleSubmit"
        >
          <el-form-item label="用户名" prop="username">
            <el-input
              v-model="form.username"
              :prefix-icon="User"
              placeholder="请输入用户名"
              autocomplete="username"
              clearable
            />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              :prefix-icon="Lock"
              placeholder="请输入密码"
              autocomplete="current-password"
              show-password
            />
          </el-form-item>

          <el-button
            class="login-card__submit"
            type="primary"
            size="large"
            :loading="submitting"
            native-type="submit"
          >
            登录管理后台
          </el-button>
        </el-form>

        <div class="login-card__divider"><span>开发测试</span></div>

        <el-button class="login-card__dev" size="large" @click="handleDevEntry">
          进入管理后台（跳过校验）
        </el-button>
        <p class="login-card__dev-hint">
          开发测试入口不校验任何凭据，仅用于开发阶段验证后台路由与菜单框架。
        </p>

        <router-link v-if="isDev" class="login-card__link" to="/dev/design-system">
          查看设计规范预览
        </router-link>
      </div>

      <p class="login-panel__footer">银盾智鉴 · 管理后台 · 仅限内部使用</p>
    </section>
  </div>
</template>

<style scoped>
.login-page {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 1fr);
  min-height: 100%;
  background: var(--ss-bg-page);
}

/* ===== 左侧品牌区 ===== */
.login-intro {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: var(--ss-space-8);
  padding: 48px 56px;
  background: var(--ss-primary);
  color: var(--ss-text-inverse);
}

.login-intro__brand {
  display: flex;
  align-items: center;
  gap: var(--ss-space-4);
}

.login-intro__mark {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  border: 1px solid rgba(255, 255, 255, 0.32);
  border-radius: var(--ss-radius-lg);
  background: rgba(255, 255, 255, 0.14);
  font-size: var(--ss-font-size-h3);
  font-weight: var(--ss-font-weight-semibold);
}

.login-intro__name {
  font-size: var(--ss-font-size-h3);
  font-weight: var(--ss-font-weight-semibold);
  letter-spacing: 0.06em;
}

.login-intro__tagline {
  margin-top: var(--ss-space-1);
  color: rgba(255, 255, 255, 0.72);
  font-size: var(--ss-font-size-sm);
}

.login-intro__body {
  max-width: 30em;
}

.login-intro__title {
  color: var(--ss-text-inverse);
  font-size: var(--ss-font-size-display);
  font-weight: var(--ss-font-weight-semibold);
  line-height: var(--ss-line-height-tight);
}

.login-intro__desc {
  margin-top: var(--ss-space-5);
  color: rgba(255, 255, 255, 0.78);
  font-size: var(--ss-font-size-body-lg);
}

.login-intro__points {
  margin: var(--ss-space-6) 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-3);
}

.login-intro__points li {
  position: relative;
  padding-left: var(--ss-space-5);
  color: rgba(255, 255, 255, 0.86);
  font-size: var(--ss-font-size-body);
}

.login-intro__points li::before {
  content: '';
  position: absolute;
  top: 0.62em;
  left: 0;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--ss-safe);
}

.login-intro__footer {
  color: rgba(255, 255, 255, 0.56);
  font-size: var(--ss-font-size-xs);
  letter-spacing: 0.04em;
}

/* ===== 右侧登录区 ===== */
.login-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--ss-space-6);
  padding: 48px var(--ss-space-6);
}

.login-card {
  width: 100%;
  max-width: 400px;
  display: flex;
  flex-direction: column;
  gap: var(--ss-space-4);
}

.login-card__title {
  font-size: var(--ss-font-size-h2);
  font-weight: var(--ss-font-weight-semibold);
}

.login-card__desc {
  margin-top: var(--ss-space-2);
  color: var(--ss-text-secondary);
  font-size: var(--ss-font-size-sm);
}

.login-card__notice {
  border-radius: var(--ss-radius-md);
}

.login-card__submit {
  width: 100%;
  margin-top: var(--ss-space-2);
}

.login-card__divider {
  display: flex;
  align-items: center;
  gap: var(--ss-space-3);
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
}

.login-card__divider::before,
.login-card__divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--ss-divider);
}

.login-card__dev {
  width: 100%;
}

.login-card__dev-hint {
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
  line-height: var(--ss-line-height-base);
}

.login-card__link {
  align-self: flex-start;
  font-size: var(--ss-font-size-sm);
}

.login-panel__footer {
  color: var(--ss-text-tertiary);
  font-size: var(--ss-font-size-xs);
}

@media (max-width: 1024px) {
  .login-page {
    grid-template-columns: 1fr;
  }

  .login-intro {
    gap: var(--ss-space-6);
    padding: 32px var(--ss-space-6);
  }

  .login-intro__title {
    font-size: var(--ss-font-size-h2);
  }

  .login-intro__points,
  .login-intro__footer {
    display: none;
  }

  .login-panel {
    padding: 32px var(--ss-space-6) 48px;
  }
}
</style>
