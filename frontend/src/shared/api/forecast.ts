import { useCallback, useEffect, useRef, useState } from 'react'
import forecastMock from '../mock/forecast-A.json'
import planMock from '../mock/plan-current.json'
import qualityMock from '../mock/quality-flags.json'
import type { CampusConfig } from '../types/campus'
import type { ForecastOverview, PlanViewModel } from '../types/forecast'
import { createCampusLookup, getCampusConfig } from './campus'
import { ApiError, mockApiEnabled, request } from './http'

type RawForecast = typeof forecastMock
type RawPlanBody = Omit<typeof planMock, 'pending'> & { shortfallOptions?: { code: string; label: string }[] }
type RawPlan = RawPlanBody & { pending: RawPlanBody | null }
const percent = (value: number | null, capacity: number) => value === null ? null : Math.round(value / capacity * 1000) / 10

function targetDate(plan: RawPlan | null) {
  const source = plan?.createdAt.slice(0, 10) ?? new Date().toISOString().slice(0, 10)
  const [year, month, day] = source.split('-').map(Number)
  const date = new Date(Date.UTC(year, month - 1, day + 1))
  return date.toISOString().slice(0, 10)
}

function adaptPlan(raw: RawPlanBody | null, config: CampusConfig): PlanViewModel | null {
  if (!raw) return null
  const lookup = createCampusLookup(config)
  const lots = new Map(config.lots.map((lot) => [lot.id, lot]))
  const snapshots = (items: RawPlanBody['before']) => items.map((item) => ({ lotId: item.lotId, name: lookup.lotName(item.lotId), count: item.count, capacity: lots.get(item.lotId)?.capacity ?? Math.max(item.count, 1), pct: item.pct }))
  return {
    version: raw.version, status: raw.status as PlanViewModel['status'], window: raw.window, reason: raw.reason,
    before: snapshots(raw.before), after: snapshots(raw.after), shortfall: raw.shortfall,
    shortfallOptions: raw.shortfallOptions,
    moves: raw.moves.map((move) => ({ ...move, fromName: lookup.lotName(move.from), toName: lookup.lotName(move.to) })),
    gateActions: raw.gateActions.map((action) => ({ ...action, gateName: lookup.gateName(action.gate) })),
    excluded: raw.excluded.map((item) => ({ ...item, lotName: lookup.lotName(item.lotId) })),
  }
}

async function loadPlan(signal?: AbortSignal): Promise<RawPlan | null> {
  if (mockApiEnabled()) return structuredClone(planMock) as RawPlan
  try { return await request<RawPlan>('/api/plan/current', { signal }) }
  catch (error) { if (error instanceof ApiError && (error.status === 404 || error.code === 'NO_PLAN')) return null; throw error }
}
async function loadQuality(signal?: AbortSignal) {
  if (mockApiEnabled()) return structuredClone(qualityMock.flags)
  return (await request<{ flags: typeof qualityMock.flags }>('/api/quality/flags', { signal })).flags
}
async function loadForecast(lotId: string, date: string, signal?: AbortSignal): Promise<RawForecast> {
  if (mockApiEnabled()) return structuredClone(forecastMock)
  return request<RawForecast>(`/api/forecast?lotId=${encodeURIComponent(lotId)}&date=${encodeURIComponent(date)}`, { signal })
}

export async function getForecastOverview(lotId?: string, signal?: AbortSignal): Promise<ForecastOverview> {
  const [config, plan, flags] = await Promise.all([getCampusConfig(signal), loadPlan(signal), loadQuality(signal)])
  const active = plan?.pending ?? plan
  const selectedLotId = lotId ?? [...(active?.before ?? [])].sort((a, b) => b.pct - a.pct)[0]?.lotId ?? config.lots[0]?.id
  if (!selectedLotId) throw new Error('Cấu hình chưa có bãi xe')
  const forecast = await loadForecast(selectedLotId, targetDate(plan), signal)
  const capacity = config.lots.find((item) => item.id === selectedLotId)?.capacity ?? 1
  const applied = adaptPlan(plan, config)
  const pending = adaptPlan(plan?.pending ?? null, config) ?? undefined
  const lowest = [...(active?.before ?? [])].sort((a, b) => a.pct - b.pct)[0]
  const lookup = createCampusLookup(config)
  return {
    campus: config, date: forecast.date, generatedAt: forecast.generatedAt,
    selectedLotId, selectedLot: lookup.lotName(selectedLotId), availableLots: config.lots.map(({ id, name }) => ({ id, name })),
    peak: { t: forecast.peak.t, pct: forecast.peak.pct }, shortage: pending?.shortfall ?? applied?.shortfall ?? 0,
    lowestLot: lowest ? { lotId: lowest.lotId, name: lookup.lotName(lowest.lotId), pct: lowest.pct, gate: lookup.gateName(config.lots.find(l => l.id === lowest.lotId)?.gate ?? '') } : undefined,
    thresholds: { warn: forecast.thresholds.yellow, design: forecast.thresholds.over, ceiling: forecast.thresholds.critical },
    points: forecast.points.map((point) => ({ t: point.t, actual: percent(point.actual, capacity), forecast: percent(point.forecast, capacity)!, afterPlan: percent(point.afterPlan, capacity)! })),
    plan: applied, pendingPlan: pending,
    qualityFlags: flags.map((flag) => ({ id: flag.id, severity: flag.type === 'signal_lost' ? 'danger' as const : 'warning' as const, message: flag.message })),
  }
}

function withForecast(current: ForecastOverview, lotId: string, forecast: RawForecast): ForecastOverview {
  const lot = current.campus.lots.find((item) => item.id === lotId)
  const capacity = lot?.capacity ?? 1
  return {
    ...current,
    date: forecast.date,
    generatedAt: forecast.generatedAt,
    selectedLotId: lotId,
    selectedLot: lot?.name ?? lotId,
    peak: { t: forecast.peak.t, pct: forecast.peak.pct },
    thresholds: { warn: forecast.thresholds.yellow, design: forecast.thresholds.over, ceiling: forecast.thresholds.critical },
    points: forecast.points.map((point) => ({ t: point.t, actual: percent(point.actual, capacity), forecast: percent(point.forecast, capacity)!, afterPlan: percent(point.afterPlan, capacity)! })),
  }
}

export async function approvePlan(version: number) { return { version, status: 'approved' as const } }

export function useForecastOverview() {
  const [data, setData] = useState<ForecastOverview>()
  const [error, setError] = useState<Error>()
  const [reloadKey, setReloadKey] = useState(0)
  const dataRef = useRef<ForecastOverview | undefined>(undefined)
  const selectionController = useRef<AbortController | undefined>(undefined)
  useEffect(() => {
    let active = true; const controller = new AbortController()
    getForecastOverview(dataRef.current?.selectedLotId, controller.signal).then((result) => { if (active) { dataRef.current = result; setData(result); setError(undefined) } }).catch((reason: unknown) => { if (active && !controller.signal.aborted) setError(reason instanceof Error ? reason : new Error('Không thể tải dự báo')) })
    return () => { active = false; controller.abort() }
  }, [reloadKey])
  useEffect(() => () => selectionController.current?.abort(), [])
  const selectLot = useCallback((id: string) => {
    const current = dataRef.current
    if (!current || id === current.selectedLotId) return
    selectionController.current?.abort()
    selectionController.current = new AbortController()
    const controller = selectionController.current
    void loadForecast(id, current.date, controller.signal).then((forecast) => {
      if (controller.signal.aborted) return
      const next = withForecast(current, id, forecast)
      dataRef.current = next; setData(next); setError(undefined)
    }).catch((reason: unknown) => { if (!controller.signal.aborted) setError(reason instanceof Error ? reason : new Error('Không thể tải dự báo')) })
  }, [])
  return { data, error, isLoading: !data && !error, selectLot, refresh: useCallback(() => setReloadKey(value => value + 1), []) }
}
