import { test, expect, json, type Board } from './fixtures'
import { shot } from './support'

test('đợt 3: ca mặc định tạo ca hôm nay, ngày mai và không nhân đôi', async ({ manager, guard, guardAccount }, info) => {
  const board = await json<Board>(await manager.request.get('/api/staff/board'))
  const before = await json<{ id: number }[]>(await manager.request.get('/api/default-shifts'))
  let shiftId: number | undefined
  try {
    await manager.goto('/nhan-su')
    await manager.getByRole('tab', { name: 'Ca mặc định' }).click()
    await manager.getByRole('button', { name: '+ Thêm ca mặc định' }).click()
    const form = manager.locator('form.board-form')
    await form.getByLabel('Bảo vệ').selectOption(String(guardAccount.id))
    await form.getByLabel('Cổng').selectOption(board.gates[0].gate)
    for (const day of ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN']) await form.getByLabel(day, { exact: true }).check()
    const createdResponse = manager.waitForResponse(r => r.url().endsWith('/api/default-shifts') && r.request().method() === 'POST')
    await form.getByRole('button', { name: 'Thêm ca mặc định' }).click()
    const created = await json<{ id: number }>(await createdResponse)
    shiftId = created.id
    await expect(manager.getByRole('listitem').filter({ hasText: guardAccount.fullName })).toContainText('Mọi ngày')
    await shot(manager, info.project.name, 'dot3-ca-mac-dinh')
    const tomorrow = new Date(`${board.day}T12:00:00+07:00`)
    tomorrow.setUTCDate(tomorrow.getUTCDate() + 1)
    for (const day of [board.day, tomorrow.toISOString().slice(0, 10)]) {
      const assigned = await json<Board>(await manager.request.get(`/api/staff/board?day=${day}`))
      expect(assigned.gates.flatMap(g => g.assignments).filter(a => a.userId === guardAccount.id)).toHaveLength(1)
    }
    // PATCH active=true chạy lại đồng bộ với cùng định nghĩa ca.
    await json(await manager.request.patch(`/api/default-shifts/${shiftId}`, { data: { active: true } }))
    const assigned = await json<Board>(await manager.request.get('/api/staff/board'))
    const mine = assigned.gates.flatMap(g => g.assignments).filter(a => a.userId === guardAccount.id)
    expect(mine).toHaveLength(1)
    await guard.reload()
    await expect(guard.getByText(/Ca mặc định|\d\d:\d\d–\d\d:\d\d/).first()).toBeVisible()
    await manager.getByRole('tab', { name: 'Ca trực và nhiệm vụ' }).click()
    const roster = manager.getByRole('region', { name: 'Bảo vệ hôm nay' })
    await expect(roster).toContainText(guardAccount.fullName)
    await shot(manager, info.project.name, 'dot3-danh-sach-bao-ve')
    expect((await manager.request.delete(`/api/assignments/${mine[0].id}`)).status()).toBe(204)
    await json(await manager.request.patch(`/api/default-shifts/${shiftId}`, { data: { active: true } }))
    const deleted = await json<Board>(await manager.request.get('/api/staff/board'))
    expect(deleted.gates.flatMap(g => g.assignments).filter(a => a.userId === guardAccount.id)).toHaveLength(0)
  } finally {
    if (shiftId !== undefined) expect((await manager.request.delete(`/api/default-shifts/${shiftId}`)).status()).toBe(204)
    const after = await json<{ id: number }[]>(await manager.request.get('/api/default-shifts'))
    for (const item of before) expect(after.map(row => row.id)).toContain(item.id)
  }
})
