import { describe, expect, it } from 'vitest'
import { isSafeMapsUrl } from './mapsUrl'
describe('maps URL', () => {
  it.each([
    ['', false],
    ['javascript:alert(1)', false],
    ['http://www.google.com/maps/dir/place', false],
    ['https://google.com.evil.test/maps/dir/place', false],
    ['https://attacker@www.google.com/maps/dir/place', false],
    ['https://www.google.com/maps/search/place', false],
    ['https://www.google.com/maps/directory/place', false],
    ['https://www.google.com/maps/dir/?api=1&destination=21,105', true],
  ])('validates %s', (url, expected) => expect(isSafeMapsUrl(url)).toBe(expected))
})
