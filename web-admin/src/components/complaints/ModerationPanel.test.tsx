import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ModerationPanel } from './ModerationPanel'

// Mutable summary data — each test can override before rendering.
let mockSummaryData = {
  rejectedCountSinceWarning: 3,
  flagged: true,
  isWarned: false,
  isBanned: false,
}

vi.mock('@/hooks/moderationQueries', () => ({
  useModerationSummaryQuery: () => ({
    data: mockSummaryData,
    isLoading: false,
  }),
  useWarnMutation: () => ({ mutate: vi.fn(), isPending: false }),
  useBanMutation: () => ({ mutate: vi.fn(), isPending: false }),
  useUnbanMutation: () => ({ mutate: vi.fn(), isPending: false }),
}))

describe('ModerationPanel', () => {
  beforeEach(() => {
    // Reset to the default flagged state before every test.
    mockSummaryData = {
      rejectedCountSinceWarning: 3,
      flagged: true,
      isWarned: false,
      isBanned: false,
    }
  })

  it('показывает бейдж флага при flagged и кнопки модерации', () => {
    render(<ModerationPanel authorId={7} complaintId={42} />)
    expect(screen.getByText(/3 отклонённ/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Предупредить/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Заблокировать/ })).toBeInTheDocument()
  })

  it('заблокированный: показывает Разблокировать и скрывает Предупредить/Заблокировать', () => {
    mockSummaryData = {
      rejectedCountSinceWarning: 0,
      flagged: false,
      isWarned: false,
      isBanned: true,
    }
    render(<ModerationPanel authorId={7} complaintId={42} />)
    expect(screen.getByRole('button', { name: /Разблокировать/ })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Предупредить/ })).toBeNull()
    expect(screen.queryByRole('button', { name: /Заблокировать/ })).toBeNull()
  })

  it('кнопка Предупредить задизаблена при пустой причине', () => {
    // Default state: flagged=true, isBanned=false, reason starts empty.
    render(<ModerationPanel authorId={7} complaintId={42} />)
    expect(screen.getByRole('button', { name: /Предупредить/ })).toBeDisabled()
  })
})
