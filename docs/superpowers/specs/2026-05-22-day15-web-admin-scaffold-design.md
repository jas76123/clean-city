# Day 15 — Web admin: scaffold + auth — дизайн

**Дата:** 2026-05-22
**Этап:** Неделя 3, День 15 (`docs/PLAN.md`)
**Цель дня:** создать с нуля проект `web-admin/`, настроить стек, реализовать
полный auth-флоу. Чекпоинт: «логин админа работает, после логина виден Layout
с сайдбаром и заглушками разделов».

---

## Контекст и решения брейншторма

- Папки `web-admin/` ещё нет — создаём с нуля рядом с `backend/` и `composeApp/`.
- Backend auth API готов: `/auth/login` (→ при 2FA `requires2fa+challengeToken`),
  `/auth/login-2fa`, `/auth/refresh`, `/auth/logout`, `/auth/admin/accept-invite`,
  `/auth/forgot-password`, `/auth/reset-password`.
- **Решение 1 — онбординг админа.** Бэкенд приглашает админа письмом со ссылкой
  `…/accept-invite?token=…`; админ ставит пароль через `POST /auth/admin/accept-invite`.
  Делаем `AcceptInvitePage`. Пункт PLAN.md «LoginPage обязательная смена пароля
  при `must_change_password=true`» **помечается устаревшим**: поле `mustChangePassword`
  не отдаётся в `UserResponse`, фронт его прочитать не может, и при обычном `/login`
  флага уже нет (его гасит accept-invite).
- **Решение 2 — слой данных.** TanStack Query поверх axios. Кэш, авто-рефетч,
  invalidation — задел под Day 16–17 (фильтры жалоб, «счётчики обновляются после действий»).
- **Решение 3 — набор auth-страниц.** Login + 2FA + AcceptInvite + Forgot + Reset
  (полный auth-флоу).
- **Решение 4 — хранение токенов (Подход A).** В `localStorage` лежит только
  `refreshToken`; `accessToken` — в памяти модуля axios. На старте приложения,
  если есть refresh-токен — зовём `/auth/refresh`. Один источник правды,
  access-токен не лежит на диске.

---

## Стек

Vite + React + TypeScript, Tailwind v4 + shadcn/ui, react-router, axios,
TanStack Query, Vitest + React Testing Library, sonner (тосты).

Сложные либы форм (react-hook-form/zod) **не тянем** — формы маленькие, хватает
локального состояния + клиентской валидации.

---

## Структура проекта

```
web-admin/
  index.html, vite.config.ts, components.json, package.json
  .env.example          # VITE_API_BASE_URL (dev → http://localhost:8081)
  src/
    main.tsx
    App.tsx             # <QueryClientProvider><AuthProvider><Router>
    api/
      client.ts         # axios instance + token-store + interceptors
      auth.ts           # login / login2fa / refresh / logout / acceptInvite / forgot / reset
      types.ts          # TS-типы ответов — зеркало Kotlin-моделей shared/
    auth/
      AuthContext.tsx   # user, status, login/submit2fa/logout
      ProtectedRoute.tsx
    pages/
      LoginPage.tsx, AcceptInvitePage.tsx
      ForgotPasswordPage.tsx, ResetPasswordPage.tsx
      placeholders/     # заглушки разделов (наполнятся Day 16–17)
    components/
      layout/  AppLayout.tsx, Sidebar.tsx, Topbar.tsx
      ui/      # shadcn-компоненты
    hooks/              # пусто на Day 15
    lib/utils.ts        # shadcn cn()
```

**Дисциплина данных:** `VITE_API_BASE_URL` и все URL — только через `.env`;
в `.env.example` — плейсхолдеры; реальный `.env` — в `.gitignore`. Никаких
email/паролей админа в коде или коммитах.

---

## Auth-слой (Подход A)

### Token-store (модуль в `client.ts`)

- `accessToken` — переменная в памяти модуля.
- `refreshToken` — `localStorage['cc_refresh']` (единственное, что переживает перезагрузку).
- `setSession(auth)`, `clearSession()`, `getAccessToken()`.

### axios instance

- **Request-interceptor:** подставляет `Authorization: Bearer <accessToken>`, если есть.
- **Response-interceptor:** на `401` (если запрос ещё не повторялся и это не сам
  `/auth/refresh`) → рефреш через **single-flight Promise** (параллельные 401 ждут
  один общий рефреш) → подставляет новый токен и повторяет запрос. Если рефреш
  упал → `clearSession()` + редирект на `/login`.

### AuthContext

Состояние: `status: 'loading' | 'authenticated' | 'unauthenticated'`, `user`.

- **На маунте:** есть `refreshToken` → `/auth/refresh` → `user` + `authenticated`;
  нет → `unauthenticated`.
- `login(email, password)` → `/auth/login`. Возвращает либо
  `{requires2fa: true, challengeToken}` (LoginPage показывает шаг ввода кода),
  либо ставит сессию.
- `submit2fa(challengeToken, code)` → `/auth/login-2fa` → ставит сессию.
- `logout()` → `/auth/logout` (best-effort) → `clearSession()` +
  `queryClient.clear()` → `unauthenticated`.

### Защита от подмены пользователя

При `logout` и при любом новом `login` сначала `clearSession()` обнуляет
`accessToken` в памяти, и `queryClient.clear()` сбрасывает кэш — данные
предыдущего админа не должны утечь в новую сессию (аналог урока про
`BearerAuthProvider.clearToken()` в mobile).

### ProtectedRoute

`loading` → спиннер; `unauthenticated` → `<Navigate to="/login">`;
`authenticated` → рендерит `AppLayout` с дочерними роутами.

---

## Роутинг и страницы

| Путь | Доступ | Содержимое |
|------|--------|-----------|
| `/login` | public | LoginPage: email+пароль → при `requires2fa` шаг ввода 6-значного кода. Ссылка «Забыли пароль?» |
| `/accept-invite?token=` | public | AcceptInvitePage: задать пароль по invite-токену → `/auth/admin/accept-invite` → сразу авторизован |
| `/forgot-password` | public | ForgotPasswordPage: email → `/auth/forgot-password` → «если email есть, письмо отправлено» |
| `/reset-password?token=` | public | ResetPasswordPage: новый пароль по токену → `/auth/reset-password` → редирект на `/login` |
| `/` | protected | редирект на `/overview` |
| `/overview`, `/complaints`, `/announcements`, `/analytics`, `/settings` | protected | заглушки «Раздел в разработке» — наполнятся Day 16–17 |

Заглушки разделов нужны на Day 15, чтобы навигация по сайдбару реально работала
и чекпоинт был проверяем.

---

## Layout

Стиль из `docs/mockups/admin-dashboard-v2.html` (цвета, отступы, структура):

- **Sidebar:** лого CleanCity + пункты Обзор / Жалобы / Объявления / Аналитика /
  Настройки, активный пункт подсвечен (`NavLink`).
- **Topbar:** заголовок текущего раздела, имя+роль админа (из `AuthContext`),
  кнопка «Выход».
- **Контент:** `<Outlet/>` для дочернего роута.

---

## Обработка ошибок

Бэкенд отдаёт единый формат `{code, message}` (`ApiError`). Хелпер
`extractApiError(err)` достаёт `code`+`message` из ответа axios. Разбор по `code`,
не по тексту сообщения:

| Ситуация | `code` | Поведение UI |
|----------|--------|--------------|
| Неверный логин/пароль | `AUTH_INVALID_CREDENTIALS` | Инлайн-ошибка под формой |
| Email не подтверждён | `AUTH_EMAIL_UNVERIFIED` | Инлайн-сообщение |
| Аккаунт заблокирован (423) | — | Инлайн: «Аккаунт временно заблокирован» |
| Неверный код 2FA | `AUTH_2FA_INVALID` | Ошибка на шаге 2FA, поле не сбрасывается |
| Истёкший invite/reset-токен | `AUTH_INVALID_TOKEN` | Сообщение + ссылка вернуться на `/login` |
| Слабый пароль | `VALIDATION_WEAK_PASSWORD` | Ошибка под полем пароля |
| 429 (rate-limit) | — | «Слишком много попыток, подождите» |
| Сеть/5xx | — | Тост (sonner) |

`401` вне страниц логина обрабатывает interceptor (рефреш или вылет на `/login`) —
отдельный UI не нужен.

**Валидация форм:** пароль — по тем же правилам, что бэкенд (иначе ловим
`VALIDATION_WEAK_PASSWORD`); проверка на клиенте до отправки + показ серверной
ошибки.

---

## Тестирование

Vitest + React Testing Library, объём — без раздувания (диплом):

- **Юнит:** single-flight логика рефреша в token-store (параллельные 401 → один
  `/auth/refresh`); `extractApiError`.
- **Компонентные:** `ProtectedRoute` редиректит неавторизованного на `/login`;
  `LoginPage` переключается на шаг 2FA при `requires2fa: true`.
- Сетевой слой мокается (msw или ручные моки axios) — без реального бэкенда.

---

## Ручной чекпоинт Day 15

1. Поднять backend локально (Docker), создать тестового админа **без 2FA**.
2. `npm run dev` → `/login` → вход → виден Layout с сайдбаром и заглушкой «Обзор».
3. Перезагрузить страницу — сессия сохранилась (рефреш отработал).
4. «Выход» → редирект на `/login`, кэш очищен.

Проверка шага 2FA в UI — только happy-path вручную, если под рукой есть админ
с настроенным TOTP. Полноценный сценарий с 2FA-админом — на Day 19 (seed данных).

---

## Что НЕ входит в Day 15 (YAGNI / следующие дни)

- Содержимое разделов Обзор / Жалобы / Объявления / Аналитика / Настройки — Day 16–17.
- Управление командой, audit-лог, 2FA-setup (показ QR) — Day 17.
- Доработка бэкенда (`mustChangePassword` в `UserResponse` и т.п.) — не требуется,
  пункт PLAN.md устарел.
- Деплой `web-admin` — Day 18.

---

## Обновление PLAN.md

В разделе «День 15» отметить выполненные пункты и заменить пункт
«LoginPage обязательная смена пароля при `must_change_password=true`» на
«AcceptInvitePage — установка пароля по invite-токену», с пометкой, что флаг
`must_change_password` фронтом не используется.
