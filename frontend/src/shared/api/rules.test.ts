import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createRule, deleteRule, listRules, parseRule } from './rules'

describe('rules api', () => {
  beforeEach(() => vi.stubGlobal('fetch', vi.fn()))
  afterEach(() => vi.unstubAllEnvs())
  it('lists rules', async () => {
    vi.stubEnv('VITE_USE_MOCK_API', 'true')
    vi.mocked(fetch).mockResolvedValue(new Response(JSON.stringify([]), { status: 200 }))
    await expect(listRules()).resolves.toEqual([])
    expect(fetch).not.toHaveBeenCalled()
  })
  it('creates, parses and deletes through the documented endpoints', async () => {
    vi.stubEnv('VITE_USE_MOCK_API', 'false')
    const draft = { name: 'Test', type: 'alert' as const, scope: 'ALL', from: '06:30', to: '08:00', metric: 'forecast_pct' as const, op: '>=' as const, value: 90, action: 'Notify' }
    vi.mocked(fetch)
      .mockResolvedValueOnce(new Response(JSON.stringify({ ...draft, id: 1, createdAt: '2026-10-01T00:00:00+07:00', enabled: true }), { status: 201 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ rule: draft, explanation: 'x', source: 'mock' }), { status: 200 }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    await expect(createRule(draft)).resolves.toMatchObject({ name: 'Test' })
    await expect(parseRule('test')).resolves.toMatchObject({ explanation: 'x' })
    await expect(deleteRule(1)).resolves.toBeUndefined()
  })
})
