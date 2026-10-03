import { render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import dashboard from '../mock/dashboard-overview.json'
import type { CampusConfig } from '../types/campus'
import type { LotStatus } from '../types/dashboard'
import { CampusMap } from './CampusMap'

describe('CampusMap variants', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('keeps the canonical HUCE roads and courtyard when API geometry is partial', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        school: 'HUCE', thresholds: {}, routes: [], map: { width: 1000, height: 700 },
        buildings: [],
        gates: [{ id: 'MAIN', name: 'Cổng API' }],
        lots: [{ id: 'A', name: 'Tên bãi từ API', capacity: 1450, gate: 'MAIN', near: 'G3' }],
      }),
    }))
    const huceLots: LotStatus[] = [
      { lotId: 'A', name: 'A', current: 10, capacity: 1450, percentage: 1, gate: 'MAIN', nearby: 'G3', status: 'available' },
      { lotId: 'C1', name: 'C1', current: 10, capacity: 300, percentage: 3, gate: 'EAST', nearby: 'H3', status: 'available' },
    ]

    render(<CampusMap lots={huceLots} />)

    expect(screen.getByRole('img', { name: 'Sơ đồ khuôn viên HUCE minh họa' })).toBeInTheDocument()
    expect(screen.getByText('SÂN TRƯỜNG')).toBeInTheDocument()
    expect(screen.getByTestId('huce-road-0')).toBeInTheDocument()
    expect(await screen.findByText('Tên bãi từ API')).toBeInTheDocument()
  })
  it('renders the student route from the same shared map component', () => {
    render(<CampusMap highlightedLotId="C" lots={dashboard.lots as LotStatus[]} showRoute variant="student" />)
    expect(screen.getByLabelText('Sơ đồ khuôn viên')).toHaveClass('sv-map')
    expect(screen.getByText('Được gợi ý')).toBeInTheDocument()
    expect(screen.getByLabelText('Tuyến từ cổng tới bãi')).toBeInTheDocument()
  })

  it('renders arbitrary lot IDs and labels from campus config', () => {
    const campus: CampusConfig = {
      school: 'HUCE',
      thresholds: { yellow: 0.8, over: 1, ceiling: 1.2 },
      gates: [
        { id: 'GATE-3', name: 'Cổng Bắc', svg: [50, 60] },
        { id: 'GATE-4', name: 'Cổng Nam' },
      ],
      buildings: [
        { id: 'LAB-NORTH', name: 'Khối Lab', nearLot: 'LAB-X9', svg: [80, 90] },
        { id: 'LIB-SOUTH', name: 'Thư viện mới', nearLot: 'LOT-Y' },
      ],
      lots: [
        { id: 'LAB-X9', name: 'Nhà xe Lab', capacity: 40, gate: 'GATE-3', near: 'LAB-NORTH', alternates: [], svg: [70, 80] },
        { id: 'LOT-Y', name: 'Nhà xe phía Nam', capacity: 80, gate: 'GATE-4', near: 'LIB-SOUTH', alternates: [] },
      ],
      routes: [],
    }
    const lots: LotStatus[] = [
      { lotId: 'LAB-X9', name: 'stale-1', current: 20, capacity: 40, percentage: 50, gate: 'GATE-3', nearby: 'LAB-NORTH', status: 'available' },
      { lotId: 'LOT-Y', name: 'stale-2', current: 72, capacity: 80, percentage: 90, gate: 'GATE-4', nearby: 'LIB-SOUTH', status: 'near-full' },
    ]

    render(<CampusMap campus={campus} lots={lots} />)

    expect(screen.getByText('Nhà xe Lab')).toBeInTheDocument()
    expect(screen.getByText('Nhà xe phía Nam')).toBeInTheDocument()
    expect(screen.getByText(/Khối Lab/)).toBeInTheDocument()
    expect(screen.getByText(/Thư viện mới/)).toBeInTheDocument()
    expect(screen.getByText('Cổng Bắc')).toBeInTheDocument()
    expect(screen.getByText('Cổng Nam')).toBeInTheDocument()
    expect(screen.queryByText('Cổng chính')).not.toBeInTheDocument()
    expect(screen.queryByText('Cổng phía Đông')).not.toBeInTheDocument()
  })
})
