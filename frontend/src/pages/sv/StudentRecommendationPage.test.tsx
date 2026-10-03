import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it } from 'vitest'
import App from '../../App'
import { SELECTION_KEY } from './state/storage'

describe('student recommendation', () => {
  beforeEach(() => localStorage.clear())
  it('redirects to selection without stored state', async () => {
    render(<MemoryRouter initialEntries={['/sv/recommendation']}><App /></MemoryRouter>)
    expect(await screen.findByRole('heading', { name: /Bạn học ở đâu/i })).toBeInTheDocument()
  })
  it('shows the approved mock recommendation', async () => {
    localStorage.setItem(SELECTION_KEY, JSON.stringify({ building: 'GDA', arrive: '07:10' }))
    render(<MemoryRouter initialEntries={['/sv/recommendation']}><App /></MemoryRouter>)
    expect(await screen.findByRole('heading', { name: /Nên gửi Bãi C/i })).toBeInTheDocument()
    expect(screen.getByText(/Cổng phía Đông từ 06:45/i)).toBeInTheDocument()
  })
})
