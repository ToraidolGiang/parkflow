import { describe, expect, it } from 'vitest'
import { getStudentRecommendation } from './studentApi'

describe('student mock API', () => {
  it('returns the contract example for Nhà hành chính', async () => {
    const result = await getStudentRecommendation({ building: 'NHC', arrive: '07:10' }, 'session-1')
    expect(result).toMatchObject({ lotId: 'C', gate: 'EAST', openFrom: '06:45', expectedPct: 96 })
  })
})
