export type Coordinate = [number, number]
export type CampusFootprint = { x: number; y: number; width: number; height: number }

export type CampusThresholds = {
  yellow?: number
  warn?: number
  over?: number
  target?: number
  targetMax?: number
  ceiling?: number
  [key: string]: number | undefined
}

export type CampusGate = {
  id: string
  name: string
  svg?: Coordinate
  [key: string]: unknown
}

export type CampusBuilding = {
  id: string
  name: string
  nearLot: string
  svg?: Coordinate
  footprint?: CampusFootprint
  [key: string]: unknown
}

export type CampusLot = {
  id: string
  name: string
  capacity: number
  max?: number
  gate: string
  near: string
  alternates?: string[]
  svg?: Coordinate
  [key: string]: unknown
}

export type CampusRoute = {
  gate: string
  lot: string
  path?: Coordinate[]
  steps?: string[]
  walkMinutes?: Record<string, number>
  [key: string]: unknown
}

export type CampusConfig = {
  school: string
  thresholds: CampusThresholds
  gates: CampusGate[]
  buildings: CampusBuilding[]
  lots: CampusLot[]
  routes: CampusRoute[]
  [key: string]: unknown
}

export type CampusLookup = {
  lot: (id: string) => CampusLot | undefined
  gate: (id: string) => CampusGate | undefined
  building: (id: string) => CampusBuilding | undefined
  lotName: (id: string) => string
  gateName: (id: string) => string
  buildingName: (id: string) => string
}

export type CampusGeometry = {
  buildings: Array<Pick<CampusBuilding, 'id' | 'name'> & { svg: Coordinate; footprint?: CampusFootprint }>
  lots: Array<Pick<CampusLot, 'id' | 'name'> & { svg: Coordinate }>
  gates: Array<Pick<CampusGate, 'id' | 'name'> & { svg: Coordinate }>
  map?: CampusMapGeometry
}

export type CampusMapGeometry = {
  viewBox: [number, number, number, number]
  roads: Coordinate[][]
  courtyard: CampusFootprint
}

export type PartialCampusGeometry = {
  buildings?: CampusBuilding[]
  lots?: CampusLot[]
  gates?: CampusGate[]
  map?: Partial<CampusMapGeometry> & { width?: number; height?: number }
}
