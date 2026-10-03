import mockConfig from '../mock/student-config.json'
import mockRecommendation from '../mock/student-recommendation.json'
import type { BuildingId, StudentConfig, StudentRecommendation, StudentSelection } from '../types'
import type { LotStatus } from '../../../shared/types/dashboard'
import { request } from './http'

type Campus = {
  buildings: { id: BuildingId; name: string; nearLot: string; svg: [number, number] }[]
  lots: { id: LotStatus['lotId']; name: string; capacity: number; count: number; gate: string; near: string; svg: [number, number] }[]
  gates: { id: string; name: string; svg: [number, number] }[]
}
export async function getStudentConfig(): Promise<StudentConfig> {
  if (import.meta.env.VITE_USE_MOCK_API === 'true') return structuredClone(mockConfig) as StudentConfig
  const config = await request<Campus>('/api/config')
  return {
    campus: config,
    buildings: config.buildings.map(b => ({ id: b.id, name: b.name, nearbyLot: config.lots.find(l => l.id === b.nearLot)?.name ?? b.nearLot })),
    lots: config.lots.map(l => {
      const percentage = Math.round(l.count / l.capacity * 1000) / 10
      return { lotId: l.id, name: l.name, current: l.count, capacity: l.capacity, percentage, gate: l.gate,
        nearby: config.buildings.find(b => b.id === l.near)?.name ?? l.near,
        status: percentage >= 100 ? 'overloaded' : percentage >= 80 ? 'near-full' : 'available' }
    }),
  }
}
export async function getStudentRecommendation(selection: StudentSelection, sessionId: string): Promise<StudentRecommendation> {
  if (import.meta.env.VITE_USE_MOCK_API === 'true') return structuredClone(mockRecommendation) as StudentRecommendation
  const query = new URLSearchParams({ ...selection, sessionId })
  return request<StudentRecommendation>(`/api/student/recommendation?${query}`)
}
