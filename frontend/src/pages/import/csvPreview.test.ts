import { describe, expect, it } from 'vitest'
import { parseCsvPreview } from './csvPreview'

describe('parseCsvPreview', () => {
  it('handles BOM, CRLF, quoted commas, escaped quotes and empty cells', async () => {
    const file = new File(['\uFEFF"card,masked",note,empty\r\nA,"said ""hello""",\r\nB,plain,value'], 'log.csv')
    await expect(parseCsvPreview(file)).resolves.toEqual({
      headers: ['card,masked', 'note', 'empty'],
      sampleRows: [['A', 'said "hello"', ''], ['B', 'plain', 'value']],
    })
  })

  it('only exposes five sample rows', async () => {
    const file = new File([`id\n${Array.from({ length: 7 }, (_, i) => i).join('\n')}`], 'log.csv')
    const result = await parseCsvPreview(file)
    expect(result.sampleRows).toHaveLength(5)
  })
})
