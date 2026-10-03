import type { AuthUser, StaffAccount } from '../types/auth'
import { request } from './http'

export const getMe = (signal?: AbortSignal) => request<AuthUser>('/api/auth/me', { signal })

export const loginRequest = (username: string, password: string) =>
  request<AuthUser>('/api/auth/login', { method: 'POST', body: { username, password } })

export const logoutRequest = () => request<void>('/api/auth/logout', { method: 'POST' })

export const changePassword = (current: string, next: string) =>
  request<void>('/api/auth/password', { method: 'POST', body: { current, next } })

export const listAccounts = (signal?: AbortSignal) => request<StaffAccount[]>('/api/users', { signal })

export type NewAccount = { username: string; fullName: string; role: AuthUser['role']; password: string }
export const createAccount = (account: NewAccount) => request<StaffAccount>('/api/users', { method: 'POST', body: account })

export type AccountPatch = { fullName?: string; role?: AuthUser['role']; active?: boolean; password?: string }
export const updateAccount = (id: number, patch: AccountPatch) => request<StaffAccount>(`/api/users/${id}`, { method: 'PATCH', body: patch })
