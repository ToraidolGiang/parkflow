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
    expect(screen.getByText('113%')).toBeInTheDocument()
    expect(screen.getByText('65 xe')).toBeInTheDocument()
    expect(screen.getByText(/Tín hiệu Bãi C gián đoạn/)).toBeInTheDocument()
    expect(screen.getByText('565/500 xe · 113%')).toBeInTheDocument()
    expect(screen.getByText('480/500 xe · 96%')).toBeInTheDocument()
    expect(screen.getAllByText(/60 sang Bãi C, 25 sang Bãi B/)).toHaveLength(2)
  })

  it('approves a pending plan and reports success', async () => {
    vi.mocked(forecastApi.approvePlan).mockResolvedValue({ version: 4, status: 'approved' })
    render(<MemoryRouter><ForecastPage /></MemoryRouter>)

    fireEvent.click(await screen.findByRole('button', { name: 'Duyệt phương án' }))

    await waitFor(() => expect(forecastApi.approvePlan).toHaveBeenCalledWith(4))
    expect(await screen.findByText('Đã duyệt phương án phiên bản 4.')).toBeInTheDocument()
  })
})
