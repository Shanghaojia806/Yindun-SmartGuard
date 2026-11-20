import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

/**
 * 管理端会话状态（本地模拟）。
 *
 * 【重要】当前未接入真实身份认证：
 * - 不请求任何登录接口，不校验密码；
 * - 仅在 sessionStorage 中记录一个开发用会话，用于刷新后保持后台入口；
 * - 后续接入真实认证时，只需替换本文件中的 [signIn] 与 [signOut] 实现，
 *   页面与路由守卫无需改动。
 */

const SESSION_STORAGE_KEY = 'silver-shield-admin-session'

export interface AdminSession {
  /** 展示用名称 */
  displayName: string
  /** 角色占位：真实系统由后端下发 */
  roleLabel: string
  /** 登录时间（本地模拟） */
  signInAt: string
}

function readStoredSession(): AdminSession | null {
  try {
    const raw = sessionStorage.getItem(SESSION_STORAGE_KEY)
    return raw ? (JSON.parse(raw) as AdminSession) : null
  } catch {
    return null
  }
}

export const useSessionStore = defineStore('session', () => {
  const session = ref<AdminSession | null>(readStoredSession())

  const isAuthenticated = computed(() => session.value !== null)
  const displayName = computed(() => session.value?.displayName ?? '未登录')
  const roleLabel = computed(() => session.value?.roleLabel ?? '—')

  /**
   * 本地模拟登录。
   *
   * 真实实现应在此调用认证接口并保存服务端签发的会话凭证；
   * 当前仅保存用户名用于界面展示。
   */
  function signIn(username: string) {
    const next: AdminSession = {
      displayName: username.trim() || '演示管理员',
      roleLabel: '系统管理员（模拟）',
      signInAt: new Date().toISOString(),
    }
    session.value = next
    try {
      sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(next))
    } catch {
      // 隐私模式下 sessionStorage 可能不可用，此时仅保留内存会话
    }
  }

  function signOut() {
    session.value = null
    try {
      sessionStorage.removeItem(SESSION_STORAGE_KEY)
    } catch {
      // 忽略存储异常
    }
  }

  return {
    session,
    isAuthenticated,
    displayName,
    roleLabel,
    signIn,
    signOut,
  }
})
