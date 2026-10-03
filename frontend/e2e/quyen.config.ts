import { defineConfig, devices } from '@playwright/test'
import { existsSync } from 'node:fs'
import { loadEnvFile } from 'node:process'
import path from 'node:path'

// Chạy từ frontend. File chứa tài khoản được .gitignore loại trừ.
const envFile = path.resolve(process.env.E2E_ENV_FILE ?? '../.env.e2e')
if (existsSync(envFile)) loadEnvFile(envFile)

export default defineConfig({
  testDir: '.',
  testMatch: /(?:layout|bell|automation|assistant|verify|shifts)\.e2e\.ts$/,
  timeout: 120_000,
  expect: { timeout: 15_000 },
  workers: 1, fullyParallel: false, retries: 0,
  outputDir: '../../tmp/quyen-playwright',
  reporter: [['list'], ['json', { outputFile: '../tmp/quyen-playwright-results.json' }]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    locale: 'vi-VN', timezoneId: 'Asia/Ho_Chi_Minh',
    trace: 'off', screenshot: 'off', video: 'off',
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } } },
    { name: 'mobile', use: { ...devices['Pixel 7'], viewport: { width: 390, height: 844 } } },
  ],
})
