import { CampusMap } from '../../../shared/components/CampusMap'
import type { LotStatus } from '../../../shared/types/dashboard'

type StudentCampusMapProps = {
  lots: LotStatus[]
  highlightedLotId: string
  showRoute?: boolean
}

export function StudentCampusMap({ lots, highlightedLotId, showRoute }: StudentCampusMapProps) {
  return <CampusMap lots={lots} variant="student" highlightedLotId={highlightedLotId} showRoute={showRoute} />
}
