import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { TrendCard } from './TrendCard'
import type { TrendsResponse } from '@/api/types'

const trends: TrendsResponse = {
  days: [],
  createdSeries: [
    { bucketStart: '2026-05-20T00:00:00Z', value: 5 },
    { bucketStart: '2026-05-21T00:00:00Z', value: 8 },
    { bucketStart: '2026-05-22T00:00:00Z', value: 6 },
  ],
  resolvedSeries: [
    { bucketStart: '2026-05-20T00:00:00Z', value: 3 },
    { bucketStart: '2026-05-21T00:00:00Z', value: 7 },
    { bucketStart: '2026-05-22T00:00:00Z', value: 9 },
  ],
  groupBy: 'day',
}

describe('TrendCard', () => {
  it('renders two lines for created and resolved', () => {
    render(<TrendCard trends={trends} />)
    expect(screen.getByTestId('trend-line-created')).toBeInTheDocument()
    expect(screen.getByTestId('trend-line-resolved')).toBeInTheDocument()
  })

  it('produces non-empty path data for both lines', () => {
    render(<TrendCard trends={trends} />)
    const created = screen.getByTestId('trend-line-created')
    const resolved = screen.getByTestId('trend-line-resolved')
    expect(created.getAttribute('d')).toBeTruthy()
    expect(resolved.getAttribute('d')).toBeTruthy()
  })

  it('renders empty state when both series are empty', () => {
    render(<TrendCard trends={{ days: [], createdSeries: [], resolvedSeries: [], groupBy: 'day' }} />)
    expect(screen.getByText(/нет данных/i)).toBeInTheDocument()
  })

  it('handles missing optional series (TrendsResponse with no createdSeries field)', () => {
    render(<TrendCard trends={{ days: [] }} />)
    expect(screen.getByText(/нет данных/i)).toBeInTheDocument()
  })

  it('renders legend', () => {
    render(<TrendCard trends={trends} />)
    expect(screen.getByText('Создано')).toBeInTheDocument()
    expect(screen.getByText('Закрыто')).toBeInTheDocument()
  })
})
