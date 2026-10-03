import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { LoginPage } from '../../pages/login/LoginPage'
import { UNAUTHORIZED_EVENT } from '../api/http'
import { AuthProvider } from './AuthContext'
import { RequireRole } from './RequireRole'
import { safeNext } from './safeNext'

const manager = { id: 1, username: 'quanly', fullName: 'Nguyễn Quản Lý', role: 'manager' }
const guard = { id: 2, username: 'baove1', fullName: 'Trần Bảo Vệ', role: 'guard' }

function json(value: unknown, status = 200) {
  return new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })
}

function app(initial: string) {
  return render(
    <MemoryRouter initialEntries={[initial]}>
      <AuthProvider>
        <Routes>
          <Route element={<LoginPage />} path="/dang-nhap" />
          <Route element={<RequireRole role="manager" />}><Route element={<p>Trang quản lý</p>} path="/" /><Route element={<p>Trang tài khoản</p>} path="/nhan-su" /></Route>
          <Route element={<RequireRole role="guard" />}><Route element={<p>Trang bảo vệ</p>} path="/bao-ve" /></Route>
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  )
}

beforeEach(() => { vi.stubEnv('VITE_USE_MOCK_API', 'false') })
afterEach(() => { vi.unstubAllGlobals(); vi.unstubAllEnvs() })

describe('đăng nhập và chặn route theo vai', () => {
  it('chưa đăng nhập thì đưa về trang đăng nhập và nhớ trang đang vào', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json({ error: 'UNAUTHORIZED' }, 401)))
    app('/nhan-su')
    expect(await screen.findByRole('heading', { name: 'Đăng nhập' })).toBeInTheDocument()
    expect(screen.queryByText('Trang tài khoản')).not.toBeInTheDocument()
  })

  it('đăng nhập đúng thì vào trang đã nhớ, đăng nhập sai hiện thông báo của máy chủ', async () => {
    const fetcher = vi.fn(async (url: string) => {
      if (url === '/api/auth/me') return json({ error: 'UNAUTHORIZED' }, 401)
      return json({ error: 'INVALID_CREDENTIALS', message: 'Sai tên đăng nhập hoặc mật khẩu.' }, 401)
    })
    vi.stubGlobal('fetch', fetcher)
    app('/dang-nhap?next=%2Fnhan-su')
    fireEvent.change(await screen.findByLabelText('Tên đăng nhập'), { target: { value: 'quanly' } })
    fireEvent.change(screen.getByLabelText('Mật khẩu'), { target: { value: 'sai' } })
    fireEvent.click(screen.getByRole('button', { name: 'Đăng nhập' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Sai tên đăng nhập hoặc mật khẩu.')

    fetcher.mockImplementation(async (url: string) => (url === '/api/auth/me' ? json({ error: 'UNAUTHORIZED' }, 401) : json(manager)))
    fireEvent.change(screen.getByLabelText('Mật khẩu'), { target: { value: 'dung-mat-khau-1' } })
    fireEvent.click(screen.getByRole('button', { name: 'Đăng nhập' }))
    expect(await screen.findByText('Trang tài khoản')).toBeInTheDocument()
  })

  it('bảo vệ vào trang quản lý thì bị đưa về trang của bảo vệ, quản lý vào trang bảo vệ thì về trang chủ', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(guard)))
    const view = app('/')
    expect(await screen.findByText('Trang bảo vệ')).toBeInTheDocument()
    view.unmount()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(manager)))
    app('/bao-ve')
    expect(await screen.findByText('Trang quản lý')).toBeInTheDocument()
  })

  it('phiên hết hạn giữa chừng (một lệnh gọi trả 401) thì quay về đăng nhập', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(manager)))
    app('/')
    expect(await screen.findByText('Trang quản lý')).toBeInTheDocument()
    window.dispatchEvent(new Event(UNAUTHORIZED_EVENT))
    expect(await screen.findByRole('heading', { name: 'Đăng nhập' })).toBeInTheDocument()
  })

  it('chế độ giao diện mô phỏng vào thẳng với vai quản lý, không gọi máy chủ', async () => {
    vi.stubEnv('VITE_USE_MOCK_API', 'true')
    const fetcher = vi.fn()
    vi.stubGlobal('fetch', fetcher)
    app('/')
    await waitFor(() => expect(screen.getByText('Trang quản lý')).toBeInTheDocument())
    expect(fetcher).not.toHaveBeenCalled()
  })
})

describe('safeNext', () => {
  it('chỉ cho quay lại đường dẫn nội bộ', () => {
    expect(safeNext('/rules', '/')).toBe('/rules')
    expect(safeNext('//evil.com', '/')).toBe('/')
    expect(safeNext('https://evil.com', '/')).toBe('/')
    expect(safeNext('/dang-nhap', '/')).toBe('/')
    expect(safeNext(null, '/bao-ve')).toBe('/bao-ve')
  })
})
