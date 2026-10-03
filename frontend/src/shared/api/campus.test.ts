import { afterEach, describe, expect, it, vi } from 'vitest'

import type { CampusConfig } from '../types/campus'
import { createCampusLookup, getCampusConfig } from './campus'

const dynamicConfig: CampusConfig = {
  school: 'HUCE',
  thresholds: { yellow: 0.8, over: 1, ceiling: 1.2 },
  gates: [{ id: 'GATE-3', name: 'Cổng Bắc', svg: [50, 60] }],
  buildings: [{ id: 'LAB-NORTH', name: 'Khối Lab', nearLot: 'LAB-X9', svg: [80, 90] }],
  lots: [{
    id: 'LAB-X9',
    name: 'Nhà xe Lab',
    capacity: 40,
    gate: 'GATE-3',
    near: 'LAB-NORTH',
    alternates: [],
    svg: [70, 80],
  }],
  routes: [],
}

describe('campus config', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.unstubAllEnvs()
  })

  it('resolves arbitrary lot, gate and building identifiers', () => {
    const lookup = createCampusLookup(dynamicConfig)

    expect(lookup.lotName('LAB-X9')).toBe('Nhà xe Lab')
    expect(lookup.gateName('GATE-3')).toBe('Cổng Bắc')
    expect(lookup.buildingName('LAB-NORTH')).toBe('Khối Lab')
    expect(lookup.lotName('UNKNOWN')).toBe('UNKNOWN')
  })

  it('loads live config from the backend', async () => {
    vi.stubEnv('VITE_USE_MOCK_API', 'false')
    const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify(dynamicConfig), {
      headers: { 'Content-Type': 'application/json' },
    }))
    vi.stubGlobal('fetch', fetcher)

    await expect(getCampusConfig()).resolves.toEqual(dynamicConfig)
    expect(fetcher).toHaveBeenCalledWith('/api/config', expect.objectContaining({ method: 'GET' }))
  })

  it('returns an isolated config copy in mock mode', async () => {
    vi.stubEnv('VITE_USE_MOCK_API', 'true')

    const first = await getCampusConfig()
    first.lots[0].name = 'Changed by caller'

    const second = await getCampusConfig()
    expect(second.school).toBe('Trường Đại học Xây dựng Hà Nội')
    expect(second.lots[0].name).not.toBe('Changed by caller')
  })
})
