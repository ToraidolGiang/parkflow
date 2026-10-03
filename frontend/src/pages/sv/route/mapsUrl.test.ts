import { describe, expect, it } from 'vitest'
import { isSafeMapsUrl } from './mapsUrl'
describe('maps URL', () => { it.each([['',false],['javascript:alert(1)',false],['https://evil.example/maps',false],['https://www.google.com/maps/dir/?api=1&destination=21,105',true]])('validates %s', (url, expected) => expect(isSafeMapsUrl(url)).toBe(expected)) })
