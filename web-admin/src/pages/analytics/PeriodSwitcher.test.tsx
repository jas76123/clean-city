import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { PeriodSwitcher } from './PeriodSwitcher'

describe('PeriodSwitcher', () => {
  it('renders all 5 periods', () => {
    render(<PeriodSwitcher value="MONTH" onChange={() => {}} />)
    expect(screen.getByText('Неделя')).toBeInTheDocument()
    expect(screen.getByText('Месяц')).toBeInTheDocument()
    expect(screen.getByText('Квартал')).toBeInTheDocument()
    expect(screen.getByText('Год')).toBeInTheDocument()
    expect(screen.getByText('Всё время')).toBeInTheDocument()
  })

  it('calls onChange with selected period', () => {
    const onChange = vi.fn()
    render(<PeriodSwitcher value="MONTH" onChange={onChange} />)
    fireEvent.click(screen.getByText('Квартал'))
    expect(onChange).toHaveBeenCalledWith('QUARTER')
  })

  it('highlights active period', () => {
    render(<PeriodSwitcher value="YEAR" onChange={() => {}} />)
    const yearBtn = screen.getByText('Год')
    expect(yearBtn.className).toContain('bg-emerald-600')
  })
})
