import dashboardOverview from '../mock/dashboard-overview.json'
import type { DashboardOverview } from '../types/dashboard'
import { useEffect, useState } from 'react'
import { request } from '../../pages/sv/api/http'

export async function getDashboardOverview(): Promise<DashboardOverview> {
  if (import.meta.env.VITE_USE_MOCK_API === 'true') return dashboardOverview as DashboardOverview
  const raw = await request<{ clock: { now: string }; totals: { count: number; overloadedLots: number; inflowLast15: number }; lots: { lotId: DashboardOverview['lots'][number]['lotId']; name: string; count: number; capacity: number; pct: number; gate: string; nearBuilding: string }[] }>('/api/lots/status')
  return { date: raw.clock.now.slice(0, 10), updatedAt: raw.clock.now.slice(11, 19), totalVehicles: raw.totals.count,
    overloadedLots: raw.totals.overloadedLots, peakWindow: 'Chưa có dự báo', recentFlow: raw.totals.inflowLast15, flow: [],
    lots: raw.lots.map(l => ({ lotId: l.lotId, name: l.name, current: l.count, capacity: l.capacity, percentage: l.pct,
      gate: l.gate === 'MAIN' ? 'Cổng Giải Phóng' : 'Cổng Trần Đại Nghĩa', nearby: l.nearBuilding,
      status: l.pct >= 100 ? 'overloaded' : l.pct >= 80 ? 'near-full' : 'available' })) }
}

export function useDashboardOverview() {
  const [data, setData] = useState<DashboardOverview>()
  const [error, setError] = useState<Error>()

  useEffect(() => {
    let active = true
    let timer: ReturnType<typeof setTimeout>
    const refresh = () => getDashboardOverview()
      .then((result) => { if (active) { setData(result); setError(undefined) } })
      .catch((reason: unknown) => { if (active) setError(reason instanceof Error ? reason : new Error('Không thể tải dữ liệu')) })
      .finally(() => { if (active) timer = setTimeout(refresh, 5000) })
    void refresh()
    return () => { active = false; clearTimeout(timer) }
  }, [])

  return { data, error, isLoading: !data && !error }
}
