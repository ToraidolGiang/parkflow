import { useState, type ChangeEvent } from 'react'
import { ApiError, mockApiEnabled } from '../../shared/api/http'
import { mapColumns } from '../../shared/api/import'
import type { ImportKind, MappingResult } from '../../shared/types/import'
import { parseCsvPreview, type CsvPreview } from './csvPreview'
import './import.css'
import './import-mapping.css'

type Step = 1 | 2 | 3
type Dataset = CsvPreview & { kind: ImportKind; fileName: string }

const fieldLabels: Record<string, string> = {
  time_in: 'Thời điểm vào', time_out: 'Thời điểm ra', gate_id: 'Cổng', card_id: 'Mã thẻ sinh viên',
  lot_id: 'Bãi', vehicle_type: 'Loại xe', class_id: 'Mã lớp', building: 'Tòa nhà',
  start_time: 'Giờ bắt đầu', end_time: 'Giờ kết thúc', students: 'Số sinh viên',
}

const sampleDatasets: Dataset[] = [
  { kind: 'swipe_log', fileName: 'log-mau.csv', headers: ['ts_in', 'ts_out', 'gate_id', 'card_id', 'lot_id', 'vehicle_type'], sampleRows: [
    ['2026-09-14 07:02:33', '2026-09-14 07:45:12', 'G1', 'a3f9…', 'A1', 'Xe máy'],
    ['2026-09-14 07:08:11', '2026-09-14 16:30:00', 'G2', 'b7c2…', 'B1', 'Xe máy'],
  ] },
  { kind: 'schedule', fileName: 'lich-hoc-mau.csv', headers: ['class_id', 'building', 'start_time', 'end_time', 'students'], sampleRows: [
    ['CNTT-01', 'H1', '07:00', '09:45', '80'],
  ] },
]

function Steps({ current }: { current: Step }) {
  return <ol className="import-steps" aria-label="Tiến độ nhập dữ liệu">{['Tải file lên', 'AI nhận diện cột', 'Xem trước & Xác nhận'].map((label, index) => { const step = index + 1; return <li className={step === current ? 'active' : step < current ? 'done' : ''} key={label}><span>{step < current ? '✓' : step}</span>{label}</li> })}</ol>
}

function UploadStep({ onFile, onSample, onInvalid }: { onFile: (kind: ImportKind, file: File) => void; onSample: () => void; onInvalid: (message: string) => void }) {
  const handleFile = (kind: ImportKind) => (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (!file) return
    const extension = file.name.split('.').pop()?.toLowerCase()
    const valid = kind === 'swipe_log' ? extension === 'csv' : extension === 'csv' || extension === 'xlsx'
    if (!valid) return onInvalid(kind === 'swipe_log' ? 'Log quẹt thẻ cần định dạng CSV.' : 'Lịch học cần định dạng CSV hoặc XLSX.')
    onFile(kind, file)
  }
  return <section className="upload-stage"><label className="upload-zone"><input accept=".csv" aria-label="Chọn file log quẹt thẻ" onChange={handleFile('swipe_log')} type="file" /><span className="upload-icon" aria-hidden="true">⇧</span><strong>Log quẹt thẻ (CSV)</strong><small>Kéo thả file hoặc <u>chọn từ máy tính</u></small></label><label className="upload-zone"><input accept=".csv,.xlsx" aria-label="Chọn file lịch học" onChange={handleFile('schedule')} type="file" /><span className="upload-icon calendar" aria-hidden="true">17</span><strong>Lịch học (CSV hoặc Excel)</strong><small>Kéo thả file hoặc <u>chọn từ máy tính</u></small></label><button className="sample-button" onClick={onSample} type="button">Dùng dữ liệu mẫu</button></section>
}

function MappingStep({ datasets, results, selected, onSelected, checked, onChecked, onNext }: { datasets: Dataset[]; results: MappingResult[]; selected: number; onSelected: (value: number) => void; checked: boolean; onChecked: (value: boolean) => void; onNext: () => void }) {
  const result = results[selected]
  const lowConfidence = result.mappings.filter((item) => item.confidence < 0.8)
  return <section className="mapping-stage"><h2>AI nhận diện cột</h2><div className="mapping-tabs" role="tablist">{datasets.map((dataset, index) => <button aria-selected={selected === index} key={dataset.kind} onClick={() => onSelected(index)} role="tab" type="button">{dataset.kind === 'swipe_log' ? 'Log quẹt thẻ' : 'Lịch học'}</button>)}<span className="ai-chip">AI</span>{result.fallback && <span className="fallback-chip">Dữ liệu mẫu</span>}</div><div className="mapping-table"><div className="mapping-head"><span>Cột trong file</span><span>Trường hệ thống (AI đề xuất)</span><span>Độ tin cậy</span></div>{result.mappings.map((item) => { const percent = Math.round(item.confidence * 100); return <div className="mapping-row" key={item.column}><code>{item.column}</code><span className="mapping-field">{fieldLabels[item.field] ?? item.field}</span><span className={`confidence${item.confidence < 0.8 ? ' review' : ''}`}><i><b style={{ width: `${percent}%` }} /></i>{percent}%</span></div> })}</div><div className="mapping-actions">{lowConfidence.length > 0 && <label><input checked={checked} onChange={(event) => onChecked(event.target.checked)} type="checkbox" /> Tôi đã kiểm tra ánh xạ {lowConfidence.map((item) => item.column).join(', ')} ({lowConfidence.map((item) => `${Math.round(item.confidence * 100)}%`).join(', ')})</label>}<button disabled={lowConfidence.length > 0 && !checked} onClick={onNext} type="button">Xem trước dữ liệu</button></div></section>
}

function PreviewStep({ dataset, mock, onRestart }: { dataset: Dataset; mock: boolean; onRestart: () => void }) {
  const [confirmed, setConfirmed] = useState(false)
  return <section className="preview-stage">{mock && <p className="fallback-banner">Dữ liệu mẫu — chưa gửi dữ liệu thật lên hệ thống.</p>}<div className="preview-kpis"><article><span>Dòng xem trước</span><strong>{dataset.sampleRows.length}</strong></article><article><span>Tệp dữ liệu</span><strong>{dataset.fileName}</strong></article><article><span>Cột đã ánh xạ</span><strong className="green">{dataset.headers.length} / {dataset.headers.length}</strong></article></div><div className="preview-table"><p>Xem trước dữ liệu · chỉ xử lý trong trình duyệt</p><div className="table-scroll"><table><thead><tr>{dataset.headers.map((value) => <th key={value}>{value}</th>)}</tr></thead><tbody>{dataset.sampleRows.map((row, rowIndex) => <tr key={rowIndex}>{row.map((value, index) => <td key={`${index}-${value}`}>{value || '—'}</td>)}</tr>)}</tbody></table></div></div><div className="preview-actions"><button className="restart-button" onClick={onRestart} type="button">← Bắt đầu lại</button><button className="confirm-import" disabled={!mock || confirmed} onClick={() => setConfirmed(true)} type="button">{confirmed ? 'Đã hoàn tất minh họa' : mock ? 'Hoàn tất bản minh họa' : 'Chưa thể nhập dữ liệu'}</button></div><p className="import-notice" role="status">{mock ? confirmed ? 'Đã hoàn tất kịch bản bằng dữ liệu mẫu; không có dữ liệu thật nào được nhập.' : 'Kịch bản dữ liệu mẫu đã sẵn sàng để trình diễn.' : 'API nhập dữ liệu chưa được backend triển khai.'}</p></section>
}

export function ImportPage() {
  const [step, setStep] = useState<Step>(1)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [datasets, setDatasets] = useState<Dataset[]>([])
  const [results, setResults] = useState<MappingResult[]>([])
  const [selected, setSelected] = useState(0)
  const [checked, setChecked] = useState(false)

  const identify = async (nextDatasets: Dataset[]) => {
    setError(''); setLoading(true)
    try {
      const nextResults = await Promise.all(nextDatasets.map(({ kind, headers, sampleRows }) => mapColumns({ kind, headers, sampleRows })))
      setDatasets(nextDatasets); setResults(nextResults); setSelected(0); setChecked(false); setStep(2)
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : 'Không thể nhận diện cột. Vui lòng thử lại.')
    } finally { setLoading(false) }
  }

  const handleFile = async (kind: ImportKind, file: File) => {
    if (file.name.toLowerCase().endsWith('.xlsx')) {
      setError('Frontend chưa có bộ đọc XLSX; dùng CSV hoặc dữ liệu mẫu.')
      return
    }
    try {
      const preview = await parseCsvPreview(file)
      await identify([{ kind, fileName: file.name, ...preview }])
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Không thể đọc file CSV.') }
  }

  const restart = () => { setStep(1); setError(''); setDatasets([]); setResults([]); setChecked(false) }
  return <div className="import-page"><header className="import-header"><h1>Nhập dữ liệu</h1><p>AI nhận diện và chuẩn hóa dữ liệu từ file của bạn</p><Steps current={step} /></header>{error && <div className="import-error" role="alert">{error}</div>}{loading && <p className="import-loading" role="status">AI đang nhận diện cột…</p>}{step === 1 && !loading && <UploadStep onFile={handleFile} onInvalid={setError} onSample={() => identify(sampleDatasets)} />}{step === 2 && results.length > 0 && <MappingStep checked={checked} datasets={datasets} onChecked={setChecked} onNext={() => setStep(3)} onSelected={(value) => { setSelected(value); setChecked(false) }} results={results} selected={selected} />}{step === 3 && <PreviewStep dataset={datasets[selected]} mock={mockApiEnabled() || results[selected].fallback} onRestart={restart} />}</div>
}
