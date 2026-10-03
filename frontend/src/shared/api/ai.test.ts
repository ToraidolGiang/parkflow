import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getAiMode, getAiReport, setAiMode } from './ai'
describe('ai api', () => { beforeEach(() => vi.stubGlobal('fetch', vi.fn()))
  it('uses report and mode contracts', async () => { vi.mocked(fetch).mockResolvedValueOnce(new Response('{}', { status: 200 })).mockResolvedValueOnce(new Response('{"mock":true}', { status: 200 })).mockResolvedValueOnce(new Response('{"mock":false}', { status: 200 })); await getAiReport('2026-10-01'); await getAiMode(); await setAiMode(false); expect(fetch).toHaveBeenNthCalledWith(1, '/api/ai/report', expect.objectContaining({ method: 'POST', body: '{"weekStart":"2026-10-01"}' })); expect(fetch).toHaveBeenLastCalledWith('/api/ai/mode', expect.objectContaining({ method: 'PUT', body: '{"mock":false}' })) })
})
