import { afterEach, describe, expect, it, vi } from 'vitest'
import { approvePlan, getForecastOverview } from './forecast'

const config = { school: 'HUCE', thresholds: { yellow: .8, over: 1, ceiling: 1.2 }, gates: [{ id: 'GATE-3', name: 'Cổng Bắc' }], buildings: [], lots: [{ id: 'LAB-X9', name: 'Nhà xe Lab', capacity: 40, gate: 'GATE-3', near: 'LAB' }], routes: [] }
const body = { version: 3, status: 'approved', createdAt: '2026-10-01T18:00:00+07:00', window: { from: '07:00', to: '08:00' }, reason: 'Điều phối', before: [{ lotId: 'LAB-X9', count: 20, pct: 50 }], after: [{ lotId: 'LAB-X9', count: 18, pct: 45 }], moves: [], excluded: [], shortfall: 0, gateActions: [], envelope: { maxMoveTotal: 10, maxTargetPct: 1, allowedDest: [] } }
const forecast = { lotId: 'LAB-X9', date: '2026-10-02', k: 1, generatedAt: '2026-10-01T18:01:00+07:00', peak: { t: '07:00', count: 20, pct: 50 }, points: [{ t: '07:00', actual: 8, forecast: 20, afterPlan: 18 }], thresholds: { yellow: 80, over: 100, critical: 120 } }
const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })

function mockFetch(plan: unknown, planStatus = 200) {
  return vi.fn((url: string | URL | Request) => {
    const path = String(url)
    if (path === '/api/config') return Promise.resolve(json(config))
    if (path === '/api/plan/current') return Promise.resolve(json(plan, planStatus))
    if (path === '/api/quality/flags') return Promise.resolve(json({ flags: [] }))
    if (path.includes('/api/forecast?lotId=LAB-X9')) return Promise.resolve(json(forecast))
    return Promise.reject(new Error(`Unexpected URL: ${path}`))
  })
}

describe('forecast API adapter', () => {
  afterEach(() => { vi.unstubAllGlobals(); vi.unstubAllEnvs() })

  it('supports string lot IDs, pending plans and converts vehicle counts to percentages', async () => {
    vi.stubEnv('VITE_USE_MOCK_API', 'false')
    vi.stubGlobal('fetch', mockFetch({ ...body, pending: { ...body, version: 4, status: 'pending_approval' } }))
    const result = await getForecastOverview('LAB-X9')
    expect(result.selectedLot).toBe('Nhà xe Lab')
    expect(result.pendingPlan?.version).toBe(4)
    expect(result.points[0]).toEqual({ t: '07:00', actual: 20, forecast: 50, afterPlan: 45 })
  })

  it('uses the applied plan when pending is null', async () => {
    vi.stubEnv('VITE_USE_MOCK_API', 'false')
    vi.stubGlobal('fetch', mockFetch({ ...body, pending: null }))
    const result = await getForecastOverview('LAB-X9')
    expect(result.plan?.version).toBe(3)
    expect(result.pendingPlan).toBeUndefined()
  })

  it('keeps forecast available when plan/current returns NO_PLAN', async () => {
    vi.stubEnv('VITE_USE_MOCK_API', 'false')
    vi.stubGlobal('fetch', mockFetch({ error: 'NO_PLAN', message: 'Chưa có kế hoạch' }, 404))
    const result = await getForecastOverview('LAB-X9')
    expect(result.plan).toBeNull()
    expect(result.points[0].forecast).toBe(50)
  })

  it('posts the reviewer and selected shortfall option', async () => {
    vi.stubEnv('VITE_USE_MOCK_API', 'false')
    const fetcher = vi.fn().mockResolvedValue(json({ version: 4, status: 'approved' }))
    vi.stubGlobal('fetch', fetcher)
    await expect(approvePlan(4, { approvedBy: 'Tuan Anh', option: 'ACCEPT_HIGHER' })).resolves.toEqual({ version: 4, status: 'approved' })
    expect(fetcher).toHaveBeenCalledWith('/api/plan/4/approve', expect.objectContaining({ method: 'POST', body: JSON.stringify({ approvedBy: 'Tuan Anh', option: 'ACCEPT_HIGHER' }) }))
  })
})
