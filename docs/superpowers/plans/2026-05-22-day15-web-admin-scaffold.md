# Day 15 — Web admin scaffold + auth — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Создать проект `web-admin/` с нуля и реализовать полный auth-флоу (вход, 2FA, accept-invite, forgot/reset) так, чтобы после логина был виден Layout с сайдбаром и заглушками разделов.

**Architecture:** Vite + React + TS SPA. Токены: `refreshToken` в `localStorage`, `accessToken` в памяти модуля axios. На старте приложения, если refresh-токен есть — single-flight вызов `/auth/refresh` восстанавливает сессию (и отдаёт `user`). Response-interceptor рефрешит токен по 401. Auth-состояние в React Context, серверные данные позже — через TanStack Query.

**Tech Stack:** Vite 6, React 19, TypeScript, Tailwind v4 (`@tailwindcss/vite`), shadcn/ui, react-router v7, axios, @tanstack/react-query v5, sonner, Vitest + React Testing Library + jsdom, msw (моки сети в тестах).

**Базовый каталог:** все пути ниже — относительно `~/Desktop/Myapp/cleancity-kmp/`.

**Спецификация:** `docs/superpowers/specs/2026-05-22-day15-web-admin-scaffold-design.md`

---

## Справка по backend API (проверено в коде)

Запросы/ответы (поля — точные имена JSON):

- `POST /auth/login` ← `{email, password}` → `200 LoginResponse {requires2fa, challengeToken?, challengeExpiresIn?, auth?}`
- `POST /auth/login-2fa` ← `{challengeToken, code}` → `200 AuthResponse`
- `POST /auth/refresh` ← `{refreshToken}` → `200 AuthResponse`
- `POST /auth/logout` ← `{refreshToken}` → `200 {message}`
- `POST /auth/admin/accept-invite` ← `{token, password}` → `200 AuthResponse`
- `POST /auth/forgot-password` ← `{email}` → `200 {message}`
- `POST /auth/reset-password` ← `{token, newPassword}` → `200 {message}`

`AuthResponse = {accessToken, refreshToken, accessExpiresIn, refreshExpiresIn, user}`
`UserResponse = {id, email, role, fullName?, emailVerified, createdAt}`
`UserRole` — enum-строка (`RESIDENT | MODERATOR | ADMIN | SUPERADMIN` — точный набор уточнить в `shared/.../UserRole.kt`, для UI важны только `ADMIN`-подобные роли).
Ошибки: `{code, message}`. Коды: `AUTH_INVALID_CREDENTIALS`, `AUTH_EMAIL_UNVERIFIED`, `AUTH_2FA_INVALID`, `AUTH_INVALID_TOKEN`, `AUTH_ACCOUNT_LOCKED`, `VALIDATION_WEAK_PASSWORD`. Статус 423 — аккаунт заблокирован, 429 — rate-limit.

**Правила пароля админа** (для клиентской валидации accept-invite/reset): минимум 12 символов, хотя бы 1 цифра, 1 заглавная буква, 1 спецсимвол.

**Backend для dev** запускается на `http://localhost:8081` (`KTOR_PORT=8081` в `backend/.env`).

---

## Карта файлов

| Файл | Ответственность |
|------|-----------------|
| `web-admin/.env.example`, `.env` | `VITE_API_BASE_URL` |
| `web-admin/src/api/types.ts` | TS-типы ответов/ошибок API |
| `web-admin/src/api/client.ts` | token-store, single-flight refresh, axios instance + interceptors |
| `web-admin/src/api/errors.ts` | `extractApiError` |
| `web-admin/src/api/auth.ts` | функции-обёртки auth-эндпоинтов |
| `web-admin/src/auth/AuthContext.tsx` | провайдер: `status`, `user`, `login/submit2fa/acceptInvite/logout` |
| `web-admin/src/auth/ProtectedRoute.tsx` | guard + рендер `AppLayout` |
| `web-admin/src/components/layout/{Sidebar,Topbar,AppLayout}.tsx` | каркас интерфейса |
| `web-admin/src/pages/LoginPage.tsx` | вход + шаг 2FA |
| `web-admin/src/pages/AcceptInvitePage.tsx` | установка пароля по invite-токену |
| `web-admin/src/pages/ForgotPasswordPage.tsx` | запрос письма сброса |
| `web-admin/src/pages/ResetPasswordPage.tsx` | новый пароль по reset-токену |
| `web-admin/src/pages/placeholders/SectionPlaceholder.tsx` | заглушка раздела |
| `web-admin/src/App.tsx` | провайдеры + роутинг |

---

## Task 1: Scaffold проекта web-admin

**Files:**
- Create: `web-admin/` (через Vite CLI)
- Create: `web-admin/.env.example`, `web-admin/.env`
- Modify: `web-admin/vite.config.ts`, `web-admin/tsconfig.app.json`
- Modify: `.gitignore` (корень репо — добавить исключения web-admin)

- [ ] **Step 1: Создать Vite-проект**

Из корня репо:
```bash
npm create vite@latest web-admin -- --template react-ts
cd web-admin
npm install
```

- [ ] **Step 2: Установить зависимости**

```bash
cd web-admin
npm install axios react-router-dom @tanstack/react-query sonner
npm install -D tailwindcss @tailwindcss/vite vitest jsdom @testing-library/react @testing-library/jest-dom @testing-library/user-event msw
```

- [ ] **Step 3: Подключить Tailwind v4**

Заменить содержимое `web-admin/src/index.css` на:
```css
@import "tailwindcss";
```

Заменить `web-admin/vite.config.ts` на:
```ts
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'path'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.ts',
  },
})
```

Добавить в `web-admin/tsconfig.app.json` внутрь `compilerOptions`:
```json
"baseUrl": ".",
"paths": { "@/*": ["./src/*"] }
```

- [ ] **Step 4: Настроить shadcn/ui**

```bash
cd web-admin
npx shadcn@latest init -d
npx shadcn@latest add button input label card sonner
```
(`-d` — дефолтные настройки. Если CLI спросит про Tailwind/цвета — base color: slate.)

- [ ] **Step 5: Создать test setup**

Create `web-admin/src/test/setup.ts`:
```ts
import '@testing-library/jest-dom/vitest'
```

Добавить в `web-admin/package.json` в `scripts`:
```json
"test": "vitest run",
"test:watch": "vitest"
```

- [ ] **Step 6: Создать env-файлы**

Create `web-admin/.env.example`:
```
# Базовый URL backend API. Dev — локальный Ktor.
VITE_API_BASE_URL=http://localhost:8081
```

Create `web-admin/.env`:
```
VITE_API_BASE_URL=http://localhost:8081
```

- [ ] **Step 7: Игнорировать артефакты в git**

Добавить в корневой `.gitignore` (файл `~/Desktop/Myapp/cleancity-kmp/.gitignore`):
```
# web-admin
web-admin/node_modules/
web-admin/dist/
web-admin/.env
```

- [ ] **Step 8: Проверить, что сборка и dev-сервер живы**

Run: `cd web-admin && npm run build`
Expected: сборка проходит без ошибок, появляется `dist/`.

Run: `cd web-admin && npm run dev` — открыть напечатанный URL, увидеть дефолтную страницу Vite, остановить (`Ctrl+C`).

- [ ] **Step 9: Commit**

```bash
cd ~/Desktop/Myapp/cleancity-kmp
git add web-admin/package.json web-admin/package-lock.json web-admin/vite.config.ts \
  web-admin/tsconfig.app.json web-admin/src web-admin/index.html web-admin/.env.example \
  web-admin/components.json web-admin/.gitignore .gitignore
git commit -m "feat(web): scaffold web-admin (Vite+React+TS, Tailwind v4, shadcn)"
```

---

## Task 2: TS-типы API

**Files:**
- Create: `web-admin/src/api/types.ts`

- [ ] **Step 1: Описать типы (зеркало Kotlin-моделей)**

Create `web-admin/src/api/types.ts`:
```ts
export type UserRole = 'RESIDENT' | 'MODERATOR' | 'ADMIN' | 'SUPERADMIN'

export interface UserResponse {
  id: number
  email: string
  role: UserRole
  fullName?: string | null
  emailVerified: boolean
  createdAt: string
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  accessExpiresIn: number
  refreshExpiresIn: number
  user: UserResponse
}

export interface LoginResponse {
  requires2fa: boolean
  challengeToken?: string | null
  challengeExpiresIn?: number | null
  auth?: AuthResponse | null
}

export interface MessageResponse {
  message: string
}

export interface ApiError {
  code: string
  message: string
}
```

- [ ] **Step 2: Сверить `UserRole` с backend**

Открыть `shared/src/commonMain/kotlin/com/example/cleancity/shared/models/UserRole.kt`, привести строковый union в `types.ts` к точному набору значений enum.

- [ ] **Step 3: Проверить компиляцию**

Run: `cd web-admin && npx tsc --noEmit`
Expected: без ошибок.

- [ ] **Step 4: Commit**

```bash
git add web-admin/src/api/types.ts
git commit -m "feat(web): типы ответов auth API"
```

---

## Task 3: Token-store + single-flight refresh (TDD)

**Files:**
- Create: `web-admin/src/api/client.ts`
- Test: `web-admin/src/api/client.test.ts`

- [ ] **Step 1: Написать падающий тест single-flight**

Create `web-admin/src/api/client.test.ts`:
```ts
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
```

- [ ] **Step 2: Запустить тест — убедиться, что падает**

Run: `cd web-admin && npx vitest run src/api/client.test.ts`
Expected: FAIL — `client.ts` не экспортирует нужные функции.

- [ ] **Step 3: Реализовать token-store**

Create `web-admin/src/api/client.ts`:
```ts
import axios from 'axios'
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
```

- [ ] **Step 4: Запустить тест — убедиться, что проходит**

Run: `cd web-admin && npx vitest run src/api/client.test.ts`
Expected: PASS, 4 теста зелёные.

- [ ] **Step 5: Commit**

```bash
git add web-admin/src/api/client.ts web-admin/src/api/client.test.ts
git commit -m "feat(web): token-store + single-flight refresh"
```

---

## Task 4: axios instance + interceptors

**Files:**
- Modify: `web-admin/src/api/client.ts`
- Test: `web-admin/src/api/client.test.ts` (дополнить)

- [ ] **Step 1: Дописать тест на 401 → refresh → retry**

Добавить в `web-admin/src/api/client.test.ts` внутрь `describe`:
```ts
  it('api: 401 → рефреш → повтор запроса с новым токеном', async () => {
    setSession(fakeAuth('access-stale'))
    let calls = 0
    server.use(
      http.get(`${BASE}/ping`, ({ request }) => {
        calls++
        const auth = request.headers.get('Authorization')
        if (auth === 'Bearer access-stale') {
          return new HttpResponse(JSON.stringify({ code: 'X', message: 'x' }), { status: 401 })
        }
        return HttpResponse.json({ ok: true })
      }),
    )
    const { api } = await import('./client')
    const res = await api.get('/ping')
    expect(res.data).toEqual({ ok: true })
    expect(calls).toBe(2)
    expect(refreshHits).toBe(1)
  })
```

- [ ] **Step 2: Запустить — убедиться, что падает**

Run: `cd web-admin && npx vitest run src/api/client.test.ts`
Expected: FAIL — `api` не экспортируется.

- [ ] **Step 3: Добавить axios instance с interceptor'ами**

Дописать в конец `web-admin/src/api/client.ts`:
```ts
export const api = axios.create({ baseURL: API_BASE })

api.interceptors.request.use((config) => {
  const t = getAccessToken()
  if (t) config.headers.Authorization = `Bearer ${t}`
  return config
})

api.interceptors.response.use(
  (res) => res,
  async (error) => {
    const original = error.config
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
          window.location.href = '/login'
        }
        return Promise.reject(error)
      }
    }
    return Promise.reject(error)
  },
)
```

- [ ] **Step 4: Запустить — убедиться, что проходит**

Run: `cd web-admin && npx vitest run src/api/client.test.ts`
Expected: PASS, 5 тестов зелёные.

- [ ] **Step 5: Commit**

```bash
git add web-admin/src/api/client.ts web-admin/src/api/client.test.ts
git commit -m "feat(web): axios instance + auto-refresh interceptors"
```

---

## Task 5: extractApiError (TDD)

**Files:**
- Create: `web-admin/src/api/errors.ts`
- Test: `web-admin/src/api/errors.test.ts`

- [ ] **Step 1: Написать падающий тест**

Create `web-admin/src/api/errors.test.ts`:
```ts
import { describe, it, expect } from 'vitest'
import { AxiosError } from 'axios'
import { extractApiError } from './errors'

describe('extractApiError', () => {
  it('достаёт code и message из ответа {code,message}', () => {
    const err = new AxiosError('fail')
    err.response = { status: 401, data: { code: 'AUTH_INVALID_CREDENTIALS', message: 'Bad' } } as never
    const r = extractApiError(err)
    expect(r.code).toBe('AUTH_INVALID_CREDENTIALS')
    expect(r.message).toBe('Bad')
  })

  it('для 423 без тела отдаёт служебный code ACCOUNT_LOCKED', () => {
    const err = new AxiosError('fail')
    err.response = { status: 423, data: {} } as never
    expect(extractApiError(err).code).toBe('ACCOUNT_LOCKED')
  })

  it('для 429 отдаёт code RATE_LIMITED', () => {
    const err = new AxiosError('fail')
    err.response = { status: 429, data: {} } as never
    expect(extractApiError(err).code).toBe('RATE_LIMITED')
  })

  it('для сетевой ошибки отдаёт code NETWORK', () => {
    const err = new AxiosError('Network Error')
    expect(extractApiError(err).code).toBe('NETWORK')
  })
})
```

- [ ] **Step 2: Запустить — убедиться, что падает**

Run: `cd web-admin && npx vitest run src/api/errors.test.ts`
Expected: FAIL — модуль `./errors` не найден.

- [ ] **Step 3: Реализовать**

Create `web-admin/src/api/errors.ts`:
```ts
import { AxiosError } from 'axios'
import type { ApiError } from './types'

const FALLBACK_MESSAGES: Record<string, string> = {
  ACCOUNT_LOCKED: 'Аккаунт временно заблокирован. Попробуйте позже.',
  RATE_LIMITED: 'Слишком много попыток. Подождите немного.',
  NETWORK: 'Нет связи с сервером. Проверьте подключение.',
  UNKNOWN: 'Произошла ошибка. Попробуйте ещё раз.',
}

/** Приводит любую ошибку axios к единому виду {code, message} для UI. */
export function extractApiError(err: unknown): ApiError {
  if (err instanceof AxiosError) {
    const status = err.response?.status
    const data = err.response?.data as Partial<ApiError> | undefined
    if (data && typeof data.code === 'string' && typeof data.message === 'string') {
      return { code: data.code, message: data.message }
    }
    if (status === 423) return { code: 'ACCOUNT_LOCKED', message: FALLBACK_MESSAGES.ACCOUNT_LOCKED }
    if (status === 429) return { code: 'RATE_LIMITED', message: FALLBACK_MESSAGES.RATE_LIMITED }
    if (!err.response) return { code: 'NETWORK', message: FALLBACK_MESSAGES.NETWORK }
  }
  return { code: 'UNKNOWN', message: FALLBACK_MESSAGES.UNKNOWN }
}
```

- [ ] **Step 4: Запустить — убедиться, что проходит**

Run: `cd web-admin && npx vitest run src/api/errors.test.ts`
Expected: PASS, 4 теста зелёные.

- [ ] **Step 5: Commit**

```bash
git add web-admin/src/api/errors.ts web-admin/src/api/errors.test.ts
git commit -m "feat(web): extractApiError — единый разбор ошибок API"
```

---

## Task 6: Функции auth API

**Files:**
- Create: `web-admin/src/api/auth.ts`

- [ ] **Step 1: Реализовать обёртки эндпоинтов**

Create `web-admin/src/api/auth.ts`:
```ts
import { api } from './client'
import type { AuthResponse, LoginResponse, MessageResponse } from './types'

export async function login(email: string, password: string): Promise<LoginResponse> {
  const res = await api.post<LoginResponse>('/auth/login', { email, password })
  return res.data
}

export async function loginTwoFactor(challengeToken: string, code: string): Promise<AuthResponse> {
  const res = await api.post<AuthResponse>('/auth/login-2fa', { challengeToken, code })
  return res.data
}

export async function acceptInvite(token: string, password: string): Promise<AuthResponse> {
  const res = await api.post<AuthResponse>('/auth/admin/accept-invite', { token, password })
  return res.data
}

export async function forgotPassword(email: string): Promise<MessageResponse> {
  const res = await api.post<MessageResponse>('/auth/forgot-password', { email })
  return res.data
}

export async function resetPassword(token: string, newPassword: string): Promise<MessageResponse> {
  const res = await api.post<MessageResponse>('/auth/reset-password', { token, newPassword })
  return res.data
}

export async function logout(refreshToken: string): Promise<void> {
  await api.post('/auth/logout', { refreshToken })
}
```

- [ ] **Step 2: Проверить компиляцию**

Run: `cd web-admin && npx tsc --noEmit`
Expected: без ошибок.

- [ ] **Step 3: Commit**

```bash
git add web-admin/src/api/auth.ts
git commit -m "feat(web): функции-обёртки auth API"
```

---

## Task 7: AuthContext + AuthProvider

**Files:**
- Create: `web-admin/src/auth/AuthContext.tsx`

- [ ] **Step 1: Реализовать контекст**

Create `web-admin/src/auth/AuthContext.tsx`:
```tsx
import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import * as authApi from '@/api/auth'
import {
  setSession,
  clearSession,
  getRefreshToken,
  refreshAccessToken,
} from '@/api/client'
import type { AuthResponse, LoginResponse, UserResponse } from '@/api/types'

type Status = 'loading' | 'authenticated' | 'unauthenticated'

interface AuthContextValue {
  status: Status
  user: UserResponse | null
  /** Возвращает LoginResponse: если requires2fa — вызвавший показывает шаг 2FA. */
  login: (email: string, password: string) => Promise<LoginResponse>
  submit2fa: (challengeToken: string, code: string) => Promise<void>
  acceptInvite: (token: string, password: string) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<Status>('loading')
  const [user, setUser] = useState<UserResponse | null>(null)
  const queryClient = useQueryClient()

  useEffect(() => {
    if (!getRefreshToken()) {
      setStatus('unauthenticated')
      return
    }
    refreshAccessToken()
      .then((auth) => {
        setUser(auth.user)
        setStatus('authenticated')
      })
      .catch(() => {
        clearSession()
        setStatus('unauthenticated')
      })
  }, [])

  function applySession(auth: AuthResponse) {
    setSession(auth)
    setUser(auth.user)
    setStatus('authenticated')
  }

  async function login(email: string, password: string): Promise<LoginResponse> {
    const res = await authApi.login(email, password)
    if (!res.requires2fa && res.auth) {
      applySession(res.auth)
    }
    return res
  }

  async function submit2fa(challengeToken: string, code: string) {
    const auth = await authApi.loginTwoFactor(challengeToken, code)
    applySession(auth)
  }

  async function acceptInvite(token: string, password: string) {
    const auth = await authApi.acceptInvite(token, password)
    applySession(auth)
  }

  async function logout() {
    const rt = getRefreshToken()
    if (rt) {
      try {
        await authApi.logout(rt)
      } catch {
        // best-effort: даже если сервер недоступен — гасим локальную сессию
      }
    }
    clearSession()
    queryClient.clear()
    setUser(null)
    setStatus('unauthenticated')
  }

  return (
    <AuthContext.Provider value={{ status, user, login, submit2fa, acceptInvite, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
```

- [ ] **Step 2: Проверить компиляцию**

Run: `cd web-admin && npx tsc --noEmit`
Expected: без ошибок.

- [ ] **Step 3: Commit**

```bash
git add web-admin/src/auth/AuthContext.tsx
git commit -m "feat(web): AuthContext — состояние сессии и операции входа"
```

---

## Task 8: ProtectedRoute (TDD)

**Files:**
- Create: `web-admin/src/auth/ProtectedRoute.tsx`
- Test: `web-admin/src/auth/ProtectedRoute.test.tsx`

- [ ] **Step 1: Написать падающий тест**

Create `web-admin/src/auth/ProtectedRoute.test.tsx`:
```tsx
import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { ProtectedRoute } from './ProtectedRoute'
import { AuthContextTestProvider } from './testUtils'

function renderAt(status: 'loading' | 'authenticated' | 'unauthenticated') {
  return render(
    <AuthContextTestProvider status={status}>
      <MemoryRouter initialEntries={['/secret']}>
        <Routes>
          <Route element={<ProtectedRoute />}>
            <Route path="/secret" element={<div>SECRET</div>} />
          </Route>
          <Route path="/login" element={<div>LOGIN PAGE</div>} />
        </Routes>
      </MemoryRouter>
    </AuthContextTestProvider>,
  )
}

describe('ProtectedRoute', () => {
  it('unauthenticated → редирект на /login', () => {
    renderAt('unauthenticated')
    expect(screen.getByText('LOGIN PAGE')).toBeInTheDocument()
    expect(screen.queryByText('SECRET')).not.toBeInTheDocument()
  })

  it('loading → показывает индикатор загрузки', () => {
    renderAt('loading')
    expect(screen.getByText(/загрузка/i)).toBeInTheDocument()
  })

  it('authenticated → рендерит защищённый контент', () => {
    renderAt('authenticated')
    expect(screen.getByText('SECRET')).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Создать тестовый провайдер контекста**

Create `web-admin/src/auth/testUtils.tsx`:
```tsx
import type { ReactNode } from 'react'
import { AuthTestContext } from './AuthTestContext'
import type { UserResponse } from '@/api/types'

const FAKE_USER: UserResponse = {
  id: 1,
  email: 'admin@cleancity.local',
  role: 'ADMIN',
  fullName: 'Тест Админ',
  emailVerified: true,
  createdAt: '2026-01-01',
}

export function AuthContextTestProvider({
  status,
  user = FAKE_USER,
  children,
}: {
  status: 'loading' | 'authenticated' | 'unauthenticated'
  user?: UserResponse
  children: ReactNode
}) {
  return (
    <AuthTestContext.Provider
      value={{
        status,
        user: status === 'authenticated' ? user : null,
        login: async () => ({ requires2fa: false }),
        submit2fa: async () => {},
        acceptInvite: async () => {},
        logout: async () => {},
      }}
    >
      {children}
    </AuthTestContext.Provider>
  )
}
```

> **Примечание для исполнителя:** чтобы тест переиспользовал реальный контекст, в Task 7 объект контекста должен быть экспортируемым. Перед реализацией ProtectedRoute вынеси `createContext` из `AuthContext.tsx` в отдельный файл и переиспользуй его (см. Step 3).

- [ ] **Step 3: Вынести объект контекста в отдельный файл**

Create `web-admin/src/auth/AuthTestContext.tsx`:
```tsx
import { createContext } from 'react'
import type { LoginResponse, UserResponse } from '@/api/types'

export interface AuthContextValue {
  status: 'loading' | 'authenticated' | 'unauthenticated'
  user: UserResponse | null
  login: (email: string, password: string) => Promise<LoginResponse>
  submit2fa: (challengeToken: string, code: string) => Promise<void>
  acceptInvite: (token: string, password: string) => Promise<void>
  logout: () => Promise<void>
}

export const AuthTestContext = createContext<AuthContextValue | null>(null)
```

Затем в `web-admin/src/auth/AuthContext.tsx`:
- удалить локальные `interface AuthContextValue` и `const AuthContext = createContext(...)`;
- добавить импорт `import { AuthTestContext, type AuthContextValue } from './AuthTestContext'`;
- заменить все `AuthContext.Provider` на `AuthTestContext.Provider`;
- в `useAuth` заменить `useContext(AuthContext)` на `useContext(AuthTestContext)`.

(Имя файла историческое — это общий объект контекста и для приложения, и для тестов.)

- [ ] **Step 4: Реализовать ProtectedRoute**

Create `web-admin/src/auth/ProtectedRoute.tsx`:
```tsx
import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from './AuthContext'
import { AppLayout } from '@/components/layout/AppLayout'

export function ProtectedRoute() {
  const { status } = useAuth()

  if (status === 'loading') {
    return (
      <div className="flex h-screen items-center justify-center text-slate-500">
        Загрузка…
      </div>
    )
  }
  if (status === 'unauthenticated') {
    return <Navigate to="/login" replace />
  }
  return (
    <AppLayout>
      <Outlet />
    </AppLayout>
  )
}
```

- [ ] **Step 5: Запустить тест — пока упадёт на отсутствии AppLayout**

Run: `cd web-admin && npx vitest run src/auth/ProtectedRoute.test.tsx`
Expected: FAIL — модуль `@/components/layout/AppLayout` не найден. Это ожидаемо; тест станет зелёным после Task 9. Перейти к Task 9, затем вернуться сюда к Step 6.

- [ ] **Step 6: (после Task 9) Запустить тест — убедиться, что проходит**

Run: `cd web-admin && npx vitest run src/auth/ProtectedRoute.test.tsx`
Expected: PASS, 3 теста зелёные.

- [ ] **Step 7: Commit**

```bash
git add web-admin/src/auth/ProtectedRoute.tsx web-admin/src/auth/ProtectedRoute.test.tsx \
  web-admin/src/auth/testUtils.tsx web-admin/src/auth/AuthTestContext.tsx web-admin/src/auth/AuthContext.tsx
git commit -m "feat(web): ProtectedRoute — guard сессии"
```

---

## Task 9: Layout (Sidebar, Topbar, AppLayout)

**Files:**
- Create: `web-admin/src/components/layout/Sidebar.tsx`
- Create: `web-admin/src/components/layout/Topbar.tsx`
- Create: `web-admin/src/components/layout/AppLayout.tsx`
- Create: `web-admin/src/components/layout/navItems.ts`

> Стиль (цвета, отступы) ориентировать на `docs/mockups/admin-dashboard-v2.html` — открыть мокап и свериться по палитре и расположению сайдбара/топбара.

- [ ] **Step 1: Список разделов навигации**

Create `web-admin/src/components/layout/navItems.ts`:
```ts
export interface NavItem {
  path: string
  label: string
}

export const NAV_ITEMS: NavItem[] = [
  { path: '/overview', label: 'Обзор' },
  { path: '/complaints', label: 'Жалобы' },
  { path: '/announcements', label: 'Объявления' },
  { path: '/analytics', label: 'Аналитика' },
  { path: '/settings', label: 'Настройки' },
]
```

- [ ] **Step 2: Sidebar**

Create `web-admin/src/components/layout/Sidebar.tsx`:
```tsx
import { NavLink } from 'react-router-dom'
import { NAV_ITEMS } from './navItems'

export function Sidebar() {
  return (
    <aside className="flex w-60 flex-col border-r border-slate-200 bg-white">
      <div className="px-6 py-5 text-lg font-semibold text-slate-800">
        CleanCity
      </div>
      <nav className="flex flex-col gap-1 px-3">
        {NAV_ITEMS.map((item) => (
          <NavLink
            key={item.path}
            to={item.path}
            className={({ isActive }) =>
              `rounded-md px-3 py-2 text-sm font-medium ${
                isActive
                  ? 'bg-slate-100 text-slate-900'
                  : 'text-slate-500 hover:bg-slate-50 hover:text-slate-800'
              }`
            }
          >
            {item.label}
          </NavLink>
        ))}
      </nav>
    </aside>
  )
}
```

- [ ] **Step 3: Topbar**

Create `web-admin/src/components/layout/Topbar.tsx`:
```tsx
import { useLocation } from 'react-router-dom'
import { useAuth } from '@/auth/AuthContext'
import { NAV_ITEMS } from './navItems'
import { Button } from '@/components/ui/button'

export function Topbar() {
  const { user, logout } = useAuth()
  const location = useLocation()
  const current = NAV_ITEMS.find((i) => location.pathname.startsWith(i.path))

  return (
    <header className="flex h-14 items-center justify-between border-b border-slate-200 bg-white px-6">
      <h1 className="text-base font-semibold text-slate-800">
        {current?.label ?? 'CleanCity'}
      </h1>
      <div className="flex items-center gap-4">
        <div className="text-right">
          <div className="text-sm font-medium text-slate-800">
            {user?.fullName ?? user?.email}
          </div>
          <div className="text-xs text-slate-400">{user?.role}</div>
        </div>
        <Button variant="outline" size="sm" onClick={() => void logout()}>
          Выход
        </Button>
      </div>
    </header>
  )
}
```

- [ ] **Step 4: AppLayout**

Create `web-admin/src/components/layout/AppLayout.tsx`:
```tsx
import type { ReactNode } from 'react'
import { Sidebar } from './Sidebar'
import { Topbar } from './Topbar'

export function AppLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex h-screen bg-slate-50">
      <Sidebar />
      <div className="flex flex-1 flex-col overflow-hidden">
        <Topbar />
        <main className="flex-1 overflow-auto p-6">{children}</main>
      </div>
    </div>
  )
}
```

- [ ] **Step 5: Проверить компиляцию и завершить Task 8 Step 6**

Run: `cd web-admin && npx tsc --noEmit`
Expected: без ошибок.

Вернуться к Task 8 Step 6: `npx vitest run src/auth/ProtectedRoute.test.tsx` → PASS.

- [ ] **Step 6: Commit**

```bash
git add web-admin/src/components/layout
git commit -m "feat(web): Layout — Sidebar, Topbar, AppLayout"
```

---

## Task 10: Заглушки разделов + роутинг App.tsx

**Files:**
- Create: `web-admin/src/pages/placeholders/SectionPlaceholder.tsx`
- Modify: `web-admin/src/App.tsx`
- Modify: `web-admin/src/main.tsx`

- [ ] **Step 1: Компонент-заглушка раздела**

Create `web-admin/src/pages/placeholders/SectionPlaceholder.tsx`:
```tsx
export function SectionPlaceholder({ title }: { title: string }) {
  return (
    <div className="flex h-full flex-col items-center justify-center text-center">
      <div className="text-lg font-semibold text-slate-700">{title}</div>
      <div className="mt-1 text-sm text-slate-400">Раздел в разработке</div>
    </div>
  )
}
```

- [ ] **Step 2: Роутинг в App.tsx**

Заменить содержимое `web-admin/src/App.tsx` на:
```tsx
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider } from '@/auth/AuthContext'
import { ProtectedRoute } from '@/auth/ProtectedRoute'
import { LoginPage } from '@/pages/LoginPage'
import { AcceptInvitePage } from '@/pages/AcceptInvitePage'
import { ForgotPasswordPage } from '@/pages/ForgotPasswordPage'
import { ResetPasswordPage } from '@/pages/ResetPasswordPage'
import { SectionPlaceholder } from '@/pages/placeholders/SectionPlaceholder'

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/accept-invite" element={<AcceptInvitePage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/reset-password" element={<ResetPasswordPage />} />
          <Route element={<ProtectedRoute />}>
            <Route path="/" element={<Navigate to="/overview" replace />} />
            <Route path="/overview" element={<SectionPlaceholder title="Обзор" />} />
            <Route path="/complaints" element={<SectionPlaceholder title="Жалобы" />} />
            <Route path="/announcements" element={<SectionPlaceholder title="Объявления" />} />
            <Route path="/analytics" element={<SectionPlaceholder title="Аналитика" />} />
            <Route path="/settings" element={<SectionPlaceholder title="Настройки" />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}
```

- [ ] **Step 3: Провайдеры в main.tsx**

Заменить содержимое `web-admin/src/main.tsx` на:
```tsx
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Toaster } from '@/components/ui/sonner'
import App from './App'
import './index.css'

const queryClient = new QueryClient()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <App />
      <Toaster richColors position="top-right" />
    </QueryClientProvider>
  </StrictMode>,
)
```

> **Примечание:** `App.tsx` импортирует страницы из Tasks 11–13. До их создания `tsc`/сборка не пройдут — это ожидаемо. Создавай страницы (Tasks 11–13), затем возвращайся к Step 4.

- [ ] **Step 4: (после Tasks 11–13) Проверить компиляцию**

Run: `cd web-admin && npx tsc --noEmit`
Expected: без ошибок.

- [ ] **Step 5: Commit**

```bash
git add web-admin/src/App.tsx web-admin/src/main.tsx web-admin/src/pages/placeholders
git commit -m "feat(web): роутинг + заглушки разделов"
```

---

## Task 11: LoginPage (TDD — переключение на шаг 2FA)

**Files:**
- Create: `web-admin/src/pages/LoginPage.tsx`
- Test: `web-admin/src/pages/LoginPage.test.tsx`

- [ ] **Step 1: Написать падающий тест**

Create `web-admin/src/pages/LoginPage.test.tsx`:
```tsx
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { LoginPage } from './LoginPage'
import { AuthTestContext } from '@/auth/AuthTestContext'
import type { AuthContextValue } from '@/auth/AuthTestContext'

function renderLogin(overrides: Partial<AuthContextValue>) {
  const value: AuthContextValue = {
    status: 'unauthenticated',
    user: null,
    login: async () => ({ requires2fa: false }),
    submit2fa: async () => {},
    acceptInvite: async () => {},
    logout: async () => {},
    ...overrides,
  }
  return render(
    <AuthTestContext.Provider value={value}>
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    </AuthTestContext.Provider>,
  )
}

describe('LoginPage', () => {
  it('при requires2fa показывает шаг ввода кода', async () => {
    const login = vi.fn(async () => ({
      requires2fa: true,
      challengeToken: 'ch-1',
      challengeExpiresIn: 300,
    }))
    renderLogin({ login })
    await userEvent.type(screen.getByLabelText(/email/i), 'admin@cleancity.local')
    await userEvent.type(screen.getByLabelText(/пароль/i), 'Secret123!xyz')
    await userEvent.click(screen.getByRole('button', { name: /войти/i }))
    expect(login).toHaveBeenCalledWith('admin@cleancity.local', 'Secret123!xyz')
    expect(await screen.findByLabelText(/код/i)).toBeInTheDocument()
  })

  it('показывает ошибку при неверных данных', async () => {
    const login = vi.fn(async () => {
      throw Object.assign(new Error('x'), {
        isAxiosError: true,
        response: { status: 401, data: { code: 'AUTH_INVALID_CREDENTIALS', message: 'Неверная пара' } },
      })
    })
    renderLogin({ login })
    await userEvent.type(screen.getByLabelText(/email/i), 'a@b.c')
    await userEvent.type(screen.getByLabelText(/пароль/i), 'wrongpass1234')
    await userEvent.click(screen.getByRole('button', { name: /войти/i }))
    expect(await screen.findByText(/неверная пара/i)).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Запустить — убедиться, что падает**

Run: `cd web-admin && npx vitest run src/pages/LoginPage.test.tsx`
Expected: FAIL — модуль `./LoginPage` не найден.

- [ ] **Step 3: Реализовать LoginPage**

Create `web-admin/src/pages/LoginPage.tsx`:
```tsx
import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '@/auth/AuthContext'
import { extractApiError } from '@/api/errors'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card } from '@/components/ui/card'

export function LoginPage() {
  const { status, login, submit2fa } = useAuth()
  const navigate = useNavigate()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [challengeToken, setChallengeToken] = useState<string | null>(null)
  const [code, setCode] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (status === 'authenticated') navigate('/overview', { replace: true })
  }, [status, navigate])

  async function handleCredentials(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      const res = await login(email, password)
      if (res.requires2fa && res.challengeToken) {
        setChallengeToken(res.challengeToken)
      }
      // если 2FA не нужен — AuthContext уже перевёл status в authenticated,
      // редирект выполнит useEffect выше.
    } catch (err) {
      setError(extractApiError(err).message)
    } finally {
      setBusy(false)
    }
  }

  async function handle2fa(e: FormEvent) {
    e.preventDefault()
    if (!challengeToken) return
    setError(null)
    setBusy(true)
    try {
      await submit2fa(challengeToken, code)
    } catch (err) {
      setError(extractApiError(err).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="flex h-screen items-center justify-center bg-slate-50">
      <Card className="w-full max-w-sm p-6">
        <h1 className="mb-1 text-lg font-semibold text-slate-800">CleanCity — админ-панель</h1>
        <p className="mb-4 text-sm text-slate-400">
          {challengeToken ? 'Введите код из приложения-аутентификатора' : 'Вход для сотрудников'}
        </p>

        {!challengeToken ? (
          <form onSubmit={handleCredentials} className="flex flex-col gap-3">
            <div className="flex flex-col gap-1">
              <Label htmlFor="email">Email</Label>
              <Input
                id="email"
                type="email"
                autoComplete="username"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
            </div>
            <div className="flex flex-col gap-1">
              <Label htmlFor="password">Пароль</Label>
              <Input
                id="password"
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </div>
            {error && <p className="text-sm text-red-600">{error}</p>}
            <Button type="submit" disabled={busy}>
              {busy ? 'Вход…' : 'Войти'}
            </Button>
            <Link to="/forgot-password" className="text-center text-sm text-slate-500 hover:underline">
              Забыли пароль?
            </Link>
          </form>
        ) : (
          <form onSubmit={handle2fa} className="flex flex-col gap-3">
            <div className="flex flex-col gap-1">
              <Label htmlFor="code">Код подтверждения</Label>
              <Input
                id="code"
                inputMode="numeric"
                autoComplete="one-time-code"
                value={code}
                onChange={(e) => setCode(e.target.value)}
                required
              />
            </div>
            {error && <p className="text-sm text-red-600">{error}</p>}
            <Button type="submit" disabled={busy}>
              {busy ? 'Проверка…' : 'Подтвердить'}
            </Button>
          </form>
        )}
      </Card>
    </div>
  )
}
```

- [ ] **Step 4: Запустить тест — убедиться, что проходит**

Run: `cd web-admin && npx vitest run src/pages/LoginPage.test.tsx`
Expected: PASS, 2 теста зелёные.

- [ ] **Step 5: Commit**

```bash
git add web-admin/src/pages/LoginPage.tsx web-admin/src/pages/LoginPage.test.tsx
git commit -m "feat(web): LoginPage — вход + шаг 2FA"
```

---

## Task 12: AcceptInvitePage

**Files:**
- Create: `web-admin/src/pages/passwordRules.ts`
- Create: `web-admin/src/pages/AcceptInvitePage.tsx`
- Test: `web-admin/src/pages/passwordRules.test.ts`

- [ ] **Step 1: Написать падающий тест валидатора пароля**

Create `web-admin/src/pages/passwordRules.test.ts`:
```ts
import { describe, it, expect } from 'vitest'
import { validateAdminPassword } from './passwordRules'

describe('validateAdminPassword', () => {
  it('принимает валидный пароль', () => {
    expect(validateAdminPassword('Secret123!xyz')).toBeNull()
  })
  it('отклоняет короткий пароль', () => {
    expect(validateAdminPassword('Ab1!')).toMatch(/12/)
  })
  it('требует цифру', () => {
    expect(validateAdminPassword('Abcdefgh!xyz')).toMatch(/цифр/i)
  })
  it('требует заглавную букву', () => {
    expect(validateAdminPassword('secret123!xyz')).toMatch(/заглавн/i)
  })
  it('требует спецсимвол', () => {
    expect(validateAdminPassword('Secret123xyzAB')).toMatch(/спецсимвол/i)
  })
})
```

- [ ] **Step 2: Запустить — убедиться, что падает**

Run: `cd web-admin && npx vitest run src/pages/passwordRules.test.ts`
Expected: FAIL — модуль не найден.

- [ ] **Step 3: Реализовать валидатор**

Create `web-admin/src/pages/passwordRules.ts`:
```ts
/** Правила пароля админа (зеркало backend AuthService.validatePassword). */
export function validateAdminPassword(pw: string): string | null {
  if (pw.length < 12) return 'Пароль должен быть не короче 12 символов'
  if (!/[0-9]/.test(pw)) return 'Пароль должен содержать цифру'
  if (!/[A-ZА-Я]/.test(pw)) return 'Пароль должен содержать заглавную букву'
  if (!/[^A-Za-zА-Яа-я0-9]/.test(pw)) return 'Пароль должен содержать спецсимвол'
  return null
}
```

- [ ] **Step 4: Запустить тест — убедиться, что проходит**

Run: `cd web-admin && npx vitest run src/pages/passwordRules.test.ts`
Expected: PASS, 5 тестов зелёные.

- [ ] **Step 5: Реализовать AcceptInvitePage**

Create `web-admin/src/pages/AcceptInvitePage.tsx`:
```tsx
import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '@/auth/AuthContext'
import { extractApiError } from '@/api/errors'
import { validateAdminPassword } from './passwordRules'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card } from '@/components/ui/card'

export function AcceptInvitePage() {
  const [params] = useSearchParams()
  const token = params.get('token')
  const { status, acceptInvite } = useAuth()
  const navigate = useNavigate()

  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (status === 'authenticated') navigate('/overview', { replace: true })
  }, [status, navigate])

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    if (password !== confirm) {
      setError('Пароли не совпадают')
      return
    }
    const ruleError = validateAdminPassword(password)
    if (ruleError) {
      setError(ruleError)
      return
    }
    if (!token) {
      setError('Ссылка-приглашение недействительна')
      return
    }
    setBusy(true)
    try {
      await acceptInvite(token, password)
      // успех → useEffect редиректит на /overview
    } catch (err) {
      setError(extractApiError(err).message)
    } finally {
      setBusy(false)
    }
  }

  if (!token) {
    return (
      <CenteredCard title="Приглашение">
        <p className="text-sm text-red-600">Ссылка-приглашение недействительна.</p>
        <Link to="/login" className="mt-3 block text-center text-sm text-slate-500 hover:underline">
          На страницу входа
        </Link>
      </CenteredCard>
    )
  }

  return (
    <CenteredCard title="Создание пароля">
      <p className="mb-4 text-sm text-slate-400">
        Задайте пароль для входа в админ-панель CleanCity.
      </p>
      <form onSubmit={handleSubmit} className="flex flex-col gap-3">
        <div className="flex flex-col gap-1">
          <Label htmlFor="password">Новый пароль</Label>
          <Input
            id="password"
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </div>
        <div className="flex flex-col gap-1">
          <Label htmlFor="confirm">Повторите пароль</Label>
          <Input
            id="confirm"
            type="password"
            autoComplete="new-password"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            required
          />
        </div>
        <p className="text-xs text-slate-400">
          Минимум 12 символов, заглавная буква, цифра и спецсимвол.
        </p>
        {error && <p className="text-sm text-red-600">{error}</p>}
        <Button type="submit" disabled={busy}>
          {busy ? 'Сохранение…' : 'Создать пароль и войти'}
        </Button>
      </form>
    </CenteredCard>
  )
}

function CenteredCard({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="flex h-screen items-center justify-center bg-slate-50">
      <Card className="w-full max-w-sm p-6">
        <h1 className="mb-1 text-lg font-semibold text-slate-800">{title}</h1>
        {children}
      </Card>
    </div>
  )
}
```

- [ ] **Step 6: Commit**

```bash
git add web-admin/src/pages/passwordRules.ts web-admin/src/pages/passwordRules.test.ts \
  web-admin/src/pages/AcceptInvitePage.tsx
git commit -m "feat(web): AcceptInvitePage + валидатор пароля админа"
```

---

## Task 13: ForgotPasswordPage + ResetPasswordPage

**Files:**
- Create: `web-admin/src/pages/ForgotPasswordPage.tsx`
- Create: `web-admin/src/pages/ResetPasswordPage.tsx`

- [ ] **Step 1: ForgotPasswordPage**

Create `web-admin/src/pages/ForgotPasswordPage.tsx`:
```tsx
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import * as authApi from '@/api/auth'
import { extractApiError } from '@/api/errors'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card } from '@/components/ui/card'

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [sent, setSent] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await authApi.forgotPassword(email)
      setSent(true)
    } catch (err) {
      setError(extractApiError(err).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="flex h-screen items-center justify-center bg-slate-50">
      <Card className="w-full max-w-sm p-6">
        <h1 className="mb-1 text-lg font-semibold text-slate-800">Восстановление пароля</h1>
        {sent ? (
          <>
            <p className="mt-2 text-sm text-slate-500">
              Если такой email зарегистрирован, на него отправлено письмо со ссылкой
              для сброса пароля.
            </p>
            <Link to="/login" className="mt-4 block text-center text-sm text-slate-500 hover:underline">
              Вернуться ко входу
            </Link>
          </>
        ) : (
          <form onSubmit={handleSubmit} className="mt-2 flex flex-col gap-3">
            <div className="flex flex-col gap-1">
              <Label htmlFor="email">Email</Label>
              <Input
                id="email"
                type="email"
                autoComplete="username"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
            </div>
            {error && <p className="text-sm text-red-600">{error}</p>}
            <Button type="submit" disabled={busy}>
              {busy ? 'Отправка…' : 'Отправить ссылку'}
            </Button>
            <Link to="/login" className="text-center text-sm text-slate-500 hover:underline">
              Вернуться ко входу
            </Link>
          </form>
        )}
      </Card>
    </div>
  )
}
```

- [ ] **Step 2: ResetPasswordPage**

Create `web-admin/src/pages/ResetPasswordPage.tsx`:
```tsx
import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import * as authApi from '@/api/auth'
import { extractApiError } from '@/api/errors'
import { validateAdminPassword } from './passwordRules'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card } from '@/components/ui/card'

export function ResetPasswordPage() {
  const [params] = useSearchParams()
  const token = params.get('token')
  const navigate = useNavigate()

  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    if (password !== confirm) {
      setError('Пароли не совпадают')
      return
    }
    const ruleError = validateAdminPassword(password)
    if (ruleError) {
      setError(ruleError)
      return
    }
    if (!token) {
      setError('Ссылка для сброса недействительна')
      return
    }
    setBusy(true)
    try {
      await authApi.resetPassword(token, password)
      toast.success('Пароль обновлён. Войдите с новым паролем.')
      navigate('/login', { replace: true })
    } catch (err) {
      setError(extractApiError(err).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="flex h-screen items-center justify-center bg-slate-50">
      <Card className="w-full max-w-sm p-6">
        <h1 className="mb-1 text-lg font-semibold text-slate-800">Новый пароль</h1>
        {!token ? (
          <>
            <p className="mt-2 text-sm text-red-600">Ссылка для сброса недействительна.</p>
            <Link to="/login" className="mt-3 block text-center text-sm text-slate-500 hover:underline">
              На страницу входа
            </Link>
          </>
        ) : (
          <form onSubmit={handleSubmit} className="mt-2 flex flex-col gap-3">
            <div className="flex flex-col gap-1">
              <Label htmlFor="password">Новый пароль</Label>
              <Input
                id="password"
                type="password"
                autoComplete="new-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </div>
            <div className="flex flex-col gap-1">
              <Label htmlFor="confirm">Повторите пароль</Label>
              <Input
                id="confirm"
                type="password"
                autoComplete="new-password"
                value={confirm}
                onChange={(e) => setConfirm(e.target.value)}
                required
              />
            </div>
            <p className="text-xs text-slate-400">
              Минимум 12 символов, заглавная буква, цифра и спецсимвол.
            </p>
            {error && <p className="text-sm text-red-600">{error}</p>}
            <Button type="submit" disabled={busy}>
              {busy ? 'Сохранение…' : 'Сохранить пароль'}
            </Button>
          </form>
        )}
      </Card>
    </div>
  )
}
```

- [ ] **Step 3: Завершить Task 10 Step 4 — проверить компиляцию всего проекта**

Run: `cd web-admin && npx tsc --noEmit`
Expected: без ошибок (все страницы, на которые ссылается `App.tsx`, теперь созданы).

- [ ] **Step 4: Commit**

```bash
git add web-admin/src/pages/ForgotPasswordPage.tsx web-admin/src/pages/ResetPasswordPage.tsx
git commit -m "feat(web): ForgotPasswordPage + ResetPasswordPage"
```

---

## Task 14: Полный прогон тестов, ручной чекпоинт, обновление PLAN.md

**Files:**
- Modify: `docs/PLAN.md`

- [ ] **Step 1: Прогнать все тесты и сборку**

Run: `cd web-admin && npm run test`
Expected: PASS — все тесты (`client`, `errors`, `ProtectedRoute`, `LoginPage`, `passwordRules`).

Run: `cd web-admin && npm run build`
Expected: сборка без ошибок.

- [ ] **Step 2: Ручной чекпоинт с реальным backend**

1. Поднять backend локально (Docker): `cd ~/Desktop/Myapp/cleancity-kmp && docker compose up -d` — дождаться `healthy`.
2. Создать тестового админа **без 2FA** (через invite или прямой INSERT в БД — способ зависит от состояния dev-окружения; цель — иметь активного админа с известным паролем и `two_factor_enabled=false`).
3. `cd web-admin && npm run dev` → открыть URL.
4. Зайти на `/overview` без входа → редирект на `/login`.
5. Ввести email+пароль → попадание на `/overview`, виден Layout: сайдбар (Обзор/Жалобы/Объявления/Аналитика/Настройки) + топбар с именем/ролью.
6. Кликнуть по пунктам сайдбара → заголовок топбара и заглушка меняются, активный пункт подсвечен.
7. Перезагрузить страницу (F5) → остаёшься авторизованным (refresh отработал).
8. «Выход» → редирект на `/login`.
9. Ввести неверный пароль → видна инлайн-ошибка.

Зафиксировать результат (скриншот/заметка). Если есть админ с настроенным TOTP — дополнительно проверить, что после пароля появляется шаг ввода кода.

- [ ] **Step 3: Обновить PLAN.md**

В `docs/PLAN.md`, раздел «День 15»:
- отметить выполненные пункты `[x]`;
- заменить строку про `LoginPage обязательная смена пароля при must_change_password=true` на:
  `[x] AcceptInvitePage — установка пароля по invite-токену (флаг must_change_password фронтом не используется, см. дизайн Day 15)`.

- [ ] **Step 4: Финальный commit**

```bash
cd ~/Desktop/Myapp/cleancity-kmp
git add docs/PLAN.md
git commit -m "docs: закрыть Day 15 в PLAN.md — web-admin scaffold + auth"
```

---

## Self-review (выполнено при написании плана)

- **Покрытие спеки:** структура проекта → Task 1; типы → Task 2; token-store + single-flight → Task 3; interceptors → Task 4; extractApiError → Task 5; auth API → Task 6; AuthContext (+защита от подмены: `clearSession`+`queryClient.clear()`) → Task 7; ProtectedRoute → Task 8; Layout → Task 9; роутинг + заглушки → Task 10; LoginPage+2FA → Task 11; AcceptInvitePage → Task 12; Forgot/Reset → Task 13; обработка ошибок по `code` → Tasks 5/11/12/13; тестирование → Tasks 3/5/8/11/12; ручной чекпоинт → Task 14. Дисциплина данных (`.env`) → Task 1.
- **Циклическая зависимость Task 8 ↔ Task 9** (ProtectedRoute импортирует AppLayout) — разрешена явно: Task 8 пишет тест, Task 9 создаёт Layout, тест зеленеет в Task 8 Step 6. Аналогично Task 10 ↔ Tasks 11–13.
- **Согласованность типов:** `AuthResponse/UserResponse/LoginResponse` определены в Task 2 и используются везде единообразно; `refreshAccessToken()` возвращает `AuthResponse` (Task 3) — interceptor (Task 4) и AuthContext (Task 7) читают из него `accessToken`/`user`; `AuthContextValue` определён в `AuthTestContext.tsx` (Task 8 Step 3) и переиспользуется и приложением, и тестами.
- **Плейсхолдеров нет** — каждый шаг содержит полный код или точную команду.
