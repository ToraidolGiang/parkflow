import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { useState } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ForecastPage } from './ForecastPage'
import * as forecastApi from '../../shared/api/forecast'

vi.mock('../../shared/api/forecast', async () => {
  const actual = await vi.importActual<typeof import('../../shared/api/forecast')>('../../shared/api/forecast')
  return { ...actual, approvePlan: vi.fn(), useForecastOverview: vi.fn() }
})

describe('ForecastPage', () => {
  const baseMockData = {
    campus: { school: 'HUCE', thresholds: { yellow: 0.8, over: 1, ceiling: 1.2 }, gates: [], buildings: [], lots: [{ id: 'A', name: 'Bãi A', capacity: 500, gate: 'MAIN', near: 'B1' }], routes: [] },
    date: '2026-10-03', generatedAt: '2026-10-02T18:00:00+07:00',
    selectedLot: 'Bãi A', selectedLotId: 'A', availableLots: [{ id: 'A', name: 'Bãi A' }],
    peak: { t: '07:30', pct: 103.4 }, shortage: 10,
    lowestLot: { lotId: 'A', name: 'Bãi A', pct: 66, gate: 'Cổng chính' },
    thresholds: { warn: 80, design: 100, ceiling: 120 },
    points: [{ t: '07:30', actual: 90, forecast: 103.4, afterPlan: 96 }],
    plan: { version: 3, status: 'approved' as const, window: { from: '07:15', to: '08:00' }, reason: 'Điều phối', before: [{ lotId: 'A', name: 'Bãi A', count: 565, capacity: 500, pct: 113 }], after: [{ lotId: 'A', name: 'Bãi A', count: 480, capacity: 500, pct: 96 }], moves: [], gateActions: [], excluded: [], shortfall: 0 },
    pendingPlan: { version: 4, status: 'pending_approval' as const, window: { from: '07:15', to: '08:00' }, reason: 'Đột biến', before: [{ lotId: 'A', name: 'Bãi A', count: 605, capacity: 500, pct: 121 }], after: [{ lotId: 'A', name: 'Bãi A', count: 490, capacity: 500, pct: 98 }], moves: [], gateActions: [], excluded: [], shortfall: 10, shortfallOptions: [{ code: 'ACCEPT_HIGHER', label: 'Chấp nhận mức cao hơn' }] },
    qualityFlags: [],
  }
  let refreshFailure: Error | undefined

  function useMockForecastOverview() {
    const [data, setData] = useState(() => structuredClone(baseMockData) as Awaited<ReturnType<typeof forecastApi.getForecastOverview>>)
    const refresh = async () => {
      if (refreshFailure) throw refreshFailure
      const next = structuredClone(baseMockData) as Awaited<ReturnType<typeof forecastApi.getForecastOverview>>
      next.pendingPlan = undefined
      if (next.plan) next.plan.version = 4
      setData(next)
      return next
    }
    return { data, error: undefined, isLoading: false, selectLot: vi.fn(), refresh }
  }

  beforeEach(() => {
    refreshFailure = undefined
    vi.mocked(forecastApi.approvePlan).mockReset()
    vi.mocked(forecastApi.useForecastOverview).mockReset()
    vi.mocked(forecastApi.useForecastOverview).mockImplementation(useMockForecastOverview)
  })

  it('shows the forecast and pending plan', async () => {
    render(<MemoryRouter><ForecastPage /></MemoryRouter>)

    expect(await screen.findByRole('heading', { name: 'Dự báo & Đề xuất' })).toBeInTheDocument()
    expect(screen.getByText('103.4%')).toBeInTheDocument()
    expect(screen.getByText('10 xe')).toBeInTheDocument()
    expect(screen.getByText('Chờ duyệt · v4')).toBeInTheDocument()
  })

  it('approves a pending plan and reports success', async () => {
    vi.mocked(forecastApi.approvePlan).mockResolvedValue({ version: 4, status: 'approved' })
    render(<MemoryRouter><ForecastPage /></MemoryRouter>)

    const approveButton = await screen.findByRole('button', { name: 'Duyệt phương án' })
    expect(approveButton).toBeDisabled()
    fireEvent.change(screen.getByLabelText('Người duyệt'), { target: { value: 'Tuan Anh' } })
    expect(approveButton).toBeDisabled()
    fireEvent.change(screen.getByLabelText('Phương án xử lý'), { target: { value: 'ACCEPT_HIGHER' } })
    fireEvent.click(approveButton)
    fireEvent.click(approveButton)

    await waitFor(() => expect(forecastApi.approvePlan).toHaveBeenCalledWith(4, { approvedBy: 'Tuan Anh', option: 'ACCEPT_HIGHER' }))
    expect(forecastApi.approvePlan).toHaveBeenCalledTimes(1)
    expect(await screen.findByText('Đã duyệt phương án phiên bản 4.')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByText('Đang áp dụng · v4')).toBeInTheDocument())
  })

  it('hides approval button when approve succeeds but refresh fails (race condition)', async () => {
    refreshFailure = new Error('Network error')
    vi.mocked(forecastApi.approvePlan).mockResolvedValue({ version: 4, status: 'approved' })
    render(<MemoryRouter><ForecastPage /></MemoryRouter>)

    fireEvent.change(await screen.findByLabelText('Người duyệt'), { target: { value: 'Admin' } })
    fireEvent.change(screen.getByLabelText('Phương án xử lý'), { target: { value: 'ACCEPT_HIGHER' } })
    fireEvent.click(screen.getByRole('button', { name: 'Duyệt phương án' }))

    expect(await screen.findByText('Đã duyệt phương án phiên bản 4.')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByText(/chưa tải lại được trạng thái/)).toBeInTheDocument())
    await waitFor(() => expect(screen.getByText('Đã duyệt · v4 (chờ đồng bộ)')).toBeInTheDocument())
    expect(screen.queryByRole('button', { name: 'Duyệt phương án' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Đã duyệt' })).not.toBeInTheDocument()

    refreshFailure = undefined
    fireEvent.click(screen.getByRole('button', { name: 'Chạy dự báo' }))

    await waitFor(() => expect(screen.getByText('Đang áp dụng · v4')).toBeInTheDocument())
    expect(screen.queryByText(/chưa tải lại được trạng thái/)).not.toBeInTheDocument()
  })
})
