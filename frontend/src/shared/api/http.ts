import { ApiError, type ApiProblem, type RequestOptions } from '../types/api'

export { ApiError }
export type { RequestOptions }

export async function request<T, TFallback = unknown>(url: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, apiKey, signal } = options
  const headers: Record<string, string> = {
    ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
    ...(apiKey ? { 'X-Api-Key': apiKey } : {}),
  }
  const response = await fetch(url, { method, headers, body: body === undefined ? undefined : JSON.stringify(body), signal })
  if (!response.ok) {
    let problem: ApiProblem<TFallback> = {}
    try { problem = await response.json() as ApiProblem<TFallback> } catch { /* non-JSON error */ }
    throw new ApiError(problem.message ?? `Không thể tải dữ liệu (${response.status})`, response.status, problem.error ?? problem.code, problem.fallback)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}
