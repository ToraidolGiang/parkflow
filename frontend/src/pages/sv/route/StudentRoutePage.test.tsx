import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import App from '../../../App'
import { SELECTION_KEY } from '../state/storage'
describe('student route', () => { it('renders both route legs and instructions', async () => { localStorage.setItem(SELECTION_KEY, JSON.stringify({ building:'GDA', arrive:'07:10' })); render(<MemoryRouter initialEntries={['/sv/route']}><App/></MemoryRouter>); expect(await screen.findByText('Đến cổng trường')).toBeInTheDocument(); expect(screen.getByText('Trong khuôn viên')).toBeInTheDocument(); expect(screen.getAllByRole('listitem')).toHaveLength(3) }) })
