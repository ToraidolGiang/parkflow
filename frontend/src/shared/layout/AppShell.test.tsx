import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { expect, it } from 'vitest'
import { AppShell } from './AppShell'

it('opens and closes the AI report drawer', () => { render(<MemoryRouter><AppShell /></MemoryRouter>); fireEvent.click(screen.getByRole('button',{name:'Xuất báo cáo'})); expect(screen.getByRole('dialog',{name:'Báo cáo AI'})).toBeInTheDocument(); expect(screen.getByText('Tóm tắt tuần (14–20/09/2026)')).toBeInTheDocument(); fireEvent.click(screen.getAllByRole('button',{name:'Đóng báo cáo'})[1]); expect(screen.queryByRole('dialog',{name:'Báo cáo AI'})).not.toBeInTheDocument() })
