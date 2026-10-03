import { mockApiEnabled, request } from './http'
import type { Alert, ParsedRule, Rule, RuleDraft } from '../types/rules'

export async function listRules(signal?: AbortSignal): Promise<Rule[]> {
  if (mockApiEnabled()) return []
  return request<Rule[]>('/api/rules', { signal })
}

export async function createRule(draft: RuleDraft, signal?: AbortSignal): Promise<Rule> {
  if (mockApiEnabled()) return { ...draft, id: Date.now(), createdAt: new Date().toISOString(), enabled: true }
  return request<Rule>('/api/rules', { method: 'POST', body: draft, signal })
}

export async function deleteRule(id: number, signal?: AbortSignal): Promise<void> {
  return request<void>(`/api/rules/${id}`, { method: 'DELETE', signal })
}

export async function listAlerts(limit = 20, signal?: AbortSignal): Promise<Alert[]> {
  if (mockApiEnabled()) return []
  return request<Alert[]>(`/api/alerts?limit=${limit}`, { signal })
}

export async function parseRule(text: string, signal?: AbortSignal): Promise<ParsedRule> {
  if (mockApiEnabled()) return { rule: { name: text, type: 'alert', scope: 'ALL', from: '06:30', to: '08:00', metric: 'forecast_pct', op: '>=', value: 90, action: 'Thông báo điều hành từ 18:00 hôm trước' }, explanation: 'Dự báo lấp đầy (%) ≥ 90 trong khung giờ đã chọn.', source: 'mock' }
  return request<ParsedRule>('/api/ai/parse-rule', { method: 'POST', body: { text }, signal })
}
