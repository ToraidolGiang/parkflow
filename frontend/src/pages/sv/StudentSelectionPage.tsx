import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import type { BuildingId } from './types'
import { useStudentFlow } from './state/StudentFlowContext'

export function StudentSelectionPage() {
  const { config, selection, choose, error, retry } = useStudentFlow()
  const [building, setBuilding] = useState<BuildingId | ''>(selection?.building ?? '')
  const [arrive, setArrive] = useState(selection?.arrive ?? '07:10')
  const navigate = useNavigate()
  if (error && !config) return <div className="student-error"><p>{error}</p><button onClick={retry}>Thử lại</button></div>
  if (!config) return <div className="student-loading" aria-busy="true">Đang tải cấu hình trường...</div>
  const submit = (event: FormEvent) => { event.preventDefault(); if (!building || !arrive) return; choose({ building, arrive }); navigate('/sv/recommendation') }
  return <section className="student-screen selection-screen"><div className="student-intro"><p className="eyebrow">Chào buổi sáng</p><h1>Bạn học ở đâu?</h1><p>Chọn điểm đến và giờ bạn dự kiến tới trường.</p></div><form onSubmit={submit}><fieldset><legend>Nơi học hôm nay</legend><div className="building-grid">{config?.buildings.map((item) => <label className={`building-option ${building === item.id ? 'selected' : ''}`} key={item.id}><input type="radio" name="building" value={item.id} checked={building === item.id} onChange={() => setBuilding(item.id)} /><span className="building-icon">⌂</span><span><strong>{item.name}</strong><small>{item.nearbyLot}</small></span><i aria-hidden="true">✓</i></label>)}</div></fieldset><label className="time-field"><span>Giờ dự kiến đến</span><input type="time" value={arrive} onChange={(e) => setArrive(e.target.value)} required /></label><p className="privacy-note">Không cần đăng nhập · ParkFlow chỉ dùng lựa chọn này để gợi ý bãi.</p><button className="student-primary" disabled={!building || !arrive}>Xem gợi ý gửi xe <span>→</span></button></form></section>
}
