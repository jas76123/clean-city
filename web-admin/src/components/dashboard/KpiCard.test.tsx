import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { KpiCard } from './KpiCard'

describe('KpiCard', () => {
  it('показывает значение и подпись', () => {
    render(<KpiCard label="Жалоб за месяц" value="247" />)
    expect(screen.getByText('247')).toBeInTheDocument()
    expect(screen.getByText('Жалоб за месяц')).toBeInTheDocument()
  })

  it('считает рост в процентах при наличии previous', () => {
    render(<KpiCard label="x" value="120" current={120} previous={100} />)
    expect(screen.getByText(/\+20%/)).toBeInTheDocument()
  })

  it('не показывает дельту если previous отсутствует или равен 0', () => {
    render(<KpiCard label="x" value="8" current={8} previous={0} />)
    expect(screen.queryByText(/%/)).not.toBeInTheDocument()
  })
})
