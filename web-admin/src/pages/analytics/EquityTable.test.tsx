import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { EquityTable } from './EquityTable'

const stats = [
  { district: 'ADLER', label: 'Адлер', count: 50, newCount: 10, resolvedCount: 30,
    medianResolutionHours: 28, slaCompliancePct: 82 },
  { district: 'CENTRAL', label: 'Центральный', count: 30, newCount: 5, resolvedCount: 20,
    medianResolutionHours: 48, slaCompliancePct: 55 },
]

describe('EquityTable', () => {
  it('renders columns and rows', () => {
    render(<EquityTable items={stats} />)
    expect(screen.getByText('Адлер')).toBeInTheDocument()
    expect(screen.getByText(/28/)).toBeInTheDocument()
    expect(screen.getByText(/82/)).toBeInTheDocument()
  })

  it('colors low-compliance row bad', () => {
    render(<EquityTable items={stats} />)
    const row = screen.getByText('Центральный').closest('tr')!
    expect(row).toHaveClass('equity-row--bad')
  })

  it('colors high-compliance row good', () => {
    render(<EquityTable items={stats} />)
    const row = screen.getByText('Адлер').closest('tr')!
    expect(row).toHaveClass('equity-row--good')
  })

  it('renders empty state when no items', () => {
    render(<EquityTable items={[]} />)
    expect(screen.getByText(/нет данных/i)).toBeInTheDocument()
  })

  it('handles null median and sla', () => {
    const items = [{ district: 'X', label: 'X', count: 0, newCount: 0, resolvedCount: 0,
      medianResolutionHours: null, slaCompliancePct: null }]
    render(<EquityTable items={items} />)
    expect(screen.getAllByText('—')).toHaveLength(2)
  })
})
