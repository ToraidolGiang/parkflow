import { ApiError, type ApiProblem, type RequestOptions } from '../types/api'

const REQUEST_TIMEOUT_MS = 15_000
const GET_CACHE_TTL_MS = 2_000
const getCache = new Map<string, { expires: number; value: unknown }>()
const getInflight = new Map<string, Promise<unknown>>()

export { ApiError }
export type { RequestOptions }

export const mockApiEnabled = () => import.meta.env.VITE_USE_MOCK_API === 'true'

/** Phát khi một lệnh gọi (không phải đăng nhập) trả 401: phiên hết hạn hoặc bị thu hồi, AuthProvider đưa về trang đăng nhập. */
export const UNAUTHORIZED_EVENT = 'parkflow:unauthorized'

function requestSignal(signal?: AbortSignal) {
  const timeoutSignal = AbortSignal.timeout(REQUEST_TIMEOUT_MS)
  return signal ? AbortSignal.any([signal, timeoutSignal]) : timeoutSignal
}

async function readProblem<TFallback>(response: Response): Promise<ApiProblem<TFallback>> {
  try {
    return await response.json() as ApiProblem<TFallback>
  } catch {
    return {}
  }
}

export async function request<T, TFallback = unknown>(
  url: string,
  options: RequestOptions = {},
): Promise<T> {
  const { method = 'GET', body, apiKey, signal } = options
  const headers: Record<string, string> = {
    ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
    ...(apiKey ? { 'X-Api-Key': apiKey } : {}),
  }

  const response = await fetch(url, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: requestSignal(signal),
  })

  if (!response.ok) {
    if (response.status === 401 && !url.startsWith('/api/auth/')) window.dispatchEvent(new Event(UNAUTHORIZED_EVENT))
    const problem = await readProblem<TFallback>(response)
    throw new ApiError<TFallback>(
      problem.message ?? `Không thể tải dữ liệu (${response.status})`,
      response.status,
      problem.error ?? problem.code,
      problem.fallback,
    )
  }

  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

/** Shares short-lived read requests between pages; mutations explicitly invalidate their URL. */
export async function requestCached<T>(url: string, options: RequestOptions = {}): Promise<T> {
  if (options.method && options.method !== 'GET') return request<T>(url, options)
  const cached = getCache.get(url)
  if (cached && cached.expires > Date.now()) return cached.value as T
  const running = getInflight.get(url)
  if (running) return running as Promise<T>
  const promise = request<T>(url, options).then((value) => {
    getCache.set(url, { value, expires: Date.now() + GET_CACHE_TTL_MS })
    return value
  }).finally(() => getInflight.delete(url))
  getInflight.set(url, promise)
  return promise
}

export function invalidateRequestCache(...urls: string[]) {
  for (const url of urls) getCache.delete(url)
}

export function clearRequestCache() {
  getCache.clear()
  getInflight.clear()
}
