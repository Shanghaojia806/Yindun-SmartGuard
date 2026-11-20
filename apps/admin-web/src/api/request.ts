import { ApiError } from './http'
import type { DataSource } from './types'

/**
 * 数据源开关与 Mock 调用入口（P09）。
 *
 * 约定：
 * - 页面只调用 src/api/*.ts 中的 Service 函数，不关心数据来自 Mock 还是真实接口；
 * - VITE_USE_MOCK 为 false 时，Service 自动切换到 src/api/http.ts 的真实请求；
 * - 所有 Mock 返回值都经过本文件，便于统一延迟、统一日志、统一失败模拟。
 *
 * 演示数据标识由页面显式展示，Mock 层不伪造"真实运营指标"。
 */

export const IS_MOCK: boolean = import.meta.env.VITE_USE_MOCK !== 'false'

export const DATA_SOURCE: DataSource = IS_MOCK ? 'mock' : 'real'

/** 页面上统一的演示数据文案，避免各页面写法不一致 */
export const MOCK_NOTICE = '演示数据'

const DEFAULT_LATENCY_MS = 260

function resolveLatency(): number {
  const raw = import.meta.env.VITE_MOCK_LATENCY_MS
  const parsed = raw ? Number(raw) : Number.NaN
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : DEFAULT_LATENCY_MS
}

const LATENCY_MS = resolveLatency()

/** 待执行的"模拟失败"标记：用于演示 Error 状态 */
let failurePending = false

/** 设置：下一次 Mock 请求抛出 503，便于验证页面错误态 */
export function simulateNextRequestFailure(): void {
  failurePending = true
}

/** 清除尚未触发的模拟失败 */
export function clearSimulatedFailure(): void {
  failurePending = false
}

export function hasSimulatedFailure(): boolean {
  return failurePending
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => {
    window.setTimeout(resolve, ms)
  })
}

/**
 * 执行一次 Mock 调用。
 *
 * 传入工厂函数而不是直接传结果，确保 404 / 数据校验等异常也发生在
 * Mock 调用内部，与真实请求的失败路径保持一致。
 */
export async function mockCall<T>(
  factory: () => T | Promise<T>,
  options: { latencyMs?: number } = {},
): Promise<T> {
  await delay(options.latencyMs ?? LATENCY_MS)

  if (failurePending) {
    failurePending = false
    throw new ApiError(
      503,
      'MOCK_SIMULATED_FAILURE',
      '演示模式：已模拟一次接口失败，请点击重试',
      `mock-${Date.now()}`,
    )
  }

  const result = await factory()
  console.info('[api] 演示数据（Mock）：本次响应不代表真实业务数据')
  return structuredClone(result)
}
