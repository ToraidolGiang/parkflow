import { mockApiEnabled } from '../../pages/sv/api/http'
import { useState, type ReactNode } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { navigationItems, type NavigationItem } from '../../routes'
import './report.css'
import { SimulationControls } from '../components/SimulationControls'

function NavIcon({ type }: { type: NavigationItem['icon'] }) {
  const paths: Record<NavigationItem['icon'], ReactNode> = {
    assistant: <><circle cx="12" cy="12" r="7" /><circle cx="12" cy="12" r="2.5" /></>,
    import: <><circle cx="12" cy="12" r="7" /><path d="M12 8v8M8 12h8" /></>,
    dashboard: <><rect x="5" y="5" width="6" height="6" /><rect x="13" y="5" width="6" height="6" /><rect x="5" y="13" width="6" height="6" /><rect x="13" y="13" width="6" height="6" /></>,
    forecast: <><path d="m12 4 8 8-8 8-8-8 8-8Z" /><path d="m12 8 4 4-4 4-4-4 4-4Z" /></>,
    rules: <><circle cx="12" cy="12" r="7" /><path d="M9 12h6M12 9v6" /></>,
    settings: <><circle cx="12" cy="12" r="7" /><circle cx="12" cy="12" r="2" /></>,
  }
  return <svg aria-hidden="true" className="nav-icon" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" strokeWidth="1.6" viewBox="0 0 24 24">{paths[type]}</svg>
}

export function AppShell() {
  const [reportOpen, setReportOpen] = useState(false)
  return <div className="app-shell">
    <aside className="sidebar">
      <NavLink aria-label="Về Bảng điều khiển" className="brand-block" to="/">
        <span className="brand-mark"><img alt="" src="/assets/parkflow-brand.png" /></span>
        <span><strong>ParkFlow</strong><small>Điều phối bãi xe</small></span>
      </NavLink>
      <nav aria-label="Điều hướng chính" className="sidebar-nav">
        {navigationItems.map((item) => <NavLink key={item.path} className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`} end={item.path === '/'} to={item.path}><NavIcon type={item.icon} /><span>{item.label}</span></NavLink>)}
      </nav>
      <p className="sidebar-foot">Dữ liệu mẫu · 21/09/2026&nbsp; 08:45</p>
    </aside>
    <div className="workspace">
      <header className="topbar"><button className="export-button" onClick={() => setReportOpen(true)} type="button">Xuất báo cáo</button><button aria-label="Tìm kiếm" className="search-button" type="button">⌕</button></header>
      {!mockApiEnabled() && <SimulationControls />}
      <main className="main-content"><Outlet /></main>
    </div>
    {reportOpen && <><button aria-label="Đóng báo cáo" className="report-backdrop" onClick={() => setReportOpen(false)} type="button" /><aside aria-label="Báo cáo AI" aria-modal="true" className="report-drawer" role="dialog"><header><h2>Báo cáo AI <span className="ai-chip">AI</span></h2><button aria-label="Đóng báo cáo" onClick={() => setReportOpen(false)} type="button">×</button></header><div className="report-body"><p>Báo cáo minh họa · tuần 14–20/09/2026</p><section><h3>Tóm tắt tuần (14–20/09/2026)</h3><p>Tổng lượt ra/vào trong tuần: <strong>48.762 lượt</strong>. Tỷ lệ lấp đầy trung bình toàn hệ thống: <strong>72%</strong>. Có 3 ngày ghi nhận tình trạng quá tải cục bộ tại Bãi A.</p></section><section><h3>Bãi căng nhất: Bãi A</h3><p>Bãi A đạt đỉnh <strong>113%</strong> vào ngày 16/09/2026 lúc 08:00. Lượng xe vượt thiết kế trong 3/5 ngày học.</p></section><section><h3>Khung giờ cao điểm</h3><p>Khung 07:15–08:00 chiếm 34% tổng lưu lượng trong ngày. Đây là khung giờ cần ưu tiên điều hướng và tăng cường nhân lực cổng.</p></section><section><h3>Đề xuất vận hành tuần tới</h3><ol><li>Sáng mai, Bãi A dự kiến 565/500 xe (113%), cần giảm tối thiểu 65 xe.</li><li>Điều hướng 85 xe: 60 sang Bãi C, 25 sang Bãi B.</li><li>Mở Cổng phía Đông sớm 15 phút và bố trí thêm bảo vệ.</li><li>Thông báo cho ban quản trị và sinh viên từ 18:00 hôm nay.</li></ol></section><section className="report-comparison"><h3>Trước / sau điều hướng · 22/09/2026</h3>{[['Bãi A',113,96],['Bãi B',89,96],['Bãi C',81,96]].map(([lot,before,after]) => <div className="report-lot" key={lot}><strong>{lot}</strong><span>Trước <i><b className="before" style={{width:`${Number(before)*.75}%`}} /></i>{before}%</span><span>Sau <i><b className="after" style={{width:`${Number(after)*.75}%`}} /></i>{after}%</span></div>)}<p>Sau điều hướng: cả ba bãi đạt 96%, không bãi nào vượt 100%.</p></section></div><footer><button type="button">↻ Tạo lại</button><button type="button">⊕ Sao chép</button><button className="download-report" type="button">↓ Tải về</button></footer></aside></>}
  </div>
}
