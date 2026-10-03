import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mapColumns } from '../../shared/api/import'
import { mockApiEnabled } from '../../shared/api/http'
import { ImportPage } from './ImportPage'

vi.mock('../../shared/api/import', () => ({ mapColumns: vi.fn() }))
vi.mock('../../shared/api/http', async (load) => {
  const actual = await load<typeof import('../../shared/api/http')>()
  return { ...actual, mockApiEnabled: vi.fn() }
})

describe('ImportPage', () => {
  beforeEach(() => {
    vi.mocked(mockApiEnabled).mockReturnValue(false)
    vi.mocked(mapColumns).mockReset()
    vi.mocked(mapColumns).mockImplementation(async (input) => ({
      mappings: input.headers.map((column) => ({ column, field: column === 'ts_in' ? 'time_in' : column, confidence: column === 'vehicle_type' ? 0.79 : 0.98 })),
      source: 'gemini', fallback: false,
    }))
  })

  it('maps real CSV headers and rows, then blocks the unavailable import API', async () => {
    render(<ImportPage />)
    const file = new File(['ts_in,vehicle_type\r\n07:00,"Xe máy"'], 'log.csv', { type: 'text/csv' })
    fireEvent.change(screen.getByLabelText('Chọn file log quẹt thẻ'), { target: { files: [file] } })

    expect(await screen.findByRole('heading', { name: 'AI nhận diện cột' })).toBeInTheDocument()
    expect(mapColumns).toHaveBeenCalledWith({ kind: 'swipe_log', headers: ['ts_in', 'vehicle_type'], sampleRows: [['07:00', 'Xe máy']] })
    expect(screen.getByText('79%')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Xem trước dữ liệu' })).toBeDisabled()
    fireEvent.click(screen.getByRole('checkbox', { name: /vehicle_type/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Xem trước dữ liệu' }))
    expect(await screen.findByText('API nhập dữ liệu chưa được backend triển khai.')).toBeInTheDocument()
    expect(screen.queryByText(/Đã nhập .* thành công/)).not.toBeInTheDocument()
  })

  it('labels an AI fallback as sample data', async () => {
    vi.mocked(mapColumns).mockResolvedValue({ mappings: [{ column: 'ts_in', field: 'time_in', confidence: 0.98 }], source: 'mock', fallback: true })
    render(<ImportPage />)
    fireEvent.change(screen.getByLabelText('Chọn file log quẹt thẻ'), { target: { files: [new File(['ts_in\n07:00'], 'log.csv')] } })
    expect(await screen.findByText('Dữ liệu mẫu')).toBeInTheDocument()
  })

  it('explains that XLSX parsing is unavailable in live mode', async () => {
    render(<ImportPage />)
    fireEvent.change(screen.getByLabelText('Chọn file lịch học'), { target: { files: [new File(['demo'], 'schedule.xlsx')] } })
    expect(await screen.findByRole('alert')).toHaveTextContent('Frontend chưa có bộ đọc XLSX; dùng CSV hoặc dữ liệu mẫu.')
    expect(mapColumns).not.toHaveBeenCalled()
  })

  it('completes the explicitly labeled mock scenario', async () => {
    vi.mocked(mockApiEnabled).mockReturnValue(true)
    vi.mocked(mapColumns).mockImplementation(async (input) => ({ mappings: input.headers.map((column) => ({ column, field: column, confidence: 0.95 })), source: 'mock', fallback: true }))
    render(<ImportPage />)
    fireEvent.click(screen.getByRole('button', { name: 'Dùng dữ liệu mẫu' }))
    await waitFor(() => expect(mapColumns).toHaveBeenCalledTimes(2))
    fireEvent.click(await screen.findByRole('button', { name: 'Xem trước dữ liệu' }))
    expect(await screen.findByText('Kịch bản dữ liệu mẫu đã sẵn sàng để trình diễn.')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Hoàn tất bản minh họa' }))
    expect(await screen.findByText('Đã hoàn tất kịch bản bằng dữ liệu mẫu; không có dữ liệu thật nào được nhập.')).toBeInTheDocument()
  })

  it('rejects an unsupported log file format', () => {
    render(<ImportPage />)
    fireEvent.change(screen.getByLabelText('Chọn file log quẹt thẻ'), { target: { files: [new File(['demo'], 'log.txt')] } })
    expect(screen.getByRole('alert')).toHaveTextContent('Log quẹt thẻ cần định dạng CSV.')
  })
})
