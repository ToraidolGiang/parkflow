import { fireEvent, render, screen } from '@testing-library/react'
import { expect, it } from 'vitest'
import { RulesPage } from './RulesPage'

it('creates a rule through request and preview dialogs', () => {
  render(<RulesPage />)
  fireEvent.click(screen.getByRole('button', { name: '+ Tạo quy tắc' }))
  expect(screen.getByRole('dialog', { name: 'Tạo quy tắc bằng tiếng Việt' })).toBeInTheDocument()
  fireEvent.change(screen.getByLabelText('Mô tả quy tắc'), { target: { value: 'Báo cho tôi từ hôm trước nếu bãi nào sắp đầy' } })
  fireEvent.click(screen.getByRole('button', { name: 'Phân tích quy tắc' }))
  expect(screen.getByDisplayValue('Dự báo lấp đầy (%)')).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: 'Lưu quy tắc' }))
  expect(screen.getByText('Đã lưu quy tắc mới.')).toBeInTheDocument()
})
