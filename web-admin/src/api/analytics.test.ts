import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import {
  getTrends, getSla,
  getOperational, getBurning, getStrategic, getReopen,
} from './analytics'

const BASE = 'http://localhost:8081'
let lastSlaUrl = ''
let lastBurningUrl = ''
let lastStrategicUrl = ''
let lastTrendsUrl = ''
let lastReopenUrl = ''

const server = setupServer(
  http.get(`${BASE}/analytics/trends`, ({ request }) => {
    lastTrendsUrl = request.url
    return HttpResponse.json({ days: [{ date: '2026-05-01', created: 3, resolved: 1 }] })
  }),
  http.get(`${BASE}/analytics/sla`, ({ request }) => {
    lastSlaUrl = request.url
    return HttpResponse.json([])
  }),
  http.get(`${BASE}/analytics/operational`, () =>
    HttpResponse.json({
      backlog: 5, overdueNow: 1, avgDtaHours24h: null, dtaTargetHours: 24,
      createdToday: 3, createdYesterday: 4, statusBreakdown: { NEW: 3 },
    }),
  ),
  http.get(`${BASE}/analytics/burning`, ({ request }) => {
    lastBurningUrl = request.url
    return HttpResponse.json([
      { id: 1, title: 'Test', districtCode: 'ADL', category: 'GARBAGE',
        createdAt: '2026-05-26T08:00:00Z', slaDueAt: '2026-05-27T08:00:00Z',
        secondsToDeadline: -3600 },
    ])
  }),
  http.get(`${BASE}/analytics/strategic`, ({ request }) => {
    lastStrategicUrl = request.url
    return HttpResponse.json({
      slaCompliancePct: 78, slaTargetPct: 80,
      medianResolutionHours: 24, p90ResolutionHours: 72,
      reopenRate: 0.08, reopenTargetPct: 10, throughput: 100,
    })
  }),
  http.get(`${BASE}/analytics/reopen`, ({ request }) => {
    lastReopenUrl = request.url
    return HttpResponse.json({ reopenRate: 0.08, reopenCount: 8, resolvedCount: 100 })
  }),
)

beforeAll(() => server.listen())
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

describe('analytics API', () => {
  it('getTrends возвращает дневной ряд', async () => {
    const res = await getTrends()
    expect(res.days[0].created).toBe(3)
  })

  it('getSla передаёт period в query-параметрах', async () => {
    await getSla('MONTH')
    expect(lastSlaUrl).toContain('period=MONTH')
  })

  it('getOperational parses snapshot', async () => {
    const s = await getOperational()
    expect(s.backlog).toBe(5)
    expect(s.dtaTargetHours).toBe(24)
  })

  it('getBurning passes limit and parses items', async () => {
    const items = await getBurning(7)
    expect(lastBurningUrl).toContain('limit=7')
    expect(items[0].secondsToDeadline).toBeLessThan(0)
  })

  it('getStrategic passes period and parses KPIs', async () => {
    const k = await getStrategic('QUARTER')
    expect(lastStrategicUrl).toContain('period=QUARTER')
    expect(k.slaTargetPct).toBe(80)
  })

  it('getReopen passes period and parses stat', async () => {
    const r = await getReopen('MONTH')
    expect(lastReopenUrl).toContain('period=MONTH')
    expect(r.resolvedCount).toBe(100)
  })

  it('getTrends forwards groupBy', async () => {
    await getTrends('WEEK', 'week')
    expect(lastTrendsUrl).toContain('period=WEEK')
    expect(lastTrendsUrl).toContain('groupBy=week')
  })

  it('getTrends with no args is backward-compatible', async () => {
    await getTrends()
    // нет period и groupBy в URL
    expect(lastTrendsUrl).not.toContain('period=')
    expect(lastTrendsUrl).not.toContain('groupBy=')
  })
})
