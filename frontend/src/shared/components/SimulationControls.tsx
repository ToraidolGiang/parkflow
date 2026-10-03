import { useEffect, useState } from 'react'
import { request } from '../../pages/sv/api/http'
import './simulation.css'

/** Log giả lập đóng gói chỉ có ngày demo này (và lịch sử đã nạp sẵn); mặc định theo ngày hôm nay sẽ bị từ chối. */
const DEMO_DATE = '2026-09-22'
type SimState = { running: boolean; simTime: string | null; speed: number; sent: number; error: string | null; spike: { lotId: string | null; pct: number } | null }
export function SimulationControls() {
  const [state, setState] = useState<SimState | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [controlKey, setControlKey] = useState('')
  const [date, setDate] = useState(DEMO_DATE)
  const [speed, setSpeed] = useState(30)
  const [lot, setLot] = useState('A')
  const [lots, setLots] = useState<{ id: string; name: string }[]>([])
  useEffect(() => {
    let active = true
    let timer: ReturnType<typeof setTimeout>
    const refresh = async () => {
      try { const next = await request<SimState>('/api/sim/state'); if (active) { setState(next); setError('') } }
      catch (e) { if (active) setError(e instanceof Error ? e.message : 'Không tải được giả lập') }
      if (active) timer = setTimeout(refresh, 3000)
    }
    void refresh()
    request<{ lots: { id: string; name: string }[] }>('/api/config').then(c => { if (active) setLots(c.lots) }).catch(() => {})
    return () => { active = false; clearTimeout(timer) }
  }, [])
  const action = async (name: string, body: unknown) => {
    setBusy(true); setError('')
    try { setState(await request<SimState>(`/api/sim/${name}`, body, controlKey)) }
    catch (e) { setError(e instanceof Error ? e.message : 'Không thực hiện được yêu cầu') }
    finally { setBusy(false) }
  }
  return <section className="simulation-controls" aria-label="Trình giả lập">
    <strong>Giả lập dữ liệu</strong>
    <details className="sim-key"><summary>Khóa điều khiển</summary><label>Chỉ cần khi máy chủ chạy không đăng nhập <input type="password" autoComplete="off" value={controlKey} onChange={e => setControlKey(e.target.value)} /></label></details>
    <label>Ngày <input type="date" value={date} disabled={state?.running || busy} onChange={e => setDate(e.target.value)} /></label>
    <label>Tốc độ <select value={speed} disabled={state?.running || busy} onChange={e => setSpeed(Number(e.target.value))}><option value={1}>×1</option><option value={30}>×30</option><option value={300}>×300</option></select></label>
    <button disabled={busy || !state} onClick={() => void action(state?.running ? 'stop' : 'start', state?.running ? {} : { date, speed })}>{state?.running ? 'Dừng giả lập' : 'Bắt đầu giả lập'}</button>
    <label>Nhà xe <select value={lot} onChange={e => setLot(e.target.value)}><option value="">Tất cả</option>{lots.map(l => <option key={l.id} value={l.id}>{l.name}</option>)}</select></label>
    <button disabled={busy || !state?.running} onClick={() => void action('spike', { ...(lot ? { lotId: lot } : {}), pct: 20 })}>Đột biến +20%</button>
    <button disabled={busy || !state?.spike} onClick={() => void action('spike', { pct: 0 })}>Tắt đột biến</button>
    <span role="status">{state?.simTime ? new Date(state.simTime).toLocaleTimeString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' }) : 'Chưa chạy'} · {state?.sent ?? 0} sự kiện{state?.spike ? ` · +${state.spike.pct}% ${state.spike.lotId ?? 'mọi bãi'}` : ''}</span>
    {(error || state?.error) && <p role="alert">{error || state?.error}</p>}
  </section>
}
