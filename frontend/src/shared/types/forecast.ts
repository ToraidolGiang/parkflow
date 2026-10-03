export type ForecastPoint = {
  t: string
  actual: number | null
  forecast: number
  afterPlan: number
}

export type PlanLot = { lotId: 'A' | 'B' | 'C' | 'D'; count: number; capacity: number; pct: number }

export type PlanStatus = 'draft' | 'approved' | 'auto_applied' | 'pending_approval'

export type ForecastOverview = {
  date: string
  generatedAt: string
  selectedLot: string
  peak: { t: string; pct: number }
  shortage: number
  lowestLot: { lotId: string; pct: number; gate: string }
  thresholds: { warn: number; design: number; ceiling: number }
  points: ForecastPoint[]
  plan: {
    version: number
    status: PlanStatus
    window: { from: string; to: string }
    before: PlanLot[]
    after: PlanLot[]
    moves: { from: string; to: string; vehicles: number }[]
    gateActions: { gate: string; openFrom: string; normalOpen: string }[]
    excluded: { lotId: string; reason: string }[]
    shortfall: number
  }
  qualityFlags: { id: string; severity: 'warning' | 'danger'; message: string }[]
}

export type ApprovalResult = { version: number; status: 'approved' }
