import { test as base, expect, type Page, type APIRequestContext, type APIResponse } from '@playwright/test'

export function credentials(name: 'E2E_MANAGER' | 'E2E_GUARD') {
  const raw = process.env[name] ?? ''
  const separator = raw.indexOf(':')
  if (separator < 1 || separator === raw.length - 1) throw new Error(`Cần ${name}=tên:mật-khẩu trong môi trường hoặc .env.e2e (không ghi vào Git).`)
  return { username: raw.slice(0, separator), password: raw.slice(separator + 1) }
}

export async function login(page: Page, role: 'manager' | 'guard') {
  const account = credentials(role === 'manager' ? 'E2E_MANAGER' : 'E2E_GUARD')
  await page.goto('/dang-nhap')
  // API tránh đưa mật khẩu vào báo cáo lỗi locator/trace.
  const response = await page.request.post('/api/auth/login', { data: account })
  expect(response.status(), `Đăng nhập vai ${role}`).toBe(200)
  await page.goto(role === 'manager' ? '/' : '/bao-ve')
  await expect(page).not.toHaveURL(/dang-nhap/)
}

export async function json<T>(response: APIResponse): Promise<T> {
  expect(response.ok(), `HTTP ${response.status()} ${new URL(response.url()).pathname}`).toBeTruthy()
  return response.json() as Promise<T>
}

export type Task = {
  id: number; title: string; status: string; ackBy: number | null; ackAt: string | null; doneAt: string | null; doneNote: string | null
  outcome: { pctCreated: number | null; pctDone: number | null; pctAfter: number | null; lots: string[] } | null
}
export type Board = { day: string; gates: { gate: string; name: string; tasks: Task[]; assignments: { id: number; userId: number }[] }[] }
export type User = { id: number; username: string; fullName: string; role: string; active: boolean }
type Fixtures = { manager: Page; guard: Page; guardAccount: User }

export const test = base.extend<Fixtures>({
  manager: async ({ browser, contextOptions, baseURL }, run, info) => {
    const context = await browser.newContext({ ...info.project.use, ...contextOptions, baseURL })
    try { const page = await context.newPage(); await login(page, 'manager'); await run(page) }
    finally { await context.close() }
  },
  guardAccount: async ({ manager }, run) => {
    const account = credentials('E2E_GUARD')
    const users = await json<User[]>(await manager.request.get('/api/users'))
    let user = users.find(item => item.username === account.username)
    let created = false
    if (!user) {
      if (process.env.E2E_CREATE_GUARD !== '1') throw new Error('Chưa có bảo vệ thử nghiệm; đặt E2E_CREATE_GUARD=1 để tạo tài khoản E2E_GUARD.')
      user = await json<User>(await manager.request.post('/api/users', { data: { ...account, role: 'guard', fullName: 'Quyên E2E' } }))
      created = true
    }
    expect(user.role).toBe('guard')
    const wasActive = user.active
    if (!wasActive && process.env.E2E_CREATE_GUARD === '1') await json(await manager.request.patch(`/api/users/${user.id}`, { data: { active: true } }))
    else expect(wasActive, 'Tài khoản bảo vệ phải đang hoạt động').toBe(true)
    try { await run(user) }
    finally {
      // API giữ tài khoản để lưu dấu vết: khóa tài khoản do bộ test tạo.
      if (created || !wasActive) await json(await manager.request.patch(`/api/users/${user.id}`, { data: { active: false } }))
    }
  },
  guard: async ({ browser, contextOptions, baseURL, guardAccount }, run, info) => {
    expect(guardAccount.id).toBeGreaterThan(0)
    const context = await browser.newContext({ ...info.project.use, ...contextOptions, baseURL })
    try { const page = await context.newPage(); await login(page, 'guard'); await run(page) }
    finally { await context.close() }
  },
})

export async function cancelOpenTask(api: APIRequestContext, id: number) {
  const board = await json<Board>(await api.get('/api/staff/board'))
  const task = board.gates.flatMap(gate => gate.tasks).find(item => item.id === id)
  if (task && ['open', 'acknowledged'].includes(task.status)) await json(await api.patch(`/api/tasks/${id}`, { data: { status: 'cancelled' } }))
}

export { expect }
