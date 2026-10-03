import { test, expect, json, cancelOpenTask, type Board, type Task } from './fixtures'
import { shot } from './support'

test('đợt 2: giao đúng việc, chuông dẫn đúng trang, đã đọc về 0', async ({ manager, guard, guardAccount }, info) => {
  const board = await json<Board>(await manager.request.get('/api/staff/board'))
  const title = `Quyên E2E chuông ${Date.now()}`
  const created = await json<Task>(await manager.request.post('/api/tasks', { data: { gate: board.gates[0].gate, title, detail: 'Nhiệm vụ kiểm thử', assignedTo: guardAccount.id, day: board.day } }))
  try {
    const bell = guard.getByRole('button', { name: /^Thông báo/ })
    await expect(bell).toHaveAccessibleName(/\d+ chưa đọc/, { timeout: 45_000 })
    await bell.click()
    const notification = guard.getByRole('button').filter({ has: guard.getByText(`Nhiệm vụ mới: ${title}`, { exact: true }) })
    await expect(notification).toBeVisible({ timeout: 45_000 })
    await shot(guard, info.project.name, 'dot2-chuong-chua-doc')
    await notification.click()
    await expect(guard).toHaveURL(/\/bao-ve$/)
    const card = guard.locator('article.task-card').filter({ hasText: title })
    await expect(card).toBeVisible()
    await card.getByRole('button', { name: 'Đã nhận nhiệm vụ' }).click()
    await expect.poll(async () => {
      const fresh = await json<Board>(await manager.request.get('/api/staff/board'))
      return fresh.gates.flatMap(g => g.tasks).find(t => t.id === created.id)?.ackBy
    }).toBe(guardAccount.id)
    await bell.click()
    const readAll = guard.getByRole('button', { name: 'Đánh dấu tất cả đã đọc' })
    if (await readAll.isEnabled()) await readAll.click()
    await expect(guard.getByRole('button', { name: 'Thông báo', exact: true })).toBeVisible()
    await expect(guard.locator('.bell-badge')).toHaveCount(0)
    await expect.poll(async () => (await json<{ unread: number }>(await guard.request.get('/api/my/notifications'))).unread).toBe(0)
    await shot(guard, info.project.name, 'dot2-chuong-da-doc')
    await expect.poll(async () => {
      const list = await json<{ items: { title: string; body: string }[] }>(await manager.request.get('/api/my/notifications?limit=100'))
      return list.items.some(item => `${item.title} ${item.body}`.includes(title) && `${item.title} ${item.body}`.includes('đã nhận'))
    }, { timeout: 30_000 }).toBe(true)
  } finally { await cancelOpenTask(manager.request, created.id) }
})
