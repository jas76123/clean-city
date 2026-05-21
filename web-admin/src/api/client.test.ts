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
})
