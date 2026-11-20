import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

/**
 * 全局应用状态（P01 仅承载工程信息，不含业务数据）。
 */
export const useAppStore = defineStore('app', () => {
  const appName = ref('银盾智鉴')
  const appTitle = ref(import.meta.env.VITE_APP_TITLE ?? '银盾智鉴 · 管理后台')
  const buildStage = ref('P03 · 路由与导航骨架')

  const fullTitle = computed(() => appTitle.value)

  return {
    appName,
    appTitle,
    buildStage,
    fullTitle,
  }
})
