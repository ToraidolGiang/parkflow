import { describe, expect, it } from 'vitest'
import overview from './dashboard-overview.json'

describe('corrected dashboard mock', () => {
  it('matches the 21/09/2026 Figma snapshot', () => {
    expect(overview.totalVehicles).toBe(1443)
    expect(overview.overloadedLots).toBe(1)
    expect(overview.recentFlow).toBe(52)
    expect(overview.lots.map((lot) => lot.percentage)).toEqual([113, 89, 92, 66])
  })
})
