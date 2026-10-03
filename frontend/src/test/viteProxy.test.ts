import { describe, expect, it } from 'vitest'
import { resolveApiProxyTarget } from '../shared/config/viteProxy'

describe('Vite API proxy target', () => {
  it('uses an explicit backend target when port 8080 is unavailable', () => {
    expect(resolveApiProxyTarget({ VITE_API_PROXY_TARGET: 'http://localhost:8081' }))
      .toBe('http://localhost:8081')
  })

  it('keeps the repository default for other machines', () => {
    expect(resolveApiProxyTarget({})).toBe('http://localhost:8080')
  })
})
