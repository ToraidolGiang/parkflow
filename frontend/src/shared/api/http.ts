import { ApiError, type ApiProblem, type RequestOptions } from '../types/api'

const REQUEST_TIMEOUT_MS = 15_000

export { ApiError }
export type { RequestOptions }

export const mockApiEnabled = () => import.meta.env.VITE_USE_MOCK_API === 'true'

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
