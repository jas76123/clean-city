import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ComplaintFilters } from './ComplaintFilters'
import type { ComplaintFilter, AnalyticsOverview } from '@/api/types'

const baseFilter: ComplaintFilter = {
  status: null, slaBreached: false, category: null, district: null, sort: 'date', page: 0,
}

const overview: AnalyticsOverview = {
  total: 247, new: 47, inProgress: 63, resolved: 137, rejected: 8, duplicate: 4,
  today: 5, week: 30, slaBreachCount: 8,
  monthlyKpis: { total: 0, prevTotal: 0, avgResolutionHours: null, prevAvgResolutionHours: null, resolvedWithin7dPct: 0, prevResolvedWithin7dPct: 0, newCount: 0, inProgressCount: 0, resolvedCount: 0, rejectedCount: 0, duplicateCount: 0 },
}

describe('ComplaintFilters', () => {
  it('клик по чипу статуса вызывает onChange со статусом и page=0', async () => {
    const onChange = vi.fn()
    render(<ComplaintFilters filter={{ ...baseFilter, page: 3 }} overview={overview} onChange={onChange} />)
    await userEvent.click(screen.getByRole('button', { name: /В работе/ }))
    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({ status: 'IN_PROGRESS', slaBreached: false, page: 0 }),
    )
  })

  it('клик по чипу SLA выставляет slaBreached и сбрасывает status', async () => {
    const onChange = vi.fn()
    render(<ComplaintFilters filter={{ ...baseFilter, status: 'NEW' }} overview={overview} onChange={onChange} />)
    await userEvent.click(screen.getByRole('button', { name: /SLA/ }))
    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({ status: null, slaBreached: true }),
    )
  })

  it('счётчики из overview отрисованы', () => {
    render(<ComplaintFilters filter={baseFilter} overview={overview} onChange={vi.fn()} />)
    expect(screen.getByRole('button', { name: /Все 247/ })).toBeInTheDocument()
  })

  it('без overview счётчики не ломают рендер', () => {
    render(<ComplaintFilters filter={baseFilter} overview={undefined} onChange={vi.fn()} />)
    expect(screen.getByRole('button', { name: /Все/ })).toBeInTheDocument()
  })
})
