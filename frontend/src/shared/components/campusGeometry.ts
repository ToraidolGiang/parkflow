import type { CampusConfig, CampusGeometry, PartialCampusGeometry } from '../types/campus'

type ConfigWithGeometry = CampusConfig & PartialCampusGeometry

export function hasCompleteCampusGeometry(config: PartialCampusGeometry): boolean {
  return Boolean(
    config.map?.viewBox?.length === 4
      && config.map.roads?.length
      && config.map.courtyard
      && config.buildings?.length && config.buildings.every(item => item.svg && item.footprint)
      && config.lots?.length && config.lots.every(item => item.svg)
      && config.gates?.length && config.gates.every(item => item.svg),
  )
}

function namesById(items: Array<{ id: string; name: string }> | undefined) {
  return new Map(items?.map(item => [item.id, item.name]) ?? [])
}

export function resolveHuceGeometry(config: ConfigWithGeometry | undefined, fallback: CampusGeometry): CampusGeometry {
  if (config && hasCompleteCampusGeometry(config)) {
    return {
      buildings: config.buildings!.map(item => ({ id: item.id, name: item.name, svg: item.svg!, footprint: item.footprint! })),
      lots: config.lots!.map(item => ({ id: item.id, name: item.name, svg: item.svg! })),
      gates: config.gates!.map(item => ({ id: item.id, name: item.name, svg: item.svg! })),
      map: config.map as CampusGeometry['map'],
    }
  }

  const buildingNames = namesById(config?.buildings)
  const lotNames = namesById(config?.lots)
  const gateNames = namesById(config?.gates)
  return {
    buildings: fallback.buildings.map(item => ({ ...item, name: buildingNames.get(item.id) ?? item.name })),
    lots: fallback.lots.map(item => ({ ...item, name: lotNames.get(item.id) ?? item.name })),
    gates: fallback.gates.map(item => ({ ...item, name: gateNames.get(item.id) ?? item.name })),
    map: fallback.map,
  }
}
