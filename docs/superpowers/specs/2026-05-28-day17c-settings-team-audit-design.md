# Day 17C — SettingsPage: команда + audit-лог (веб-админка)

**Дата:** 2026-05-28
**Статус:** дизайн утверждён, готов к плану реализации
**Контекст:** День 17, пункт 17C. 17A (Dashboard) и 17B (Announcements) закрыты. Из 17C по плану значились «профиль + смена пароля + 2FA + команда + audit-лог» — скоп сужен до **команда + audit-лог**, остальное переведено в backlog.

## Цель

Заменить заглушку `SectionPlaceholder` на маршруте `/settings` рабочей страницей с двумя секциями:

1. **Команда** — список сотрудников (активные / замороженные / ожидают), приглашение нового, заморозка / разморозка, отзыв pending-приглашения.
2. **Журнал событий** — последние 50 записей из `audit_log` (кто, что, когда, IP).

Одновременно фиксируется упрощение модели ролей: `INSPECTOR` удаляется, остаются `RESIDENT`, `OPERATOR`, `ADMIN`. Управление командой доступно только `ADMIN`; `OPERATOR` видит обе секции в read-only.

## Объём (scope)

Поддерживаемые операции:

- **Список сотрудников** — `GET /auth/admin/users?status=active|frozen|pending`
- **Приглашение** — `POST /auth/admin/invite` (существующий эндпоинт, тело сужается до ролей `ADMIN | OPERATOR`)
- **Заморозка** — `POST /auth/admin/users/{id}/freeze` (revoke всех refresh-токенов)
- **Разморозка** — `POST /auth/admin/users/{id}/unfreeze`
- **Отзыв приглашения** — `DELETE /auth/admin/invitations/{id}` (id = `users.id` pending-сотрудника)
- **Audit-лог** — `GET /auth/admin/audit-log?limit=50`

### Не входит в 17C (явно в backlog)

- Профиль (ФИО / email / телефон / должность), `PATCH /auth/me`
- Смена пароля (`POST /auth/change-password`)
- UI для 2FA setup и активных сессий (бэкенд `POST /auth/2fa/setup`, `GET /auth/sessions` остаются)
- Hard-delete активного сотрудника
- Редактирование роли / района сотрудника через UI
- Фильтры и пагинация в audit-логе
- E2E на «JWT продолжает работать после заморозки до истечения TTL» — задокументировано как E3, не лечится

## Удаление роли `INSPECTOR` (предусловие 17C)

В текущей кодовой базе `INSPECTOR` упомянут в 9 местах, но **ни одного пользователя с этой ролью не существует** (нет в seed, нет в миграциях). Это мёртвый код от ранних версий SPEC.

Удаляется механически (одна фаза, без миграции БД, схема `users.role varchar(20)` без enum-constraint):

| Файл | Что меняется |
|---|---|
| `shared/.../UserRole.kt` | удалить `INSPECTOR`, поправить комментарий |
| `shared/.../AuthRequests.kt` | комментарий в `AdminInviteRequest.role` |
| `backend/.../ComplaintService.kt:92` | `ADMIN_ROLES = setOf(ADMIN, OPERATOR)` |
| `backend/.../AnnouncementService.kt:21` | то же |
| `backend/.../AnalyticsRoutes.kt:17` | то же |
| `backend/.../UserRepository.kt:42-48` | `findAdmins()` без INSPECTOR |
| `backend/.../JwtConfig.kt:106-118` | TTL-ветки без INSPECTOR |
| `backend/.../AuthRoutes.kt:237` | guard `/admin/invite` сужается до `role == ADMIN`; `req.role` принимает только `ADMIN | OPERATOR` |
| `web-admin/src/api/types.ts:1` | `UserRole` без `INSPECTOR` |

Тест `UserRoleEnumTest` фиксирует удаление: `UserRole.valueOf("INSPECTOR")` должно бросать `IllegalArgumentException`.

## Матрица доступа

| Эндпоинт | `ADMIN` | `OPERATOR` |
|---|---|---|
| `GET /auth/admin/users` | ✓ | ✓ (read-only) |
| `GET /auth/admin/audit-log` | ✓ | ✓ |
| `POST /auth/admin/invite` | ✓ | ✗ `403 FORBIDDEN` |
| `POST /auth/admin/users/{id}/freeze` | ✓ | ✗ |
| `POST /auth/admin/users/{id}/unfreeze` | ✓ | ✗ |
| `DELETE /auth/admin/invitations/{id}` | ✓ | ✗ |
| Жалобы / объявления / аналитика | ✓ | ✓ (как сейчас) |

Frontend читает `role` из JWT-claims и **не рендерит** кнопки-действия для `OPERATOR` (не disabled — отсутствуют). Бэкенд независимо от UI проверяет роль на каждой ручке.

## Бэкенд

### DTO (shared module)

```kotlin
@Serializable
enum class TeamStatus { ACTIVE, FROZEN, PENDING }

@Serializable
data class TeamMemberDto(
    val id: Long,
    val email: String,
    val fullName: String?,
    val role: String,           // "ADMIN" | "OPERATOR"
    val district: String?,
    val status: TeamStatus,
    val createdAt: String,      // ISO-8601 с offset
    val lastLoginAt: String?,   // null для pending
    val invitedAt: String?      // createdAt для pending, иначе null
)

@Serializable
data class TeamMembersResponse(val items: List<TeamMemberDto>)

@Serializable
data class AuditEntryDto(
    val id: Long,
    val timestamp: String,      // ISO-8601 с offset
    val actorEmail: String?,    // null для системных действий
    val action: String,         // AuditAction.name
    val targetType: String?,
    val targetId: String?,
    val ip: String?,
    val details: String?
)

@Serializable
data class AuditLogResponse(val items: List<AuditEntryDto>)
```

### Классификация статусов

| Статус | Условие |
|---|---|
| `ACTIVE` | `role IN (ADMIN, OPERATOR)` AND `is_active = true` AND `email_verified = true` |
| `FROZEN` | `role IN (ADMIN, OPERATOR)` AND `is_active = false` AND `email_verified = true` |
| `PENDING` | `role IN (ADMIN, OPERATOR)` AND `is_active = false` AND `email_verified = false` |

`RESIDENT` исключён из всех выборок.

### Новые `AuditAction`

Enum определён в `backend/.../database/tables/AuditLog.kt` (тот же файл, что и таблица). Колонка `audit_log.action varchar(50)` — application-level enum, **миграция БД не нужна**.

- `ADMIN_USER_FROZEN`
- `ADMIN_USER_UNFROZEN`
- `ADMIN_INVITE_REVOKED`

### Новые `ErrorCodes`

- `ADMIN_CANNOT_FREEZE_SELF` (403)
- `LAST_ACTIVE_ADMIN` (409)
- `INVITE_NOT_ACCEPTED` (400)
- `NOT_A_PENDING_INVITE` (400)

### Инварианты домена

| Код | Правило | HTTP | Где enforce |
|---|---|---|---|
| I1 | Нельзя freeze самого себя | 403 `ADMIN_CANNOT_FREEZE_SELF` | `AuthService.freezeUser` start |
| I2 | После freeze должен остаться хотя бы один активный `ADMIN` | 409 `LAST_ACTIVE_ADMIN` | `AuthService.freezeUser` (count + check в одной транзакции) |
| I3 | Нельзя unfreeze pending (`email_verified=false`) — должен пройти accept-invite | 400 `INVITE_NOT_ACCEPTED` | `AuthService.unfreezeUser` |
| I4 | Нельзя revoke не-pending запись | 400 `NOT_A_PENDING_INVITE` | `AuthService.revokeInvitation` |
| I5 | После freeze все refresh-токены target ревокаются (логаут на всех устройствах) | — | `AuthService.freezeUser` после `setActive(false)` |
| I6 | `/auth/admin/*` для freeze/unfreeze/revoke/invite — только `ADMIN`; для list/audit-log — `ADMIN` или `OPERATOR` | 403 | в route handler |

Примечание к I2: проверка делается на target=ADMIN. Если target=OPERATOR — заморозить можно всегда. Если target=ADMIN — `SELECT count(*) FROM users WHERE role='ADMIN' AND is_active=true` должен быть `> 1`, иначе 409.

### Сервис (`AuthService.kt`)

```kotlin
suspend fun listTeamMembers(status: TeamStatus?): List<TeamMemberDto>
suspend fun freezeUser(actorId: Long, targetId: Long, ip: String?, ua: String?)
suspend fun unfreezeUser(actorId: Long, targetId: Long, ip: String?, ua: String?)
suspend fun revokeInvitation(actorId: Long, targetId: Long, ip: String?, ua: String?)
suspend fun recentAuditEvents(limit: Int = 50): List<AuditEntryDto>
```

`revokeInvitation` физически удаляет pending-юзера (`users.delete(targetId)`) после `emailTokens.invalidateInviteForUser` — pending-row пустая (placeholder password, нет внешних ссылок), retention не нужен.

### Repository

- `UserRepository.listByTeamStatus(status: TeamStatus?): List<UserRow>` — новый метод. Фильтрация по `role IN (ADMIN, OPERATOR)` и комбинации `is_active`/`email_verified`. `status=null` → все три класса.
- `TokenRepository.invalidateInviteForUser(userId: Long): Int` — новый. Помечает `revoked_at = NOW()` для всех непогашенных `ADMIN_INVITE`-токенов юзера. Возвращает число затронутых строк.
- `TokenRepository.revokeAllUserRefreshTokens(userId: Long)` — **уже существует** в `TokenRepository.kt:103`, переиспользуем для I5.
- `AuditLogRepository` — **новый класс** (read-only, отделён от записывающего `AuditLogger`). Метод `findRecent(limit: Int): List<AuditEntryWithActor>`, где `AuditEntryWithActor` объединяет `AuditLog`-row и `users.email` через LEFT JOIN по `audit_log.actor_user_id`.

### Эндпоинты (`AuthRoutes.kt`, под `authenticate("auth-jwt")`)

```kotlin
get("/admin/users") {
    requireTeamReadAccess()  // ADMIN или OPERATOR
    val status = call.parameters["status"]?.let { TeamStatus.valueOf(it.uppercase()) }
    call.respond(TeamMembersResponse(service.listTeamMembers(status)))
}

post("/admin/users/{id}/freeze") {
    requireAdminOnly()
    val targetId = call.parameters["id"]!!.toLong()
    service.freezeUser(call.requireUserId(), targetId, call.clientIp(), call.userAgentSafe())
    call.respond(HttpStatusCode.NoContent)
}

post("/admin/users/{id}/unfreeze") { /* симметрично */ }

delete("/admin/invitations/{id}") {
    requireAdminOnly()
    val targetId = call.parameters["id"]!!.toLong()
    service.revokeInvitation(call.requireUserId(), targetId, call.clientIp(), call.userAgentSafe())
    call.respond(HttpStatusCode.NoContent)
}

get("/admin/audit-log") {
    requireTeamReadAccess()
    val limit = call.parameters["limit"]?.toIntOrNull()?.coerceIn(1, 50) ?: 50
    call.respond(AuditLogResponse(service.recentAuditEvents(limit)))
}
```

Существующий `POST /auth/admin/invite` (`AuthRoutes.kt:234`) меняется минимально: role-guard ужесточается до `role == ADMIN`; валидация `req.role` отвергает всё кроме `ADMIN | OPERATOR`.

## Frontend

### Routing

`App.tsx:29` — `<Route path="/settings" element={<SettingsPage />} />`. Пункт «Настройки» в сайдбаре остаётся видимым для обеих ролей.

### Файловая раскладка

```
web-admin/src/
├─ api/
│  └─ admin.ts                        ← новый: 6 функций
├─ pages/
│  ├─ SettingsPage.tsx                ← новый: компонует две секции
│  ├─ SettingsPage.test.tsx           ← 8 кейсов
│  └─ settings/
│     ├─ TeamSection.tsx              ← табы + Пригласить + invalidate
│     ├─ TeamTable.tsx                ← одна таблица под текущий таб
│     ├─ TeamTable.test.tsx           ← 2 кейса
│     ├─ InviteMemberDialog.tsx       ← только ADMIN, role = ADMIN|OPERATOR
│     ├─ ConfirmActionDialog.tsx      ← переиспользуем для freeze/unfreeze/revoke
│     ├─ AuditLogSection.tsx          ← заголовок + таблица
│     ├─ AuditTable.tsx               ← 50 строк, без пагинации
│     └─ actionLabels.ts              ← AuditAction.name → русская строка
```

### Типы (`api/types.ts`)

```ts
export type UserRole = 'RESIDENT' | 'OPERATOR' | 'ADMIN'  // INSPECTOR удалён
export type TeamStatus = 'ACTIVE' | 'FROZEN' | 'PENDING'

export type TeamMemberDto = {
  id: number
  email: string
  fullName: string | null
  role: 'ADMIN' | 'OPERATOR'
  district: string | null
  status: TeamStatus
  createdAt: string
  lastLoginAt: string | null
  invitedAt: string | null
}

export type AuditEntryDto = {
  id: number
  timestamp: string
  actorEmail: string | null
  action: string
  targetType: string | null
  targetId: string | null
  ip: string | null
  details: string | null
}
```

### API-клиент (`api/admin.ts`)

```ts
listTeamMembers(status?: 'active' | 'frozen' | 'pending'): Promise<TeamMemberDto[]>
inviteTeamMember(email: string, role: 'ADMIN' | 'OPERATOR'): Promise<UserResponse>
freezeUser(userId: number): Promise<void>
unfreezeUser(userId: number): Promise<void>
revokeInvitation(userId: number): Promise<void>
recentAuditEvents(limit?: number): Promise<AuditEntryDto[]>
```

### Структура `SettingsPage`

```
SettingsPage
├─ <PageHeader title="Настройки" />
├─ <TeamSection>
│    ├─ Header: «Команда» + кнопка «Пригласить сотрудника» (рендер только если role==='ADMIN')
│    ├─ <Tabs defaultValue="active">
│    │     [Активные (N)] [Замороженные (N)] [Ожидают (N)]
│    │     ↓
│    │     <TeamTable status={tab} role={currentRole} />
│    │       колонки: Email · ФИО · Роль · Район · Последний вход · Действия
│    │       действия зависят от tab+role:
│    │         active   + ADMIN → [Заморозить]
│    │         frozen   + ADMIN → [Разморозить]
│    │         pending  + ADMIN → [Отозвать]
│    │         OPERATOR         → колонка «Действия» отсутствует
│    └─ EmptyState на каждый таб
└─ <AuditLogSection>
     ├─ Header: «Журнал событий»
     └─ <AuditTable />
           колонки: Время · Кто · Действие · Объект · IP
           50 последних, без пагинации
```

### TanStack Query

- `useTeamMembers(status)` — `queryKey: ['team', status]`. Refetch on focus.
- `useAuditEvents()` — `queryKey: ['audit-log']`. Refetch on focus.

После каждого успешного действия (freeze/unfreeze/revoke/invite) — invalidate `['team']` и `['audit-log']`.

### Confirm-диалоги

| Действие | Заголовок | Сообщение | Кнопка |
|---|---|---|---|
| Заморозить | «Заморозить сотрудника?» | «Все активные сессии будут отозваны. Сотрудник не сможет войти.» | «Заморозить» (destructive) |
| Разморозить | «Разморозить сотрудника?» | «Сотрудник снова сможет войти в систему.» | «Разморозить» |
| Отозвать | «Отозвать приглашение?» | «Ссылка из письма перестанет работать.» | «Отозвать» (destructive) |

### Обработка ошибок (соответствие `ErrorCodes` → текст)

| Код | Текст в UI |
|---|---|
| `ADMIN_CANNOT_FREEZE_SELF` | «Нельзя заморозить собственный аккаунт.» |
| `LAST_ACTIVE_ADMIN` | «Это последний активный администратор. Сначала добавь и активируй другого.» |
| `INVITE_NOT_ACCEPTED` | «Этот сотрудник ещё не принял приглашение. Дождитесь активации или отзовите приглашение.» |
| `NOT_A_PENDING_INVITE` | «Это уже активный сотрудник, отзыв приглашения недоступен.» |
| `FORBIDDEN` | «Действие доступно только администраторам.» |

Показ через `sonner` toast (как в `AnnouncementsPage`).

### Стилистика

Переиспользуются shadcn-компоненты, которые уже есть: `Card`, `Tabs`, `Table`, `Dialog`, `Badge`, `Button`. Цветовая схема — как в `ComplaintsPage`. Бейдж роли: ADMIN — синий, OPERATOR — серый. Бейдж статуса: ACTIVE — зелёный, FROZEN — оранжевый, PENDING — синий dashed.

## Тестирование

### Backend (integration, Ktor TestApplication + Testcontainers)

`AuthAdminTeamRoutesTest`:

- `listTeamMembers_returns_active_frozen_pending_by_status`
- `listTeamMembers_omits_residents`
- `operator_can_list_but_cannot_freeze`
- `freeze_revokes_refresh_tokens` ← покрывает checkpoint из PLAN.md «заморозка отзывает refresh-токены»
- `freeze_self_returns_403`
- `freeze_last_active_admin_returns_409`
- `freeze_admin_when_other_admin_exists_succeeds`
- `unfreeze_pending_returns_400`
- `revoke_invitation_invalidates_token_and_removes_user`
- `revoke_invitation_on_active_user_returns_400`
- `audit_log_returns_recent_events_with_actor_email`
- `audit_log_limit_max_50`
- `invite_role_inspector_returns_400`
- `operator_cannot_invite`

`UserRoleEnumTest`:

- `inspector_value_no_longer_exists` — `UserRole.valueOf("INSPECTOR")` бросает `IllegalArgumentException`

### Frontend (Vitest + Testing Library)

`SettingsPage.test.tsx`:

- `renders_team_and_audit_sections`
- `admin_sees_invite_and_action_buttons`
- `operator_does_not_see_action_buttons`
- `freeze_action_shows_confirm_dialog_with_revoke_warning`
- `freeze_success_invalidates_team_query`
- `last_active_admin_error_shows_friendly_toast`
- `tab_counts_update_after_action`
- `pending_tab_shows_revoke_button_only`

`TeamTable.test.tsx`:

- `empty_state_per_tab`
- `last_login_formatting`

### Edge cases (документированы)

| # | Сценарий | Поведение |
|---|---|---|
| E1 | Двое ADMIN одновременно морозят друг друга | I2 (count в той же транзакции). Победитель — кто первый; второй получает 409 `LAST_ACTIVE_ADMIN` |
| E2 | Stale UI: кнопка «Заморозить» на уже замороженном | API вернёт ошибку → toast → invalidate query → список самокорректируется |
| E3 | Заморозка не лечит уже выпущенный access-JWT (TTL 15 мин) | Документировано. Refresh-токены ревокаются → как только access истечёт, рефреш вернёт 401. Полноценный blacklist JWT — overkill для MVP |
| E4 | Race: invite принят ровно при revoke | Либо invite успел (status flip → revoke вернёт 400 NOT_A_PENDING_INVITE), либо revoke успел (token consumed → accept-invite вернёт 400 INVALID_TOKEN). Оба корректны |
| E5 | Invite на уже зарегистрированный email | Существующий `EmailAlreadyRegisteredException` → 409 |
| E6 | Audit-лог пуст (свежая БД) | `{items: []}` → EmptyState «События будут появляться по мере действий в системе» |
| E7 | `actorEmail = null` (системное действие) | Frontend показывает «Система» |

### Ручной smoke (перед коммитом)

1. Логин ADMIN → `/settings`
2. Пригласить `invitee1@test.local` с ролью OPERATOR
3. Mailpit → accept-invite → пароль установлен
4. Видим нового OPERATOR в табе «Активные»
5. Логин под OPERATOR → `/settings` → список без кнопок, audit-лог виден
6. Назад под ADMIN → Заморозить этого OPERATOR
7. У OPERATOR refresh → 401, выкидывает на login
8. Audit-лог: `ADMIN_INVITE_SENT`, `ADMIN_INVITE_ACCEPTED`, `ADMIN_USER_FROZEN` с правильными actorEmail
9. Разморозить → OPERATOR заново может залогиниться
10. Пригласить ещё одного → «Отозвать» из таба «Ожидают» → ссылка из письма не работает

## Раскладка фаз реализации (для writing-plans)

Семь фаз, каждая компилируется и тестируется отдельно:

1. **Cleanup INSPECTOR** — механический рефакторинг (10 точек), существующие тесты остаются зелёными
2. **DTO + AuditAction + ErrorCodes** — новые типы в shared/backend, без логики
3. **Repository** — `UserRepository.listByTeamStatus`, `TokenRepository.invalidateInviteForUser`, новый `AuditLogRepository.findRecent`. `TokenRepository.revokeAllUserRefreshTokens` уже есть.
4. **Service + Routes** — `freezeUser/unfreezeUser/revokeInvitation/listTeamMembers/recentAuditEvents` + 5 новых ручек + ужесточение `/admin/invite`
5. **Backend тесты** — `AuthAdminTeamRoutesTest` (14 кейсов) + `UserRoleEnumTest`
6. **Frontend** — типы, API-клиент, компоненты, замена route
7. **Frontend тесты + ручной smoke + правка `PLAN.md`** — `[x]` напротив 17C-пунктов

Грубая оценка размера: ~25 файлов изменяется/создаётся, ~14 backend + ~10 frontend тестов. Объём на 1 рабочий день при сосредоточенной работе.

## Вне объёма

- Профиль / смена пароля / 2FA UI / сессии UI — backlog (бэкенд готов)
- Hard-delete активного сотрудника, редактирование ФИО/роли/района — backlog
- Фильтры и пагинация audit-лога — backlog
- Уведомления-тогглы из мока (новая жалоба, SLA, дайджест) — нет бэкенда, YAGNI
- Аватарка сотрудника — нет бэкенда, YAGNI
- PDF «Сводный отчёт за месяц» — это **17D**, отдельный спек

## Ссылки

- План: `docs/PLAN.md` (день 17, пункт 17C, строки 447–451)
- Мокап: `docs/mockups/admin-dashboard-v2.html` (`#screen-settings`, строки 1389+)
- Существующий бэкенд auth: `backend/.../auth/AuthRoutes.kt`, `AuthService.kt`
- Существующий audit: `backend/.../database/tables/AuditLog.kt`, `AuditAction.kt`
- Предыдущие спеки 17: `2026-05-24-day17a-dashboard-design.md`, `2026-05-24-day17b-announcements-design.md`
