import { test, expect } from './fixtures'
import { shot } from './support'

test('đợt 1: sinh viên và nhân sự có lối đi hai chiều', async ({ page }, info) => {
  await page.goto('/sv')
  await page.getByRole('link', { name: 'Nhân sự đăng nhập' }).click()
  await expect(page.getByRole('heading', { name: 'Đăng nhập' })).toBeVisible()
  await shot(page, info.project.name, 'dot1-dang-nhap')
  await page.getByRole('link', { name: /Về trang sinh viên/ }).first().click()
  await expect(page).toHaveURL(/\/sv$/)
  await shot(page, info.project.name, 'dot1-sinh-vien')
})

for (const role of ['student', 'manager', 'guard'] as const) {
  test(`đợt 1: 404 quay về đúng trang chủ ${role}`, async ({ page, manager, guard }, info) => {
    const target = role === 'student' ? page : role === 'manager' ? manager : guard
    const home = role === 'student' ? '/sv' : role === 'guard' ? '/bao-ve' : '/'
    await target.goto(`${role === 'student' ? '/sv' : role === 'guard' ? '/bao-ve' : ''}/khong-co-trang-quyen-e2e`)
    await expect(target.getByRole('heading', { name: 'Không tìm thấy trang' })).toBeVisible()
    await shot(target, info.project.name, `dot1-404-${role}`)
    await target.locator('.not-found-home').click()
    await expect(target).toHaveURL(url => url.pathname === home)
  })
}

test('đợt 1: vào trang khác vai có thông báo lý do', async ({ manager, guard }, info) => {
  await manager.goto('/bao-ve')
  await expect(manager).toHaveURL(url => url.pathname === '/')
  await expect(manager.getByRole('status').filter({ hasText: /dành cho bảo vệ/i })).toBeVisible()
  await shot(manager, info.project.name, 'dot1-quan-ly-khac-vai')
  await guard.goto('/rules')
  await expect(guard).toHaveURL(/\/bao-ve$/)
  await expect(guard.getByRole('status').filter({ hasText: /dành cho quản lý/i })).toBeVisible()
  await shot(guard, info.project.name, 'dot1-bao-ve-khac-vai')
})

test('đợt 1: bảo vệ có header, tab, nội dung đúng cột và Tổng quan rộng hơn', async ({ guard }, info) => {
  await expect(guard.getByRole('heading', { name: 'Nhiệm vụ của bạn' })).toBeVisible()
  const width = info.project.use.viewport!.width
  expect(guard.viewportSize()?.width).toBe(width)
  const header = (await guard.locator('.guard-header-inner').boundingBox())!
  const main = (await guard.locator('.guard-main').boundingBox())!
  expect(Math.abs(header.x + header.width / 2 - main.x - main.width / 2)).toBeLessThan(4)
  const tabs = guard.locator(width >= 900 ? '.guard-tabs-top' : '.guard-tabs-bottom')
  await expect(tabs).toBeVisible()
  const tabBox = (await tabs.boundingBox())!
  expect(tabBox.x).toBeGreaterThanOrEqual(0)
  expect(tabBox.x + tabBox.width).toBeLessThanOrEqual(width + 1)
  if (width >= 900) {
    expect(tabBox.x).toBeGreaterThanOrEqual(header.x)
    expect(tabBox.x + tabBox.width).toBeLessThanOrEqual(header.x + header.width)
  }
  await shot(guard, info.project.name, 'dot1-bao-ve-nhiem-vu')
  await guard.goto('/bao-ve/tong-quan')
  await expect(guard.getByRole('heading', { name: 'Bảng điều khiển' })).toBeVisible()
  if (width >= 900) expect((await guard.locator('.guard-main').boundingBox())!.width).toBeGreaterThan(main.width)
  expect(await guard.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1)
  await shot(guard, info.project.name, 'dot1-bao-ve-tong-quan')
})
