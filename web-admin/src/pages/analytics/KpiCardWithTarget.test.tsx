import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { KpiCardWithTarget } from './KpiCardWithTarget'

describe('KpiCardWithTarget', () => {
  it('shows value, target and good color when meeting target (higher is better)', () => {
    render(<KpiCardWithTarget
      label="% within SLA"
      value={85}
      unit="%"
      target={80}
      direction="higher-better"
    />)
    expect(screen.getByText(/85/)).toBeInTheDocument()
    expect(screen.getByText(/цель ≥ 80/)).toBeInTheDocument()
    expect(screen.getByTestId('kpi-card-with-target')).toHaveClass('kpi-card--good')
  })

  it('shows bad color when below target (higher is better)', () => {
    render(<KpiCardWithTarget label="x" value={50} unit="%" target={80} direction="higher-better" />)
    expect(screen.getByTestId('kpi-card-with-target')).toHaveClass('kpi-card--bad')
  })

  it('inverts logic for lower-better direction', () => {
    render(<KpiCardWithTarget label="reopen" value={5} unit="%" target={10} direction="lower-better" />)
    expect(screen.getByTestId('kpi-card-with-target')).toHaveClass('kpi-card--good')
  })

  it('renders null value as em dash', () => {
    render(<KpiCardWithTarget label="x" value={null} unit="%" target={80} direction="higher-better" />)
    expect(screen.getByText(/—/)).toBeInTheDocument()
  })

  it('wraps in anchor when href is provided', () => {
    render(<KpiCardWithTarget label="x" value={80} unit="%" target={80} direction="higher-better" href="/complaints" />)
    const link = screen.getByRole('link')
    expect(link).toHaveAttribute('href', '/complaints')
  })
})
