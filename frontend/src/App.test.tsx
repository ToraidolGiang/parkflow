import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { expect, it } from 'vitest'
import App from './App'

it('renders the shared shell and routes to the frontend screens', () => {
  render(<MemoryRouter><App /></MemoryRouter>)
  expect(screen.getByRole('navigation', { name: 'Điều hướng chính' })).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Dự báo & Đề xuất' })).toHaveAttribute('href', '/forecast')
  expect(screen.getByRole('link', { name: 'Trợ lý' })).toHaveAttribute('href', '/assistant')
})
