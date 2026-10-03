import type { CampusConfig } from './campus'

export type ForecastPoint = { t: string; actual: number | null; forecast: number; afterPlan: number }
export type PlanLot = { lotId: string; name: string; count: number; capacity: number; pct: number }
export type PlanStatus = 'draft' | 'approved' | 'auto_applied' | 'pending_approval'
export type PlanViewModel = {
  version: number
  status: PlanStatus
  window: { from: string; to: string }
  reason?: string
  before: PlanLot[]
  after: PlanLot[]
  moves: { from: string; fromName: string; to: string; toName: string; vehicles: number }[]
  gateActions: { gate: string; gateName: string; openFrom: string; normalOpen: string }[]
  excluded: { lotId: string; lotName: string; reason: string }[]
  shortfall: number
  shortfallOptions?: { code: string; label: string }[]
}

export type ForecastOverview = {
  campus: CampusConfig
  date: string
  generatedAt: string
  selectedLot: string
  selectedLotId: string
  availableLots: { id: string; name: string }[]
  peak: { t: string; pct: number }
  shortage: number
  lowestLot?: { lotId: string; name: string; pct: number; gate: string }
  thresholds: { warn: number; design: number; ceiling: number }
  points: ForecastPoint[]
  plan: PlanViewModel | null
  pendingPlan?: PlanViewModel
  qualityFlags: { id: string; severity: 'warning' | 'danger'; message: string }[]
}

export type ApprovalResult = { version: number; status: 'approved' }
export type ApprovalInput = { approvedBy: string; option?: string }
export type Compliance = { version: number; overall: number; byMove: { from: string; to: string; issued: number; arrived: number; compliance: number }[] }
