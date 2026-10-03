export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE'
export type RequestOptions = { method?: HttpMethod; body?: unknown; apiKey?: string; signal?: AbortSignal }
export type ApiProblem<TFallback = unknown> = { error?: string; code?: string; message?: string; fallback?: TFallback }
export class ApiError<TFallback = unknown> extends Error {
  constructor(message: string, readonly status: number, readonly code?: string, readonly fallback?: TFallback) {
    super(message)
    this.name = 'ApiError'
  }
}
