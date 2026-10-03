import { request } from './http'
import type { AiMode, AiReport } from '../types/ai'

export async function getAiReport(weekStart: string, signal?: AbortSignal): Promise<AiReport> {
  return request<AiReport>('/api/ai/report', { method: 'POST', body: { weekStart }, signal })
}
export async function getAiMode(signal?: AbortSignal): Promise<AiMode> { return request<AiMode>('/api/ai/mode', { signal }) }
export async function setAiMode(mock: boolean, signal?: AbortSignal): Promise<AiMode> { return request<AiMode>('/api/ai/mode', { method: 'PUT', body: { mock }, signal }) }
