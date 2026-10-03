import { useState, type ChangeEvent } from 'react'
import './import.css'
import './import-mapping.css'

type Step = 1 | 2 | 3
type Mapping = { column: string; field: string; confidence: number }

const logMappings: Mapping[] = [
  { column: 'ts_in', field: 'Thời điểm vào', confidence: 98 },
  { column: 'ts_out', field: 'Thời điểm ra', confidence: 97 },
  { column: 'gate_id', field: 'Cổng', confidence: 95 },
  { column: 'card_id', field: 'Mã thẻ sinh viên', confidence: 92 },
  { column: 'lot_id', field: 'Bãi', confidence: 88 },
  { column: 'vehicle_type', field: 'Loại xe', confidence: 79 },
]

const scheduleMappings: Mapping[] = [
  { column: 'class_id', field: 'Mã lớp', confidence: 98 },
  { column: 'building', field: 'Tòa nhà', confidence: 96 },
  { column: 'start_time', field: 'Giờ bắt đầu', confidence: 97 },
  { column: 'end_time', field: 'Giờ kết thúc', confidence: 96 },
  { column: 'students', field: 'Số sinh viên', confidence: 94 },
]

const previewRows = [
  ['2026-09-14 07:02:33', '2026-09-14 07:45:12', 'G1', 'a3f9…', 'Bãi A', 'Xe máy'],
  ['2026-09-14 07:08:11', '2026-09-14 16:30:00', 'G2', 'b7c2…', 'Bãi B', 'Xe máy'],
  ['2026-09-14 07:15:44', '2026-09-14 18:00:22', 'G1', 'e91d…', 'Bãi A', 'Xe đạp'],
  ['2026-09-14 07:21:09', '2026-09-14 17:12:55', 'G2', 'd8a4…', 'Bãi C', 'Xe máy'],
  ['2026-09-14 07:29:38', '2026-09-14 16:58:10', 'G1', 'f26e…', 'Bãi D', 'Xe máy'],
]

function Steps({ current }: { current: Step }) {
  return <ol className="import-steps" aria-label="Tiến độ nhập dữ liệu">{['Tải file lên', 'AI nhận diện cột', 'Xem trước & Xác nhận'].map((label, index) => { const step = index + 1; return <li className={step === current ? 'active' : step < current ? 'done' : ''} key={label}><span>{step < current ? '✓' : step}</span>{label}</li> })}</ol>
}

function UploadStep({ onSample, onInvalid }: { onSample: () => void; onInvalid: (message: string) => void }) {
  const handleFile = (kind: 'log' | 'schedule') => (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (!file) return
    const extension = file.name.split('.').pop()?.toLowerCase()
    const valid = kind === 'log' ? extension === 'csv' : extension === 'csv' || extension === 'xlsx'
    if (!valid) onInvalid(kind === 'log' ? 'Log quẹt thẻ cần định dạng CSV.' : 'Lịch học cần định dạng CSV hoặc XLSX.')
  }
  return <section className="upload-stage"><label className="upload-zone"><input accept=".csv" aria-label="Chọn file log quẹt thẻ" onChange={handleFile('log')} type="file" /><span className="upload-icon" aria-hidden="true">⇧</span><strong>Log quẹt thẻ (CSV)</strong><small>Kéo thả file hoặc <u>chọn từ máy tính</u></small></label><label className="upload-zone"><input accept=".csv,.xlsx" aria-label="Chọn file lịch học" onChange={handleFile('schedule')} type="file" /><span className="upload-icon calendar" aria-hidden="true">17</span><strong>Lịch học (CSV hoặc Excel)</strong><small>Kéo thả file hoặc <u>chọn từ máy tính</u></small></label><button className="sample-button" onClick={onSample} type="button">Dùng dữ liệu mẫu</button></section>
}

function MappingStep({ checked, onChecked, onNext }: { checked: boolean; onChecked: (value: boolean) => void; onNext: () => void }) {
  const [source, setSource] = useState<'log' | 'schedule'>('log')
  const mappings = source === 'log' ? logMappings : scheduleMappings
  return <section className="mapping-stage"><h2>AI nhận diện cột</h2><div className="mapping-tabs" role="tablist"><button aria-selected={source === 'log'} onClick={() => setSource('log')} role="tab" type="button">Log quẹt thẻ</button><button aria-selected={source === 'schedule'} onClick={() => setSource('schedule')} role="tab" type="button">Lịch học</button><span className="ai-chip">AI</span></div><div className="mapping-table"><div className="mapping-head"><span>Cột trong file</span><span>Trường hệ thống (AI đề xuất)</span><span>Độ tin cậy</span></div>{mappings.map((item) => <div className="mapping-row" key={item.column}><code>{item.column}</code><span className="mapping-field">{item.field}</span><span className={`confidence${item.confidence < 90 ? ' review' : ''}`}><i><b style={{ width: `${item.confidence}%` }} /></i>{item.confidence}%</span></div>)}</div><div className="mapping-actions">{source === 'log' && <label><input checked={checked} onChange={(event) => onChecked(event.target.checked)} type="checkbox" /> Tôi đã kiểm tra ánh xạ vehicle_type (79%)</label>}<button disabled={source === 'log' && !checked} onClick={onNext} type="button">Xem trước dữ liệu</button></div></section>
}

function PreviewStep({ onConfirm, confirmed }: { onConfirm: () => void; confirmed: boolean }) {
  return <section className="preview-stage"><div className="preview-kpis"><article><span>Tổng bản ghi</span><strong>24.381</strong></article><article><span>Khoảng thời gian</span><strong>24/08/2026 – 20/09/2026</strong></article><article><span>Cột đã ánh xạ</span><strong className="green">11 / 11</strong></article></div><div className="preview-table"><p>Xem trước log mẫu · mã thẻ đã ẩn danh</p><div className="table-scroll"><table><thead><tr>{['Thời điểm vào', 'Thời điểm ra', 'Cổng', 'Mã ẩn danh', 'Bãi', 'Loại xe'].map((value) => <th key={value}>{value}</th>)}</tr></thead><tbody>{previewRows.map((row) => <tr key={row.join('-')}>{row.map((value, index) => <td key={`${value}-${index}`}>{value}</td>)}</tr>)}</tbody></table></div></div><div className="preview-actions"><button className="restart-button" type="button">← Bắt đầu lại</button><button className="confirm-import" disabled={confirmed} onClick={onConfirm} type="button">{confirmed ? 'Đã xác nhận' : 'Xác nhận nhập dữ liệu'}</button></div>{confirmed && <p className="import-success" role="status">Đã nhập 24.381 bản ghi thành công.</p>}</section>
}

export function ImportPage() {
  const [step, setStep] = useState<Step>(1)
  const [error, setError] = useState('')
  const [checked, setChecked] = useState(false)
  const [confirmed, setConfirmed] = useState(false)
  return <div className="import-page"><header className="import-header"><h1>Nhập dữ liệu</h1><p>AI nhận diện và chuẩn hóa dữ liệu từ file của bạn</p><Steps current={step} /></header>{error && <div className="import-error" role="alert">{error}</div>}{step === 1 && <UploadStep onInvalid={setError} onSample={() => { setError(''); setStep(2) }} />}{step === 2 && <MappingStep checked={checked} onChecked={setChecked} onNext={() => setStep(3)} />}{step === 3 && <PreviewStep confirmed={confirmed} onConfirm={() => setConfirmed(true)} />}</div>
}
