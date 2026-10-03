import { afterEach, describe, expect, it, vi } from 'vitest'

import { ApiError, mockApiEnabled, request } from './http'

describe('shared HTTP client', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.unstubAllEnvs()
  })

  it.each([
    ['GET', undefined],
    ['DELETE', undefined],
  ] as const)('sends an explicit %s request without a JSON content header', async (method, body) => {
    const fetcher = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ ok: true }), {
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetcher)

    await request('/api/example', { method, body })

    expect(fetcher).toHaveBeenCalledWith('/api/example', expect.objectContaining({
      method,
      body: undefined,
      headers: {},
    }))
  })

  it('sends a JSON body and optional API key', async () => {
    const fetcher = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ ok: true }), {
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetcher)

    await request('/api/example', {
      method: 'POST',
      body: { value: 1 },
      apiKey: 'demo-key',
    })

    expect(fetcher).toHaveBeenCalledWith('/api/example', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ value: 1 }),
      headers: {
        'Content-Type': 'application/json',
        'X-Api-Key': 'demo-key',
      },
    }))
  })

  it('preserves a structured 503 fallback in ApiError', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      error: 'AI_UNAVAILABLE',
      message: 'AI tạm thời không dùng được',
      fallback: { mappings: [], source: 'mock' },
    }), {
      status: 503,
      headers: { 'Content-Type': 'application/json' },
    })))

    await expect(request('/api/ai/map-columns')).rejects.toMatchObject({
      name: 'ApiError',
      status: 503,
      code: 'AI_UNAVAILABLE',
      fallback: { mappings: [], source: 'mock' },
    })
  })

  it('uses a safe fallback message when an error response is not JSON', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('gateway down', {
      status: 502,
      headers: { 'Content-Type': 'text/plain' },
    })))

    await expect(request('/api/example')).rejects.toEqual(
      expect.objectContaining<ApiError>({
        name: 'ApiError',
        status: 502,
        message: 'Không thể tải dữ liệu (502)',
      }),
    )
  })

  it('combines the caller signal with the request timeout signal', async () => {
    const fetcher = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ ok: true }), {
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetcher)
    const controller = new AbortController()

    await request('/api/example', { signal: controller.signal })

    const init = fetcher.mock.calls[0]?.[1] as RequestInit
    expect(init.signal).toBeInstanceOf(AbortSignal)
    controller.abort()
    expect(init.signal?.aborted).toBe(true)
  })

  it('enables mock mode only for the literal true value', () => {
    vi.stubEnv('VITE_USE_MOCK_API', 'true')
    expect(mockApiEnabled()).toBe(true)

    vi.stubEnv('VITE_USE_MOCK_API', 'TRUE')
    expect(mockApiEnabled()).toBe(false)
  })
})
