import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, beforeEach, vi } from 'vitest'

// Existing visual flow tests explicitly exercise the shipped design fixtures.
beforeEach(() => { vi.stubEnv('VITE_USE_MOCK_API', 'true') })

afterEach(cleanup)
