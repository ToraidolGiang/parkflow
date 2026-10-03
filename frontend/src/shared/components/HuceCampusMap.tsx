import type { CampusGeometry } from '../types/campus'
import type { LotStatus } from '../types/dashboard'
import './huce-campus-map.css'

type Props = { campus: CampusGeometry; lots: LotStatus[]; highlightedLotId?: string; route?: [number, number][]; compact?: boolean }
const tone = (pct: number) => pct >= 120 ? '#a91b4a' : pct >= 100 ? '#e34848' : pct >= 80 ? '#c56b17' : '#16875c'

export function HuceCampusMap({ campus, lots, highlightedLotId, route, compact }: Props) {
  const box = campus.map?.viewBox ?? [0, 0, 1000, 700]
  return <div className={`huce-map ${compact ? 'huce-map-compact' : ''}`}>
    <div className="huce-map-heading"><span>KHUÔN VIÊN HUCE</span><small>Sơ đồ vị trí tương đối</small></div>
    <div className="huce-map-layout">
      <svg viewBox={box.join(' ')} role="img" aria-label="Sơ đồ khuôn viên HUCE minh họa">
        <rect x="20" y="30" width={box[2] - 40} height={box[3] - 65} rx="30" fill="#edf3f6" />
        {campus.map?.courtyard && <g><rect {...campus.map.courtyard} rx="18" fill="#dceadf" /><text x={campus.map.courtyard.x + campus.map.courtyard.width / 2} y={campus.map.courtyard.y + campus.map.courtyard.height / 2} textAnchor="middle" className="huce-courtyard-label">SÂN TRƯỜNG</text></g>}
        {(campus.map?.roads ?? []).map((road, i) => <g data-testid={`huce-road-${i}`} key={i}><polyline points={road.map(p => p.join(',')).join(' ')} fill="none" stroke="#fff" strokeWidth="30" strokeLinejoin="round" /><polyline points={road.map(p => p.join(',')).join(' ')} fill="none" stroke="#c4d0d7" strokeWidth="2" strokeDasharray="6 7" /></g>)}
        {campus.buildings.map(b => <g key={b.id}><rect {...(b.footprint ?? { x: b.svg[0] - 55, y: b.svg[1] - 25, width: 110, height: 50 })} rx="8" fill="#163a60" stroke="#fff" strokeWidth="3" /><text x={b.svg[0]} y={b.svg[1] + 7} textAnchor="middle" className="huce-building-label">{b.name}</text></g>)}
        {route && <polyline points={route.map(p => p.join(',')).join(' ')} fill="none" stroke="#2478ea" strokeWidth="9" strokeLinecap="round" strokeLinejoin="round" aria-label="Tuyến từ cổng tới bãi" />}
        {campus.lots.map(l => {
          const status = lots.find(s => s.lotId === l.id)
          const selected = l.id === highlightedLotId
          return <g key={l.id} aria-label={`${l.name}${selected ? ', được gợi ý' : ''}`}>
            {selected && <circle cx={l.svg[0]} cy={l.svg[1]} r="37" fill="#dbeaff" stroke="#2478ea" strokeWidth="3" />}
            <rect x={l.svg[0] - 29} y={l.svg[1] - 23} width="58" height="46" rx="13" fill={status ? tone(status.percentage) : '#66778b'} stroke="white" strokeWidth="3" />
            <text x={l.svg[0]} y={l.svg[1] - (status ? 2 : -6)} textAnchor="middle" className="huce-parking-label">{l.id}</text>{status && <text x={l.svg[0]} y={l.svg[1] + 15} textAnchor="middle" className="huce-parking-pct">{Math.round(status.percentage)}%</text>}
            <title>{l.name}: {status ? `${status.current}/${status.capacity} xe` : 'Chưa có số liệu'}</title>
          </g>
        })}
        {campus.gates.map(g => <g key={g.id}><rect x={g.svg[0] - 26} y={g.svg[1] - 7} width="52" height="14" rx="6" fill="#d49a36" /><text x={g.svg[0]} y={g.svg[1] + (g.id === 'MAIN' ? 31 : -21)} textAnchor="middle" className="huce-gate-label">{g.name}</text></g>)}
      </svg>
      <div className="huce-lot-list" aria-label="Tình trạng nhà xe">
        <p className="huce-list-title">NHÀ XE</p>
        {campus.lots.map(l => {
          const s = lots.find(lot => lot.lotId === l.id)
          return <div key={l.id} className={`huce-lot-detail ${l.id === highlightedLotId ? 'is-selected' : ''}`}>
            <span className="huce-lot-code" style={{ background: s ? tone(s.percentage) : '#66778b' }}>{l.id}</span>
            <div><strong>{l.name}</strong><small>{s ? `${s.current.toLocaleString('vi-VN')} / ${s.capacity.toLocaleString('vi-VN')} xe · ${s.percentage}%` : 'Chưa có số liệu'}</small>{l.id === highlightedLotId && <em>Được gợi ý</em>}</div>
          </div>
        })}
        <p className="huce-map-tip">A/B: hai hầm sau G3<br />C1/C2: khu nhà xe quanh H3</p>
      </div>
    </div>
    <div className="huce-legend"><span><i style={{ background: tone(0) }} />Còn chỗ</span><span><i style={{ background: tone(80) }} />Từ 80%</span><span><i style={{ background: tone(100) }} />Từ 100%</span><span><i style={{ background: tone(120) }} />Từ 120%</span></div>
  </div>
}
