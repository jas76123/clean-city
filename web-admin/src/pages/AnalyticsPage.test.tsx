import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { AnalyticsPage } from './AnalyticsPage'
import type { AnalyticsOverview, SlaStat } from '@/api/types'

const BASE = 'http://localhost:8081'

const overview: AnalyticsOverview = {
  total: 50, new: 10, inProgress: 15, resolved: 25, rejected: 0, duplicate: 0,
  today: 2, week: 8, slaBreachCount: 3,
  monthlyKpis: {
    total: 50, prevTotal: 40, avgResolutionHours: 41, prevAvgResolutionHours: 50,
    resolvedWithin7dPct: 78, prevResolvedWithin7dPct: 70,
  },
}

const slaWeek: SlaStat[] = [
  { category: 'GARBAGE', label: 'Мусор', slaHours: 24, avgResolutionHours: 38, breachPct: 60, resolvedCount: 5 },
]
const slaMonth: SlaStat[] = [
  { category: 'ROADS', label: 'Дороги', slaHours: 72, avgResolutionHours: 50, breachPct: 0, resolvedCount: 9 },
]

let slaPeriods: string[] = []

const srv = setupServer(
  http.get(`${BASE}/analytics/overview`, () => HttpResponse.json(overview)),
  http.get(`${BASE}/analytics/trends`, () => HttpResponse.json({ days: [] })),
  http.get(`${BASE}/analytics/votes-impact`, () => HttpResponse.json([])),
  http.get(`${BASE}/analytics/sla`, ({ request }) => {
    const p = new URL(request.url).searchParams.get('period') ?? ''
    slaPeriods.push(p)
    return HttpResponse.json(p === 'WEEK' ? slaWeek : slaMonth)
  }),
)

beforeAll(() => srv.listen())
afterEach(() => {
  srv.resetHandlers()
  slaPeriods = []
})
afterAll(() => srv.close())

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <AnalyticsPage />
    </QueryClientProvider>,
  )
}

describe('AnalyticsPage', () => {
  it('рендерит SLA-список и trend-карты для периода по умолчанию (MONTH)', async () => {
    renderPage()
    expect(await screen.findByText('Дороги')).toBeInTheDocument()
    expect(screen.getByText('SLA по категориям')).toBeInTheDocument()
  })

  it('смена периода перезапрашивает данные с новым period', async () => {
    renderPage()
    await screen.findByText('Дороги')
    await userEvent.click(screen.getByRole('button', { name: 'Неделя' }))
    await waitFor(() => expect(screen.getByText('Мусор')).toBeInTheDocument())
    expect(slaPeriods).toContain('WEEK')
  })
})
