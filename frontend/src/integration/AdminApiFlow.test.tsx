import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, expect, it, vi } from 'vitest'
import App from '../App'

const config = {
  school: 'HUCE', thresholds: { yellow: 0.8, over: 1, ceiling: 1.2 },
  gates: [{ id: 'GATE-3', name: 'Cổng Bắc', svg: [50, 60] }],
  buildings: [{ id: 'LAB-NORTH', name: 'Khối Lab', nearLot: 'LAB-X9', svg: [80, 90] }],
  lots: [{ id: 'LAB-X9', name: 'Nhà xe Lab', capacity: 40, max: 48, gate: 'GATE-3', near: 'LAB-NORTH', alternates: [], svg: [70, 80] }],
  routes: [],
}
const status = {
  clock: { now: '2026-10-01T08:00:00+07:00' }, updatedAt: '2026-10-01T08:00:00+07:00',
  lots: [{ lotId: 'LAB-X9', count: 42, capacity: 40, pct: 105, gate: 'GATE-3', nearBuilding: 'LAB-NORTH', dataFlag: 'ok' }],
  totals: { count: 42, overloadedLots: 1, inflowLast15: 6 },
}
const planBody = {
  version: 7, status: 'approved', createdAt: '2026-10-01T18:00:00+07:00', window: { from: '07:00', to: '08:00' }, reason: 'Điều phối',
  before: [{ lotId: 'LAB-X9', count: 42, pct: 105 }], after: [{ lotId: 'LAB-X9', count: 38, pct: 95 }],
  moves: [], excluded: [], shortfall: 0, gateActions: [], envelope: { maxMoveTotal: 10, maxTargetPct: 1, allowedDest: [] },
}
const pending = { ...planBody, version: 8, status: 'pending_approval', shortfall: 4, shortfallOptions: [{ code: 'ACCEPT_HIGHER', label: 'Chấp nhận mức cao hơn' }] }
const forecast = {
  lotId: 'LAB-X9', date: '2026-10-02', k: 1, generatedAt: '2026-10-01T18:01:00+07:00',
  peak: { t: '07:30', count: 42, pct: 105 }, points: [{ t: '07:30', actual: 40, forecast: 42, afterPlan: 38 }],
  thresholds: { yellow: 80, over: 100, critical: 120 },
}
const json = (value: unknown) => new Response(JSON.stringify(value), { headers: { 'Content-Type': 'application/json' } })

afterEach(() => { vi.unstubAllEnvs(); vi.unstubAllGlobals() })

it('runs dashboard to plan approval against the documented APIs', async () => {
  vi.stubEnv('VITE_USE_MOCK_API', 'false')
  let approved = false
  const requested: string[] = []
  const fetcher = vi.fn(async (input: string | URL | Request, init?: RequestInit) => {
    const url = String(input)
    requested.push(`${init?.method ?? 'GET'} ${url}`)
    if (url === '/api/config') return json(config)
    if (url === '/api/lots/status') return json(status)
    if (url === '/api/quality/flags') return json({ flags: [] })
    if (url.startsWith('/api/forecast?')) return json(forecast)
    if (url === '/api/plan/current') return json({ ...planBody, pending: approved ? null : pending })
    if (url === '/api/sim/state') return json({ running: false, simTime: null, speed: 30, sent: 0, error: null, spike: null })
    if (url === '/api/plan/8/approve' && init?.method === 'POST') {
      expect(JSON.parse(String(init.body))).toEqual({ approvedBy: 'Tuan Anh', option: 'ACCEPT_HIGHER' })
      approved = true
      return json({ version: 8, status: 'approved' })
    }
    throw new Error(`Unexpected request: ${init?.method ?? 'GET'} ${url}`)
  })
  vi.stubGlobal('fetch', fetcher)

  render(<MemoryRouter initialEntries={['/']}><App /></MemoryRouter>)
  expect(await screen.findByRole('heading', { name: 'Bảng điều khiển' })).toBeInTheDocument()
  expect(screen.getAllByText('Nhà xe Lab').length).toBeGreaterThan(0)
  expect(screen.getAllByText('105%').length).toBeGreaterThan(0)

  fireEvent.click(screen.getByRole('link', { name: /Dự báo & Đề xuất/ }))
  expect(await screen.findByRole('heading', { name: 'Dự báo & Đề xuất' })).toBeInTheDocument()
  fireEvent.change(await screen.findByLabelText('Người duyệt'), { target: { value: 'Tuan Anh' } })
  fireEvent.change(screen.getByLabelText('Phương án xử lý'), { target: { value: 'ACCEPT_HIGHER' } })
  fireEvent.click(screen.getByRole('button', { name: 'Duyệt phương án' }))

  expect(await screen.findByText('Đã duyệt phương án phiên bản 8.')).toBeInTheDocument()
  await waitFor(() => expect(screen.getByText('Đang áp dụng · v7')).toBeInTheDocument())
  expect(requested).toEqual(expect.arrayContaining([
    'GET /api/lots/status', 'GET /api/plan/current', 'POST /api/plan/8/approve',
  ]))
  expect(requested.some((entry) => entry.includes('/api/import/upload') || /\/api\/import\/.*\/confirm/.test(entry))).toBe(false)
})
