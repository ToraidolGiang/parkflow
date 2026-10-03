import { CampusMap } from '../../../shared/components/CampusMap'
import type { LotStatus } from '../../../shared/types/dashboard'
import { useStudentFlow } from '../state/StudentFlowContext'

type StudentCampusMapProps = {
  lots: LotStatus[]
  highlightedLotId: string
  showRoute?: boolean
}

export function StudentCampusMap({ lots, highlightedLotId, showRoute }: StudentCampusMapProps) {
  const { config, recommendation } = useStudentFlow()
  if (config?.campus) return <div><svg viewBox="0 0 1000 700" role="img" aria-label="Sơ đồ khuôn viên HUCE minh họa" style={{ width: '100%' }}>
    <path d="M520 650 V165 H800 V40" fill="none" stroke="#dfe7ef" strokeWidth="28" />
    {config.campus.buildings.map(b => <g key={b.id}><rect x={b.svg[0] - 55} y={b.svg[1] - 24} width="110" height="48" rx="5" fill="#e6edf6" /><text x={b.svg[0]} y={b.svg[1] + 5} textAnchor="middle" fontSize="18">{b.name}</text></g>)}
    {showRoute && recommendation && <polyline points={recommendation.route.path.map(p => p.join(',')).join(' ')} fill="none" stroke="#087d53" strokeWidth="8" aria-label="Tuyến từ cổng tới bãi" />}
    {config.campus.lots.map(l => <g key={l.id}><circle cx={l.svg[0]} cy={l.svg[1]} r="22" fill={l.id === highlightedLotId ? '#087d53' : '#2666b0'} /><text x={l.svg[0]} y={l.svg[1] + 6} textAnchor="middle" fill="white" fontSize="16">{l.id}</text><title>{l.name}</title></g>)}
    {config.campus.gates.map(g => <text key={g.id} x={g.svg[0]} y={g.svg[1] + 28} textAnchor="middle" fontSize="18">{g.name}</text>)}
  </svg><small>Sơ đồ minh họa · cần xác minh lối xe thực tế</small></div>
  return <CampusMap lots={lots} variant="student" highlightedLotId={highlightedLotId} showRoute={showRoute} />
}
