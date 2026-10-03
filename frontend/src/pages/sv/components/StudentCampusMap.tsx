import { CampusMap } from '../../../shared/components/CampusMap'
import { HuceCampusMap } from '../../../shared/components/HuceCampusMap'
import type { CampusConfig } from '../../../shared/types/campus'
import type { LotStatus } from '../../../shared/types/dashboard'
import { useStudentFlow } from '../state/StudentFlowContext'

type StudentCampusMapProps = {
  lots: LotStatus[]
  highlightedLotId: string
  showRoute?: boolean
}

export function StudentCampusMap({ lots, highlightedLotId, showRoute }: StudentCampusMapProps) {
  const { config, recommendation } = useStudentFlow()
  if (config?.campus) return <div><HuceCampusMap campus={config.campus as CampusConfig} lots={lots} highlightedLotId={highlightedLotId} route={showRoute ? recommendation?.route.path : undefined} /><small>Sơ đồ minh họa · cần xác minh lối xe thực tế</small></div>
  return <CampusMap lots={lots} variant="student" highlightedLotId={highlightedLotId} showRoute={showRoute} />
}
