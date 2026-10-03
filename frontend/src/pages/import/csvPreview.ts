export type CsvPreview = { headers: string[]; sampleRows: string[][] }

function readText(file: File) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onerror = () => reject(reader.error ?? new Error('Không thể đọc file CSV.'))
    reader.onload = () => resolve(String(reader.result ?? ''))
    reader.readAsText(file, 'UTF-8')
  })
}

function parseRows(text: string) {
  const rows: string[][] = []
  let row: string[] = []
  let cell = ''
  let quoted = false

  for (let index = 0; index < text.length; index += 1) {
    const char = text[index]
    if (char === '"') {
      if (quoted && text[index + 1] === '"') { cell += '"'; index += 1 } else quoted = !quoted
    } else if (char === ',' && !quoted) {
      row.push(cell); cell = ''
    } else if ((char === '\n' || char === '\r') && !quoted) {
      if (char === '\r' && text[index + 1] === '\n') index += 1
      row.push(cell); rows.push(row); row = []; cell = ''
    } else cell += char
  }
  if (cell || row.length) { row.push(cell); rows.push(row) }
  return rows
}

export async function parseCsvPreview(file: File): Promise<CsvPreview> {
  const rows = parseRows((await readText(file)).replace(/^\uFEFF/, ''))
  const [headers = [], ...data] = rows
  if (!headers.length || headers.every((header) => !header.trim())) throw new Error('File CSV không có hàng tiêu đề.')
  return { headers, sampleRows: data.slice(0, 5) }
}
