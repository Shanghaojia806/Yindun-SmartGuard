/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 业务接口基地址；留空时使用同源相对路径 */
  readonly VITE_API_BASE_URL?: string
  /** 页面标题 */
  readonly VITE_APP_TITLE?: string
  /** 是否启用 Mock Adapter；'false' 时走真实接口 */
  readonly VITE_USE_MOCK?: string
  /** Mock 请求模拟延迟（毫秒） */
  readonly VITE_MOCK_LATENCY_MS?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
