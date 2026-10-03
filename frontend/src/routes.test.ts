import { describe, expect, it } from 'vitest'
import { navigationItems } from './routes'

describe('ParkFlow route model', () => {
  it('exposes all six sidebar destinations in the corrected Figma order', () => {
    expect(navigationItems.map((item) => item.label)).toEqual([
      'Trợ lý',
      'Nhập dữ liệu',
      'Bảng điều khiển',
      'Dự báo & Đề xuất',
      'Quy tắc vận hành',
      'Cấu hình trường',
    ])
  })

  it('maps each sidebar destination to a stable route', () => {
    expect(navigationItems.map((item) => item.path)).toEqual([
      '/assistant',
      '/import',
      '/',
      '/forecast',
      '/rules',
      '/settings',
    ])
  })
})
