import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { listAnnouncements, createAnnouncement, unpublishAnnouncement } from './announcements'

const BASE = 'http://localhost:8081'
const server = setupServer()

beforeAll(() => server.listen())
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

describe('api/announcements', () => {
  it('listAnnouncements запрашивает limit=100', async () => {
    let seenUrl = ''
    server.use(
      http.get(`${BASE}/announcements`, ({ request }) => {
        seenUrl = request.url
        return HttpResponse.json({ items: [], total: 0 })
      }),
    )
    const res = await listAnnouncements()
    expect(res).toEqual({ items: [], total: 0 })
    expect(seenUrl).toContain('limit=100')
  })

  it('createAnnouncement шлёт POST с телом запроса', async () => {
    let body: unknown
    server.use(
      http.post(`${BASE}/announcements`, async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({ id: 1 }, { status: 201 })
      }),
    )
    await createAnnouncement({ title: 'T', body: 'B', iconStyle: 'INFO', districts: [] })
    expect(body).toMatchObject({ title: 'T', body: 'B', iconStyle: 'INFO', districts: [] })
  })

  it('unpublishAnnouncement шлёт DELETE по id', async () => {
    let method = ''
    server.use(
      http.delete(`${BASE}/announcements/:id`, ({ request, params }) => {
        method = request.method
        expect(params.id).toBe('7')
        return new HttpResponse(null, { status: 204 })
      }),
    )
    await unpublishAnnouncement(7)
    expect(method).toBe('DELETE')
  })
})
