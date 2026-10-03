import { useEffect, useState } from 'react'
import forecastOverview from '../mock/forecast-overview.json'
import type { ApprovalResult, ForecastOverview } from '../types/forecast'

export async function getForecastOverview(): Promise<ForecastOverview> {
  return Promise.resolve(forecastOverview as ForecastOverview)
}

export async function approvePlan(version: number): Promise<ApprovalResult> {
  return Promise.resolve({ version, status: 'approved' })
}

export function useForecastOverview() {
  const [data, setData] = useState<ForecastOverview>()
  const [error, setError] = useState<Error>()

  useEffect(() => {
    let active = true
    getForecastOverview()
      .then((result) => { if (active) setData(result) })
      .catch((reason: unknown) => { if (active) setError(reason instanceof Error ? reason : new Error('Không thể tải dự báo')) })
    return () => { active = false }
  }, [])

  return { data, error, isLoading: !data && !error }
}
