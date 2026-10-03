import { expect, it, vi } from 'vitest'
import { request } from './http'

it('sends JSON and reports API errors with status', async () => {
  const fetcher = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ ok: true })))
    .mockResolvedValueOnce(new Response(JSON.stringify({ message: 'Không hợp lệ' }), { status: 400 }))
  vi.stubGlobal('fetch', fetcher)
  await expect(request('/api/example', { method: 'POST', body: { id: 1 } })).resolves.toEqual({ ok: true })
  expect(fetcher).toHaveBeenCalledWith('/api/example', expect.objectContaining({
    method: 'POST', body: '{"id":1}', headers: { 'Content-Type': 'application/json' },
  }))
  await expect(request('/api/example')).rejects.toMatchObject({ status: 400, message: 'Không hợp lệ' })
  vi.unstubAllGlobals()
})
