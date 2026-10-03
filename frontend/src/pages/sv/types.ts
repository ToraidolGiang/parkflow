import type { LotStatus } from '../../shared/types/dashboard'

export type BuildingId = 'NHC' | 'TV' | 'GDA' | 'KTT'
export type StudentSelection = { building: BuildingId; arrive: string }
export type StudentBuilding = { id: BuildingId; name: string; nearbyLot: string }
export type StudentConfig = { buildings: StudentBuilding[]; lots: LotStatus[] }
export type StudentRecommendation = {
  lotId: 'A' | 'B' | 'C' | 'D'; lotName: string; gate: 'MAIN' | 'EAST'; gateName: string
  openFrom: string; expectedPct: number; reason: string; planVersion: number
  changed: boolean; changeMessage: string | null; updatedAt: string
  route: { mapsUrl: string; path: [number, number][]; steps: string[]; walkMinutes: number }
}
