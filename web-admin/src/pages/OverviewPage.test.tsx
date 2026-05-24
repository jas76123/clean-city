import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { OverviewPage } from './OverviewPage'
import type { AnalyticsOverview } from '@/api/types'

const BASE = 'http://localhost:8081'

function overview(slaBreachCount: number): AnalyticsOverview {
  return {
    total: 50, new: 10, inProgress: 15, resolved: 25, rejected: 0, duplicate: 0,
    today: 2, week: 8, slaBreachCount,
    monthlyKpis: {
      total: 50, prevTotal: 40, avgResolutionHours: 41, prevAvgResolutionHours: 50,
      resolvedWithin7dPct: 78, prevResolvedWithin7dPct: 70,
    },
  }
}

function server(slaBreachCount: number) {
  return setupServer(
    http.get(`${BASE}/analytics/overview`, () => HttpResponse.json(overview(slaBreachCount))),
    http.get(`${BASE}/analytics/by-district`, () => HttpResponse.json([])),
    http.get(`${BASE}/analytics/by-category`, () => HttpResponse.json([])),
  )
}

const srv = server(8)
beforeAll(() => srv.listen())
afterEach(() => srv.resetHandlers())
afterAll(() => srv.close())

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <OverviewPage />
    </QueryClientProvider>,
  )
}

describe('OverviewPage', () => {
  it('показывает KPI и пайплайн статусов из overview', async () => {
    renderPage()
    expect(await screen.findByText('Жалоб за месяц')).toBeInTheDocument()
    expect(screen.getByText('Распределение по статусам')).toBeInTheDocument()
    expect(screen.getByText('+25%')).toBeInTheDocument() // 50 vs 40
    expect(screen.getByText('Топ-5 по категориям проблем')).toBeInTheDocument()
  })

  it('показывает SLA-баннер когда slaBreachCount > 0', async () => {
    renderPage()
    expect(await screen.findByText(/превысили норматив SLA/)).toBeInTheDocument()
  })

  it('скрывает SLA-баннер когда slaBreachCount = 0', async () => {
    srv.use(http.get(`${BASE}/analytics/overview`, () => HttpResponse.json(overview(0))))
    renderPage()
    expect(await screen.findByText('Жалоб за месяц')).toBeInTheDocument()
    expect(screen.queryByText(/превысили норматив SLA/)).not.toBeInTheDocument()
  })
})
