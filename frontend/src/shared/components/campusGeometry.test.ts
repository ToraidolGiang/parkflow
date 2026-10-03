import { describe, expect, it } from 'vitest'
import geometryJson from '../mock/huce-campus-geometry.json'
import type { CampusConfig, CampusGeometry } from '../types/campus'
import { hasCompleteCampusGeometry, resolveHuceGeometry } from './campusGeometry'

const fallback = geometryJson as CampusGeometry
const partial = {
  school: 'HUCE', thresholds: {}, routes: [],
  buildings: fallback.buildings.map(item => ({ id: item.id, name: item.name, svg: item.svg, nearLot: 'A' })),
  lots: fallback.lots.map(item => ({ ...item, capacity: 1, gate: 'MAIN', near: 'G3' })),
  gates: fallback.gates,
  map: { width: 1000, height: 700 },
} as CampusConfig

describe('HUCE campus geometry', () => {
  it('pins the canonical simulator geometry', () => {
    expect(fallback.map!.viewBox).toEqual([0, 0, 720, 960])
    expect(fallback.map!.roads).toHaveLength(3)
    expect(fallback.map!.courtyard).toEqual({ x: 365, y: 470, width: 135, height: 180 })
    expect(fallback.buildings.find(item => item.id === 'H1')?.footprint).toEqual({ x: 135, y: 310, width: 130, height: 360 })
  })

  it('uses the whole fallback coordinate system for incomplete API geometry', () => {
    const result = resolveHuceGeometry(partial, fallback)
    expect(hasCompleteCampusGeometry(partial)).toBe(false)
    expect(result.map).toEqual(fallback.map)
    expect(result.buildings.find(item => item.id === 'H1')).toEqual(fallback.buildings.find(item => item.id === 'H1'))
  })

  it('overlays API labels without moving fallback geometry', () => {
    const renamed = { ...partial, lots: partial.lots.map(item => item.id === 'A' ? { ...item, name: 'Tên bãi từ API' } : item) }
    const result = resolveHuceGeometry(renamed, fallback)
    expect(result.lots.find(item => item.id === 'A')?.name).toBe('Tên bãi từ API')
    expect(result.lots.find(item => item.id === 'A')?.svg).toEqual([390, 260])
  })

  it('uses complete API geometry atomically', () => {
    const complete = { ...partial, ...fallback } as CampusConfig & CampusGeometry
    expect(hasCompleteCampusGeometry(complete)).toBe(true)
    expect(resolveHuceGeometry(complete, fallback)).toEqual(fallback)
  })
})
