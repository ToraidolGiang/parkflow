import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import App from '../../App'
import { SELECTION_KEY } from './state/storage'

describe('student selection', () => {
  it('stores a valid selection and opens the recommendation', async () => {
    localStorage.clear()
    render(<MemoryRouter initialEntries={['/sv']}><App /></MemoryRouter>)
    fireEvent.click(await screen.findByRole('radio', { name: /Giảng đường A/i }))
    fireEvent.click(screen.getByRole('button', { name: /Xem gợi ý gửi xe/i }))
    expect(await screen.findByRole('heading', { name: /Nên gửi Bãi C/i })).toBeInTheDocument()
    expect(localStorage.getItem(SELECTION_KEY)).toContain('GDA')
  })
})
