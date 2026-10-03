import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, expect, it, vi } from 'vitest'
import App from '../../App'

afterEach(() => vi.unstubAllGlobals())
it('shows HUCE selection and an unapproved fallback from live API responses', async () => {
  vi.stubEnv('VITE_USE_MOCK_API', 'false')
  localStorage.clear(); sessionStorage.clear()
  const fetcher = vi.fn(async (url: string) => new Response(JSON.stringify(url === '/api/config' ? {
    buildings: [{ id: 'H3', name: 'H3', nearLot: 'C1', svg: [350, 90] }],
    lots: [{ id: 'C1', name: 'Nhà xe C1 gần H3', count: 20, capacity: 300, gate: 'EAST', near: 'H3', svg: [330, 155] }],
    gates: [{ id: 'MAIN', name: 'Cổng Giải Phóng', svg: [520, 650] }],
  } : {
    lotId: 'C1', lotName: 'Nhà xe C1 gần H3', gate: 'MAIN', gateName: 'Cổng Giải Phóng', openFrom: '06:00',
    expectedPct: 6.7, reason: 'Nhà xe gần nơi học', planVersion: null, changed: false, changeMessage: null, updatedAt: '06:30',
    route: { mapsUrl: '', path: [[520, 650], [330, 155]], steps: ['Đến C1'], walkMinutes: 1 },
  })))
  vi.stubGlobal('fetch', fetcher)
  render(<MemoryRouter initialEntries={['/sv']}><App /></MemoryRouter>)
  fireEvent.click(await screen.findByRole('radio', { name: /H3/ }))
  fireEvent.click(screen.getByRole('button', { name: /Xem gợi ý gửi xe/ }))
  expect(await screen.findByRole('heading', { name: /Nên gửi Nhà xe C1/ })).toBeInTheDocument()
  expect(screen.getByText('Chưa có kế hoạch áp dụng')).toBeInTheDocument()
  expect(screen.queryByText(/Kế hoạch #.*đã duyệt/)).not.toBeInTheDocument()
  expect(screen.getByRole('img', { name: 'Sơ đồ khuôn viên HUCE minh họa' })).toBeInTheDocument()
  expect(fetcher.mock.calls.some(([url]) => url.includes('building=H3') && url.includes('sessionId='))).toBe(true)
})
