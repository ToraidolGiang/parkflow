import type { CampusConfig } from './campus'

export type DataFlag = 'ok' | 'suspect' | 'signal_lost' | 'estimated'

export type QualityFlag = {
  id: string
  lotId: string
  type: 'missing_out' | 'implausible_count' | 'signal_lost' | string
  since: string
  message: string
}

export type LotStatus = {
  lotId: string
  name: string
  current: number
  capacity: number
  percentage: number
  gate: string
  nearby: string
  status: 'available' | 'near-full' | 'overloaded'
  dataFlag?: DataFlag
  sourceLabel?: string
  lastEventAt?: string | null
}

export type FlowPoint = { time: string; value: number }

export type DashboardOverview = {
  campus: CampusConfig
  date: string
  updatedAt: string
  totalVehicles: number
  overloadedLots: number
  peakWindow: string
  recentFlow: number
  lots: LotStatus[]
  flow: FlowPoint[]
  qualityFlags: QualityFlag[]
  forecastAvailable: boolean
}
