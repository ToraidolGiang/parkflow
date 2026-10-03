import type { Compliance } from '../types/forecast'
import { mockApiEnabled, request } from './http'

export async function getCompliance(version: number, signal?: AbortSignal): Promise<Compliance | null> {
  if (mockApiEnabled()) return null
  return request<Compliance>(`/api/plan/${version}/compliance`, { signal })
}
