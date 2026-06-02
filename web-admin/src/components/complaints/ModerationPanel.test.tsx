import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ModerationPanel } from './ModerationPanel'

vi.mock('@/hooks/moderationQueries', () => ({
  useModerationSummaryQuery: () => ({
    data: { rejectedCountSinceWarning: 3, flagged: true, isWarned: false, isBanned: false },
    isLoading: false,
  }),
  useWarnMutation: () => ({ mutate: vi.fn(), isPending: false }),
  useBanMutation: () => ({ mutate: vi.fn(), isPending: false }),
  useUnbanMutation: () => ({ mutate: vi.fn(), isPending: false }),
}))

describe('ModerationPanel', () => {
  it('показывает бейдж флага при flagged и кнопки модерации', () => {
    render(<ModerationPanel authorId={7} complaintId={42} />)
    expect(screen.getByText(/3 отклонённ/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Предупредить/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Забанить/ })).toBeInTheDocument()
  })
})
