import { useState } from 'react'
import { Link } from 'react-router-dom'
import { CampusMap } from '../../shared/components/CampusMap'
import dashboard from '../../shared/mock/dashboard-overview.json'
import type { LotStatus } from '../../shared/types/dashboard'
import './assistant.css'
import './assistant-match.css'

type View = 'overview' | 'analyzing' | 'result'
const question = 'Sáng mai có bãi nào nguy cơ quá tải không?'

export function AssistantPage() {
  const [view, setView] = useState<View>('overview')
  const [input, setInput] = useState('')
  const ask = (value: string) => { setInput(value); setView('analyzing') }
  return <div className="assistant-page"><section className="assistant-chat"><header><div><h1>Trợ lý <span className="ai-chip">AI</span></h1><p>Hỏi bất cứ điều gì về bãi xe</p></div><div><span className="alert-chip">1 bãi cảnh báo</span><span className="time-chip">07:15–08:00</span></div></header><div className="chat-body">{view === 'overview' ? <div className="assistant-empty"><div className="bot-avatar"><img alt="ParkFlow AI" src="/assets/parkflow-brand.png" /></div><strong>Trợ lý AI ParkFlow</strong><p>Đặt câu hỏi hoặc chọn gợi ý bên dưới</p>{[question, 'Tuần này bãi nào căng nhất, vào mấy giờ?', 'Sáng mai cần thêm bảo vệ ở cổng nào?'].map((item) => <button key={item} onClick={() => ask(item)} type="button">{item}</button>)}</div> : <><div className="user-bubble">{input}</div>{view === 'analyzing' ? <div className="bot-bubble loading"><strong>AI đang phân tích dữ liệu…</strong><i /><i /><button onClick={() => setView('result')} type="button">Xem kết quả minh họa</button></div> : <div className="bot-bubble result"><span className="ai-chip">AI</span><p>Dựa trên dữ liệu dự báo, <strong>Bãi A có nguy cơ quá tải vào sáng mai (22/09/2026).</strong> Dự kiến đạt <strong>113%</strong> sức chứa trong khung 07:15–08:00, thiếu khoảng 65 chỗ.</p><p>Đề xuất điều hướng 85 xe: 60 sang Bãi C, 25 sang Bãi B; đồng thời mở Cổng phía Đông sớm 15 phút.</p><Link to="/rules">Tạo quy tắc từ đề xuất này</Link></div>}</>}</div><form onSubmit={(event) => { event.preventDefault(); if (input.trim()) ask(input) }}><input aria-label="Nhập câu hỏi" onChange={(event) => setInput(event.target.value)} placeholder="Nhập câu hỏi…" value={input} /><button aria-label="Gửi câu hỏi" type="submit">→</button></form></section><section className="assistant-insight"><h2>{view === 'result' ? 'Dự báo Bãi A – Sáng mai 22/09/2026' : view === 'analyzing' ? 'AI đang phân tích dữ liệu…' : 'Khuôn viên hôm nay'}</h2>{view === 'overview' ? <><p className="assistant-data-note">Dữ liệu mô phỏng · {dashboard.date} {dashboard.updatedAt} · 1.443 xe tại 4 bãi</p><CampusMap lots={dashboard.lots as LotStatus[]} /></> : view === 'analyzing' ? <><div className="skeleton large"/><div className="skeleton large"/></> : <><div className="assistant-chart"><span>120%</span><svg viewBox="0 0 600 180"><polyline points="0,155 70,120 140,75 220,28 300,50 380,82 460,130 530,145 600,110"/><polyline className="after" points="0,160 70,132 140,94 220,57 300,68 380,100 460,143 530,154 600,125"/></svg></div><div className="assistant-proposal"><strong>Đề xuất vận hành</strong><p>Điều hướng 85 xe khỏi Bãi A và mở Cổng phía Đông từ 06:45.</p></div></>}</section></div>
}
