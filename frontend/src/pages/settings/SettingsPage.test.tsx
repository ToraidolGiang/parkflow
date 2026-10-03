import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { CampusConfig } from '../../shared/types/campus'
import { getCampusConfig, updateCampusConfig } from '../../shared/api/campus'
import { SettingsPage } from './SettingsPage'

vi.mock('../../shared/api/campus', () => ({
  getCampusConfig: vi.fn(),
  updateCampusConfig: vi.fn(),
}))

const config: CampusConfig = {
  school: 'HUCE',
  thresholds: { yellow: 0.8 },
  gates: [{ id: 'GATE-X', name: 'Cổng Bắc' }],
  buildings: [{ id: 'BUILD-X', name: 'Tòa X', nearLot: 'LOT-X' }],
  lots: [
    { id: 'LOT-X', name: 'Bãi thử X', capacity: 40, max: 48, gate: 'GATE-X', near: 'BUILD-X', alternates: ['LOT-Y', 'LOT-Z'] },
    { id: 'LOT-Y', name: 'Bãi thử Y', capacity: 50, max: 60, gate: 'GATE-X', near: 'BUILD-X', alternates: [] },
    { id: 'LOT-Z', name: 'Bãi thử Z', capacity: 60, max: 72, gate: 'GATE-X', near: 'BUILD-X', alternates: [] },
  ],
  routes: [],
}

describe('SettingsPage', () => {
  beforeEach(() => {
    vi.mocked(getCampusConfig).mockResolvedValue(structuredClone(config))
    vi.mocked(updateCampusConfig).mockResolvedValue(structuredClone(config))
  })

  it('loads and renders dynamic config values', async () => {
    render(<SettingsPage />)
    expect(screen.getByRole('status')).toHaveTextContent('Đang tải cấu hình')
    expect(await screen.findByDisplayValue('Bãi thử X')).toBeInTheDocument()
    expect(screen.getByDisplayValue('HUCE')).toBeInTheDocument()
    expect(screen.getAllByText('Cổng Bắc').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Tòa X').length).toBeGreaterThan(0)
  })

  it('saves edited values with the complete config and API key', async () => {
    render(<SettingsPage />)
    const lotName = await screen.findByDisplayValue('Bãi thử X')
    fireEvent.change(lotName, { target: { value: 'Bãi thử đã sửa' } })
    fireEvent.change(screen.getByLabelText('API key'), { target: { value: 'SIMULATOR' } })
    fireEvent.click(screen.getByRole('button', { name: 'Lưu cấu hình' }))

    await waitFor(() => expect(updateCampusConfig).toHaveBeenCalledWith(expect.objectContaining({
      school: 'HUCE',
      lots: expect.arrayContaining([expect.objectContaining({ id: 'LOT-X', name: 'Bãi thử đã sửa' })]),
    }), 'SIMULATOR', expect.any(AbortSignal)))
  })

  it('keeps edited values and reports a save error', async () => {
    vi.mocked(updateCampusConfig).mockRejectedValue(new Error('Không có quyền cập nhật'))
    render(<SettingsPage />)
    const lotName = await screen.findByDisplayValue('Bãi thử X')
    fireEvent.change(lotName, { target: { value: 'Bãi thử đã sửa' } })
    fireEvent.change(screen.getByLabelText('API key'), { target: { value: 'SIMULATOR' } })
    fireEvent.click(screen.getByRole('button', { name: 'Lưu cấu hình' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Không có quyền cập nhật')
    expect(screen.getByDisplayValue('Bãi thử đã sửa')).toBeInTheDocument()
  })

  it('submits alternative lots in the edited priority order', async () => {
    render(<SettingsPage />)
    await screen.findByDisplayValue('Bãi thử X')
    fireEvent.click(screen.getByRole('button', { name: 'LOT-Z lên' }))
    fireEvent.change(screen.getByLabelText('LOT-X thêm bãi thay thế'), { target: { value: 'LOT-Z' } })
    fireEvent.click(screen.getByRole('button', { name: 'LOT-X thêm' }))
    fireEvent.change(screen.getByLabelText('API key'), { target: { value: 'SIMULATOR' } })
    fireEvent.click(screen.getByRole('button', { name: 'Lưu cấu hình' }))

    await waitFor(() => expect(updateCampusConfig).toHaveBeenCalledWith(expect.objectContaining({
      lots: expect.arrayContaining([expect.objectContaining({ id: 'LOT-X', alternates: ['LOT-Z', 'LOT-Y'] })]),
    }), 'SIMULATOR', expect.any(AbortSignal)))
  })
})
