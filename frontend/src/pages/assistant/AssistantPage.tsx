import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { chatWithAssistant, type AssistantChatResponse, type AssistantTurn } from '../../shared/api/assistant'
import { useDashboardOverview } from '../../shared/api/dashboard'
import { getForecastOverview } from '../../shared/api/forecast'
import type { ForecastOverview } from '../../shared/types/forecast'
import { CampusMap } from '../../shared/components/CampusMap'
import './assistant.css'
import './assistant-match.css'

type View = 'overview' | 'analyzing' | 'result'
const question = 'Sáng mai có bãi nào nguy cơ quá tải không?'
function lotFromAnswer(answer: AssistantChatResponse | undefined, lots: Array<{ lotId: string; name: string }>) {
  const label = answer?.facts.find((fact) => /bãi|nhà xe/i.test(fact.label))?.label?.trim()
  if (!label) return undefined
  const exactName = lots.find((lot) => lot.name.toLocaleLowerCase() === label.toLocaleLowerCase())
  if (exactName) return exactName.lotId
  const code = label.match(/(?:bãi|nhà xe)\s+([a-z]+\d*)/i)?.[1]?.toUpperCase()
  return lots.find((lot) => lot.lotId.toUpperCase() === code)?.lotId
}
function renderMarkdown(text: string) {
  return text.split(/\r?\n/).map((line, index) => {
    const isBullet = /^[-*]\s+/.test(line)
    const lineText = isBullet ? line.replace(/^[-*]\s+/, '') : line
    const parts = lineText.split(/(\*\*.+?\*\*)/g).filter(Boolean)
    const content = parts.map((part, partIndex) => part.startsWith('**') && part.endsWith('**')
      ? <strong key={partIndex}>{part.slice(2, -2)}</strong>
      : <span key={partIndex}>{part}</span>)
    return isBullet ? <li key={index}>{content}</li> : <p key={index}>{content}</p>
  })
}

export function AssistantPage() {
  const { data: dashboard, error, isLoading } = useDashboardOverview()
  const [view, setView] = useState<View>('overview'); const [input, setInput] = useState(''); const [answer, setAnswer] = useState<AssistantChatResponse>(); const [chatError, setChatError] = useState(''); const [forecast, setForecast] = useState<ForecastOverview>(); const [forecastError, setForecastError] = useState('')
  const history = useRef<AssistantTurn[]>([]); const controller = useRef<AbortController | undefined>(undefined)
  useEffect(() => { if (view !== 'result' || !dashboard) return; const id = lotFromAnswer(answer, dashboard.lots) ?? dashboard.lots[0]?.lotId; if (!id) return; const abort = new AbortController(); setForecast(undefined); setForecastError(''); void getForecastOverview(id, abort.signal).then(setForecast).catch((reason: unknown) => { if (!abort.signal.aborted) setForecastError(reason instanceof Error ? reason.message : 'Không thể tải dự báo') }); return () => abort.abort() }, [answer, dashboard, view])
  const ask = async (value: string) => { const questionValue = value.trim(); if (!questionValue) return; controller.current?.abort(); const next = new AbortController(); controller.current = next; setInput(questionValue); setChatError(''); setAnswer(undefined); setView('analyzing'); try { const result = await chatWithAssistant(questionValue, history.current, next.signal); if (next.signal.aborted) return; history.current = [...history.current, { role: 'user' as const, content: questionValue }, { role: 'assistant' as const, content: result.answer }].slice(-8); setAnswer(result); setView('result') } catch (reason) { if (!next.signal.aborted) { setChatError(reason instanceof Error ? reason.message : 'Không thể nhận câu trả lời từ trợ lý.'); setView('result') } } }
  if (error && !dashboard) return <div className="assistant-page"><div className="screen-status" role="alert">Không thể tải dữ liệu khuôn viên.</div></div>
  if (isLoading || !dashboard) return <div className="assistant-page"><div className="screen-status">Đang tải dữ liệu khuôn viên…</div></div>
  const chart = forecast ? <div className="assistant-chart"><div className="forecast-legend"><span>80% Sắp đầy</span><span>100% Quá tải</span><span>120% Chạm trần</span></div><svg viewBox="0 0 600 180" aria-label="Biểu đồ dự báo">{forecast.points.length > 1 && <polyline points={forecast.points.map((point, index) => `${index * (600 / (forecast.points.length - 1))},${180 - Math.min(170, (point.forecast ?? 0) * 1.4)}`).join(' ')} />}</svg></div> : <p role="status">{forecastError || 'Đang tải dự báo…'}</p>
  return <div className="assistant-page"><section className="assistant-chat"><header><div><h1>Trợ lý <span className="ai-chip">AI</span></h1><p>Hỏi bất cứ điều gì về bãi xe</p></div><div>{view !== 'overview' && <button className="new-chat-button" type="button" onClick={() => { controller.current?.abort(); setView('overview'); setInput(''); setAnswer(undefined); setChatError(''); history.current = [] }}>← Sơ đồ khuôn viên</button>}<span className="alert-chip">{dashboard.overloadedLots} bãi cảnh báo</span><span className="time-chip">{dashboard.peakWindow}</span></div></header><div className="chat-body">{view === 'overview' ? <div className="assistant-empty"><div className="bot-avatar"><img alt="ParkFlow AI" src="/assets/parkflow-brand.png" /></div><strong>Trợ lý AI ParkFlow</strong><p>Đặt câu hỏi hoặc chọn gợi ý bên dưới</p>{[question, 'Tuần này bãi nào căng nhất, vào mấy giờ?', 'Sáng mai cần thêm bảo vệ ở cổng nào?'].map((item) => <button key={item} onClick={() => void ask(item)} type="button">{item}</button>)}</div> : <><div className="user-bubble">{input}</div>{view === 'analyzing' ? <div className="bot-bubble loading"><strong>AI đang phân tích dữ liệu…</strong><i /><i /></div> : <div className="bot-bubble result">{chatError ? <><strong role="alert">{chatError}</strong><button onClick={() => void ask(input)} type="button">Thử lại</button></> : <><span className="ai-chip">{answer?.source === 'mock' ? 'Dữ liệu mẫu' : 'AI'}</span><div className="assistant-markdown">{renderMarkdown(answer?.answer ?? '')}</div><div className="assistant-facts">{answer?.facts.map((fact) => <div className="assistant-fact" key={`${fact.label}-${fact.value}`}><strong>{fact.label}</strong><span>{fact.value}</span></div>)}</div>{answer?.suggestions.length ? <div className="assistant-suggestions">{answer.suggestions.map((suggestion) => <button key={suggestion} onClick={() => void ask(suggestion)} type="button">{suggestion}</button>)}</div> : null}<Link to="/rules">Tạo quy tắc từ đề xuất này</Link></>}</div>}</>}</div><form onSubmit={(event) => { event.preventDefault(); void ask(input) }}><input aria-label="Nhập câu hỏi" onChange={(event) => setInput(event.target.value)} placeholder="Nhập câu hỏi…" value={input} /><button aria-label="Gửi câu hỏi" disabled={!input.trim()} type="submit">→</button></form></section><section className="assistant-insight"><h2>{view === 'result' ? `Dự báo ${answer?.facts.find((fact) => /bãi|nhà xe/i.test(fact.label))?.label ?? 'bãi xe'} – Sáng mai ${dashboard.date}` : view === 'analyzing' ? 'AI đang phân tích dữ liệu…' : 'Khuôn viên hôm nay'}</h2>{error && <p className="assistant-data-warning" role="alert">Dữ liệu mới nhất chưa tải được, đang hiển thị lần cập nhật gần nhất.</p>}{view === 'overview' ? <><p className="assistant-data-note">Dữ liệu hệ thống · {dashboard.date} {dashboard.updatedAt} · {dashboard.totalVehicles.toLocaleString('vi-VN')} xe tại {dashboard.lots.length} bãi</p><CampusMap campus={dashboard.campus} lots={dashboard.lots} /></> : view === 'analyzing' ? <><div className="skeleton large" /><div className="skeleton large" /></> : <>{chart}<div className="assistant-proposal"><strong>Đề xuất vận hành</strong><p>{forecast?.pendingPlan?.reason ?? forecast?.plan?.reason ?? 'Chưa có đề xuất vận hành cho bãi này.'}</p><Link to="/forecast">Xem kế hoạch</Link></div></>}</section></div>
}
