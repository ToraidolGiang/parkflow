import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from '../../../App'
import { SELECTION_KEY } from '../state/storage'

describe('student route', () => {
  beforeEach(() => { localStorage.clear(); sessionStorage.clear() })
  afterEach(() => { vi.unstubAllEnvs(); vi.unstubAllGlobals() })

  it('redirects to selection when route state is missing', async () => {
    render(<MemoryRouter initialEntries={['/sv/route']}><App /></MemoryRouter>)
    expect(await screen.findByRole('heading', { name: 'Bạn học ở đâu?' })).toBeInTheDocument()
  })

  it('renders both route legs and instructions', async () => {
    localStorage.setItem(SELECTION_KEY, JSON.stringify({ building: 'GDA', arrive: '07:10' }))
    render(<MemoryRouter initialEntries={['/sv/route']}><App /></MemoryRouter>)
    expect(await screen.findByText('Đến cổng trường')).toBeInTheDocument()
    expect(screen.getByText('Trong khuôn viên')).toBeInTheDocument()
    expect(screen.getAllByRole('listitem')).toHaveLength(3)
  })

  it('does not draw a fake route when backend route details are empty', async () => {
    vi.stubEnv('VITE_USE_MOCK_API', 'false')
    localStorage.setItem(SELECTION_KEY, JSON.stringify({ building: 'H3', arrive: '07:10' }))
    vi.stubGlobal('fetch', vi.fn(async (url: string) => new Response(JSON.stringify(url === '/api/config' ? {
      buildings: [{ id: 'H3', name: 'Nhà H3', nearLot: 'C1', svg: [350, 90] }],
      lots: [{ id: 'C1', name: 'Nhà xe C1', count: 20, capacity: 300, gate: 'EAST', near: 'H3', svg: [330, 155] }],
      gates: [{ id: 'EAST', name: 'Cổng Trần Đại Nghĩa', svg: [520, 650] }],
    } : {
      lotId: 'C1', lotName: 'Nhà xe C1', gate: 'EAST', gateName: 'Cổng Trần Đại Nghĩa', openFrom: '06:00',
      expectedPct: 6.7, reason: 'Gần nơi học', planVersion: null, changed: false, changeMessage: null, updatedAt: '06:30',
      route: { mapsUrl: '', path: [], steps: [], walkMinutes: 1 },
    }))))

    render(<MemoryRouter initialEntries={['/sv/route']}><App /></MemoryRouter>)

    expect(await screen.findByText(/Chưa có tuyến nội bộ chi tiết/)).toHaveTextContent('Cổng Trần Đại Nghĩa')
    expect(screen.getAllByText('Nhà xe C1').length).toBeGreaterThan(0)
    expect(screen.queryByLabelText('Tuyến từ cổng tới bãi')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Link Maps không hợp lệ' })).toBeDisabled()
  })
})
