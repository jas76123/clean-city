import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest'
import {
  useOperationalQuery,
  useBurningQuery,
  useStrategicQuery,
  useReopenQuery,
  useTrendsRangeQuery,
} from './dashboardQueries'
import type { ReactNode } from 'react'

const BASE = 'http://localhost:8081'

const server = setupServer(
  http.get(`${BASE}/analytics/operational`, () => HttpResponse.json({
    backlog: 7, overdueNow: 2, avgDtaHours24h: 5, dtaTargetHours: 24,
    createdToday: 3, createdYesterday: 4, statusBreakdown: {},
  })),
  http.get(`${BASE}/analytics/burning`, () => HttpResponse.json([])),
  http.get(`${BASE}/analytics/strategic`, () => HttpResponse.json({
    slaCompliancePct: 78, slaTargetPct: 80,
    medianResolutionHours: null, p90ResolutionHours: null,
    reopenRate: 0, reopenTargetPct: 10, throughput: 0,
  })),
  http.get(`${BASE}/analytics/reopen`, () => HttpResponse.json({
    reopenRate: 0, reopenCount: 0, resolvedCount: 0,
  })),
  http.get(`${BASE}/analytics/trends`, () => HttpResponse.json({
    days: [], createdSeries: [], resolvedSeries: [], groupBy: 'day',
  })),
)

beforeAll(() => server.listen())
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

function wrap() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
}

describe('dashboard hooks', () => {
  it('useOperationalQuery returns snapshot', async () => {
    const { result } = renderHook(() => useOperationalQuery(), { wrapper: wrap() })
    await waitFor(() => expect(result.current.data?.backlog).toBe(7))
  })

  it('useStrategicQuery with period', async () => {
    const { result } = renderHook(() => useStrategicQuery('MONTH'), { wrapper: wrap() })
    await waitFor(() => expect(result.current.data?.slaTargetPct).toBe(80))
  })

  it('useReopenQuery returns stat', async () => {
    const { result } = renderHook(() => useReopenQuery('MONTH'), { wrapper: wrap() })
    await waitFor(() => expect(result.current.data?.resolvedCount).toBe(0))
  })

  it('useBurningQuery returns list', async () => {
    const { result } = renderHook(() => useBurningQuery(10), { wrapper: wrap() })
    await waitFor(() => expect(result.current.data).toEqual([]))
  })

  it('useTrendsRangeQuery returns series', async () => {
    const { result } = renderHook(() => useTrendsRangeQuery('WEEK', 'day'), { wrapper: wrap() })
    await waitFor(() => expect(result.current.data?.groupBy).toBe('day'))
  })
})
