import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
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
  fireEvent.change(screen.getByLabelText(/Chỉ cần khi máy chủ chạy không đăng nhập/), { target: { value: 'test-control' } })
  await waitFor(() => expect(screen.getByRole('button', { name: 'Bắt đầu giả lập' })).toBeEnabled())
  fireEvent.click(screen.getByRole('button', { name: 'Bắt đầu giả lập' }))
  await screen.findByRole('button', { name: 'Dừng giả lập' })
  fireEvent.click(screen.getByRole('button', { name: 'Đột biến +20%' }))
  await waitFor(() => expect(fetcher.mock.calls.some(([url, init]) => url === '/api/sim/spike' && init?.body === JSON.stringify({ lotId: 'A', pct: 20 }))).toBe(true))
  expect(fetcher.mock.calls.some(([url, init]) => url === '/api/sim/start' && init?.body === JSON.stringify({ date: '2026-09-22', speed: 30 }))).toBe(true)
  expect(fetcher.mock.calls.filter(([url]) => url.startsWith('/api/sim/') && !url.endsWith('/state') && !url.endsWith('/dates')).every(([, init]) => new Headers(init?.headers).get('X-Api-Key') === 'test-control')).toBe(true)
})

it('offers only controls supported by the current simulator API', async () => {
  const fetcher = vi.fn(async (url: string) => new Response(JSON.stringify(
    url === '/api/config' ? { lots: [] } :
      url === '/api/sim/dates' ? { dates: [], default: '2026-09-22' } : {
        running: url.endsWith('/start'), simTime: null, speed: 30, sent: 0, accepted: 0,
        duplicate: 0, remaining: 10, error: null, spike: null,
      },
  )))
  vi.stubGlobal('fetch', fetcher)
  render(<SimulationControls />)
  await waitFor(() => expect(screen.getByRole('button', { name: 'Bắt đầu giả lập' })).toBeEnabled())
  fireEvent.click(screen.getByRole('button', { name: 'Bắt đầu giả lập' }))
  await screen.findByRole('button', { name: 'Dừng giả lập' })

  expect(screen.getByLabelText('Tốc độ')).toBeDisabled()
  expect(screen.queryByRole('button', { name: 'Tạm dừng' })).not.toBeInTheDocument()
  expect(screen.queryByLabelText('Thời điểm mô phỏng')).not.toBeInTheDocument()
  expect(fetcher.mock.calls.some(([url]) => url === '/api/sim/dates')).toBe(false)
})
