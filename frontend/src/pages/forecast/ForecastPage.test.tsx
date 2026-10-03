import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ForecastPage } from './ForecastPage'
import * as forecastApi from '../../shared/api/forecast'

vi.mock('../../shared/api/forecast', async () => {
  const actual = await vi.importActual<typeof import('../../shared/api/forecast')>('../../shared/api/forecast')
  return { ...actual, approvePlan: vi.fn() }
})

describe('ForecastPage', () => {
  beforeEach(() => vi.mocked(forecastApi.approvePlan).mockReset())

  it('shows the forecast, quality warning, and before/after allocation', async () => {
    render(<MemoryRouter><ForecastPage /></MemoryRouter>)

    expect(await screen.findByRole('heading', { name: 'Dự báo & Đề xuất' })).toBeInTheDocument()
    expect(screen.getByText('103.4%')).toBeInTheDocument()
    expect(screen.getByText('10 xe')).toBeInTheDocument()
    expect(screen.getAllByText(/Nhà xe C2/).length).toBeGreaterThan(0)
    expect(screen.getByText('605/1450 xe · 121%')).toBeInTheDocument()
    expect(screen.getByText('490/1450 xe · 98%')).toBeInTheDocument()
    expect(screen.getByText((_, element) => element?.tagName === 'LI' && Boolean(element.textContent?.includes('76 xe từ Nhà xe hầm 1 sau G3 sang Nhà xe C1 gần H3')))).toBeInTheDocument()
  })

  it('approves a pending plan and reports success', async () => {
    vi.mocked(forecastApi.approvePlan).mockResolvedValue({ version: 4, status: 'approved' })
    render(<MemoryRouter><ForecastPage /></MemoryRouter>)

    const approveButton = await screen.findByRole('button', { name: 'Duyệt phương án' })
    expect(approveButton).toBeDisabled()
    fireEvent.change(screen.getByLabelText('Người duyệt'), { target: { value: 'Tuan Anh' } })
    expect(approveButton).toBeDisabled()
    fireEvent.change(screen.getByLabelText('Phương án xử lý'), { target: { value: 'ACCEPT_HIGHER' } })
    fireEvent.click(approveButton)
    fireEvent.click(approveButton)

    await waitFor(() => expect(forecastApi.approvePlan).toHaveBeenCalledWith(4, { approvedBy: 'Tuan Anh', option: 'ACCEPT_HIGHER' }))
    expect(forecastApi.approvePlan).toHaveBeenCalledTimes(1)
    expect(await screen.findByText('Đã duyệt phương án phiên bản 4.')).toBeInTheDocument()
  })
})
