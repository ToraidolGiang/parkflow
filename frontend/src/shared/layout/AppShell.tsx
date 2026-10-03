import { useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { navigationItems } from '../../routes'

export function AppShell() {
  const [reportOpen, setReportOpen] = useState(false)
  return <div className="app-shell">
    <aside className="sidebar">
      <NavLink className="brand-block" to="/">ParkFlow</NavLink>
      <nav aria-label="Điều hướng chính" className="sidebar-nav">
        {navigationItems.map(({ path, label }) => <NavLink className="nav-link" end={path === '/'} key={path} to={path}>{label}</NavLink>)}
      </nav>
    </aside>
    <main className="main-content"><button type="button" onClick={() => setReportOpen(true)}>Xuất báo cáo</button><Outlet /></main>
    {reportOpen && <><button type="button" aria-label="Đóng báo cáo" onClick={() => setReportOpen(false)} /><aside role="dialog" aria-label="Báo cáo AI"><button type="button" aria-label="Đóng báo cáo" onClick={() => setReportOpen(false)}>×</button><h2>Báo cáo AI</h2><h3>Tóm tắt tuần (14–20/09/2026)</h3><p>Tổng hợp vận hành và đề xuất điều phối.</p></aside></>}
  </div>
}
