import { useCallback, useEffect, useState } from 'react'

import { getCampusConfig, updateCampusConfig } from '../../shared/api/campus'
import type { CampusConfig } from '../../shared/types/campus'

export function useCampusSettings() {
  const [config, setConfig] = useState<CampusConfig | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string>()
  const [success, setSuccess] = useState<string>()

  const load = useCallback(async (signal?: AbortSignal) => {
    setLoading(true)
    setError(undefined)
    try {
      setConfig(await getCampusConfig(signal))
    } catch (reason) {
      if (!signal?.aborted) setError(reason instanceof Error ? reason.message : 'Không thể tải cấu hình')
    } finally {
      if (!signal?.aborted) setLoading(false)
    }
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    void load(controller.signal)
    return () => controller.abort()
  }, [load])

  const update = useCallback((change: (current: CampusConfig) => CampusConfig) => {
    setConfig((current) => current ? change(current) : current)
    setSuccess(undefined)
    setError(undefined)
  }, [])

  const save = useCallback(async (apiKey: string) => {
    if (!config) return
    setSaving(true)
    setError(undefined)
    setSuccess(undefined)
    const controller = new AbortController()
    try {
      setConfig(await updateCampusConfig(config, apiKey, controller.signal))
      setSuccess('Đã lưu cấu hình')
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Không thể lưu cấu hình')
    } finally {
      setSaving(false)
    }
  }, [config])

  return { config, loading, saving, error, success, load, update, save }
}
