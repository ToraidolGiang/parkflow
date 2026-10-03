import type { BuildingId, StudentSelection } from '../types'

export const SELECTION_KEY = 'parkflow.student.selection'
const SESSION_KEY = 'parkflow.student.session'
const buildings: BuildingId[] = ['NHC', 'TV', 'GDA', 'KTT', 'H1', 'H2', 'H3', 'G3', 'A1', 'A2', 'TN']
const timePattern = /^([01]\d|2[0-3]):[0-5]\d$/

export function isStudentSelection(value: unknown): value is StudentSelection {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Record<string, unknown>
  return buildings.includes(candidate.building as BuildingId) && typeof candidate.arrive === 'string' && timePattern.test(candidate.arrive)
}

export function readStoredSelection(): StudentSelection | null {
  try { const value = JSON.parse(localStorage.getItem(SELECTION_KEY) ?? 'null'); return isStudentSelection(value) ? value : null } catch { return null }
}
export function writeStoredSelection(value: StudentSelection) { localStorage.setItem(SELECTION_KEY, JSON.stringify(value)) }
export function getOrCreateSessionId() {
  const current = sessionStorage.getItem(SESSION_KEY)
  if (current) return current
  const id = globalThis.crypto?.randomUUID?.() ?? `sv-${Date.now()}-${Math.random().toString(36).slice(2)}`
  sessionStorage.setItem(SESSION_KEY, id)
  return id
}
