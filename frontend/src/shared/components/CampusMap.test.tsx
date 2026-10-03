import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import dashboard from '../mock/dashboard-overview.json'
import type { LotStatus } from '../types/dashboard'
import { CampusMap } from './CampusMap'

describe('CampusMap variants', () => {
  it('renders the student route from the same shared map component', () => {
    render(<CampusMap highlightedLotId="C" lots={dashboard.lots as LotStatus[]} showRoute variant="student" />)
    expect(screen.getByLabelText('Sơ đồ khuôn viên')).toHaveClass('sv-map')
    expect(screen.getByText('Được gợi ý')).toBeInTheDocument()
    expect(screen.getByLabelText('Tuyến từ cổng tới bãi')).toBeInTheDocument()
  })
})
