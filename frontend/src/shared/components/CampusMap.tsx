import type { LotStatus } from '../types/dashboard'

type CampusMapProps = {
  lots: LotStatus[]
  variant?: 'admin' | 'student'
  highlightedLotId?: string
  showRoute?: boolean
}

export function CampusMap({ lots, variant = 'admin', highlightedLotId, showRoute = false }: CampusMapProps) {
  const orderedLots = ['A', 'B', 'D', 'C'].map((id) => lots.find((lot) => lot.lotId === id)).filter(Boolean) as LotStatus[]
  if (variant === 'student') {
    return <div className="sv-map" aria-label="Sơ đồ khuôn viên"><div className="sv-map-grid">
      {orderedLots.map((lot) => <div className={`sv-lot ${lot.status} ${lot.lotId === highlightedLotId ? 'highlighted' : ''}`} key={lot.lotId}><strong>{lot.name}</strong><span>{lot.percentage}%</span><small>{lot.nearby}</small>{lot.lotId === highlightedLotId && <em>Được gợi ý</em>}</div>)}
      <i className="sv-road-v"/><i className="sv-road-h"/>{showRoute && <div className="route-line" aria-label="Tuyến từ cổng tới bãi">➜ ─ ┐<br />&nbsp; ↓</div>}<b className="sv-gate">Cổng phía Đông</b>
    </div></div>
  }
  return <div className="campus-map" aria-label="Sơ đồ khuôn viên và tình trạng các bãi xe">
    <p className="map-note">Sơ đồ minh họa · vị trí tương đối</p>
    <div className="map-grid">
      {orderedLots.map((lot) => <article className={`map-lot status-${lot.status}`} key={lot.lotId}>
        <div className="map-lot-heading"><strong>{lot.name}</strong><span>{lot.percentage}%</span></div>
        <p>{lot.current} / {lot.capacity} xe</p><div className="nearby-building">▥ {lot.nearby}</div>
      </article>)}
      <span className="road road-vertical" aria-hidden="true" /><span className="road road-horizontal" aria-hidden="true" />
      <span className="gate gate-east">Cổng phía Đông</span><span className="gate gate-main">Cổng chính</span>
    </div>
    <div className="map-legend"><span><i className="legend-dot available" />Còn chỗ &lt;80%</span><span><i className="legend-dot near-full" />Sắp đầy 80–&lt;100%</span><span><i className="legend-dot overloaded" />Quá tải ≥100%</span><span><i className="legend-dot maximum" />Chạm trần ≥120%</span></div>
  </div>
}
