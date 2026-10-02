import { defineConfig, devices } from '@playwright/test'

/**
 * Kiểm thử trực quan (không chạy trong CI). Mặc định chạy với Vite local (cổng 5173, proxy tới backend 8080).
 * Chạy với cloud: E2E_BASE_URL=https://<DOMAIN> E2E_MANAGER=user:pass E2E_GUARD=user:pass npx playwright test
 * Ảnh chụp lưu vào docs/test-results/e2e/<E2E_LABEL> (mặc định "local").
 */
export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.e2e.ts',
  timeout: 90_000,
  workers: 1,
  fullyParallel: false,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    locale: 'vi-VN',
    timezoneId: 'Asia/Ho_Chi_Minh',
    trace: 'off',
    screenshot: 'off',
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1366, height: 850 } } },
    { name: 'mobile', use: { ...devices['Pixel 7'] } },
  ],
})
