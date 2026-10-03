import { NavLink, Outlet } from 'react-router-dom'
import { navigationItems } from '../../routes'

export function AppShell() {
  return <div className="app-shell">
    <aside className="sidebar">
      <NavLink className="brand-block" to="/">ParkFlow</NavLink>
      <nav aria-label="Điều hướng chính" className="sidebar-nav">
        {navigationItems.map(({ path, label }) => <NavLink className="nav-link" end={path === '/'} key={path} to={path}>{label}</NavLink>)}
      </nav>
    </aside>
    <main className="main-content"><Outlet /></main>
  </div>
}
