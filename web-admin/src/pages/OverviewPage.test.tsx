import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { OverviewPage } from './OverviewPage'
import type { ReactNode } from 'react'

const BASE = 'http://localhost:8081'

const server = setupServer(
  http.get(`${BASE}/analytics/operational`, () => HttpResponse.json({
    backlog: 12, overdueNow: 3, avgDtaHours24h: 5, dtaTargetHours: 24,
    createdToday: 7, createdYesterday: 4,
    statusBreakdown: { NEW: 5, IN_PROGRESS: 7, RESOLVED: 100, REJECTED: 2, DUPLICATE: 1 },
  })),
  http.get(`${BASE}/analytics/burning`, () => HttpResponse.json([
    {
      id: 1, title: 'Сломанная урна', districtCode: 'ADL', category: 'GARBAGE',
      createdAt: '2026-05-26T08:00:00Z', slaDueAt: '2026-05-26T09:00:00Z',
      secondsToDeadline: -3600,
    },
  ])),
)

beforeAll(() => server.listen())
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

function wrap(node: ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return (
    <QueryClientProvider client={qc}>
      <MemoryRouter>{node}</MemoryRouter>
    </QueryClientProvider>
  )
}

describe('OverviewPage', () => {
  it('renders 4 KPI cards', async () => {
    render(wrap(<OverviewPage />))
    expect(await screen.findByText(/Очередь/i)).toBeInTheDocument()
    expect(await screen.findByText(/Просрочено/i)).toBeInTheDocument()
    expect(await screen.findByText(/Среднее время отклика/i)).toBeInTheDocument()
    expect(await screen.findByText(/Создано сегодня/i)).toBeInTheDocument()
  })

  it('renders Status Pipeline with all 5 statuses and counts from breakdown', async () => {
    render(wrap(<OverviewPage />))
    expect(await screen.findByText('В обработке')).toBeInTheDocument()
    expect(screen.getByText('В работе')).toBeInTheDocument()
    expect(screen.getByText('Решено')).toBeInTheDocument()
    expect(screen.getByText('Отклонено')).toBeInTheDocument()
    expect(screen.getByText('Дубликаты')).toBeInTheDocument()
    expect(screen.getByText('100')).toBeInTheDocument() // RESOLVED
  })

  it('renders Burning Queue table with seeded item', async () => {
    render(wrap(<OverviewPage />))
    expect(await screen.findByText('Сломанная урна')).toBeInTheDocument()
  })

  it('shows delta vs yesterday', async () => {
    render(wrap(<OverviewPage />))
    // createdToday=7, createdYesterday=4 → +3
    expect(await screen.findByText(/▲ \+3/)).toBeInTheDocument()
  })
})
