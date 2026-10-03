import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import { AppShell } from './AppShell'

beforeEach(() => {
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('network')))
})

it('opens and closes the AI report drawer', async () => {
  render(<MemoryRouter><AppShell /></MemoryRouter>)
  fireEvent.click(screen.getByRole('button', { name: 'Xuất báo cáo' }))
  expect(screen.getByRole('dialog', { name: 'Báo cáo AI' })).toBeInTheDocument()
  // Loading state first, then fallback content after API error
  await waitFor(() => expect(screen.getByText('Tóm tắt tuần (14–20/09/2026)')).toBeInTheDocument())
  expect(screen.getByText(/Chưa tải được báo cáo/)).toBeInTheDocument()
  fireEvent.click(screen.getAllByRole('button', { name: 'Đóng báo cáo' })[1])
  expect(screen.queryByRole('dialog', { name: 'Báo cáo AI' })).not.toBeInTheDocument()
})

it('renders API markdown when report succeeds', async () => {
  vi.mocked(fetch).mockResolvedValueOnce(new Response(JSON.stringify({
    markdown: '## Tóm tắt tuần\nTuần này có 50.000 lượt.\n## Bãi căng nhất\nBãi B.\n## Khung giờ cao điểm\n07:00–08:00.\n## Đề xuất vận hành tuần tới\nĐiều hướng.',
    source: 'groq',
  }), { status: 200 }))
  render(<MemoryRouter><AppShell /></MemoryRouter>)
  fireEvent.click(screen.getByRole('button', { name: 'Xuất báo cáo' }))
  await waitFor(() => expect(screen.getByText('Tuần này có 50.000 lượt.')).toBeInTheDocument())
  expect(screen.getByText('groq')).toBeInTheDocument()
  expect(screen.queryByText(/Chưa tải được/)).not.toBeInTheDocument()
})
