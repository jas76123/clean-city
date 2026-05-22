import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { getTrends, getSla } from './analytics'

const BASE = 'http://localhost:8081'
let lastSlaUrl = ''

const server = setupServer(
  http.get(`${BASE}/analytics/trends`, () =>
    HttpResponse.json({ days: [{ date: '2026-05-01', created: 3, resolved: 1 }] }),
  ),
  http.get(`${BASE}/analytics/sla`, ({ request }) => {
    lastSlaUrl = request.url
    return HttpResponse.json([])
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
})
