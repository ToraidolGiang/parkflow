import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { DashboardPage } from './DashboardPage'

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
})

describe('Dashboard simulator controls', () => {
  it('starts the simulator and applies a 20 percent spike', async () => {
    render(<MemoryRouter><DashboardPage /></MemoryRouter>)
    fireEvent.click(await screen.findByRole('button', { name: 'Bắt đầu mô phỏng' }))
    expect(screen.getByText('Đang phát dữ liệu')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Tăng đột biến 20%' }))
    expect(screen.getByRole('status')).toHaveTextContent('Đã tăng lưu lượng mô phỏng thêm 20%')
  })

  it('keeps the last snapshot visible when a poll fails', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    vi.stubEnv('VITE_USE_MOCK_API', 'false')
    let statusCalls = 0
    vi.stubGlobal('fetch', vi.fn((url: string | URL | Request) => {
      const path = String(url)
      if (path === '/api/config') return Promise.resolve(new Response(JSON.stringify({
        school: 'HUCE', thresholds: { yellow: 0.8, over: 1, ceiling: 1.2 },
        gates: [{ id: 'NORTH', name: 'Cổng Bắc' }], buildings: [{ id: 'LAB', name: 'Khối Lab', nearLot: 'X' }],
        lots: [{ id: 'X', name: 'Bãi thử nghiệm', capacity: 10, gate: 'NORTH', near: 'LAB', alternates: [] }], routes: [],
      }), { headers: { 'Content-Type': 'application/json' } }))
      if (path === '/api/lots/status') {
        statusCalls += 1
        if (statusCalls > 1) return Promise.reject(new Error('network down'))
        return Promise.resolve(new Response(JSON.stringify({
          clock: { mode: 'realtime', now: '2026-10-01T08:00:00+07:00' }, updatedAt: '2026-10-01T08:00:00+07:00',
          lots: [{ lotId: 'X', name: 'X', count: 7, capacity: 10, maxCapacity: 12, pct: 70, level: 'green', gate: 'NORTH', nearBuilding: 'LAB', lastEventAt: null, dataFlag: 'ok' }],
          totals: { count: 7, overloadedLots: 0, inflowLast15: 2 },
        }), { headers: { 'Content-Type': 'application/json' } }))
      }
      if (path === '/api/quality/flags') return Promise.resolve(new Response(JSON.stringify({ flags: [] }), { headers: { 'Content-Type': 'application/json' } }))
      if (path.startsWith('/api/forecast?')) return Promise.resolve(new Response(JSON.stringify({
        lotId: 'X', date: '2026-10-01', k: 1, generatedAt: '2026-10-01T08:00:00+07:00',
        peak: { t: '08:30', count: 9, pct: 90 }, points: [], thresholds: { yellow: 80, over: 100, critical: 120 },
      }), { headers: { 'Content-Type': 'application/json' } }))
      return Promise.reject(new Error(`Unexpected URL: ${path}`))
    }))

    render(<MemoryRouter><DashboardPage /></MemoryRouter>)
    expect((await screen.findAllByText('Bãi thử nghiệm')).length).toBeGreaterThan(0)
    expect(screen.getByText('7')).toBeInTheDocument()

    await act(async () => { await vi.advanceTimersByTimeAsync(5_000) })
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Dữ liệu chưa thể cập nhật'))
    expect(screen.getAllByText('Bãi thử nghiệm').length).toBeGreaterThan(0)
    expect(screen.getAllByText('7').length).toBeGreaterThan(0)
  })
})
