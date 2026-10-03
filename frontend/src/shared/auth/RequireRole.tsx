import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { homeFor, type Role } from '../types/auth'
import { useAuth } from './AuthContext'

/** Chặn cả nhóm route theo vai: chưa đăng nhập thì về trang đăng nhập (nhớ trang đang vào), sai vai thì về trang chủ của vai mình. */
export function RequireRole({ role }: { role: Role }) {
  const { status, user } = useAuth()
  const location = useLocation()
  if (status === 'loading') return <div className="auth-loading" role="status">Đang kiểm tra đăng nhập…</div>
  if (!user) return <Navigate replace to={`/dang-nhap?next=${encodeURIComponent(location.pathname + location.search)}`} />
  if (user.role !== role) return <Navigate replace to={homeFor(user)} />
  return <Outlet />
}
