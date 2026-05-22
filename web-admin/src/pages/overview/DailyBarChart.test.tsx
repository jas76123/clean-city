import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { DailyBarChart } from './DailyBarChart'
import type { DailyPoint } from '@/api/types'

describe('DailyBarChart', () => {
  it('показывает «Нет данных» при пустом ряде', () => {
    render(<DailyBarChart days={[]} />)
    expect(screen.getByText('Нет данных')).toBeInTheDocument()
  })

  it('рисует колонку на каждый день, когда есть данные', () => {
    const days: DailyPoint[] = [
      { date: '2026-05-21', created: 4, resolved: 1 },
      { date: '2026-05-22', created: 7, resolved: 0 },
    ]
    render(<DailyBarChart days={days} />)

    expect(screen.queryByText('Нет данных')).not.toBeInTheDocument()
    expect(document.querySelector('[title="2026-05-21"]')).toBeInTheDocument()
    expect(document.querySelector('[title="2026-05-22"]')).toBeInTheDocument()
  })
})
