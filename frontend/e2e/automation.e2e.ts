import { test, expect, json } from './fixtures'
import { shot } from './support'

type Automation = { mode: string; ackMinutes: number }
type Plan = { version: number; status: string; shortfall: number; pending?: Plan }

test('đợt 3: đổi đủ ba chế độ, lưu hạn nhận và khôi phục cài đặt ban đầu', async ({ manager }, info) => {
  const original = await json<Automation>(await manager.request.get('/api/automation'))
  try {
    await manager.goto('/forecast')
    for (const [label, mode] of [['Thủ công', 'manual'], ['Tự động hoàn toàn', 'auto'], ['Bán tự động', 'semi']]) {
      const radio = manager.getByRole('radiogroup', { name: 'Chọn chế độ' }).getByRole('radio', { name: new RegExp(label) })
      await radio.click()
      await expect(radio).toHaveAttribute('aria-checked', 'true')
      await expect.poll(async () => (await json<Automation>(await manager.request.get('/api/automation'))).mode).toBe(mode)
      await manager.reload()
      await expect(radio).toHaveAttribute('aria-checked', 'true')
      await shot(manager, info.project.name, `dot3-che-do-${mode}`)
    }
    const minutes = original.ackMinutes === 10 ? 11 : 10
    await manager.getByLabel('Hạn nhận việc (phút)').fill(String(minutes))
    await manager.getByRole('button', { name: 'Lưu hạn' }).click()
    await expect.poll(async () => (await json<Automation>(await manager.request.get('/api/automation'))).ackMinutes).toBe(minutes)
    await manager.reload()
    await expect(manager.getByLabel('Hạn nhận việc (phút)')).toHaveValue(String(minutes))
  } finally {
    await json(await manager.request.put('/api/automation', { data: { mode: original.mode, ackMinutes: original.ackMinutes } }))
    const restored = await json<Automation>(await manager.request.get('/api/automation'))
    expect(restored.mode).toBe(original.mode)
    expect(restored.ackMinutes).toBe(original.ackMinutes)
  }
})

test('đợt 3: kế hoạch mới không thiếu chỗ tự áp dụng trong Bán tự động', async ({ manager }, info) => {
  test.skip(process.env.E2E_OBSERVE_NEW_PLAN !== '1', 'Cần vòng phát hành kế hoạch mới có dữ liệu dự báo; đặt E2E_OBSERVE_NEW_PLAN=1 khi môi trường đã sẵn sàng. Bản nháp cũ không chứng minh tiêu chí này.')
  const original = await json<Automation>(await manager.request.get('/api/automation'))
  const response = await manager.request.get('/api/plan/current')
  expect([200, 404]).toContain(response.status())
  const before = response.ok() ? await response.json() as Plan : undefined
  const version = before?.pending?.version ?? before?.version ?? 0
  try {
    await json(await manager.request.put('/api/automation', { data: { mode: 'semi' } }))
    let candidate: Plan | undefined
    await expect.poll(async () => {
      const current = await manager.request.get('/api/plan/current')
      if (current.status() === 404) return false
      const plan = await json<Plan>(current)
      candidate = plan.pending ?? plan
      return candidate.version > version
    }, { timeout: 80_000, intervals: [2000] }).toBe(true)
    expect(candidate!.shortfall, 'Dữ liệu nghiệm thu phải tạo kế hoạch không thiếu chỗ').toBe(0)
    expect(['approved', 'auto_applied']).toContain(candidate!.status)
    const applied = await json<Plan>(await manager.request.get('/api/plan/current'))
    expect(applied.version).toBe(candidate!.version)
    await manager.goto('/forecast')
    await expect(manager.getByText(/đang áp dụng/i).first()).toBeVisible()
    await shot(manager, info.project.name, 'dot3-ke-hoach-tu-ap-dung')
  } finally { await json(await manager.request.put('/api/automation', { data: { mode: original.mode, ackMinutes: original.ackMinutes } })) }
})
