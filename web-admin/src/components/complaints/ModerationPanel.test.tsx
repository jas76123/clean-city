import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { ModerationPanel } from './ModerationPanel'

// Раскрыть свёрнутый по умолчанию раздел модерации.
const expandPanel = () =>
  fireEvent.click(screen.getByRole('button', { name: /Модерация автора/ }))

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

  it('флаг виден в свёрнутом заголовке, кнопки появляются после раскрытия', () => {
    render(<ModerationPanel authorId={7} complaintId={42} />)
    // Бейдж флага виден сразу, даже когда раздел свёрнут.
    expect(screen.getByText(/3 отклонённ/i)).toBeInTheDocument()
    // Контролы скрыты до клика.
    expect(screen.queryByRole('button', { name: /Предупредить/ })).toBeNull()
    expandPanel()
    expect(screen.getByRole('button', { name: /Предупредить/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Заблокировать/ })).toBeInTheDocument()
  })

  it('заблокированный: после раскрытия показывает Разблокировать и скрывает Предупредить/Заблокировать', () => {
    mockSummaryData = {
      rejectedCountSinceWarning: 0,
      flagged: false,
      isWarned: false,
      isBanned: true,
    }
    render(<ModerationPanel authorId={7} complaintId={42} />)
    // Бейдж «заблокирован» виден в свёрнутом заголовке.
    expect(screen.getByText(/заблокирован/i)).toBeInTheDocument()
    expandPanel()
    expect(screen.getByRole('button', { name: /Разблокировать/ })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Предупредить/ })).toBeNull()
    expect(screen.queryByRole('button', { name: /Заблокировать/ })).toBeNull()
  })

  it('кнопка Предупредить задизаблена при пустой причине', () => {
    // Default state: flagged=true, isBanned=false, reason starts empty.
    render(<ModerationPanel authorId={7} complaintId={42} />)
    expandPanel()
    expect(screen.getByRole('button', { name: /Предупредить/ })).toBeDisabled()
  })
})
