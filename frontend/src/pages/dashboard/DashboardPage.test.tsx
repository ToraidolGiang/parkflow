import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { DashboardPage } from './DashboardPage'

describe('Dashboard simulator controls', () => {
  it('starts the simulator and applies a 20 percent spike', async () => {
    render(<MemoryRouter><DashboardPage /></MemoryRouter>)
    fireEvent.click(await screen.findByRole('button', { name: 'Bắt đầu mô phỏng' }))
    expect(screen.getByText('Đang phát dữ liệu')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Tăng đột biến 20%' }))
    expect(screen.getByRole('status')).toHaveTextContent('Đã tăng lưu lượng mô phỏng thêm 20%')
  })
})
