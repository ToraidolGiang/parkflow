import { createCampusLookup } from '../api/campus'
import type { CampusConfig } from '../types/campus'
import type { LotStatus } from '../types/dashboard'

type CampusMapProps = {
  lots: LotStatus[]
  variant?: 'admin' | 'student'
  highlightedLotId?: string
  showRoute?: boolean
  campus?: CampusConfig
}

export function CampusMap({ lots, variant = 'admin', highlightedLotId, showRoute = false, campus }: CampusMapProps) {
  const lookup = campus ? createCampusLookup(campus) : undefined
  const lotOrder = campus ? new Map(campus.lots.map((lot, index) => [lot.id, index])) : undefined
  const orderedLots = lots
    .map((lot, index) => {
      const configured = lookup?.lot(lot.lotId)
      const gateId = configured?.gate ?? lot.gate
      const buildingId = configured?.near ?? lot.nearby
      return {
        ...lot,
        name: configured?.name ?? lot.name,
        gate: lookup?.gateName(gateId) ?? lot.gate,
        nearby: lookup?.buildingName(buildingId) ?? lot.nearby,
        svg: configured?.svg,
        sourceIndex: index,
      }
    })
    .sort((left, right) => (lotOrder?.get(left.lotId) ?? left.sourceIndex) - (lotOrder?.get(right.lotId) ?? right.sourceIndex))
  const gates = campus?.gates
    ?? [...new Set(orderedLots.map((lot) => lot.gate).filter(Boolean))].map((name) => ({ id: name, name, svg: undefined }))
  const highlightedGate = orderedLots.find((lot) => lot.lotId === highlightedLotId)?.gate ?? gates[0]?.name

  if (variant === 'student') {
    return <div className="sv-map" aria-label="Sơ đồ khuôn viên"><div className="sv-map-grid">
      {orderedLots.map((lot) => <div className={`sv-lot ${lot.status} ${lot.lotId === highlightedLotId ? 'highlighted' : ''}`} data-svg={lot.svg?.join(',')} key={lot.lotId}><strong>{lot.name}</strong><span>{lot.percentage}%</span><small>{lot.nearby}</small>{lot.lotId === highlightedLotId && <em>Được gợi ý</em>}</div>)}
      <i className="sv-road-v"/><i className="sv-road-h"/>{showRoute && <div className="route-line" aria-label="Tuyến từ cổng tới bãi">➜ ─ ┐<br />&nbsp; ↓</div>}{highlightedGate && <b className="sv-gate">{highlightedGate}</b>}
    </div></div>
  }
  return <div className="campus-map" aria-label="Sơ đồ khuôn viên và tình trạng các bãi xe">
    <p className="map-note">Sơ đồ minh họa · vị trí tương đối</p>
    <div className="map-grid">
      {orderedLots.map((lot) => <article className={`map-lot status-${lot.status}`} data-svg={lot.svg?.join(',')} key={lot.lotId}>
        <div className="map-lot-heading"><strong>{lot.name}</strong><span>{lot.percentage}%</span></div>
        <p>{lot.current} / {lot.capacity} xe</p><div className="nearby-building">▥ {lot.nearby}</div>
      </article>)}
      <span className="road road-vertical" aria-hidden="true" /><span className="road road-horizontal" aria-hidden="true" />
      {gates.map((gate, index) => {
        const coordinate = gate.svg
        const position = coordinate
          ? (coordinate[1] >= 350 ? 'gate-main' : 'gate-east')
          : (index === 0 ? 'gate-east' : index === 1 ? 'gate-main' : 'gate-extra')
        return <span className={`gate ${position}`} key={gate.id}>{gate.name}</span>
      })}
    </div>
    <div className="map-legend"><span><i className="legend-dot available" />Còn chỗ &lt;80%</span><span><i className="legend-dot near-full" />Sắp đầy 80–&lt;100%</span><span><i className="legend-dot overloaded" />Quá tải ≥100%</span><span><i className="legend-dot maximum" />Chạm trần ≥120%</span></div>
  </div>
}
