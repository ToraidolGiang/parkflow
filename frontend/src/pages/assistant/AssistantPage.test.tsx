import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { useDashboardOverview } from '../../shared/api/dashboard'
import { chatWithAssistant } from '../../shared/api/assistant'
import { AssistantPage } from './AssistantPage'

vi.mock('../../shared/api/dashboard', () => ({
  useDashboardOverview: vi.fn(),
}))

vi.mock('../../shared/api/assistant', () => ({
  chatWithAssistant: vi.fn(),
}))

const dashboard = {
  campus: {
    school: 'HUCE', thresholds: { over: 1 }, gates: [{ id: 'G1', name: 'Cổng chính' }],
    buildings: [{ id: 'H3', name: 'H3', nearLot: 'C1' }],
    lots: [
      { id: 'A', name: 'Nhà xe A', capacity: 500, gate: 'G1', near: 'H3', alternates: [] },
      { id: 'B', name: 'Nhà xe B', capacity: 500, gate: 'G1', near: 'H3', alternates: [] },
      { id: 'C1', name: 'Nhà xe C1', capacity: 300, gate: 'G1', near: 'H3', alternates: [] },
      { id: 'C2', name: 'Nhà xe C2', capacity: 300, gate: 'G1', near: 'H3', alternates: [] },
    ], routes: [],
  },
  date: '2026-10-01', updatedAt: '08:45:00', totalVehicles: 100,
  overloadedLots: 0, peakWindow: '07:15–08:00', recentFlow: 10, flow: [], qualityFlags: [], forecastAvailable: true,
  lots: [
    { lotId: 'A', name: 'Nhà xe A', current: 100, capacity: 500, percentage: 20, gate: 'Cổng chính', nearby: 'H3', status: 'available' as const },
    { lotId: 'B', name: 'Nhà xe B', current: 100, capacity: 500, percentage: 20, gate: 'Cổng chính', nearby: 'H3', status: 'available' as const },
    { lotId: 'C1', name: 'Nhà xe C1', current: 200, capacity: 300, percentage: 67, gate: 'Cổng chính', nearby: 'H3', status: 'available' as const },
    { lotId: 'C2', name: 'Nhà xe C2', current: 100, capacity: 300, percentage: 33, gate: 'Cổng chính', nearby: 'H3', status: 'available' as const },
  ],
}

describe('AssistantPage', () => {
  it('shows an API error when the initial dashboard load fails', () => {
    vi.mocked(useDashboardOverview).mockReturnValue({ data: undefined, error: new Error('offline'), isLoading: false, isRefreshing: false, refresh: vi.fn() })
    render(<MemoryRouter><AssistantPage /></MemoryRouter>)
    expect(screen.getByRole('alert')).toHaveTextContent('Không thể tải dữ liệu khuôn viên.')
  })

  it('keeps the last dashboard data visible when polling fails', () => {
    vi.mocked(useDashboardOverview).mockReturnValue({ data: dashboard, error: new Error('poll failed'), isLoading: false, isRefreshing: false, refresh: vi.fn() })
    render(<MemoryRouter><AssistantPage /></MemoryRouter>)
    expect(screen.getByText('Nhà xe C1')).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('Dữ liệu mới nhất chưa tải được')
  })

  it('renders lot names and counts from the dashboard API result', () => {
    vi.mocked(useDashboardOverview).mockReturnValue({ data: dashboard, error: undefined, isLoading: false, isRefreshing: false, refresh: vi.fn() })
    render(<MemoryRouter><AssistantPage /></MemoryRouter>)
    expect(screen.getByText('Nhà xe C1')).toBeInTheDocument()
    expect(screen.getByText('Nhà xe C2')).toBeInTheDocument()
    expect(screen.queryByText('Bãi D')).not.toBeInTheDocument()
  })

  it('sends a question to the backend and renders the returned answer', async () => {
    vi.mocked(useDashboardOverview).mockReturnValue({ data: dashboard, error: undefined, isLoading: false, isRefreshing: false, refresh: vi.fn() })
    vi.mocked(chatWithAssistant).mockResolvedValue({ answer: 'Nhà xe C1 còn nhiều chỗ.', facts: [{ label: 'Nhà xe C1', value: '67%' }], suggestions: ['Câu hỏi tiếp theo'], source: 'mock' })
    render(<MemoryRouter><AssistantPage /></MemoryRouter>)
    expect(screen.getByText('Trợ lý AI ParkFlow')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Sáng mai có bãi nào nguy cơ quá tải không?' }))
    expect(screen.getAllByText('AI đang phân tích dữ liệu…')).toHaveLength(2)
    expect(await screen.findByText('Nhà xe C1 còn nhiều chỗ.')).toBeInTheDocument()
    expect(chatWithAssistant).toHaveBeenCalledWith('Sáng mai có bãi nào nguy cơ quá tải không?', [], expect.any(AbortSignal))
    expect(screen.getByText('Dữ liệu mẫu')).toBeInTheDocument()
    expect(screen.getByText(/Nhà xe C1:/)).toBeInTheDocument()
  })
})
