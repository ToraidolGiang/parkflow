import { afterEach, expect, it, vi } from 'vitest'
import { getCompliance } from './compliance'

afterEach(() => { vi.unstubAllGlobals(); vi.unstubAllEnvs() })

it('loads compliance for the selected plan version', async () => {
  vi.stubEnv('VITE_USE_MOCK_API', 'false')
  const response = { version: 3, overall: .72, byMove: [{ from: 'A', to: 'C1', issued: 10, arrived: 8, compliance: .8 }] }
  const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify(response), { status: 200 }))
  vi.stubGlobal('fetch', fetcher)
  await expect(getCompliance(3)).resolves.toEqual(response)
  expect(fetcher).toHaveBeenCalledWith('/api/plan/3/compliance', expect.objectContaining({ method: 'GET' }))
})
