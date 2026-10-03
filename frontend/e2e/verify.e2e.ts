import { test, expect, json, cancelOpenTask, type Board, type Task } from './fixtures'
import { shot } from './support'

test('đợt 3: giao, nhận, báo xong có ghi chú và đối chiếu kết quả đo', async ({ manager, guard, guardAccount }, info) => {
  const board = await json<Board>(await manager.request.get('/api/staff/board'))
  const title = `Quyên E2E xác minh ${Date.now()}`
  const created = await json<Task>(await manager.request.post('/api/tasks', { data: { gate: board.gates[0].gate, title, assignedTo: guardAccount.id, day: board.day } }))
  try {
    // Mức lấp đầy lúc tạo việc do bộ quét 10 giây ghi; việc xong trước khi kịp đo thì cố ý không có số lúc tạo (không bịa).
    // Nên chờ bộ quét đo xong rồi mới cho bảo vệ làm.
    await expect.poll(async () => {
      const fresh = await json<Board>(await manager.request.get('/api/staff/board'))
      return typeof fresh.gates.flatMap(g => g.tasks).find(task => task.id === created.id)?.outcome?.pctCreated
    }, { timeout: 30_000 }).toBe('number')
    await guard.reload()
    const card = guard.locator('article.task-card').filter({ hasText: title })
    await card.getByRole('button', { name: 'Đã nhận nhiệm vụ' }).click()
    const done = card.getByRole('button', { name: 'Hoàn thành' })
    await expect(done).toBeDisabled()
    await card.getByLabel(/Ghi chú cho/).fill('ab')
    await expect(done).toBeDisabled()
    await shot(guard, info.project.name, 'dot3-ghi-chu-qua-ngan')
    const note = 'Quyên E2E đã kiểm tra nhiệm vụ thử nghiệm'
    await card.getByLabel(/Ghi chú cho/).fill(note)
    await done.click()
    await guard.locator('details.done-tasks summary').click()
    await expect(card).toContainText(note)
    await expect(card).toContainText('Hoàn thành lúc')
    await shot(guard, info.project.name, 'dot3-bao-ve-hoan-thanh')
    let completed: Task | undefined
    await expect.poll(async () => {
      const fresh = await json<Board>(await manager.request.get('/api/staff/board'))
      completed = fresh.gates.flatMap(g => g.tasks).find(task => task.id === created.id)
      return completed?.status
    }).toBe('done')
    expect(completed!.ackBy).toBe(guardAccount.id)
    expect(completed!.ackAt).toBeTruthy()
    expect(completed!.doneAt).toBeTruthy()
    expect(completed!.doneNote).toBe(note)
    expect(completed!.outcome?.pctCreated).toEqual(expect.any(Number))
    expect(completed!.outcome?.pctDone).toEqual(expect.any(Number))
    expect(completed!.outcome!.lots.length).toBeGreaterThan(0)
    await manager.goto('/nhan-su')
    const row = manager.locator('.board-list li').filter({ hasText: title })
    await expect(row).toContainText('Tạo ')
    await expect(row).toContainText('nhận ')
    await expect(row).toContainText('Xong ')
    await expect(row).toContainText(note)
    await expect(row).toContainText('Mức lấp đầy bãi')
    await shot(manager, info.project.name, 'dot3-ket-qua-do')
  } finally { await cancelOpenTask(manager.request, created.id) }
})
