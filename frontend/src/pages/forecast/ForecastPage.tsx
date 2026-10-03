import { useState } from 'react'
import { approvePlan, useForecastOverview } from '../../shared/api/forecast'
import type { ForecastPoint, PlanLot } from '../../shared/types/forecast'
import './forecast.css'

function ForecastChart({ points, thresholds }: { points: ForecastPoint[]; thresholds: { warn: number; design: number; ceiling: number } }) {
  const width = 1020, height = 250, left = 48, right = 20, top = 18, bottom = 42
  const x = (index: number) => left + index * ((width - left - right) / (points.length - 1))
  const y = (value: number) => top + ((130 - value) / 130) * (height - top - bottom)
  const line = (key: 'forecast' | 'afterPlan') => points.map((point, index) => `${x(index)},${y(point[key])}`).join(' ')
  return <div className="forecast-chart"><svg aria-label="Biểu đồ dự báo lấp đầy Bãi A" role="img" viewBox={`0 0 ${width} ${height}`}>
    {[0, 30, 60, 90, 100, 120].map((value) => <g key={value}><line className={value >= 90 ? 'forecast-threshold' : 'forecast-grid'} x1={left} x2={width - right} y1={y(value)} y2={y(value)} /><text x="4" y={y(value) + 4}>{value}%</text></g>)}
    {points.map((point, index) => <g key={point.t}><line className="forecast-grid" x1={x(index)} x2={x(index)} y1={top} y2={height - bottom} />{index % 2 === 0 && <text textAnchor="middle" x={x(index)} y={height - 15}>{point.t}</text>}</g>)}
    <polyline className="forecast-line before" fill="none" points={line('forecast')} />
    <polyline className="forecast-line after" fill="none" points={line('afterPlan')} />
    <text className="threshold-label" textAnchor="end" x={width - right - 4} y={y(thresholds.ceiling) - 5}>{thresholds.ceiling}% trần</text>
    <text className="threshold-label" textAnchor="end" x={width - right - 4} y={y(thresholds.design) - 5}>{thresholds.design}% thiết kế</text>
    <text className="threshold-label" textAnchor="end" x={width - right - 4} y={y(thresholds.warn) + 13}>{thresholds.warn}% cảnh báo</text>
  </svg><div className="forecast-legend"><span><i className="line-key before" />Trước điều hướng</span><span><i className="line-key after" />Sau khi áp dụng</span></div></div>
}

function AllocationRow({ before, after }: { before: PlanLot; after: PlanLot }) {
  const delta = after.count - before.count
  return <div className="allocation-row"><div className="allocation-title"><strong>Bãi {before.lotId}</strong><span>{delta > 0 ? '+' : ''}{delta} xe</span></div>
    <div className="allocation-measure"><span>Trước</span><div><i className={`allocation-bar ${before.pct >= 100 ? 'danger' : 'primary'}`} style={{ width: `${Math.min(before.pct, 120) / 1.2}%` }} /></div><b>{before.count}/{before.capacity} xe · {before.pct}%</b></div>
    <div className="allocation-measure"><span>Sau</span><div><i className="allocation-bar success" style={{ width: `${Math.min(after.pct, 120) / 1.2}%` }} /></div><b>{after.count}/{after.capacity} xe · {after.pct}%</b></div>
  </div>
}

export function ForecastPage() {
  const { data, error, isLoading } = useForecastOverview()
  const [approvalMessage, setApprovalMessage] = useState('')
  const [approvalError, setApprovalError] = useState('')
  const [isApproving, setIsApproving] = useState(false)

  if (isLoading) return <div className="screen-status" role="status">Đang tải dự báo…</div>
  if (error || !data) return <div className="screen-status" role="alert">Không thể tải dữ liệu dự báo. Vui lòng thử lại.</div>

  const approve = async () => {
    setIsApproving(true); setApprovalError('')
    try {
      const result = await approvePlan(data.plan.version)
      setApprovalMessage(`Đã duyệt phương án phiên bản ${result.version}.`)
    } catch {
      setApprovalError('Không thể duyệt phương án. Vui lòng thử lại.')
    } finally { setIsApproving(false) }
  }

  return <div className="forecast-page">
    <header className="forecast-header"><div><h1>Dự báo &amp; Đề xuất</h1><p>Phân tích xu hướng và đề xuất vận hành từ AI · cập nhật {data.generatedAt}</p></div><div className="forecast-actions"><button className="day-type" type="button">Ngày học bình thường</button><button className="run-forecast" type="button">Chạy dự báo</button></div></header>
    <section className="forecast-kpis" aria-label="Tổng quan dự báo">
      <article><span>Cao điểm sáng mai · {data.date}</span><strong className="orange">{data.peak.pct}%</strong><small>so với sức chứa thiết kế · Bãi A · {data.plan.window.from}–{data.plan.window.to}</small></article>
      <article><span>Thiếu chỗ dự kiến</span><strong className="red">{data.shortage} xe</strong><small>Bãi A · cần giảm tối thiểu {data.shortage} xe</small></article>
      <article><span>Bãi có độ lấp đầy thấp nhất</span><strong className="green">Bãi {data.lowestLot.lotId}</strong><small>Dự kiến đỉnh {data.lowestLot.pct}% · chung {data.lowestLot.gate}</small></article>
    </section>
    {data.qualityFlags.map((flag) => <div className={`quality-flag ${flag.severity}`} role="status" key={flag.id}><strong>Cờ chất lượng dữ liệu</strong><span>{flag.message}</span></div>)}
    <section className="forecast-panel chart-panel"><div className="panel-heading"><h2>Biểu đồ dự báo lấp đầy</h2><span>{data.selectedLot}</span></div><ForecastChart points={data.points} thresholds={data.thresholds} /></section>
    <section className="forecast-panel allocation-panel"><h2>Trước / sau điều hướng · {data.date}</h2><p>Bãi A cần giảm tối thiểu {data.shortage} xe. Điều hướng 85 xe: 60 sang Bãi C, 25 sang Bãi B.</p>{data.plan.before.slice(0, 3).map((before, index) => <AllocationRow after={data.plan.after[index]} before={before} key={before.lotId} />)}<p className="allocation-result">Sau điều hướng: cả ba bãi đạt 96%, không bãi nào vượt 100%.</p></section>
    <section className="forecast-panel proposal-panel"><div><h2>Đề xuất vận hành <span className="ai-chip">AI</span></h2><ul><li>Điều hướng 85 xe từ Bãi A: <strong>60 sang Bãi C, 25 sang Bãi B</strong> trong khung {data.plan.window.from}–{data.plan.window.to}.</li><li>Mở <strong>{data.plan.gateActions[0].gate}</strong> sớm 15 phút ({data.plan.gateActions[0].openFrom} thay vì {data.plan.gateActions[0].normalOpen}).</li><li>Không chọn Bãi {data.plan.excluded[0].lotId} dù còn chỗ vì {data.plan.excluded[0].reason.toLowerCase()}.</li></ul></div><div className="approval-block"><span className="pending-badge">Chờ duyệt · v{data.plan.version}</span><button disabled={isApproving || Boolean(approvalMessage)} onClick={approve} type="button">{isApproving ? 'Đang duyệt…' : approvalMessage ? 'Đã duyệt' : 'Duyệt phương án'}</button></div></section>
    <div aria-live="polite">{approvalMessage && <p className="approval-success">{approvalMessage}</p>}{approvalError && <p className="approval-error">{approvalError}</p>}</div>
  </div>
}
