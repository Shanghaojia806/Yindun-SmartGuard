import type { ApiEnvelope, ApiErrorBody } from './types'

/**
 * 统一请求层。
 *
 * 设计约束（见 docs/requirements/PRODUCT_BASELINE.md 3.4）：
 * - 视图层不得直接拼接请求，所有调用收敛在 src/api。
 * - 返回体统一解包 envelope.data。
 * - Mock 响应（X-Data-Source: mock）会输出显式告警，避免演示数据被当成真实数据。
 */

const BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? ''
const DEFAULT_TIMEOUT_MS = 15000

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly traceId?: string

  constructor(status: number, code: string, message: string, traceId?: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.traceId = traceId
  }
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  query?: Record<string, string | number | boolean | undefined>
  body?: unknown
  headers?: Record<string, string>
  timeoutMs?: number
}

function buildUrl(path: string, query?: RequestOptions['query']): string {
  const url = `${BASE_URL}${path}`
  if (!query) {
    return url
  }
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined) {
      search.append(key, String(value))
    }
  }
  const qs = search.toString()
  return qs ? `${url}?${qs}` : url
}

function parseJson(text: string): unknown {
  if (!text) {
    return null
  }
  try {
    return JSON.parse(text) as unknown
  } catch {
    return text
  }
}

function toErrorBody(payload: unknown, status: number): ApiErrorBody {
  if (payload !== null && typeof payload === 'object' && 'error' in payload) {
    const candidate = (payload as { error: unknown }).error
    if (candidate !== null && typeof candidate === 'object') {
      const record = candidate as Record<string, unknown>
      return {
        code: typeof record.code === 'string' ? record.code : 'UNKNOWN_ERROR',
        message: typeof record.message === 'string' ? record.message : '请求失败',
        traceId: typeof record.traceId === 'string' ? record.traceId : undefined,
      }
    }
  }
  return {
    code: `HTTP_${status}`,
    message: typeof payload === 'string' && payload ? payload : '请求失败',
  }
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const controller = new AbortController()
  const timeoutId = window.setTimeout(() => controller.abort(), options.timeoutMs ?? DEFAULT_TIMEOUT_MS)

  try {
    const response = await fetch(buildUrl(path, options.query), {
      method: options.method ?? 'GET',
      headers: {
        Accept: 'application/json',
        ...(options.body === undefined ? {} : { 'Content-Type': 'application/json' }),
        ...options.headers,
      },
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: controller.signal,
    })

    if (response.headers.get('X-Data-Source') === 'mock') {
      console.warn(`[api] 演示数据（Mock）：${options.method ?? 'GET'} ${path}`)
    }

    const payload = parseJson(await response.text())

    if (!response.ok) {
      const body = toErrorBody(payload, response.status)
      throw new ApiError(response.status, body.code, body.message, body.traceId)
    }

    if (payload !== null && typeof payload === 'object' && 'data' in payload) {
      return (payload as ApiEnvelope<T>).data
    }
    return payload as T
  } finally {
    window.clearTimeout(timeoutId)
  }
}

export const http = {
  get: <T>(path: string, options: Omit<RequestOptions, 'method' | 'body'> = {}) =>
    request<T>(path, { ...options, method: 'GET' }),
  post: <T>(path: string, body?: unknown, options: Omit<RequestOptions, 'method' | 'body'> = {}) =>
    request<T>(path, { ...options, method: 'POST', body }),
  patch: <T>(path: string, body?: unknown, options: Omit<RequestOptions, 'method' | 'body'> = {}) =>
    request<T>(path, { ...options, method: 'PATCH', body }),
  put: <T>(path: string, body?: unknown, options: Omit<RequestOptions, 'method' | 'body'> = {}) =>
    request<T>(path, { ...options, method: 'PUT', body }),
  delete: <T>(path: string, options: Omit<RequestOptions, 'method' | 'body'> = {}) =>
    request<T>(path, { ...options, method: 'DELETE' }),
}
