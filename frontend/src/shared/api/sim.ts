import { useEffect, useState } from 'react'
import { mockApiEnabled, requestCached } from './http'

export type SimState = {
  running: boolean
  paused?: boolean
  simTime: string | null
  speed: number
  sent: number
  error: string | null
  spike: { lotId: string | null; pct: number } | null
}

export async function getSimState(signal?: AbortSignal): Promise<SimState> {
  if (mockApiEnabled()) return { running: false, simTime: null, speed: 30, sent: 0, error: null, spike: null }
  return requestCached<SimState>('/api/sim/state', { signal })
}

/** Trạng thái giả lập công khai (`GET /api/sim/state`), làm mới định kỳ; null khi chưa tải được. */
export function useSimState(intervalMs = 5000): SimState | null {
  const [state, setState] = useState<SimState | null>(null)
  useEffect(() => {
    let active = true
    let timer: ReturnType<typeof setTimeout> | undefined
    const poll = async () => {
      try {
        const next = await getSimState()
        if (active) setState(next)
      } catch {
        // giữ trạng thái lần trước
      }
      if (active) timer = setTimeout(() => void poll(), intervalMs)
    }
    void poll()
    return () => { active = false; if (timer) clearTimeout(timer) }
  }, [intervalMs])
  return state
}

/** "2026-09-22T07:42:10+07:00" thành { date: "22/09", time: "07:42" } (cắt chuỗi, không phụ thuộc múi giờ trình duyệt). */
export function simClock(simTime: string | null) {
  if (!simTime) return { date: '', time: '' }
  return { date: `${simTime.slice(8, 10)}/${simTime.slice(5, 7)}`, time: simTime.slice(11, 16) }
}
