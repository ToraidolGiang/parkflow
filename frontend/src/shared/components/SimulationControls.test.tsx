import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import { currentVietnamDate } from '../utils/date'
import { SimulationControls } from './SimulationControls'

afterEach(() => vi.unstubAllGlobals())
it('starts the API simulator and submits a scoped 20 percent spike', async () => {
  const fetcher = vi.fn(async (url: string, init?: RequestInit) => {
    if (url === '/api/config') return new Response(JSON.stringify({ lots: [{ id: 'A', name: 'Hầm A' }] }))
    return new Response(JSON.stringify({ running: url.endsWith('/start') || url.endsWith('/spike'), simTime: null, speed: 30, sent: 0, error: null,
      spike: url.endsWith('/spike') ? JSON.parse(init?.body as string) : null }))
  })
  vi.stubGlobal('fetch', fetcher)
  render(<SimulationControls />)
  fireEvent.change(screen.getByLabelText('Khóa điều khiển'), { target: { value: 'test-control' } })
  await waitFor(() => expect(screen.getByRole('button', { name: 'Bắt đầu giả lập' })).toBeEnabled())
  fireEvent.click(screen.getByRole('button', { name: 'Bắt đầu giả lập' }))
  await screen.findByRole('button', { name: 'Dừng giả lập' })
  fireEvent.click(screen.getByRole('button', { name: 'Đột biến +20%' }))
  await waitFor(() => expect(fetcher.mock.calls.some(([url, init]) => url === '/api/sim/spike' && init?.body === JSON.stringify({ lotId: 'A', pct: 20 }))).toBe(true))
  expect(fetcher.mock.calls.some(([url, init]) => url === '/api/sim/start' && init?.body === JSON.stringify({ date: currentVietnamDate(), speed: 30 }))).toBe(true)
  expect(fetcher.mock.calls.filter(([url]) => url.startsWith('/api/sim/') && !url.endsWith('/state')).every(([, init]) => new Headers(init?.headers).get('X-Api-Key') === 'test-control')).toBe(true)
})
