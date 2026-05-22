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

  it('показывает отрицательную дельту', () => {
    render(<KpiCard label="x" value="80" current={80} previous={100} />)
    expect(screen.getByText(/-20%/)).toBeInTheDocument()
  })

  it('при lowerIsBetter снижение показателя считается хорошим (зелёным)', () => {
    render(<KpiCard label="x" value="80" current={80} previous={100} lowerIsBetter />)
    expect(screen.getByText(/-20%/).className).toContain('emerald')
  })

  it('нулевую дельту показывает нейтрально, не красным', () => {
    render(<KpiCard label="x" value="100" current={100} previous={100} />)
    const badge = screen.getByText('0%')
    expect(badge.className).not.toContain('red')
    expect(badge.className).toContain('slate')
  })
})
