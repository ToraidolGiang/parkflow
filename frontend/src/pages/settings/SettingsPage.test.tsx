import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { SettingsPage } from './SettingsPage'

describe('SettingsPage', () => { it('shows the configured lots and school hours', () => { render(<SettingsPage />); expect(screen.getByRole('heading',{name:'Cấu hình trường'})).toBeInTheDocument(); expect(screen.getByDisplayValue('500')).toBeInTheDocument(); expect(screen.getAllByDisplayValue('Cổng phía Đông')).toHaveLength(2); expect(screen.getByDisplayValue('07:00')).toBeInTheDocument() }) })
