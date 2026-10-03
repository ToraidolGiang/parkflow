import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { getDashboardBootstrap, useDashboardOverview } from './dashboard'

const config = {
  school: 'HUCE',
  thresholds: { yellow: 0.8, warn: 0.9, over: 1, ceiling: 1.2 },
  gates: [{ id: 'GATE-3', name: 'Cổng Bắc', svg: [50, 60] }],
  buildings: [{ id: 'LAB-NORTH', name: 'Khối Lab', nearLot: 'LAB-X9', svg: [80, 90] }],
  lots: [{ id: 'LAB-X9', name: 'Nhà xe Lab', capacity: 40, max: 48, gate: 'GATE-3', near: 'LAB-NORTH', alternates: [], svg: [70, 80] }],
  routes: [],
}

const status = {
  clock: { mode: 'realtime', now: '2026-10-01T08:00:00+07:00' },
  updatedAt: '2026-10-01T08:00:00+07:00',
  lots: [{
    lotId: 'LAB-X9', name: 'outdated backend label', count: 20, capacity: 40,
    maxCapacity: 48, pct: 50, level: 'yellow', gate: 'GATE-3', nearBuilding: 'LAB-NORTH',
    lastEventAt: null, dataFlag: 'estimated',
  }],
  totals: { count: 20, overloadedLots: 0, inflowLast15: 4 },
}

const forecast = {
  lotId: 'LAB-X9', date: '2026-10-01', k: 1, generatedAt: '2026-10-01T08:00:00+07:00',
  peak: { t: '08:30', count: 38, pct: 95 }, points: [],
  thresholds: { yellow: 80, over: 100, critical: 120 },
}

function json(value: unknown) {
  return new Response(JSON.stringify(value), { headers: { 'Content-Type': 'application/json' } })
}

function successfulFetch(url: string | URL | Request) {
  const path = String(url)
  if (path === '/api/config') return Promise.resolve(json(config))
  if (path === '/api/lots/status') return Promise.resolve(json(status))
  if (path === '/api/quality/flags') return Promise.resolve(json({ flags: [{ id: 'q1', lotId: 'LAB-X9', type: 'missing_out', since: status.clock.now, message: 'Thiếu lượt ra' }] }))
  if (path.startsWith('/api/forecast?')) return Promise.resolve(json(forecast))
  return Promise.reject(new Error(`Unexpected URL: ${path}`))
}

describe('dashboard API', () => {
  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
    vi.unstubAllEnvs()
  })

  it('maps arbitrary IDs and keeps data quality separate from occupancy state', async () => {
    vi.stubEnv('VITE_USE_MOCK_API', 'false')
    vi.stubGlobal('fetch', vi.fn(successfulFetch))

    const result = await getDashboardBootstrap()

    expect(result.lots[0]).toMatchObject({
      lotId: 'LAB-X9',
      name: 'Nhà xe Lab',
      gate: 'Cổng Bắc',
      nearby: 'Khối Lab',
      dataFlag: 'estimated',
      sourceLabel: 'Số ước tính',
      status: 'available',
    })
    expect(result.qualityFlags).toHaveLength(1)
    expect(result.peakWindow).toBe('08:30')
  })

  it('waits for an in-flight poll and stops polling after unmount', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    vi.stubEnv('VITE_USE_MOCK_API', 'false')
    let statusCalls = 0
    let releasePoll: ((response: Response) => void) | undefined
    const fetcher = vi.fn((url: string | URL | Request) => {
      if (String(url) === '/api/lots/status') {
        statusCalls += 1
        if (statusCalls === 2) return new Promise<Response>((resolve) => { releasePoll = resolve })
      }
      return successfulFetch(url)
    })
    vi.stubGlobal('fetch', fetcher)

    const { result, unmount } = renderHook(() => useDashboardOverview())
    await waitFor(() => expect(result.current.data).toBeDefined())
    expect(result.current.data?.lots[0].name).toBe('Nhà xe Lab')

    await act(async () => { await vi.advanceTimersByTimeAsync(5_000) })
    expect(statusCalls).toBe(2)
    await act(async () => { await vi.advanceTimersByTimeAsync(10_000) })
    expect(statusCalls).toBe(2)

    unmount()
    releasePoll?.(json(status))
    await act(async () => { await vi.advanceTimersByTimeAsync(5_000) })
    expect(statusCalls).toBe(2)
  })
})
