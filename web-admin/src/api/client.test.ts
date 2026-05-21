import { describe, it, expect, beforeEach, afterEach, afterAll, beforeAll } from 'vitest'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { setSession, clearSession, getAccessToken, refreshAccessToken } from './client'
import type { AuthResponse } from './types'

const BASE = 'http://localhost:8081'

function fakeAuth(token: string): AuthResponse {
  return {
    accessToken: token,
    refreshToken: 'refresh-' + token,
    accessExpiresIn: 900,
    refreshExpiresIn: 2592000,
    user: { id: 1, email: 'a@b.c', role: 'ADMIN', emailVerified: true, createdAt: '2026-01-01' },
  }
}

let refreshHits = 0
const server = setupServer(
  http.post(`${BASE}/auth/refresh`, async () => {
    refreshHits++
    // REQUIRED for the single-flight test: the 20 ms delay ensures that all 3
    // concurrent refreshAccessToken() calls are in-flight simultaneously before
    // any of them resolves. Without this delay the second and third calls would
    // arrive after the first has already settled, so refreshPromise would already
    // be null and each call would issue its own request — defeating the test.
    await new Promise((r) => setTimeout(r, 20))
    return HttpResponse.json(fakeAuth('access-new'))
  }),
)

beforeAll(() => server.listen())
afterEach(() => server.resetHandlers())
afterAll(() => server.close())
beforeEach(() => {
  refreshHits = 0
  clearSession()
})

describe('token store', () => {
  it('setSession сохраняет access в память, refresh в localStorage', () => {
    setSession(fakeAuth('access-1'))
    expect(getAccessToken()).toBe('access-1')
    expect(localStorage.getItem('cc_refresh')).toBe('refresh-access-1')
  })

  it('clearSession обнуляет токены', () => {
    setSession(fakeAuth('access-1'))
    clearSession()
    expect(getAccessToken()).toBeNull()
    expect(localStorage.getItem('cc_refresh')).toBeNull()
  })

  it('параллельные refreshAccessToken делают один запрос /auth/refresh', async () => {
    setSession(fakeAuth('access-1'))
    const [a, b, c] = await Promise.all([
      refreshAccessToken(),
      refreshAccessToken(),
      refreshAccessToken(),
    ])
    expect(refreshHits).toBe(1)
    expect(a.accessToken).toBe('access-new')
    expect(b.accessToken).toBe('access-new')
    expect(c.accessToken).toBe('access-new')
    expect(getAccessToken()).toBe('access-new')
  })

  it('refreshAccessToken без refresh-токена бросает ошибку', async () => {
    await expect(refreshAccessToken()).rejects.toThrow()
  })

  it('после неудачного refresh следующий вызов делает новый запрос', async () => {
    setSession(fakeAuth('access-1'))
    // Override: first refresh fails with 401
    server.use(
      http.post(`${BASE}/auth/refresh`, () =>
        new HttpResponse(JSON.stringify({ code: 'X', message: 'x' }), { status: 401 }),
      ),
    )
    await expect(refreshAccessToken()).rejects.toBeDefined()
    // Restore default success handler so the second call can succeed.
    // This proves refreshPromise was reset (.finally) and does not stay locked.
    server.resetHandlers()
    const auth = await refreshAccessToken()
    expect(auth.accessToken).toBe('access-new')
  })

  it('api: 401 → рефреш → повтор запроса с новым токеном', async () => {
    setSession(fakeAuth('access-stale'))
    let calls = 0
    let lastAuth: string | null = null
    server.use(
      http.get(`${BASE}/ping`, ({ request }) => {
        calls++
        const auth = request.headers.get('Authorization')
        if (auth === 'Bearer access-stale') {
          return new HttpResponse(JSON.stringify({ code: 'X', message: 'x' }), { status: 401 })
        }
        lastAuth = auth
        return HttpResponse.json({ ok: true })
      }),
    )
    const { api } = await import('./client')
    const res = await api.get('/ping')
    expect(res.data).toEqual({ ok: true })
    expect(calls).toBe(2)
    expect(refreshHits).toBe(1)
    expect(lastAuth).toBe('Bearer access-new')
  })
})
