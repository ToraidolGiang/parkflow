import dashboardOverview from '../mock/dashboard-overview.json'
import type { DashboardOverview } from '../types/dashboard'
import { useEffect, useState } from 'react'

export async function getDashboardOverview(): Promise<DashboardOverview> {
  return Promise.resolve(dashboardOverview as DashboardOverview)
}

export function useDashboardOverview() {
  const [data, setData] = useState<DashboardOverview>()
  const [error, setError] = useState<Error>()

  useEffect(() => {
    let active = true
    getDashboardOverview()
      .then((result) => { if (active) setData(result) })
      .catch((reason: unknown) => { if (active) setError(reason instanceof Error ? reason : new Error('Không thể tải dữ liệu')) })
    return () => { active = false }
  }, [])

  return { data, error, isLoading: !data && !error }
}
