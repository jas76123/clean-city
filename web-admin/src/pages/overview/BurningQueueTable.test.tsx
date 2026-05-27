import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { BurningQueueTable } from './BurningQueueTable'

const items = [
  { id: 1, title: 'Сломанная урна', districtCode: 'ADL', category: 'GARBAGE',
    createdAt: '2026-05-26T08:00:00Z', slaDueAt: '2026-05-26T09:00:00Z',
    secondsToDeadline: -3600 },
  { id: 2, title: 'Темно во дворе', districtCode: 'CEN', category: 'LIGHTING',
    createdAt: '2026-05-26T08:00:00Z', slaDueAt: '2026-05-28T08:00:00Z',
    secondsToDeadline: 7200 },
]

describe('BurningQueueTable', () => {
  it('renders rows in given order', () => {
    render(<MemoryRouter><BurningQueueTable items={items} /></MemoryRouter>)
    const rows = screen.getAllByTestId('burning-row')
    expect(rows).toHaveLength(2)
    expect(rows[0]).toHaveTextContent('Сломанная урна')
  })

  it('highlights overdue rows', () => {
    render(<MemoryRouter><BurningQueueTable items={items} /></MemoryRouter>)
    const overdueRow = screen.getAllByTestId('burning-row')[0]
    expect(overdueRow).toHaveClass('burning-row--overdue')
  })

  it('renders empty state', () => {
    render(<MemoryRouter><BurningQueueTable items={[]} /></MemoryRouter>)
    expect(screen.getByText(/нет горящих жалоб/i)).toBeInTheDocument()
  })

  it('links each row to /complaints/:id', () => {
    render(<MemoryRouter><BurningQueueTable items={items} /></MemoryRouter>)
    const link1 = screen.getByText('Сломанная урна').closest('a')
    expect(link1).toHaveAttribute('href', '/complaints/1')
  })
})
