import { mockApiEnabled, request } from './http'

export type GateStatus = {
  gate: string
  name: string
  capacityPer15: number
  inflowLast15: number
  utilization: number
  level: 'green' | 'yellow' | 'red' | 'critical'
  source: string
  expectedIn: { t: string; count: number }[]
  action: { openFrom: string; normalOpen: string; extraGuard: string } | null
}

export type GateStatusResponse = { clock: { mode: string; now: string }; gates: GateStatus[] }
export type HealthResponse = { status: string; db: string }

export async function getGateStatus(signal?: AbortSignal): Promise<GateStatusResponse> {
  if (mockApiEnabled()) return { clock: { mode: 'mock', now: new Date().toISOString() }, gates: [] }
  return request<GateStatusResponse>('/api/gates/status', { signal })
}

export async function getHealth(signal?: AbortSignal): Promise<HealthResponse> {
  if (mockApiEnabled()) return { status: 'ok', db: 'up' }
  return request<HealthResponse>('/api/health', { signal })
}
