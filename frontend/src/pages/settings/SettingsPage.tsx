import { useState } from 'react'

import type { CampusConfig } from '../../shared/types/campus'
import { useCampusSettings } from './useCampusSettings'
import './settings.css'

function Field({ label, value, onChange, type = 'text' }: { label: string; value: string | number; onChange?: (value: string) => void; type?: string }) {
  return <label className="settings-field"><span>{label}</span><input type={type} value={value} onChange={(event) => onChange?.(event.target.value)} /></label>
}

function updateLot(config: CampusConfig, lotId: string, patch: Record<string, unknown>): CampusConfig {
  return { ...config, lots: config.lots.map((lot) => lot.id === lotId ? { ...lot, ...patch } : lot) }
}

export function SettingsPage() {
  const { config, loading, saving, error, success, load, update, save } = useCampusSettings()
  const [apiKey, setApiKey] = useState('')

  if (loading) return <div className="settings-page" role="status">Đang tải cấu hình</div>
  if (error && !config) return <div className="settings-page"><p className="settings-error" role="alert">{error}</p><button className="primary-action" type="button" onClick={() => void load()}>Thử lại</button></div>
  if (!config) return null

  const gateName = (id: string) => config.gates.find((gate) => gate.id === id)?.name ?? id
  const buildingName = (id: string) => config.buildings.find((building) => building.id === id)?.name ?? id

  return <div className="settings-page">
    <header className="settings-header"><div><h1>Cấu hình trường</h1><p>{config.school} · Cơ sở chính</p></div><div className="settings-header-actions"><label className="api-key-field"><span>API key</span><input aria-label="API key" type="password" value={apiKey} onChange={(event) => setApiKey(event.target.value)} placeholder="EVENTS_API_KEY" /></label><button className="primary-action" type="button" disabled={saving || !apiKey} onClick={() => void save(apiKey)}>{saving ? 'Đang lưu…' : 'Lưu cấu hình'}</button></div></header>
    <div className="settings-content">
      {error && <p className="settings-error" role="alert">{error}</p>}
      {success && <p className="settings-success" role="status">{success}</p>}
      <section className="settings-summary"><Field label="Tên trường" value={config.school} onChange={(value) => update((current) => ({ ...current, school: value }))} /></section>
      <section className="lots-section"><h2>Bãi xe · {config.lots.length} bãi</h2><div className="lots-form"><div className="lots-head"><span>Bãi</span><span>Thiết kế (xe)</span><span>Tối đa (xe)</span><span>Cổng vào</span><span>Tòa nhà gần</span></div>{config.lots.map((lot) => <div className="lot-form-row" key={lot.id}>
        <Field label={`${lot.id} tên`} value={lot.name} onChange={(value) => update((current) => updateLot(current, lot.id, { name: value }))} />
        <Field label={`${lot.id} thiết kế`} type="number" value={lot.capacity} onChange={(value) => update((current) => updateLot(current, lot.id, { capacity: Number(value) }))} />
        <Field label={`${lot.id} tối đa`} type="number" value={lot.max ?? lot.capacity} onChange={(value) => update((current) => updateLot(current, lot.id, { max: Number(value) }))} />
        <select aria-label={`${lot.id} cổng vào`} value={lot.gate} onChange={(event) => update((current) => updateLot(current, lot.id, { gate: event.target.value }))}>{config.gates.map((gate) => <option key={gate.id} value={gate.id}>{gate.name}</option>)}</select>
        <select aria-label={`${lot.id} tòa nhà gần`} value={lot.near} onChange={(event) => update((current) => updateLot(current, lot.id, { near: event.target.value }))}>{config.buildings.map((building) => <option key={building.id} value={building.id}>{building.name}</option>)}</select>
        <small>{gateName(lot.gate)} · {buildingName(lot.near)}</small>
      </div>)}</div></section>
      <div className="settings-lists"><section><h2>Danh sách cổng</h2><div>{config.gates.map((gate) => <span key={gate.id}>{gate.name}</span>)}</div></section><section><h2>Danh sách tòa nhà</h2><div>{config.buildings.map((building) => <span key={building.id}>{building.name}</span>)}</div></section></div>
      <section className="advanced-settings"><h2>Ưu tiên bãi thay thế</h2>{config.lots.map((lot) => <p key={lot.id}><strong>{lot.id}</strong> → {(lot.alternates ?? []).map((id) => config.lots.find((candidate) => candidate.id === id)?.name ?? id).join(' → ') || 'Không có'}</p>)}</section>
    </div>
  </div>
}
