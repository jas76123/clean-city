import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { VotesImpactCard } from './VotesImpactCard'

const buckets = [
  { bucket: '0', count: 100, avgResolutionHours: 48 },
  { bucket: '1-9', count: 50, avgResolutionHours: 32 },
  { bucket: '10-49', count: 20, avgResolutionHours: 18 },
  { bucket: '50+', count: 5, avgResolutionHours: 12 },
]

describe('VotesImpactCard', () => {
  it('renders one bar per bucket with data', () => {
    render(<VotesImpactCard buckets={buckets} />)
    expect(screen.getAllByTestId('votes-bar')).toHaveLength(4)
  })

  it('shows mean resolution per bucket', () => {
    render(<VotesImpactCard buckets={buckets} />)
    expect(screen.getByText('48.0ч')).toBeInTheDocument()
    expect(screen.getByText('12.0ч')).toBeInTheDocument()
  })

  it('skips buckets with null avgResolutionHours', () => {
    const partial = [
      { bucket: '0', count: 10, avgResolutionHours: null },
      { bucket: '1-9', count: 5, avgResolutionHours: 24 },
    ]
    render(<VotesImpactCard buckets={partial} />)
    expect(screen.getAllByTestId('votes-bar')).toHaveLength(1)
  })

  it('renders empty state when all buckets are null', () => {
    const empty = [
      { bucket: '0', count: 0, avgResolutionHours: null },
      { bucket: '1-9', count: 0, avgResolutionHours: null },
    ]
    render(<VotesImpactCard buckets={empty} />)
    expect(screen.getByText(/нет данных/i)).toBeInTheDocument()
  })
})
