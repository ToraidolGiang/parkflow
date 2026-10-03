import { useRef, useState } from 'react'
import { approvePlan, useForecastOverview } from '../../shared/api/forecast'
import { ApiError } from '../../shared/api/http'
import type { ForecastPoint, PlanLot } from '../../shared/types/forecast'
import './forecast.css'

function ForecastChart({ points, thresholds, lotName }: { points: ForecastPoint[]; thresholds: { warn: number; design: number; ceiling: number }; lotName: string }) {
  const width = 1020, height = 250, left = 48, right = 20, top = 18, bottom = 42
  const x = (index: number) => points.length < 2
    ? left + (width - left - right) / 2
    : left + index * ((width - left - right) / (points.length - 1))
  const y = (value: number) => top + ((130 - value) / 130) * (height - top - bottom)
  const line = (key: 'forecast' | 'afterPlan') => points.map((point, index) => `${x(index)},${y(point[key])}`).join(' ')
  return <div className="forecast-chart"><svg aria-label={`Biểu đồ dự báo lấp đầy ${lotName}`} role="img" viewBox={`0 0 ${width} ${height}`}>
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
  return <div className="allocation-row"><div className="allocation-title"><strong>{before.name}</strong><span>{delta > 0 ? '+' : ''}{delta} xe</span></div>
    <div className="allocation-measure"><span>Trước</span><div><i className={`allocation-bar ${before.pct >= 100 ? 'danger' : 'primary'}`} style={{ width: `${Math.min(before.pct, 120) / 1.2}%` }} /></div><b>{before.count}/{before.capacity} xe · {before.pct}%</b></div>
    <div className="allocation-measure"><span>Sau</span><div><i className="allocation-bar success" style={{ width: `${Math.min(after.pct, 120) / 1.2}%` }} /></div><b>{after.count}/{after.capacity} xe · {after.pct}%</b></div>
  </div>
}

export function ForecastPage() {
  const { data, error, isLoading, selectLot, refresh } = useForecastOverview()
  const [approvalMessage, setApprovalMessage] = useState('')
  const [approvalError, setApprovalError] = useState('')
  const [isApproving, setIsApproving] = useState(false)
  const [approvedBy, setApprovedBy] = useState('')
  const [option, setOption] = useState('')
  const approvalLock = useRef(false)
  const [approvedVersion, setApprovedVersion] = useState<number>()

  if (isLoading) return <div className="screen-status" role="status">Đang tải dự báo…</div>
  if (!data) return <div className="screen-status" role={error ? 'alert' : 'status'}><p>{error ? 'Không thể tải dữ liệu dự báo.' : 'Đang tải dự báo…'}</p>{error && <button onClick={refresh}>Thử lại</button>}</div>

  const plan = data.pendingPlan ?? data.plan

  const approve = async () => {
    if (approvalLock.current || !data.pendingPlan) return
    if (approvedVersion === data.pendingPlan.version) return
    approvalLock.current = true
    setIsApproving(true); setApprovalError('')
    try {
      const result = await approvePlan(data.pendingPlan.version, { approvedBy: approvedBy.trim(), ...(option ? { option } : {}) })
      setApprovedVersion(result.version)
      setApprovalMessage(`Đã duyệt phương án phiên bản ${result.version}.`)
      try { await refresh() }
      catch { setApprovalError('Đã duyệt thành công nhưng chưa tải lại được trạng thái. Hãy bấm Chạy dự báo để làm mới.') }
    } catch (reason) {
      if (reason instanceof ApiError && (reason.status === 404 || reason.status === 409)) {
        await refresh().catch(() => undefined)
        setApprovalError('Kế hoạch đã thay đổi, vui lòng kiểm tra lại.')
      } else if (reason instanceof ApiError && reason.status === 400) setApprovalError(reason.message)
      else setApprovalError('Không thể duyệt phương án. Vui lòng thử lại.')
    } finally { approvalLock.current = false; setIsApproving(false) }
  }

  const runForecast = async () => {
    try {
      await refresh()
      setApprovalError('')
    } catch {
      setApprovalError('Không thể tải lại dự báo. Vui lòng thử lại.')
    }
  }

  return <div className="forecast-page">
    <header className="forecast-header"><div><h1>Dự báo &amp; Đề xuất</h1><p>Phân tích xu hướng và đề xuất vận hành từ AI · cập nhật {data.generatedAt}</p></div><div className="forecast-actions"><select aria-label="Chọn bãi xe" onChange={(event) => selectLot(event.target.value)} value={data.selectedLotId}>{data.availableLots.map(lot => <option key={lot.id} value={lot.id}>{lot.name}</option>)}</select><button className="run-forecast" onClick={runForecast} type="button">Chạy dự báo</button></div></header>
    <section className="forecast-kpis" aria-label="Tổng quan dự báo">
      <article><span>Cao điểm sáng mai · {data.date}</span><strong className="orange">{data.peak.pct}%</strong><small>so với sức chứa thiết kế · {data.selectedLot}{plan ? ` · ${plan.window.from}–${plan.window.to}` : ''}</small></article>
      <article><span>Thiếu chỗ dự kiến</span><strong className="red">{data.shortage} xe</strong><small>{data.selectedLot} · cần giảm tối thiểu {data.shortage} xe</small></article>
      <article><span>Bãi có độ lấp đầy thấp nhất</span><strong className="green">{data.lowestLot?.name ?? 'Chưa có kế hoạch'}</strong><small>{data.lowestLot ? `Dự kiến đỉnh ${data.lowestLot.pct}% · chung ${data.lowestLot.gate}` : 'Chưa có dữ liệu phân bổ'}</small></article>
    </section>
    {data.qualityFlags.map((flag) => <div className={`quality-flag ${flag.severity}`} role="status" key={flag.id}><strong>Cờ chất lượng dữ liệu</strong><span>{flag.message}</span></div>)}
    <section className="forecast-panel chart-panel"><div className="panel-heading"><h2>Biểu đồ dự báo lấp đầy</h2><span>{data.selectedLot}</span></div><ForecastChart lotName={data.selectedLot} points={data.points} thresholds={data.thresholds} /></section>
    {!plan ? <section className="forecast-panel empty-plan"><h2>Chưa có kế hoạch</h2><p>Dự báo vẫn sẵn sàng; chưa có phương án phân luồng cho ngày này.</p></section> : <><section className="forecast-panel allocation-panel"><h2>Trước / sau điều hướng · {data.date}</h2><p>{plan.reason}</p>{plan.before.map((before) => { const after = plan.after.find(item => item.lotId === before.lotId); return after ? <AllocationRow after={after} before={before} key={before.lotId} /> : null })}</section>
    <section className="forecast-panel proposal-panel"><div><h2>Đề xuất vận hành <span className="ai-chip">AI</span></h2><ul>{plan.moves.map(move => <li key={`${move.from}-${move.to}`}>Điều hướng <strong>{move.vehicles} xe từ {move.fromName} sang {move.toName}</strong>.</li>)}{plan.gateActions.map(action => <li key={action.gate}>Mở <strong>{action.gateName}</strong> lúc {action.openFrom} thay vì {action.normalOpen}.</li>)}{plan.excluded.map(item => <li key={item.lotId}>Không chọn {item.lotName} vì {item.reason.toLowerCase()}.</li>)}</ul></div><div className="approval-block"><span className="pending-badge">{data.pendingPlan && approvedVersion === data.pendingPlan.version ? 'Đã duyệt' : data.pendingPlan ? 'Chờ duyệt' : 'Đang áp dụng'} · v{plan.version}{data.pendingPlan && approvedVersion === data.pendingPlan.version ? ' (chờ đồng bộ)' : ''}</span>{data.pendingPlan && approvedVersion !== data.pendingPlan.version && <><label>Người duyệt<input aria-label="Người duyệt" onChange={event => setApprovedBy(event.target.value)} value={approvedBy} /></label>{data.pendingPlan.shortfall > 0 && <label>Phương án xử lý<select aria-label="Phương án xử lý" onChange={event => setOption(event.target.value)} value={option}><option value="">Chọn phương án</option>{data.pendingPlan.shortfallOptions?.map(item => <option key={item.code} value={item.code}>{item.label}</option>)}</select></label>}<button disabled={isApproving || !approvedBy.trim() || (data.pendingPlan.shortfall > 0 && !option)} onClick={approve} type="button">{isApproving ? 'Đang duyệt…' : 'Duyệt phương án'}</button></>}</div></section></>}
    <div aria-live="polite">{approvalMessage && <p className="approval-success">{approvalMessage}</p>}{approvalError && <p className="approval-error">{approvalError}</p>}</div>
  </div>
}
