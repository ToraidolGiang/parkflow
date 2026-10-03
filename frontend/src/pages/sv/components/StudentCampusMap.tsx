import type { LotStatus } from '../../../shared/types/dashboard'
export function StudentCampusMap({ lots, highlightedLotId, showRoute = false }: { lots: LotStatus[]; highlightedLotId: string; showRoute?: boolean }) {
  const ordered = ['A','B','D','C'].map((id) => lots.find((lot) => lot.lotId === id)).filter(Boolean) as LotStatus[]
  return <div className="sv-map" aria-label="Sơ đồ khuôn viên"><div className="sv-map-grid">{ordered.map((lot) => <div className={`sv-lot ${lot.status} ${lot.lotId === highlightedLotId ? 'highlighted' : ''}`} key={lot.lotId}><strong>{lot.name}</strong><span>{lot.percentage}%</span><small>{lot.nearby}</small>{lot.lotId === highlightedLotId && <em>Được gợi ý</em>}</div>)}<i className="sv-road-v"/><i className="sv-road-h"/>{showRoute && <div className="route-line" aria-label="Tuyến từ cổng tới bãi">➜ ─ ┐<br />&nbsp; ↓</div>}<b className="sv-gate">Cổng phía Đông</b></div></div>
}
