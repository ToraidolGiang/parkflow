import type { CampusConfig } from '../types/campus'
import type { LotStatus } from '../types/dashboard'

type Props = { campus: CampusConfig; lots: LotStatus[]; highlightedLotId?: string; route?: [number, number][] }

const color = (pct: number) => pct >= 120 ? '#a91b4a' : pct >= 100 ? '#e34848' : pct >= 80 ? '#c56b17' : '#16875c'

export function HuceCampusMap({ campus, lots, highlightedLotId, route }: Props) {
  const box = campus.map?.viewBox ?? [0, 0, 720, 960]
  return <div className="huce-campus-map" aria-label="Sơ đồ khuôn viên HUCE">
    <svg viewBox={box.join(' ')} role="img" aria-label="Sơ đồ khuôn viên HUCE minh họa">
      <rect x="20" y="30" width={box[2] - 40} height={box[3] - 65} rx="30" fill="#edf3f6" />
      {campus.map?.courtyard && <rect {...campus.map.courtyard} rx="18" fill="#dceadf" />}
      {(campus.map?.roads ?? []).map((road, i) => <g key={i}><polyline points={road.map(p => p.join(',')).join(' ')} fill="none" stroke="#fff" strokeWidth="30" strokeLinejoin="round" /><polyline points={road.map(p => p.join(',')).join(' ')} fill="none" stroke="#c4d0d7" strokeWidth="2" strokeDasharray="6 7" /></g>)}
      {campus.buildings.map(b => <g key={b.id}><rect x={(b.svg?.[0] ?? 0) - 55} y={(b.svg?.[1] ?? 0) - 25} width="110" height="50" rx="8" fill="#163a60" stroke="#fff" strokeWidth="3" /><text x={b.svg?.[0]} y={(b.svg?.[1] ?? 0) + 7} textAnchor="middle" fill="white" fontSize="22" fontWeight="700">{b.name}</text></g>)}
      {route && <polyline points={route.map(p => p.join(',')).join(' ')} fill="none" stroke="#2478ea" strokeWidth="9" strokeLinecap="round" strokeLinejoin="round" aria-label="Tuyến từ cổng tới bãi" />}
      {campus.lots.map(l => { const s = lots.find(x => x.lotId === l.id); const p = s?.percentage ?? 0; const x = l.svg?.[0] ?? 0; const y = l.svg?.[1] ?? 0; return <g key={l.id} aria-label={l.name}>{l.id === highlightedLotId && <circle cx={x} cy={y} r="37" fill="#dbeaff" stroke="#2478ea" strokeWidth="3" />}<rect x={x - 29} y={y - 23} width="58" height="46" rx="13" fill={color(p)} stroke="white" strokeWidth="3" /><text x={x} y={y + 6} textAnchor="middle" fill="white" fontSize="20" fontWeight="800">{l.id}</text><title>{l.name}: {s ? `${s.current}/${s.capacity} xe` : 'Chưa có số liệu'}</title></g> })}
      {campus.gates.map(g => { const x = g.svg?.[0] ?? 0; const y = g.svg?.[1] ?? 0; return <g key={g.id}><rect x={x - 26} y={y - 7} width="52" height="14" rx="6" fill="#d49a36" /><text x={x} y={g.id === 'MAIN' ? y + 31 : y - 21} textAnchor="middle" fill="#906723" fontSize="19" fontWeight="700">{g.name}</text></g> })}
    </svg>
  </div>
}
