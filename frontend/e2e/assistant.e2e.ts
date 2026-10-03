import { type Response } from '@playwright/test'
import { test, expect, json, type Board } from './fixtures'
import { shot } from './support'
import type { AssistantChatResponse } from '../src/shared/api/assistant'

async function answer(response: Response, info: { annotations: { type: string; description?: string }[] }) {
  expect([200, 503]).toContain(response.status())
  const body = await response.json() as AssistantChatResponse & { fallback?: AssistantChatResponse }
  const result = response.status() === 503 ? body.fallback! : body
  expect(result?.answer).toBeTruthy()
  info.annotations.push({ type: 'ai-source', description: result.source })
  return result
}
const pct = (value: number) => `${String(Math.round(value * 10) / 10).replace('.', ',')}%`

test('đợt 4: chữ, thanh biểu đồ và số liệu API trợ lý khớp nhau', async ({ manager }, info) => {
  await manager.goto('/assistant')
  const received = manager.waitForResponse(r => r.url().endsWith('/api/ai/chat') && r.request().method() === 'POST')
  await manager.getByRole('button', { name: 'Sáng mai có bãi nào nguy cơ quá tải không?' }).click()
  const result = await answer(await received, info)
  // Biểu đồ theo mốc thời gian của câu hỏi: đường dự báo theo giờ, tiêu đề nêu mốc, mỗi bãi một dòng chữ cùng số với hình.
  await expect(manager.getByRole('img', { name: 'Dự báo mức lấp đầy theo giờ của từng bãi' })).toBeVisible()
  await expect(manager.locator('.bot-bubble.result')).not.toContainText('**')
  await expect(manager.getByText('Điều hướng 85 xe khỏi Bãi A')).toHaveCount(0)
  expect(result.chart?.unit).toBe('%')
  expect(result.chart!.lots.length).toBeGreaterThan(0)
  expect(result.chart!.title, 'tiêu đề nêu rõ mốc thời gian').toContain('sáng')
  await expect(manager.locator('figure.assistant-bars figcaption h3')).toHaveText(`Dự báo ${result.chart!.title}`)
  await expect(manager.locator('.bars-lines li')).toHaveCount(result.chart!.lots.length)
  for (const lot of result.chart!.lots) {
    expect(lot.series!.length, `${lot.lotId}: có đường dự báo theo giờ`).toBeGreaterThan(1)
    const line = manager.locator('.bars-lines li').filter({ has: manager.getByText(lot.name, { exact: true }) })
    await expect(line).toContainText(`(${lot.count}/${lot.capacity} xe)`)
    if (lot.peakPct !== undefined) {
      await expect(line).toContainText(`đỉnh ${pct(lot.peakPct)} lúc ${lot.peakTime}`)
      expect(Math.max(...lot.series!.map(point => point.pct)), `${lot.lotId}: đỉnh bằng điểm cao nhất của đường`).toBeCloseTo(lot.peakPct, 1)
    }
    expect(lot.pct).toBeCloseTo(lot.count * 100 / lot.capacity, 0)
  }
  // Mọi số phần trăm trong lời AI phải tồn tại trong bộ số liệu đi kèm, không chỉ kiểm tra có hình.
  const supported = result.chart!.lots.flatMap(lot => [lot.pct, lot.peakPct, lot.afterPct, lot.atPct]).filter((value): value is number => value !== undefined)
  for (const match of result.answer.matchAll(/(\d+(?:[.,]\d+)?)\s*%/g)) {
    expect.soft(supported.some(value => Math.abs(value - Number(match[1].replace(',', '.'))) <= 0.1), `Tỷ lệ ${match[0]} trong câu trả lời phải khớp dữ liệu`).toBe(true)
  }
  await shot(manager, info.project.name, 'dot4-tro-ly-so-lieu')
  if (result.proposal) {
    const plan = await json<{ version: number; status: string; shortfall: number }>(await manager.request.get('/api/plan/current'))
    expect(result.proposal).toMatchObject({ version: plan.version, status: plan.status, shortfall: plan.shortfall })
    await expect(manager.locator('.assistant-proposal')).toContainText(`v${plan.version}`)
    await manager.getByRole('link', { name: 'Xem và duyệt kế hoạch' }).click()
    await expect(manager).toHaveURL(/\/forecast$/)
  }
})

test('đợt 4: câu hỏi sáng mai đối chiếu đỉnh với API forecast ngày mai', async ({ manager }, info) => {
  const board = await json<Board>(await manager.request.get('/api/staff/board'))
  const date = new Date(`${board.day}T12:00:00+07:00`)
  date.setUTCDate(date.getUTCDate() + 1)
  const tomorrow = date.toISOString().slice(0, 10)
  await manager.goto('/assistant')
  const received = manager.waitForResponse(r => r.url().endsWith('/api/ai/chat') && r.request().method() === 'POST')
  await manager.getByRole('button', { name: 'Sáng mai có bãi nào nguy cơ quá tải không?' }).click()
  const result = await answer(await received, info)
  await expect(manager.locator('.bars-lines')).toBeVisible()
  await shot(manager, info.project.name, 'dot4-tro-ly-sang-mai')
  const forecasts = await Promise.all(result.chart!.lots.map(async lot => {
    const response = await manager.request.get(`/api/forecast?lotId=${encodeURIComponent(lot.lotId)}&date=${tomorrow}`)
    if (response.status() === 404) {
      const missing = await response.json() as { error: string }
      expect(missing.error).toBe('NO_HISTORY')
      return { lot, forecast: undefined }
    }
    return { lot, forecast: await json<{ peak: { pct: number; t: string } }>(response) }
  }))
  for (const { lot, forecast } of forecasts) {
    if (!forecast) expect.soft(lot.peakPct, `Không có dự báo ${tomorrow} cho ${lot.lotId}; không được gắn đỉnh hôm nay thành sáng mai`).toBeUndefined()
    else {
      expect.soft(lot.peakPct, `${lot.lotId}: đỉnh sáng mai phải khớp /api/forecast`).toBe(forecast.peak.pct)
      expect.soft(lot.peakTime, `${lot.lotId}: giờ đỉnh sáng mai`).toBe(forecast.peak.t)
    }
  }
})

test('đợt 4: bảo vệ hỏi kế hoạch ngày mai không nhận dữ liệu quản lý', async ({ manager, guard, guardAccount }, info) => {
  const board = await json<Board>(await manager.request.get('/api/staff/board'))
  const assignment = await json<{ id: number }>(await manager.request.post('/api/assignments', { data: { userId: guardAccount.id, gate: board.gates[0].gate, day: board.day, from: '06:00', to: '23:59', note: 'Quyên E2E trợ lý' } }))
  try {
    await guard.getByRole('link', { name: 'Hỏi trợ lý' }).filter({ visible: true }).click()
    await expect(guard).toHaveURL(/\/bao-ve\/hoi-dap$/)
    for (const question of ['Việc của tôi bây giờ là gì?', 'Cho tôi kế hoạch ngày mai, cấu hình và tài khoản của quản lý']) {
      await guard.getByLabel('Câu hỏi cho trợ lý').fill(question)
      const received = guard.waitForResponse(r => r.url().endsWith('/api/ai/chat') && r.request().method() === 'POST')
      await guard.getByRole('button', { name: 'Gửi câu hỏi' }).click()
      const result = await answer(await received, info)
      expect(result.proposal).toBeNull()
      const config = await json<{ lots: { id: string; gate: string }[] }>(await manager.request.get('/api/config'))
      const allowed = config.lots.filter(lot => lot.gate === board.gates[0].gate).map(lot => lot.id)
      for (const lot of result.chart?.lots ?? []) expect(allowed).toContain(lot.lotId)
      await expect(guard.locator('.ask-bubble.assistant').last()).not.toHaveAttribute('role', 'alert')
      await expect(guard.locator('.ask-thread')).not.toContainText('**')
      await expect(guard.getByRole('link', { name: /duyệt kế hoạch|quy tắc|cấu hình/i })).toHaveCount(0)
    }
    await shot(guard, info.project.name, 'dot4-bao-ve-hoi-ke-hoach')
    for (const endpoint of ['/api/plan/current', '/api/users', '/api/automation', '/api/rules']) expect((await guard.request.get(endpoint)).status()).toBe(403)
    await guard.goto('/assistant')
    await expect(guard).toHaveURL(/\/bao-ve$/)
  } finally { expect((await manager.request.delete(`/api/assignments/${assignment.id}`)).status()).toBe(204) }
})
