import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ImportPage } from './ImportPage'

describe('ImportPage', () => {
  it('moves through mapping and preview before confirming sample data', async () => {
    render(<ImportPage />)

    expect(screen.getByRole('heading', { name: 'Nhập dữ liệu' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Dùng dữ liệu mẫu' }))

    expect(await screen.findByRole('heading', { name: 'AI nhận diện cột' })).toBeInTheDocument()
    expect(screen.getByText('vehicle_type')).toBeInTheDocument()
    expect(screen.getByText('79%')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Xem trước dữ liệu' })).toBeDisabled()

    fireEvent.click(screen.getByRole('checkbox', { name: /Tôi đã kiểm tra ánh xạ vehicle_type/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Xem trước dữ liệu' }))

    expect(await screen.findByText('24.381')).toBeInTheDocument()
    expect(screen.getByText('11 / 11')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận nhập dữ liệu' }))
    expect(await screen.findByText('Đã nhập 24.381 bản ghi thành công.')).toBeInTheDocument()
  })

  it('rejects an unsupported log file format', () => {
    render(<ImportPage />)
    const input = screen.getByLabelText('Chọn file log quẹt thẻ')
    const file = new File(['demo'], 'log.txt', { type: 'text/plain' })
    fireEvent.change(input, { target: { files: [file] } })
    expect(screen.getByRole('alert')).toHaveTextContent('Log quẹt thẻ cần định dạng CSV.')
  })
})
