import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { AssistantPage } from './AssistantPage'

describe('AssistantPage', () => {
  it('moves from overview through analysis to a result', async () => {
    render(<MemoryRouter><AssistantPage /></MemoryRouter>)
    expect(screen.getByText('Trợ lý AI ParkFlow')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Sáng mai có bãi nào nguy cơ quá tải không?' }))
    expect(screen.getAllByText('AI đang phân tích dữ liệu…')).toHaveLength(2)
    fireEvent.click(screen.getByRole('button', { name: 'Xem kết quả minh họa' }))
    expect(await screen.findByText(/Bãi A có nguy cơ quá tải/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Tạo quy tắc từ đề xuất này' })).toHaveAttribute('href', '/rules')
  })
})
