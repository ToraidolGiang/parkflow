import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { getMe, loginRequest, logoutRequest } from '../api/auth'
import { UNAUTHORIZED_EVENT, mockApiEnabled } from '../api/http'
import type { AuthUser } from '../types/auth'

type Status = 'loading' | 'anonymous' | 'authenticated'
type Value = { status: Status; user: AuthUser | null; login: (username: string, password: string) => Promise<AuthUser>; logout: () => Promise<void> }

const MOCK_USER: AuthUser = { id: 0, username: 'dev', fullName: 'Chế độ phát triển', role: 'manager' }
const Context = createContext<Value | null>(null)

/**
 * Người dùng đăng nhập hiện tại (cookie phiên do máy chủ cấp; trình duyệt không giữ mật khẩu hay token).
 * Chỉ để điều hướng và ẩn/hiện giao diện: quyền thật do backend kiểm ở mọi `/api/**`.
 * Chế độ mô phỏng giao diện (VITE_USE_MOCK_API) và máy chủ tắt đăng nhập (`/api/auth/me` trả vai quản lý "dev") đều vào thẳng.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<{ status: Status; user: AuthUser | null }>(
    mockApiEnabled() ? { status: 'authenticated', user: MOCK_USER } : { status: 'loading', user: null },
  )

  useEffect(() => {
    if (mockApiEnabled()) return
    let active = true
    getMe()
      .then((user) => { if (active) setState({ status: 'authenticated', user }) })
      .catch(() => { if (active) setState({ status: 'anonymous', user: null }) })
    return () => { active = false }
  }, [])

  // Phiên hết hạn giữa chừng: bất kỳ lệnh gọi nào trả 401 thì quay về trang đăng nhập.
  useEffect(() => {
    const expire = () => setState({ status: 'anonymous', user: null })
    window.addEventListener(UNAUTHORIZED_EVENT, expire)
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, expire)
  }, [])

  const login = useCallback(async (username: string, password: string) => {
    const user = await loginRequest(username, password)
    setState({ status: 'authenticated', user })
    return user
  }, [])

  const logout = useCallback(async () => {
    try { await logoutRequest() } finally { setState({ status: 'anonymous', user: null }) }
  }, [])

  const value = useMemo(() => ({ ...state, login, logout }), [state, login, logout])
  return <Context.Provider value={value}>{children}</Context.Provider>
}

// Không có provider (kiểm thử từng màn riêng lẻ): coi như quản lý để màn hình dựng được. Backend vẫn là nơi chặn quyền.
const FALLBACK: Value = { status: 'authenticated', user: MOCK_USER, login: async () => MOCK_USER, logout: async () => undefined }

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): Value {
  return useContext(Context) ?? FALLBACK
}
