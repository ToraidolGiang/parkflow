import { beforeEach, describe, expect, it, vi } from 'vitest'
import { request } from './http'
import { mapColumns } from './import'

vi.mock('./http', async (load) => {
  const actual = await load<typeof import('./http')>()
  return { ...actual, mockApiEnabled: () => false, request: vi.fn() }
})

describe('mapColumns', () => {
  beforeEach(() => { vi.mocked(request).mockReset() })

  it('posts real headers and sample rows', async () => {
    vi.mocked(request).mockResolvedValue({
      mappings: [{ column: 'ts_in', field: 'time_in', confidence: 0.98 }],
      source: 'gemini',
    })
    const input = { kind: 'swipe_log' as const, headers: ['ts_in'], sampleRows: [['07:00']] }

    await expect(mapColumns(input)).resolves.toMatchObject({ source: 'gemini', fallback: false })
    expect(request).toHaveBeenCalledWith('/api/ai/map-columns', expect.objectContaining({
      method: 'POST', body: input,
    }))
  })

  it('returns a labeled fallback from an AI 503', async () => {
    vi.mocked(request).mockRejectedValue({ name: 'ApiError', message: 'AI lỗi', status: 503, code: 'AI_UNAVAILABLE', fallback: {
      mappings: [{ column: 'ts_in', field: 'time_in', confidence: 0.98 }], source: 'mock',
    } })

    const result = await mapColumns({ kind: 'swipe_log', headers: ['ts_in'], sampleRows: [] })
    expect(result).toMatchObject({ source: 'mock', fallback: true })
  })

  it('does not hide an AI 503 without fallback', async () => {
    const error = { name: 'ApiError', message: 'AI lỗi', status: 503, code: 'AI_UNAVAILABLE' }
    vi.mocked(request).mockRejectedValue(error)
    let caught: unknown
    try { await mapColumns({ kind: 'schedule', headers: ['class_id'], sampleRows: [] }) } catch (cause) { caught = cause }
    expect(caught).toBe(error)
  })
})
