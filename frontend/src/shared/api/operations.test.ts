import { afterEach, describe, expect, it, vi } from 'vitest'
import { getGateStatus, getHealth } from './operations'

describe('operations API adapter', () => {
  afterEach(() => { vi.unstubAllGlobals(); vi.unstubAllEnvs() })

  it('loads gate status from the live endpoint', async () => {
    vi.stubEnv('VITE_USE_MOCK_API', 'false')
    const response = { clock: { mode: 'realtime', now: '2026-10-01T08:00:00+07:00' }, gates: [{ gate: 'MAIN', name: 'Cổng chính', capacityPer15: 200, inflowLast15: 20, utilization: .1, level: 'green', source: 'estimated', expectedIn: [], action: null }] }
    const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify(response), { status: 200 }))
    vi.stubGlobal('fetch', fetcher)
    await expect(getGateStatus()).resolves.toEqual(response)
    expect(fetcher).toHaveBeenCalledWith('/api/gates/status', expect.objectContaining({ method: 'GET' }))
  })

  it('loads backend health from the live endpoint', async () => {
    vi.stubEnv('VITE_USE_MOCK_API', 'false')
    const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify({ status: 'ok', db: 'up' }), { status: 200 }))
    vi.stubGlobal('fetch', fetcher)
    await expect(getHealth()).resolves.toEqual({ status: 'ok', db: 'up' })
    expect(fetcher).toHaveBeenCalledWith('/api/health', expect.objectContaining({ method: 'GET' }))
  })
})
