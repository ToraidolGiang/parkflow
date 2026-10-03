export type ImportKind = 'swipe_log' | 'schedule'

export type ColumnMapping = {
  column: string
  field: string
  confidence: number
}

export type MappingInput = {
  kind: ImportKind
  headers: string[]
  sampleRows: string[][]
}

export type MappingResponse = {
  mappings: ColumnMapping[]
  source: string
}

export type MappingResult = MappingResponse & {
  fallback: boolean
}
