import type { LotStatus } from '../../shared/types/dashboard'

export type BuildingId = 'NHC' | 'TV' | 'GDA' | 'KTT' | 'H1' | 'H2' | 'H3' | 'G3' | 'A1' | 'A2' | 'TN'
export type StudentSelection = { building: BuildingId; arrive: string }
export type StudentBuilding = { id: BuildingId; name: string; nearbyLot: string }
export type StudentConfig = { buildings: StudentBuilding[]; lots: LotStatus[]; campus?: {
  buildings: { id: string; name: string; svg: [number, number] }[]
  lots: { id: string; name: string; svg: [number, number] }[]
  gates: { id: string; name: string; svg: [number, number] }[]
} }
export type StudentRecommendation = {
  lotId: LotStatus['lotId']; lotName: string; gate: 'MAIN' | 'EAST'; gateName: string
  openFrom: string; expectedPct: number; reason: string; planVersion: number | null
  changed: boolean; changeMessage: string | null; updatedAt: string
  route: { mapsUrl: string; path: [number, number][]; steps: string[]; walkMinutes: number }
}
