import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { chatWithAssistant, type AssistantChatResponse, type AssistantTurn } from '../../shared/api/assistant'
import { useDashboardOverview } from '../../shared/api/dashboard'
import { CampusMap } from '../../shared/components/CampusMap'
import './assistant.css'
import './assistant-match.css'

type View = 'overview' | 'analyzing' | 'result'
const question = 'Sáng mai có bãi nào nguy cơ quá tải không?'

export function AssistantPage() {
  const { data: dashboard, error, isLoading } = useDashboardOverview()
  const [view, setView] = useState<View>('overview')
  const [input, setInput] = useState('')
  const [answer, setAnswer] = useState<AssistantChatResponse>()
  const [chatError, setChatError] = useState('')
  const history = useRef<AssistantTurn[]>([])
  const controller = useRef<AbortController | undefined>(undefined)
  const ask = async (value: string) => {
    const questionValue = value.trim()
    if (!questionValue) return
    controller.current?.abort()
    const nextController = new AbortController()
    controller.current = nextController
    setInput(questionValue)
    setChatError('')
    setAnswer(undefined)
    setView('analyzing')
    try {
      const result = await chatWithAssistant(questionValue, history.current, nextController.signal)
      if (nextController.signal.aborted) return
      history.current = [...history.current, { role: 'user' as const, content: questionValue }, { role: 'assistant' as const, content: result.answer }].slice(-8)
      setAnswer(result)
      setView('result')
    } catch (reason) {
      if (nextController.signal.aborted) return
      setChatError(reason instanceof Error ? reason.message : 'Không thể nhận câu trả lời từ trợ lý.')
      setView('result')
    }
  }

  if (error && !dashboard) return <div className="assistant-page"><div className="screen-status" role="alert">Không thể tải dữ liệu khuôn viên.</div></div>
  if (isLoading || !dashboard) return <div className="assistant-page"><div className="screen-status">Đang tải dữ liệu khuôn viên…</div></div>

  return <div className="assistant-page">
    <section className="assistant-chat">
      <header><div><h1>Trợ lý <span className="ai-chip">AI</span></h1><p>Hỏi bất cứ điều gì về bãi xe</p></div><div>{view !== 'overview' && <button className="new-chat-button" type="button" onClick={() => { controller.current?.abort(); setView('overview'); setInput(''); setAnswer(undefined); setChatError(''); history.current = [] }}>← Sơ đồ khuôn viên</button>}<span className="alert-chip">{dashboard.overloadedLots} bãi cảnh báo</span><span className="time-chip">{dashboard.peakWindow}</span></div></header>
      <div className="chat-body">
        {view === 'overview' ? <div className="assistant-empty"><div className="bot-avatar"><img alt="ParkFlow AI" src="/assets/parkflow-brand.png" /></div><strong>Trợ lý AI ParkFlow</strong><p>Đặt câu hỏi hoặc chọn gợi ý bên dưới</p>{[question, 'Tuần này bãi nào căng nhất, vào mấy giờ?', 'Sáng mai cần thêm bảo vệ ở cổng nào?'].map((item) => <button key={item} onClick={() => void ask(item)} type="button">{item}</button>)}</div> : <><div className="user-bubble">{input}</div>{view === 'analyzing' ? <div className="bot-bubble loading"><strong>AI đang phân tích dữ liệu…</strong><i /><i /></div> : <div className="bot-bubble result">{chatError ? <><strong role="alert">{chatError}</strong><button onClick={() => void ask(input)} type="button">Thử lại</button></> : <><span className="ai-chip">{answer?.source === 'mock' ? 'Dữ liệu mẫu' : 'AI'}</span><p>{answer?.answer}</p>{answer?.facts.map((fact) => <p className="assistant-fact" key={`${fact.label}-${fact.value}`}><strong>{fact.label}:</strong> {fact.value}</p>)}{answer?.suggestions.length ? <div className="assistant-suggestions">{answer.suggestions.map((suggestion) => <button key={suggestion} onClick={() => void ask(suggestion)} type="button">{suggestion}</button>)}</div> : null}<Link to="/rules">Tạo quy tắc từ đề xuất này</Link></>}</div>}</>}
      </div>
      <form onSubmit={(event) => { event.preventDefault(); void ask(input) }}><input aria-label="Nhập câu hỏi" onChange={(event) => setInput(event.target.value)} placeholder="Nhập câu hỏi…" value={input} /><button aria-label="Gửi câu hỏi" disabled={!input.trim()} type="submit">→</button></form>
    </section>
    <section className="assistant-insight">
      <h2>{view === 'result' ? `Dự báo ${dashboard.lots[0]?.name ?? 'bãi xe'} – Sáng mai ${dashboard.date}` : view === 'analyzing' ? 'AI đang phân tích dữ liệu…' : 'Khuôn viên hôm nay'}</h2>
      {error && <p className="assistant-data-warning" role="alert">Dữ liệu mới nhất chưa tải được, đang hiển thị lần cập nhật gần nhất.</p>}
      {view === 'overview' ? <><p className="assistant-data-note">Dữ liệu hệ thống · {dashboard.date} {dashboard.updatedAt} · {dashboard.totalVehicles.toLocaleString('vi-VN')} xe tại {dashboard.lots.length} bãi</p><CampusMap campus={dashboard.campus} lots={dashboard.lots} /></> : view === 'analyzing' ? <><div className="skeleton large"/><div className="skeleton large"/></> : <><div className="assistant-chart"><span>120%</span><svg viewBox="0 0 600 180"><polyline points="0,155 70,120 140,75 220,28 300,50 380,82 460,130 530,145 600,110"/><polyline className="after" points="0,160 70,132 140,94 220,57 300,68 380,100 460,143 530,154 600,125"/></svg></div><div className="assistant-proposal"><strong>Đề xuất vận hành</strong><p>Điều hướng 85 xe khỏi Bãi A và mở Cổng phía Đông từ 06:45.</p></div></>}
    </section>
  </div>
}
