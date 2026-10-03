import { beforeEach, describe, expect, it } from 'vitest'
import { getOrCreateSessionId, readStoredSelection, SELECTION_KEY } from './storage'

describe('student storage', () => {
  beforeEach(() => { localStorage.clear(); sessionStorage.clear() })

  it('rejects malformed and unknown stored selections', () => {
    localStorage.setItem(SELECTION_KEY, '{bad json')
    expect(readStoredSelection()).toBeNull()
    localStorage.setItem(SELECTION_KEY, JSON.stringify({ building: 'XYZ', arrive: '07:10' }))
    expect(readStoredSelection()).toBeNull()
  })

  it('keeps one session id for the browser tab', () => {
    expect(getOrCreateSessionId()).toBe(getOrCreateSessionId())
  })
})
