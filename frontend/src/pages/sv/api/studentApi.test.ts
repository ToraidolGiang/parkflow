import { afterEach, describe, expect, it, vi } from 'vitest'
import { getStudentConfig, getStudentRecommendation } from './studentApi'

afterEach(() => vi.unstubAllGlobals())

describe('student mock API', () => {
  it('returns the contract example for Nhà hành chính', async () => {
    const result = await getStudentRecommendation({ building: 'NHC', arrive: '07:10' }, 'session-1')
    expect(result).toMatchObject({ lotId: 'C', gate: 'EAST', openFrom: '06:45', expectedPct: 96 })
  })
})

describe('student live API', () => {
  it('sends building, arrival and stable session id to backend', async () => {
    vi.stubEnv('VITE_USE_MOCK_API', 'false')
    const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify({ lotId: 'C1', planVersion: null })))
    vi.stubGlobal('fetch', fetcher)
    expect(await getStudentRecommendation({ building: 'H3', arrive: '06:30' }, 'same-session')).toMatchObject({ lotId: 'C1', planVersion: null })
    const url = new URL(fetcher.mock.calls[0][0] as string, 'http://localhost')
    expect(url.pathname).toBe('/api/student/recommendation')
    expect(Object.fromEntries(url.searchParams)).toEqual({ building: 'H3', arrive: '06:30', sessionId: 'same-session' })
  })
  it('adapts HUCE configuration to the existing selection screen', async () => {
    vi.stubEnv('VITE_USE_MOCK_API', 'false')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      buildings: [{ id: 'H3', name: 'H3', nearLot: 'C1', svg: [300, 100] }], gates: [],
      lots: [{ id: 'C1', name: 'Nhà xe C1', capacity: 300, count: 320, gate: 'EAST', near: 'H3', svg: [300, 150] }],
    }))))
    const config = await getStudentConfig()
    expect(config.buildings[0].nearbyLot).toBe('Nhà xe C1')
    expect(config.lots[0]).toMatchObject({ lotId: 'C1', current: 320, percentage: 106.7, status: 'overloaded' })
  })
  it('surfaces backend failure instead of silently returning mock recommendations', async () => {
    vi.stubEnv('VITE_USE_MOCK_API', 'false')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ message: 'Cần cấu hình HUCE' }), { status: 409 })))
    await expect(getStudentConfig()).rejects.toThrow('Cần cấu hình HUCE')
  })
})
