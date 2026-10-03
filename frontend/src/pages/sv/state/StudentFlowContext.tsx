import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { getStudentConfig, getStudentRecommendation } from '../api/studentApi'
import type { StudentConfig, StudentRecommendation, StudentSelection } from '../types'
import { getOrCreateSessionId, readStoredSelection, writeStoredSelection } from './storage'

type Flow = { selection: StudentSelection | null; config: StudentConfig | null; recommendation: StudentRecommendation | null; loading: boolean; error: string | null; choose: (v: StudentSelection) => void; retry: () => void }
const Context = createContext<Flow | null>(null)

export function StudentFlowProvider({ children }: { children: ReactNode }) {
  const [selection, setSelection] = useState(readStoredSelection)
  const [config, setConfig] = useState<StudentConfig | null>(null)
  const [recommendation, setRecommendation] = useState<StudentRecommendation | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    let active = true
    getStudentConfig().then((value) => { if (active) { setConfig(value); setError(null) } })
      .catch((reason: unknown) => { if (active) setError(reason instanceof Error ? reason.message : 'Không tải được cấu hình trường.') })
    return () => { active = false }
  }, [attempt])
  useEffect(() => {
    if (!selection || !config) return
    if (!config.buildings.some(b => b.id === selection.building)) { setSelection(null); return }
    let active = true; setLoading(true); setError(null)
    const refresh = () => getStudentRecommendation(selection, getOrCreateSessionId())
      .then((value) => { if (active) { setRecommendation(value); setError(null) } })
      .catch((reason: unknown) => { if (active) setError(reason instanceof Error ? reason.message : 'Không tải được gợi ý. Vui lòng thử lại.') })
      .finally(() => { if (active) setLoading(false) })
    // Schedule the next poll after the previous request completes; avoid overlapping requests.
    let timer: ReturnType<typeof setTimeout>
    const poll = async () => { await refresh(); if (active) timer = setTimeout(poll, 5000) }
    void poll()
    return () => { active = false; clearTimeout(timer) }
  }, [selection, config, attempt])
  const choose = (value: StudentSelection) => { writeStoredSelection(value); setSelection(value); setRecommendation(null) }
  return <Context.Provider value={{ selection, config, recommendation, loading, error, choose, retry: () => setAttempt((v) => v + 1) }}>{children}</Context.Provider>
}
// Shared with route pages; keeping the hook beside its provider makes this small feature self-contained.
// eslint-disable-next-line react-refresh/only-export-components
export function useStudentFlow() { const value = useContext(Context); if (!value) throw new Error('StudentFlowProvider is missing'); return value }
