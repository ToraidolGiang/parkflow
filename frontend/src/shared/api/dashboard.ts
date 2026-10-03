import { useCallback, useEffect, useRef, useState } from 'react'
import dashboardOverview from '../mock/dashboard-overview.json'
import forecastMock from '../mock/forecast-A.json'
import lotsStatusMock from '../mock/lots-status.json'
import qualityFlagsMock from '../mock/quality-flags.json'
import type { CampusConfig } from '../types/campus'
import type { DashboardOverview, DataFlag, QualityFlag } from '../types/dashboard'
import { createCampusLookup, getCampusConfig } from './campus'
import { ApiError, mockApiEnabled, request } from './http'

type RawLot = {
  lotId: string
  count: number
  capacity: number
  pct: number
  gate: string
  nearBuilding: string
  dataFlag?: DataFlag
  lastEventAt?: string | null
}

type RawStatus = {
  clock: { now: string }
  totals: { count: number; overloadedLots: number; inflowLast15: number }
  lots: RawLot[]
}

type RawForecast = {
  peak?: { t: string; pct: number }
}

function getStatus(percentage: number, config: CampusConfig) {
  const nearFull = config.thresholds.yellow ?? config.thresholds.warn ?? 0.8
  const full = config.thresholds.over ?? 1
  if (percentage >= full * 100) return 'overloaded' as const
  if (percentage >= nearFull * 100) return 'near-full' as const
  return 'available' as const
}

function sourceLabel(flag: DataFlag) {
  if (flag === 'estimated') return 'Số ước tính'
  if (flag === 'signal_lost') return 'Mất tín hiệu'
  if (flag === 'suspect') return 'Cần kiểm tra'
  return undefined
}

async function getLotsStatus(signal?: AbortSignal): Promise<RawStatus> {
  if (mockApiEnabled()) return structuredClone(lotsStatusMock) as RawStatus
  return request<RawStatus>('/api/lots/status', { signal })
}

async function getQualityFlags(signal?: AbortSignal): Promise<QualityFlag[]> {
  if (mockApiEnabled()) return structuredClone(qualityFlagsMock.flags) as QualityFlag[]
  const response = await request<{ flags: QualityFlag[] }>('/api/quality/flags', { signal })
  return response.flags
}

async function getPeak(lotId: string, date: string, signal?: AbortSignal) {
  try {
    const forecast = mockApiEnabled()
      ? structuredClone(forecastMock) as RawForecast
      : await request<RawForecast>(`/api/forecast?lotId=${encodeURIComponent(lotId)}&date=${encodeURIComponent(date)}`, { signal })
    return forecast.peak?.t
  } catch (error) {
    if (error instanceof ApiError && (error.status === 404 || error.code === 'NO_HISTORY')) return undefined
    throw error
  }
}

function buildOverview(config: CampusConfig, raw: RawStatus, flags: QualityFlag[], peakWindow?: string): DashboardOverview {
  const lookup = createCampusLookup(config)
  return {
    campus: config,
    date: raw.clock.now.slice(0, 10),
    updatedAt: raw.clock.now.slice(11, 19),
    totalVehicles: raw.totals.count,
    overloadedLots: raw.totals.overloadedLots,
    peakWindow: peakWindow ?? 'Chưa đủ dữ liệu',
    recentFlow: raw.totals.inflowLast15,
    flow: mockApiEnabled() ? dashboardOverview.flow : [],
    qualityFlags: flags,
    forecastAvailable: Boolean(peakWindow),
    lots: raw.lots.map((lot) => {
      const flag = lot.dataFlag ?? 'ok'
      return {
        lotId: lot.lotId,
        name: lookup.lotName(lot.lotId),
        current: lot.count,
        capacity: lot.capacity,
        percentage: lot.pct,
        gate: lookup.gateName(lot.gate),
        nearby: lookup.buildingName(lot.nearBuilding),
        status: getStatus(lot.pct, config),
        dataFlag: flag,
        sourceLabel: sourceLabel(flag),
        lastEventAt: lot.lastEventAt,
      }
    }),
  }
}

export async function getDashboardStatus(config: CampusConfig, signal?: AbortSignal) {
  const raw = await getLotsStatus(signal)
  return buildOverview(config, raw, [])
}

export async function getDashboardBootstrap(signal?: AbortSignal): Promise<DashboardOverview> {
  const [config, raw, flags] = await Promise.all([
    getCampusConfig(signal),
    getLotsStatus(signal),
    getQualityFlags(signal),
  ])
  const busiest = [...raw.lots].sort((a, b) => b.pct - a.pct)[0]
  const peakWindow = busiest ? await getPeak(busiest.lotId, raw.clock.now.slice(0, 10), signal) : undefined
  return buildOverview(config, raw, flags, peakWindow)
}

export function useDashboardOverview() {
  const [data, setData] = useState<DashboardOverview>()
  const [error, setError] = useState<Error>()
  const [isRefreshing, setIsRefreshing] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)
  const dataRef = useRef<DashboardOverview | undefined>(undefined)

  useEffect(() => {
    let active = true
    let timer: ReturnType<typeof setTimeout> | undefined
    let controller: AbortController | undefined

    const schedule = () => {
      if (active) timer = setTimeout(() => void poll(), 5000)
    }

    const poll = async () => {
      controller = new AbortController()
      if (dataRef.current) setIsRefreshing(true)
      try {
        const current = dataRef.current
        const next = current
          ? {
              ...await getDashboardStatus(current.campus, controller.signal),
              peakWindow: current.peakWindow,
              forecastAvailable: current.forecastAvailable,
              qualityFlags: current.qualityFlags,
              flow: current.flow,
            }
          : await getDashboardBootstrap(controller.signal)
        if (!active) return
        dataRef.current = next
        setData(next)
        setError(undefined)
      } catch (reason) {
        if (!active || controller.signal.aborted) return
        setError(reason instanceof Error ? reason : new Error('Không thể tải dữ liệu'))
      } finally {
        if (active) {
          setIsRefreshing(false)
          schedule()
        }
      }
    }

    void poll()
    return () => {
      active = false
      if (timer) clearTimeout(timer)
      controller?.abort()
    }
  }, [reloadKey])

  const refresh = useCallback(() => setReloadKey((value) => value + 1), [])
  return { data, error, isLoading: !data && !error, isRefreshing, refresh }
}
