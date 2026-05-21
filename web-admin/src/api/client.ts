import axios from 'axios'
import type { InternalAxiosRequestConfig } from 'axios'
import type { AuthResponse } from './types'

const API_BASE = import.meta.env.VITE_API_BASE_URL as string
const REFRESH_KEY = 'cc_refresh'

let accessToken: string | null = null
let refreshPromise: Promise<AuthResponse> | null = null

export function getAccessToken(): string | null {
  return accessToken
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_KEY)
}

export function setSession(auth: AuthResponse): void {
  accessToken = auth.accessToken
  localStorage.setItem(REFRESH_KEY, auth.refreshToken)
}

export function clearSession(): void {
  accessToken = null
  localStorage.removeItem(REFRESH_KEY)
}

// Голый axios без interceptor'ов — чтобы /auth/refresh не зациклился на собственном 401.
const bare = axios.create({ baseURL: API_BASE })

/**
 * Обновляет access-токен. Single-flight: параллельные вызовы ждут один общий запрос.
 */
export function refreshAccessToken(): Promise<AuthResponse> {
  if (refreshPromise) return refreshPromise
  const token = getRefreshToken()
  if (!token) return Promise.reject(new Error('No refresh token'))
  refreshPromise = bare
    .post<AuthResponse>('/auth/refresh', { refreshToken: token })
    .then((res) => {
      setSession(res.data)
      return res.data
    })
    .finally(() => {
      refreshPromise = null
    })
  return refreshPromise
}

export const api = axios.create({ baseURL: API_BASE })

api.interceptors.request.use((config) => {
  const t = getAccessToken()
  if (t) config.headers.Authorization = `Bearer ${t}`
  return config
})

api.interceptors.response.use(
  (res) => res,
  async (error) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined
    const status = error.response?.status
    if (status === 401 && original && !original._retry) {
      original._retry = true
      try {
        const auth = await refreshAccessToken()
        original.headers.Authorization = `Bearer ${auth.accessToken}`
        return api(original)
      } catch {
        clearSession()
        if (window.location.pathname !== '/login') {
          // Hard-redirect intentionally: this interceptor runs outside React/router context.
          // A full page reload re-runs AuthProvider, which sees no refresh token and
          // settles on status=unauthenticated, landing the user on the login page cleanly.
          window.location.href = '/login'
        }
        return Promise.reject(error)
      }
    }
    return Promise.reject(error)
  },
)
