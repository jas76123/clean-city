import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { AnalyticsPage } from './AnalyticsPage'
import type { ReactNode } from 'react'

const BASE = 'http://localhost:8081'

let strategicPeriods: string[] = []

const srv = setupServer(
  http.get(`${BASE}/analytics/strategic`, ({ request }) => {
    const p = new URL(request.url).searchParams.get('period') ?? ''
    strategicPeriods.push(p)
    return HttpResponse.json({
      slaCompliancePct: 78, slaTargetPct: 80,
      medianResolutionHours: 24, p90ResolutionHours: 72,
      reopenRate: 0.08, reopenTargetPct: 10, throughput: 145,
    })
  }),
  http.get(`${BASE}/analytics/reopen`, () => HttpResponse.json({
    reopenRate: 0.08, reopenCount: 12, resolvedCount: 150,
  })),
  http.get(`${BASE}/analytics/trends`, () => HttpResponse.json({
    days: [], createdSeries: [], resolvedSeries: [], groupBy: 'day',
  })),
  http.get(`${BASE}/analytics/by-district`, () => HttpResponse.json([])),
  http.get(`${BASE}/analytics/by-category`, () => HttpResponse.json([])),
  http.get(`${BASE}/analytics/sla`, () => HttpResponse.json([])),
  http.get(`${BASE}/analytics/votes-impact`, () => HttpResponse.json([])),
)

beforeAll(() => srv.listen())
afterEach(() => {
  srv.resetHandlers()
  strategicPeriods = []
})
afterAll(() => srv.close())

function wrap(node: ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={qc}>{node}</QueryClientProvider>
}

describe('AnalyticsPage', () => {
  it('renders visible strategic KPI cards and headers', async () => {
    render(wrap(<AnalyticsPage />))
    expect((await screen.findAllByText(/Соблюдение норматива/i)).length).toBeGreaterThan(0)
    expect(screen.getByText(/Время решения/)).toBeInTheDocument()
    expect(screen.getByText(/Закрыто за период/i)).toBeInTheDocument()
    expect(screen.getByText('145')).toBeInTheDocument()  // throughput
  })

  it('does not render Export PDF button', async () => {
    render(wrap(<AnalyticsPage />))
    await screen.findByText(/Закрыто за период/)
    expect(screen.queryByText(/Экспорт PDF/i)).not.toBeInTheDocument()
  })

  it('changes period via switcher and refetches', async () => {
    render(wrap(<AnalyticsPage />))
    await screen.findByText(/Закрыто за период/)
    // initial fetch with default MONTH
    expect(strategicPeriods).toContain('MONTH')
    await userEvent.click(screen.getByRole('button', { name: 'Квартал' }))
    await waitFor(() => expect(strategicPeriods).toContain('QUARTER'))
  })
})
