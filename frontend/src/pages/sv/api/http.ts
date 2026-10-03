export const mockApiEnabled = () => import.meta.env.VITE_USE_MOCK_API === 'true'

export async function request<T>(url: string, body?: unknown, apiKey?: string): Promise<T> {
  const response = await fetch(url, {
    method: body === undefined ? 'GET' : 'POST',
    headers: { ...(body === undefined ? {} : { 'Content-Type': 'application/json' }), ...(apiKey ? { 'X-Api-Key': apiKey } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(15000),
  })
  if (!response.ok) {
    const problem = await response.json().catch(() => ({})) as { message?: string }
    throw new Error(problem.message ?? `Không thể tải dữ liệu (${response.status})`)
  }
  return response.json() as Promise<T>
}
