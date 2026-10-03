import { useState, type FormEvent } from 'react'
import { Link, Navigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../../shared/auth/AuthContext'
import { safeNext } from '../../shared/auth/safeNext'
import { homeFor } from '../../shared/types/auth'
import '../../shared/auth/auth.css'

export function LoginPage() {
  const { status, user, login } = useAuth()
  const [params] = useSearchParams()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  if (status === 'authenticated' && user) return <Navigate replace to={safeNext(params.get('next'), homeFor(user))} />

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (busy) return
    setBusy(true)
    setError('')
    try {
      await login(username.trim(), password)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Không đăng nhập được. Thử lại sau.')
      setBusy(false)
    }
  }

  return (
    <main className="login-page">
      <form className="login-card" onSubmit={(event) => void submit(event)}>
        <div className="login-brand"><img alt="" src="/assets/parkflow-brand.png" /><div><strong>ParkFlow</strong><span>Điều phối bãi xe</span></div></div>
        <h1>Đăng nhập</h1>
        <p className="login-hint">Dành cho quản lý vận hành và bảo vệ. Tài khoản do quản lý cấp.</p>
        <label>Tên đăng nhập
          <input autoCapitalize="none" autoComplete="username" autoFocus onChange={(event) => setUsername(event.target.value)} required value={username} />
        </label>
        <label>Mật khẩu
          <input autoComplete="current-password" onChange={(event) => setPassword(event.target.value)} required type="password" value={password} />
        </label>
        {error && <p className="login-error" role="alert">{error}</p>}
        <button disabled={busy || !username.trim() || !password} type="submit">{busy ? 'Đang đăng nhập…' : 'Đăng nhập'}</button>
        <p className="login-student">Bạn là sinh viên? <Link to="/sv">Xem gợi ý gửi xe, không cần đăng nhập</Link></p>
      </form>
    </main>
  )
}
