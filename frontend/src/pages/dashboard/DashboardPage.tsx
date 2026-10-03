import { mockApiEnabled } from '../sv/api/http'
import { Link } from 'react-router-dom'
import { useState } from 'react'
import { useDashboardOverview } from '../../shared/api/dashboard'
import { CampusMap } from '../../shared/components/CampusMap'
import type { FlowPoint, LotStatus } from '../../shared/types/dashboard'
import './dashboard.css'

const numberFormat = new Intl.NumberFormat('vi-VN')

function OccupancyChart({ lots }: { lots: LotStatus[] }) {
  return <div className="occupancy-chart"><div className="chart-scale"><span>0%</span><span>30%</span><span>60%</span><span>90%</span><span>100%</span><span>120%</span></div><div className="capacity-line" />
    {lots.map((lot) => <div className="occupancy-row" key={lot.lotId}><span>{lot.name}</span><div className="occupancy-track"><span className={`occupancy-bar status-${lot.status}`} style={{ width: `${Math.min(lot.percentage / 1.2, 100)}%` }} /></div><span>{lot.percentage}%</span></div>)}
  </div>
}

function FlowChart({ points }: { points: FlowPoint[] }) {
  const width = 560, height = 176, padX = 34, padY = 20, max = 360
  const positions = points.map((point, index) => ({ ...point, x: padX + index * ((width - padX * 2) / (points.length - 1)), y: height - padY - (point.value / max) * (height - padY * 2) }))
  return <div className="flow-chart"><svg aria-label="Biểu đồ lưu lượng xe vào theo 15 phút" role="img" viewBox={`0 0 ${width} ${height}`}>
    {[0, 90, 180, 270, 360].map((value) => { const y = height - padY - (value / max) * (height - padY * 2); return <g key={value}><line className="grid-line" x1={padX} x2={width - 8} y1={y} y2={y} /><text x="4" y={y + 4}>{value}</text></g> })}
    {positions.map(({ time, x }) => <g key={time}><line className="grid-line" x1={x} x2={x} y1={padY} y2={height - padY} /><text textAnchor="middle" x={x} y={height - 3}>{time}</text></g>)}
    <polyline className="flow-line" fill="none" points={positions.map(({ x, y }) => `${x},${y}`).join(' ')} />
  </svg></div>
}

function StatusBadge({ status }: { status: LotStatus['status'] }) {
  const labels = { available: 'Còn chỗ', 'near-full': 'Sắp đầy', overloaded: 'Quá tải' }
  return <span className={`status-badge status-${status}`}>{labels[status]}</span>
}

export function DashboardPage() {
  const { data, isLoading, error } = useDashboardOverview()
  const [simulatorRunning, setSimulatorRunning] = useState(false)
  const [simulatorMessage, setSimulatorMessage] = useState('')
  if (isLoading) return <div className="screen-status">Đang tải bảng điều khiển…</div>
  if (error || !data) return <div className="screen-status">Không thể tải dữ liệu bảng điều khiển.</div>
  return <div className="dashboard-page">
    <header className="dashboard-header"><div><h1>Bảng điều khiển</h1><p>Dữ liệu mô phỏng · {data.date} cập nhật {data.updatedAt}</p></div><div className="dashboard-actions">{mockApiEnabled() && <><span className={`simulator-state ${simulatorRunning ? 'is-running' : ''}`}>{simulatorRunning ? 'Đang phát dữ liệu' : 'Mô phỏng đang dừng'}</span><button className="secondary-button" onClick={() => { setSimulatorRunning((value) => !value); setSimulatorMessage('') }}>{simulatorRunning ? 'Dừng mô phỏng' : 'Bắt đầu mô phỏng'}</button><button className="spike-button" disabled={!simulatorRunning} onClick={() => setSimulatorMessage('Đã tăng lưu lượng mô phỏng thêm 20%')}>Tăng đột biến 20%</button></>}<Link className="assistant-button" to="/assistant">Hỏi trợ lý</Link></div></header>
    {simulatorMessage && <div className="simulator-notice" role="status">{simulatorMessage}</div>}
    <section className="kpi-grid" aria-label="Chỉ số tổng quan">
      <article className="kpi-card"><span>Tổng xe hiện tại</span><strong>{numberFormat.format(data.totalVehicles)}</strong><small>Tổng 4 bãi · cập nhật {data.updatedAt}</small></article>
      <article className="kpi-card"><span>Số bãi quá tải</span><strong className="danger">{data.overloadedLots} / {data.lots.length}</strong><small>{data.lots.filter(l => l.percentage >= 100).map(l => l.name).join(', ') || 'Các bãi còn chỗ'}</small></article>
      <article className="kpi-card"><span>Cao điểm dự kiến</span><strong className="warning">{data.peakWindow}</strong><small>{mockApiEnabled() ? 'Bãi A dự kiến 113%' : 'Dự báo theo kế hoạch được duyệt'}</small></article>
      <article className="kpi-card"><span>Lưu lượng 15 phút gần nhất</span><strong className="blue">{data.recentFlow}</strong><small>xe / 15 phút gần nhất</small></article>
    </section>
    <section className="dashboard-grid">
      <article className="panel map-panel"><h2>Bản đồ khuôn viên</h2><CampusMap lots={data.lots} /></article>
      <article className="panel occupancy-panel"><h2>Mức lấp đầy lúc {data.updatedAt}</h2><p className="panel-subtitle">so với sức chứa thiết kế · Trên 100%: xe lấn lối đi</p><OccupancyChart lots={data.lots} /></article>
      <article className="panel table-panel"><h2>Trạng thái bãi xe</h2><div className="table-wrap"><table><thead><tr><th>Bãi</th><th>Hiện tại</th><th>Lấp đầy</th><th>Trạng thái</th><th>Cổng</th></tr></thead><tbody>{data.lots.map((lot) => <tr key={lot.lotId}><td>{lot.name}</td><td>{lot.current} / {lot.capacity}</td><td className={`text-${lot.status}`}>{lot.percentage}%</td><td><StatusBadge status={lot.status} /></td><td>{lot.gate}</td></tr>)}</tbody></table></div></article>
      <article className="panel flow-panel"><h2>Lưu lượng xe vào (xe / 15 phút)</h2>{data.flow.length > 1 ? <FlowChart points={data.flow} /> : <p>Chưa có chuỗi lưu lượng; xem số xe vào 15 phút gần nhất ở trên.</p>}</article>
    </section>
  </div>
}
