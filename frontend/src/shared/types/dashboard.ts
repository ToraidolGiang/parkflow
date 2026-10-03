export type LotStatus = {
  lotId: 'A' | 'B' | 'C' | 'D'
  name: string
  current: number
  capacity: number
  percentage: number
  gate: string
  nearby: string
  status: 'available' | 'near-full' | 'overloaded'
}

export type FlowPoint = { time: string; value: number }

export type DashboardOverview = {
  date: string
  updatedAt: string
  totalVehicles: number
  overloadedLots: number
  peakWindow: string
  recentFlow: number
  lots: LotStatus[]
  flow: FlowPoint[]
}
