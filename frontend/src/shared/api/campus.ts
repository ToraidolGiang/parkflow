import mockCampusConfig from '../mock/campus-config.json'
import type { CampusConfig, CampusLookup } from '../types/campus'
import { mockApiEnabled, request } from './http'

export function createCampusLookup(config: CampusConfig): CampusLookup {
  const lots = new Map(config.lots.map((lot) => [lot.id, lot]))
  const gates = new Map(config.gates.map((gate) => [gate.id, gate]))
  const buildings = new Map(config.buildings.map((building) => [building.id, building]))

  return {
    lot: (id) => lots.get(id),
    gate: (id) => gates.get(id),
    building: (id) => buildings.get(id),
    lotName: (id) => lots.get(id)?.name ?? id,
    gateName: (id) => gates.get(id)?.name ?? id,
    buildingName: (id) => buildings.get(id)?.name ?? id,
  }
}

export async function getCampusConfig(signal?: AbortSignal): Promise<CampusConfig> {
  if (mockApiEnabled()) return structuredClone(mockCampusConfig) as unknown as CampusConfig
  return request<CampusConfig>('/api/config', { signal })
}

export async function updateCampusConfig(config: CampusConfig, apiKey: string, signal?: AbortSignal): Promise<CampusConfig> {
  if (mockApiEnabled()) return structuredClone(config)
  return request<CampusConfig>('/api/config', {
    method: 'PUT',
    body: config,
    apiKey,
    signal,
  })
}
