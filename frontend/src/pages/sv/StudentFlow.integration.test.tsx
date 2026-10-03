import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it } from 'vitest'
import App from '../../App'

describe('student mobile flow', () => {
  beforeEach(() => { localStorage.clear(); sessionStorage.clear() })
  it('completes selection, recommendation and route guidance', async () => {
    render(<MemoryRouter initialEntries={['/sv']}><App /></MemoryRouter>)
    fireEvent.click(await screen.findByRole('radio', { name: /Nhà hành chính/i }))
    fireEvent.click(screen.getByRole('button', { name: /Xem gợi ý gửi xe/i }))
    expect(await screen.findByRole('heading', { name: /Nên gửi Bãi C/i })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('link', { name: /Chỉ đường tới Bãi C/i }))
    expect(await screen.findByText(/Đi bộ khoảng/i)).toHaveTextContent('2 phút')
  })
})
