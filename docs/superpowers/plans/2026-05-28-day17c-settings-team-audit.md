# Day 17C — SettingsPage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Реализовать страницу `/settings` в веб-админке CleanCity с двумя секциями (Команда + Audit-лог) и удалить устаревшую роль `INSPECTOR` из всей кодовой базы.

**Architecture:** Бэкенд расширяется 5 новыми ручками в `AuthRoutes`, переиспользует существующие `AuthService.inviteAdmin` и `TokenRepository.revokeAllUserRefreshTokens`. Доменные инварианты (I1–I5) enforce-ятся в `AuthService`, а не в роутах. Frontend следует паттерну `ComplaintsPage`: страница компонует презентационные подкомпоненты, данные через TanStack Query, тосты через `sonner`, ошибки через `extractApiError`.

**Tech Stack:** Backend — Ktor + Exposed ORM + H2/Postgres + JUnit5 + Testcontainers. Frontend — React 19 + TypeScript + Vite + TanStack Query v5 + shadcn/ui (Tabs, Dialog, Badge) + Vitest + Testing Library.

**Спек:** `docs/superpowers/specs/2026-05-28-day17c-settings-team-audit-design.md`

**Рабочая директория:** все пути ниже — относительно корня репо `cleancity-kmp/` (если не указано иное). Backend-команды запускать из корня; frontend-команды — из `web-admin/`. Ветка: `day17c-settings-team-audit` (создать первым шагом, если ещё не).

---

## Структура файлов

### Backend (Kotlin)

| Файл | Действие | Ответственность |
|---|---|---|
| `shared/src/commonMain/kotlin/com/example/cleancity/shared/models/UserRole.kt` | modify | удалить `INSPECTOR` |
| `shared/src/commonMain/kotlin/com/example/cleancity/shared/requests/auth/AuthRequests.kt` | modify | поправить комментарий `AdminInviteRequest.role` |
| `shared/src/commonMain/kotlin/com/example/cleancity/shared/responses/admin/TeamStatus.kt` | create | enum `ACTIVE/FROZEN/PENDING` |
| `shared/src/commonMain/kotlin/com/example/cleancity/shared/responses/admin/TeamMemberDto.kt` | create | DTO сотрудника + `TeamMembersResponse` |
| `shared/src/commonMain/kotlin/com/example/cleancity/shared/responses/admin/AuditEntryDto.kt` | create | DTO события + `AuditLogResponse` |
| `backend/.../database/tables/AuditLog.kt` | modify | +3 значения `AuditAction` |
| `backend/.../auth/ErrorCodes.kt` | modify | +4 кода |
| `backend/.../complaints/ComplaintService.kt:92` | modify | убрать `INSPECTOR` из `ADMIN_ROLES` |
| `backend/.../announcements/AnnouncementService.kt:21` | modify | то же |
| `backend/.../analytics/AnalyticsRoutes.kt:17` | modify | то же |
| `backend/.../auth/UserRepository.kt` | modify | `findAdmins()` без `INSPECTOR`, + новый `listByTeamStatus` |
| `backend/.../auth/JwtConfig.kt:106-118` | modify | TTL-ветки без `INSPECTOR` |
| `backend/.../auth/TokenRepository.kt` | modify | + `invalidateInviteForUser` |
| `backend/.../auth/AuditLogRepository.kt` | create | новый read-only репо: `findRecent` |
| `backend/.../auth/AuthService.kt` | modify | + 5 методов (listTeamMembers/freezeUser/unfreezeUser/revokeInvitation/recentAuditEvents) + новые исключения |
| `backend/.../auth/AuthRoutes.kt` | modify | + 5 ручек, ужесточить `/admin/invite` |
| `backend/src/test/kotlin/.../auth/UserRoleEnumTest.kt` | create | фиксация удаления `INSPECTOR` |
| `backend/src/test/kotlin/.../auth/AuthAdminTeamRoutesTest.kt` | create | 14 интеграционных тестов |

### Frontend (TypeScript / React)

| Файл | Действие | Ответственность |
|---|---|---|
| `web-admin/src/api/types.ts` | modify | `UserRole` без `INSPECTOR`, + `TeamStatus`, `TeamMemberDto`, `AuditEntryDto` |
| `web-admin/src/api/admin.ts` | create | 6 функций над axios |
| `web-admin/src/pages/settings/actionLabels.ts` | create | словарь `AuditAction → русская строка` |
| `web-admin/src/pages/settings/ConfirmActionDialog.tsx` | create | переиспользуемый confirm |
| `web-admin/src/pages/settings/InviteMemberDialog.tsx` | create | форма инвайта (только ADMIN) |
| `web-admin/src/pages/settings/TeamTable.tsx` | create | таблица под текущий таб |
| `web-admin/src/pages/settings/TeamTable.test.tsx` | create | 2 кейса |
| `web-admin/src/pages/settings/TeamSection.tsx` | create | табы + кнопка Пригласить + invalidate |
| `web-admin/src/pages/settings/AuditTable.tsx` | create | плоская таблица 50 строк |
| `web-admin/src/pages/settings/AuditLogSection.tsx` | create | заголовок + таблица |
| `web-admin/src/pages/SettingsPage.tsx` | create | компонует обе секции |
| `web-admin/src/pages/SettingsPage.test.tsx` | create | 8 кейсов |
| `web-admin/src/App.tsx:29` | modify | заменить `<SectionPlaceholder>` на `<SettingsPage />` |
| `docs/PLAN.md` | modify | проставить `[x]` в пунктах Day 17C |

---

## Справка по существующему бэкенду (НЕ меняем структуру, только используем)

| Класс/файл | Что используем |
|---|---|
| `TokenRepository.revokeAllUserRefreshTokens(userId)` | существует (строка 103), для I5 |
| `TokenRepository.consumeEmailToken(tokenId)` | существует (63), переиспользует внутри `invalidateInviteForUser` |
| `EmailTokenPurpose.ADMIN_INVITE` | существует |
| `AuthService.inviteAdmin(actorId, email, role, ip, ua)` | существует (288), переиспользуем как есть |
| `DbAuditLogger.log(...)` | существует, переиспользуем |
| `UserRepository.findAdmins()` | существует (42), правим (убираем INSPECTOR) |
| `users` таблица | колонка `is_active`, `email_verified` — определяют статус |
| `audit_log.action varchar(50)` | application-level enum — миграция БД НЕ нужна |

---

## Task 1: Создать ветку + удалить роль INSPECTOR

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/cleancity/shared/models/UserRole.kt`
- Modify: `shared/src/commonMain/kotlin/com/example/cleancity/shared/requests/auth/AuthRequests.kt`
- Modify: `backend/src/main/kotlin/com/example/cleancity/complaints/ComplaintService.kt`
- Modify: `backend/src/main/kotlin/com/example/cleancity/announcements/AnnouncementService.kt`
- Modify: `backend/src/main/kotlin/com/example/cleancity/analytics/AnalyticsRoutes.kt`
- Modify: `backend/src/main/kotlin/com/example/cleancity/auth/UserRepository.kt`
- Modify: `backend/src/main/kotlin/com/example/cleancity/auth/JwtConfig.kt`
- Modify: `backend/src/main/kotlin/com/example/cleancity/auth/AuthRoutes.kt`
- Modify: `web-admin/src/api/types.ts`
- Create: `backend/src/test/kotlin/com/example/cleancity/auth/UserRoleEnumTest.kt`

- [ ] **Step 1: Создать ветку**

```bash
git checkout -b day17c-settings-team-audit
```

- [ ] **Step 2: Написать тест, фиксирующий удаление INSPECTOR**

Создать `backend/src/test/kotlin/com/example/cleancity/auth/UserRoleEnumTest.kt`:

```kotlin
package com.example.cleancity.auth

import com.example.cleancity.shared.models.UserRole
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals

class UserRoleEnumTest {

    @Test
    fun `INSPECTOR value no longer exists`() {
        assertFailsWith<IllegalArgumentException> {
            UserRole.valueOf("INSPECTOR")
        }
    }

    @Test
    fun `only three roles remain`() {
        val names = UserRole.values().map { it.name }.toSet()
        assertEquals(setOf("RESIDENT", "OPERATOR", "ADMIN"), names)
    }
}
```

- [ ] **Step 3: Запустить тест — должен упасть на компиляции (INSPECTOR ещё в enum)**

```bash
./gradlew :backend:test --tests "com.example.cleancity.auth.UserRoleEnumTest" -q
```
Expected: FAIL (`only three roles remain` — лишний INSPECTOR в множестве).

- [ ] **Step 4: Удалить INSPECTOR из enum**

`shared/src/commonMain/kotlin/com/example/cleancity/shared/models/UserRole.kt` — было:

```kotlin
/**
 * ...
 * В MVP OPERATOR/INSPECTOR имеют те же права что ADMIN
 */
@Serializable
enum class UserRole {
    RESIDENT,
    OPERATOR,
    INSPECTOR,
    ADMIN
}
```

Стало:

```kotlin
/**
 * Роли пользователей CleanCity.
 *
 * - RESIDENT — житель: создаёт жалобы, голосует.
 * - OPERATOR — сотрудник: обрабатывает жалобы, публикует объявления, видит команду в read-only.
 * - ADMIN — администратор: всё, что OPERATOR, плюс управление командой.
 */
@Serializable
enum class UserRole {
    RESIDENT,
    OPERATOR,
    ADMIN
}
```

- [ ] **Step 5: Поправить комментарий в AdminInviteRequest**

`shared/src/commonMain/kotlin/com/example/cleancity/shared/requests/auth/AuthRequests.kt:59` — найти строку `val role: String   // ADMIN | OPERATOR | INSPECTOR` и заменить на:

```kotlin
val role: String   // "ADMIN" | "OPERATOR"
```

- [ ] **Step 6: Убрать INSPECTOR из ADMIN_ROLES в трёх файлах**

`backend/.../complaints/ComplaintService.kt:92` — найти строку:

```kotlin
private val ADMIN_ROLES = setOf(UserRole.ADMIN, UserRole.OPERATOR, UserRole.INSPECTOR)
```

и в `AnnouncementService.kt:21`, `AnalyticsRoutes.kt:17` — заменить на:

```kotlin
private val ADMIN_ROLES = setOf(UserRole.ADMIN, UserRole.OPERATOR)
```

- [ ] **Step 7: Убрать INSPECTOR из UserRepository.findAdmins**

`backend/.../auth/UserRepository.kt:42-48` — было:

```kotlin
/**
 * Возвращает всех пользователей с админскими ролями
 * (ADMIN / OPERATOR / INSPECTOR), независимо от is_active.
 */
fun findAdmins(): List<UserRow> = transaction {
    Users.selectAll().where {
        (Users.role eq UserRole.ADMIN.name) or
            (Users.role eq UserRole.OPERATOR.name) or
            (Users.role eq UserRole.INSPECTOR.name)
    }.map { it.toUserRow() }
}
```

Стало:

```kotlin
/**
 * Возвращает всех пользователей с админскими ролями (ADMIN / OPERATOR),
 * независимо от is_active.
 */
fun findAdmins(): List<UserRow> = transaction {
    Users.selectAll().where {
        (Users.role eq UserRole.ADMIN.name) or
            (Users.role eq UserRole.OPERATOR.name)
    }.map { it.toUserRow() }
}
```

- [ ] **Step 8: Убрать INSPECTOR из JwtConfig TTL-веток**

`backend/.../auth/JwtConfig.kt:106-118` — найти обе `when`-ветки и заменить:

```kotlin
UserRole.OPERATOR, UserRole.INSPECTOR, UserRole.ADMIN -> ADMIN_15M
```

на

```kotlin
UserRole.OPERATOR, UserRole.ADMIN -> ADMIN_15M
```

(аналогично для `ADMIN_8H` ветки строкой 118)

- [ ] **Step 9: Ужесточить guard и валидацию роли в `/admin/invite`**

`backend/.../auth/AuthRoutes.kt:234-253` — изменить ТОЛЬКО две проверки (try/catch с `inviteAdmin` ниже **не трогать**):

Было:

```kotlin
if (role !in setOf(UserRole.ADMIN, UserRole.OPERATOR, UserRole.INSPECTOR)) {
    throw ForbiddenException("Admins only")
}
val req = call.receive<AdminInviteRequest>()
val targetRole = runCatching { UserRole.valueOf(req.role) }.getOrNull()
if (targetRole == null || targetRole == UserRole.RESIDENT) {
    throw BadRequestException("Invalid invite role", ErrorCodes.VALIDATION_BAD_FIELD)
}
```

Стало:

```kotlin
if (role != UserRole.ADMIN) {
    throw ForbiddenException("Только администраторы могут приглашать сотрудников")
}
val req = call.receive<AdminInviteRequest>()
val targetRole = runCatching { UserRole.valueOf(req.role) }.getOrNull()
if (targetRole == null || targetRole !in setOf(UserRole.ADMIN, UserRole.OPERATOR)) {
    throw BadRequestException("Роль должна быть ADMIN или OPERATOR", ErrorCodes.VALIDATION_BAD_FIELD)
}
```

Остальной код блока (`try { service.inviteAdmin(...) } catch (...)`) **оставить без изменений**.

- [ ] **Step 10: Поправить frontend types.ts**

`web-admin/src/api/types.ts:1` — было:

```ts
export type UserRole = 'RESIDENT' | 'OPERATOR' | 'INSPECTOR' | 'ADMIN'
```

Стало:

```ts
export type UserRole = 'RESIDENT' | 'OPERATOR' | 'ADMIN'
```

- [ ] **Step 11: Прогнать оба test-suite — все должны быть зелёные**

```bash
./gradlew :backend:test -q
cd web-admin && npm test -- --run
cd ..
```
Expected: оба зелёные, никаких регрессий.

- [ ] **Step 12: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
refactor(roles): удалить INSPECTOR из UserRole

INSPECTOR никогда не использовался в seed и миграциях, остался от
ранних версий SPEC. Чистим 9 точек кода, фронт-тип и фиксируем
удаление тестом.

В рамках 17C сужаем guard /auth/admin/invite до role==ADMIN.
EOF
)"
```

---

## Task 2: Добавить новые AuditAction и ErrorCodes

**Files:**
- Modify: `backend/src/main/kotlin/com/example/cleancity/database/tables/AuditLog.kt`
- Modify: `backend/src/main/kotlin/com/example/cleancity/auth/ErrorCodes.kt`

- [ ] **Step 1: Добавить 3 значения в AuditAction**

`backend/.../database/tables/AuditLog.kt` — в конец enum (перед последней `}`):

```kotlin
enum class AuditAction {
    LOGIN_SUCCESS,
    LOGIN_FAIL,
    LOGIN_LOCKED,
    TWOFA_SETUP_STARTED,
    TWOFA_ENABLED,
    TWOFA_FAIL,
    TWOFA_LOGIN_SUCCESS,
    PASSWORD_RESET,
    REFRESH_REVOKED,
    SESSION_REVOKED,
    ADMIN_INVITE_SENT,
    ADMIN_INVITE_ACCEPTED,
    COMPLAINT_STATUS_CHANGE,
    ACCOUNT_DELETED,
    ADMIN_USER_FROZEN,
    ADMIN_USER_UNFROZEN,
    ADMIN_INVITE_REVOKED
}
```

- [ ] **Step 2: Добавить ErrorCodes**

Открыть `backend/.../auth/ErrorCodes.kt` и добавить в конец object-а (перед `}`):

```kotlin
const val ADMIN_CANNOT_FREEZE_SELF = "ADMIN_CANNOT_FREEZE_SELF"
const val LAST_ACTIVE_ADMIN = "LAST_ACTIVE_ADMIN"
const val INVITE_NOT_ACCEPTED = "INVITE_NOT_ACCEPTED"
const val NOT_A_PENDING_INVITE = "NOT_A_PENDING_INVITE"
```

- [ ] **Step 3: Проверить компиляцию**

```bash
./gradlew :backend:compileKotlin -q
```
Expected: SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/kotlin/com/example/cleancity/database/tables/AuditLog.kt \
        backend/src/main/kotlin/com/example/cleancity/auth/ErrorCodes.kt
git commit -m "feat(audit): добавить AuditAction и ErrorCodes для Day 17C"
```

---

## Task 3: Shared DTO — TeamStatus, TeamMemberDto, AuditEntryDto

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/cleancity/shared/responses/admin/TeamStatus.kt`
- Create: `shared/src/commonMain/kotlin/com/example/cleancity/shared/responses/admin/TeamMemberDto.kt`
- Create: `shared/src/commonMain/kotlin/com/example/cleancity/shared/responses/admin/AuditEntryDto.kt`

- [ ] **Step 1: Создать TeamStatus**

`shared/src/commonMain/kotlin/com/example/cleancity/shared/responses/admin/TeamStatus.kt`:

```kotlin
package com.example.cleancity.shared.responses.admin

import kotlinx.serialization.Serializable

@Serializable
enum class TeamStatus {
    ACTIVE,   // is_active=true && email_verified=true
    FROZEN,   // is_active=false && email_verified=true
    PENDING   // is_active=false && email_verified=false (есть валидный ADMIN_INVITE)
}
```

- [ ] **Step 2: Создать TeamMemberDto + Response**

`shared/src/commonMain/kotlin/com/example/cleancity/shared/responses/admin/TeamMemberDto.kt`:

```kotlin
package com.example.cleancity.shared.responses.admin

import kotlinx.serialization.Serializable

@Serializable
data class TeamMemberDto(
    val id: Long,
    val email: String,
    val fullName: String?,
    val role: String,            // "ADMIN" | "OPERATOR"
    val district: String?,
    val status: TeamStatus,
    val createdAt: String,       // ISO-8601 с offset
    val lastLoginAt: String?,    // null для pending
    val invitedAt: String?       // createdAt для pending, иначе null
)

@Serializable
data class TeamMembersResponse(val items: List<TeamMemberDto>)
```

- [ ] **Step 3: Создать AuditEntryDto + Response**

`shared/src/commonMain/kotlin/com/example/cleancity/shared/responses/admin/AuditEntryDto.kt`:

```kotlin
package com.example.cleancity.shared.responses.admin

import kotlinx.serialization.Serializable

@Serializable
data class AuditEntryDto(
    val id: Long,
    val timestamp: String,       // ISO-8601 с offset (audit_log.created_at)
    val actorEmail: String?,     // null для системных действий
    val action: String,          // AuditAction.name
    val targetType: String?,     // например "user"
    val targetId: String?,
    val ip: String?,
    val details: String?         // audit_log.metadata (свободный текст)
)

@Serializable
data class AuditLogResponse(val items: List<AuditEntryDto>)
```

- [ ] **Step 4: Проверить компиляцию shared**

```bash
./gradlew :shared:build -q
```
Expected: SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/cleancity/shared/responses/admin/
git commit -m "feat(shared): добавить TeamMemberDto, AuditEntryDto, TeamStatus"
```

---

## Task 4: UserRepository.listByTeamStatus

**Files:**
- Modify: `backend/src/main/kotlin/com/example/cleancity/auth/UserRepository.kt`
- Modify: `backend/src/test/kotlin/com/example/cleancity/auth/UserRepositoryTest.kt` (или создать новый тест-файл)

- [ ] **Step 1: Написать падающие тесты**

Открыть `backend/src/test/kotlin/com/example/cleancity/auth/UserRepositoryTest.kt` и добавить в конец класса (импорты — добавить `TeamStatus`):

```kotlin
@Test
fun `listByTeamStatus ACTIVE returns only active admins and operators`() {
    initDb()
    seedUser("admin@test.local", UserRole.ADMIN, isActive = true, emailVerified = true)
    seedUser("op@test.local", UserRole.OPERATOR, isActive = true, emailVerified = true)
    seedUser("frozen@test.local", UserRole.ADMIN, isActive = false, emailVerified = true)
    seedUser("resident@test.local", UserRole.RESIDENT, isActive = true, emailVerified = true)

    val rows = UserRepository().listByTeamStatus(TeamStatus.ACTIVE)
    val emails = rows.map { it.email }.toSet()
    assertEquals(setOf("admin@test.local", "op@test.local"), emails)
}

@Test
fun `listByTeamStatus FROZEN returns only frozen non-residents`() {
    initDb()
    seedUser("frozen@test.local", UserRole.ADMIN, isActive = false, emailVerified = true)
    seedUser("active@test.local", UserRole.OPERATOR, isActive = true, emailVerified = true)
    seedUser("pending@test.local", UserRole.OPERATOR, isActive = false, emailVerified = false)

    val rows = UserRepository().listByTeamStatus(TeamStatus.FROZEN)
    assertEquals(listOf("frozen@test.local"), rows.map { it.email })
}

@Test
fun `listByTeamStatus PENDING returns only invited not-yet-accepted`() {
    initDb()
    seedUser("pending@test.local", UserRole.ADMIN, isActive = false, emailVerified = false)
    seedUser("active@test.local", UserRole.ADMIN, isActive = true, emailVerified = true)
    seedUser("frozen@test.local", UserRole.ADMIN, isActive = false, emailVerified = true)

    val rows = UserRepository().listByTeamStatus(TeamStatus.PENDING)
    assertEquals(listOf("pending@test.local"), rows.map { it.email })
}

@Test
fun `listByTeamStatus null returns all three statuses, omits residents`() {
    initDb()
    seedUser("a@test.local", UserRole.ADMIN, isActive = true, emailVerified = true)
    seedUser("b@test.local", UserRole.OPERATOR, isActive = false, emailVerified = true)
    seedUser("c@test.local", UserRole.OPERATOR, isActive = false, emailVerified = false)
    seedUser("r@test.local", UserRole.RESIDENT, isActive = true, emailVerified = true)

    val rows = UserRepository().listByTeamStatus(null)
    assertEquals(3, rows.size)
    assertTrue(rows.none { it.role == UserRole.RESIDENT })
}
```

Если `seedUser` в существующем тесте не принимает `isActive`/`emailVerified` — добавить эти параметры с дефолтами `true`/`true`. Импорты в начале файла дополнить:

```kotlin
import com.example.cleancity.shared.responses.admin.TeamStatus
import kotlin.test.assertTrue
```

- [ ] **Step 2: Запустить тесты — упадут на отсутствии метода**

```bash
./gradlew :backend:test --tests "com.example.cleancity.auth.UserRepositoryTest" -q
```
Expected: FAIL (`listByTeamStatus` не найден).

- [ ] **Step 3: Добавить метод в UserRepository**

`backend/.../auth/UserRepository.kt` — добавить в конец класса (перед методом-конвертером `toUserRow`):

```kotlin
/**
 * Список сотрудников (ADMIN/OPERATOR) по статусу команды.
 * status = null → все три статуса (без RESIDENT).
 */
fun listByTeamStatus(status: com.example.cleancity.shared.responses.admin.TeamStatus?): List<UserRow> = transaction {
    val staffRoles = (Users.role eq UserRole.ADMIN.name) or (Users.role eq UserRole.OPERATOR.name)
    val statusFilter = when (status) {
        com.example.cleancity.shared.responses.admin.TeamStatus.ACTIVE ->
            (Users.isActive eq true) and (Users.emailVerified eq true)
        com.example.cleancity.shared.responses.admin.TeamStatus.FROZEN ->
            (Users.isActive eq false) and (Users.emailVerified eq true)
        com.example.cleancity.shared.responses.admin.TeamStatus.PENDING ->
            (Users.isActive eq false) and (Users.emailVerified eq false)
        null -> null
    }
    val combined = if (statusFilter == null) staffRoles else staffRoles and statusFilter
    Users.selectAll().where { combined }
        .orderBy(Users.createdAt to org.jetbrains.exposed.sql.SortOrder.DESC)
        .map { it.toUserRow() }
}
```

- [ ] **Step 4: Запустить тесты — должны пройти**

```bash
./gradlew :backend:test --tests "com.example.cleancity.auth.UserRepositoryTest" -q
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/example/cleancity/auth/UserRepository.kt \
        backend/src/test/kotlin/com/example/cleancity/auth/UserRepositoryTest.kt
git commit -m "feat(auth): UserRepository.listByTeamStatus для админ-панели команды"
```

---

## Task 5: TokenRepository.invalidateInviteForUser

**Files:**
- Modify: `backend/src/main/kotlin/com/example/cleancity/auth/TokenRepository.kt`
- Create: `backend/src/test/kotlin/com/example/cleancity/auth/TokenRepositoryInvalidateInviteTest.kt`

- [ ] **Step 1: Написать падающий тест**

Создать `backend/src/test/kotlin/com/example/cleancity/auth/TokenRepositoryInvalidateInviteTest.kt`:

```kotlin
package com.example.cleancity.auth

import com.example.cleancity.database.tables.EmailTokenPurpose
import com.example.cleancity.database.tables.EmailTokens
import com.example.cleancity.database.tables.Users
import com.example.cleancity.shared.models.UserRole
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TokenRepositoryInvalidateInviteTest {

    private fun initDb() {
        Database.connect(
            "jdbc:h2:mem:tok-inv-${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.drop(EmailTokens, Users)
            SchemaUtils.create(Users, EmailTokens)
        }
    }

    private fun seedUser(): Long = transaction {
        Users.insert {
            it[Users.email] = "u@test.local"
            it[Users.passwordHash] = "x"
            it[Users.role] = UserRole.OPERATOR.name
            it[Users.isActive] = false
            it[Users.emailVerified] = false
            it[Users.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
            it[Users.passwordChangedAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }[Users.id]
    }

    @Test
    fun `invalidateInviteForUser consumes pending ADMIN_INVITE tokens`() {
        initDb()
        val userId = seedUser()
        val repo = TokenRepository()
        repo.createEmailToken(userId, EmailTokenPurpose.ADMIN_INVITE, 3600)
        repo.createEmailToken(userId, EmailTokenPurpose.ADMIN_INVITE, 3600)

        val count = repo.invalidateInviteForUser(userId)
        assertEquals(2, count)

        transaction {
            val rows = EmailTokens.selectAll().where {
                (EmailTokens.userId eq userId) and
                    (EmailTokens.purpose eq EmailTokenPurpose.ADMIN_INVITE.name)
            }.toList()
            assertEquals(2, rows.size)
            rows.forEach { assertNotNull(it[EmailTokens.consumedAt]) }
        }
    }

    @Test
    fun `invalidateInviteForUser does not touch other purposes`() {
        initDb()
        val userId = seedUser()
        val repo = TokenRepository()
        repo.createEmailToken(userId, EmailTokenPurpose.RESET_PASSWORD, 3600)

        val count = repo.invalidateInviteForUser(userId)
        assertEquals(0, count)

        transaction {
            val resetTokens = EmailTokens.selectAll().where {
                EmailTokens.purpose eq EmailTokenPurpose.RESET_PASSWORD.name
            }.toList()
            assertEquals(1, resetTokens.size)
            // не помечен consumed
            assertEquals(null, resetTokens[0][EmailTokens.consumedAt])
        }
    }
}
```

Импорт `import org.jetbrains.exposed.sql.and` добавить если нужен.

- [ ] **Step 2: Запустить тест — должен упасть на отсутствии метода**

```bash
./gradlew :backend:test --tests "com.example.cleancity.auth.TokenRepositoryInvalidateInviteTest" -q
```
Expected: FAIL.

- [ ] **Step 3: Реализовать invalidateInviteForUser**

`backend/.../auth/TokenRepository.kt` — добавить метод после `consumeEmailToken` (около строки 67):

```kotlin
/**
 * Помечает consumed_at = NOW() для всех непогашенных ADMIN_INVITE-токенов
 * указанного пользователя. Возвращает число затронутых строк.
 */
fun invalidateInviteForUser(userId: Long): Int = transaction {
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    EmailTokens.update({
        (EmailTokens.userId eq userId) and
            (EmailTokens.purpose eq EmailTokenPurpose.ADMIN_INVITE.name) and
            EmailTokens.consumedAt.isNull()
    }) {
        it[EmailTokens.consumedAt] = now
    }
}
```

Если колонка называется иначе (проверить в `database/tables/EmailTokens.kt` — может быть `consumedAt` или `revokedAt`) — использовать актуальное имя. **Перед написанием — прочитать `EmailTokens.kt` Read-ом и подтвердить имя колонки.**

- [ ] **Step 4: Запустить тест — должен пройти**

```bash
./gradlew :backend:test --tests "com.example.cleancity.auth.TokenRepositoryInvalidateInviteTest" -q
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/example/cleancity/auth/TokenRepository.kt \
        backend/src/test/kotlin/com/example/cleancity/auth/TokenRepositoryInvalidateInviteTest.kt
git commit -m "feat(auth): TokenRepository.invalidateInviteForUser для отзыва pending-инвайтов"
```

---

## Task 6: AuditLogRepository (новый read-only класс)

**Files:**
- Create: `backend/src/main/kotlin/com/example/cleancity/auth/AuditLogRepository.kt`
- Create: `backend/src/test/kotlin/com/example/cleancity/auth/AuditLogRepositoryTest.kt`

- [ ] **Step 1: Написать падающий тест**

Создать `backend/src/test/kotlin/com/example/cleancity/auth/AuditLogRepositoryTest.kt`:

```kotlin
package com.example.cleancity.auth

import com.example.cleancity.database.tables.AuditAction
import com.example.cleancity.database.tables.AuditLog
import com.example.cleancity.database.tables.Users
import com.example.cleancity.shared.models.UserRole
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuditLogRepositoryTest {

    private fun initDb() {
        Database.connect(
            "jdbc:h2:mem:audit-${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.drop(AuditLog, Users)
            SchemaUtils.create(Users, AuditLog)
        }
    }

    private fun seedUser(email: String): Long = transaction {
        Users.insert {
            it[Users.email] = email
            it[Users.passwordHash] = "x"
            it[Users.role] = UserRole.ADMIN.name
            it[Users.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
            it[Users.passwordChangedAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }[Users.id]
    }

    private fun seedAudit(actorId: Long?, action: AuditAction, createdAt: OffsetDateTime) = transaction {
        AuditLog.insert {
            it[AuditLog.actorUserId] = actorId
            it[AuditLog.action] = action.name
            it[AuditLog.targetType] = "user"
            it[AuditLog.targetId] = (actorId ?: 0L).toString()
            it[AuditLog.createdAt] = createdAt
        }
    }

    @Test
    fun `findRecent returns entries DESC by createdAt with actorEmail joined`() {
        initDb()
        val adminId = seedUser("admin@test.local")
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        seedAudit(adminId, AuditAction.LOGIN_SUCCESS, now.minusMinutes(10))
        seedAudit(adminId, AuditAction.ADMIN_USER_FROZEN, now.minusMinutes(1))
        seedAudit(null, AuditAction.PASSWORD_RESET, now.minusMinutes(5))

        val rows = AuditLogRepository().findRecent(50)
        assertEquals(3, rows.size)
        // DESC: most recent first
        assertEquals(AuditAction.ADMIN_USER_FROZEN.name, rows[0].action)
        assertEquals("admin@test.local", rows[0].actorEmail)
        assertEquals(AuditAction.PASSWORD_RESET.name, rows[1].action)
        assertNull(rows[1].actorEmail)  // системное действие
    }

    @Test
    fun `findRecent respects limit`() {
        initDb()
        val adminId = seedUser("admin@test.local")
        repeat(60) { i ->
            seedAudit(adminId, AuditAction.LOGIN_SUCCESS, OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(i.toLong()))
        }

        val rows = AuditLogRepository().findRecent(50)
        assertEquals(50, rows.size)
    }
}
```

- [ ] **Step 2: Запустить — упадёт на отсутствии класса**

```bash
./gradlew :backend:test --tests "com.example.cleancity.auth.AuditLogRepositoryTest" -q
```
Expected: FAIL.

- [ ] **Step 3: Создать AuditLogRepository**

`backend/src/main/kotlin/com/example/cleancity/auth/AuditLogRepository.kt`:

```kotlin
package com.example.cleancity.auth

import com.example.cleancity.database.tables.AuditLog
import com.example.cleancity.database.tables.Users
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime

data class AuditEntryRow(
    val id: Long,
    val createdAt: OffsetDateTime,
    val actorEmail: String?,
    val action: String,
    val targetType: String?,
    val targetId: String?,
    val ip: String?,
    val details: String?
)

class AuditLogRepository {

    fun findRecent(limit: Int): List<AuditEntryRow> = transaction {
        AuditLog.join(Users, JoinType.LEFT, additionalConstraint = { AuditLog.actorUserId eq Users.id })
            .selectAll()
            .orderBy(AuditLog.createdAt to SortOrder.DESC)
            .limit(limit)
            .map { row ->
                AuditEntryRow(
                    id = row[AuditLog.id],
                    createdAt = row[AuditLog.createdAt],
                    actorEmail = row.getOrNull(Users.email),
                    action = row[AuditLog.action],
                    targetType = row[AuditLog.targetType],
                    targetId = row[AuditLog.targetId],
                    ip = row[AuditLog.ip],
                    details = row[AuditLog.metadata]
                )
            }
    }
}
```

Если `ResultRow.getOrNull` не работает с конкретной версией Exposed — заменить на `runCatching { row[Users.email] }.getOrNull()`. Перед коммитом проверить компиляцию.

- [ ] **Step 4: Запустить тест — должен пройти**

```bash
./gradlew :backend:test --tests "com.example.cleancity.auth.AuditLogRepositoryTest" -q
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/example/cleancity/auth/AuditLogRepository.kt \
        backend/src/test/kotlin/com/example/cleancity/auth/AuditLogRepositoryTest.kt
git commit -m "feat(audit): AuditLogRepository.findRecent с LEFT JOIN на users.email"
```

---

## Task 7: AuthService — listTeamMembers + recentAuditEvents

**Files:**
- Modify: `backend/src/main/kotlin/com/example/cleancity/auth/AuthService.kt`

Эти два метода — чистые маппинги без бизнес-логики, тестируются вместе с роутами. Поэтому здесь только реализация; покрытие — через `AuthAdminTeamRoutesTest` (Task 11).

- [ ] **Step 1: Расширить конструктор AuthService и добавить методы**

`backend/.../auth/AuthService.kt` — в конструктор добавить параметр (после `audit: AuditLogger = NoopAuditLogger`):

```kotlin
private val auditLog: AuditLogRepository = AuditLogRepository(),
```

В конец класса (перед закрывающей `}`) добавить:

```kotlin
suspend fun listTeamMembers(status: TeamStatus?): List<TeamMemberDto> {
    val rows = users.listByTeamStatus(status)
    return rows.map { it.toTeamMemberDto() }
}

suspend fun recentAuditEvents(limit: Int = 50): List<AuditEntryDto> {
    val rows = auditLog.findRecent(limit.coerceIn(1, 50))
    return rows.map {
        AuditEntryDto(
            id = it.id,
            timestamp = it.createdAt.toString(),
            actorEmail = it.actorEmail,
            action = it.action,
            targetType = it.targetType,
            targetId = it.targetId,
            ip = it.ip,
            details = it.details
        )
    }
}

private fun UserRow.toTeamMemberDto(): TeamMemberDto {
    val status = when {
        isActive && emailVerified -> TeamStatus.ACTIVE
        !isActive && emailVerified -> TeamStatus.FROZEN
        else -> TeamStatus.PENDING
    }
    return TeamMemberDto(
        id = id,
        email = email,
        fullName = fullName,
        role = role.name,
        district = district,
        status = status,
        createdAt = createdAt.toString(),
        lastLoginAt = lastLoginAt?.toString(),
        invitedAt = if (status == TeamStatus.PENDING) createdAt.toString() else null
    )
}
```

Импорты добавить:

```kotlin
import com.example.cleancity.shared.responses.admin.AuditEntryDto
import com.example.cleancity.shared.responses.admin.TeamMemberDto
import com.example.cleancity.shared.responses.admin.TeamStatus
```

Если поля `district`/`lastLoginAt` отсутствуют в `UserRow` — добавить их (Read `UserRepository.kt`, проверить `data class UserRow`, дополнить и расширить mapping в `toUserRow()`). Это часть Task 7, если не сделано — fix-up до коммита.

- [ ] **Step 2: Поправить все места конструирования AuthService**

Например `Application.kt:134-148`, `AuthRoutesTest`, `AccountDeletionRoutesTest`. Добавить параметр `auditLog = AuditLogRepository()` (либо передать `null` через default — но default уже есть в конструкторе, тогда менять не надо).

**Проверить**: если default-параметр работает — этот шаг не нужен. Прогнать компиляцию:

```bash
./gradlew :backend:compileKotlin -q
```

- [ ] **Step 3: Компиляция backend зелёная**

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/kotlin/com/example/cleancity/auth/AuthService.kt
git add -u backend/src/main/kotlin/com/example/cleancity/auth/UserRepository.kt  # если расширяли UserRow
git commit -m "feat(auth): AuthService.listTeamMembers и recentAuditEvents"
```

---

## Task 8: AuthService.freezeUser (I1, I2, I5)

**Files:**
- Modify: `backend/src/main/kotlin/com/example/cleancity/auth/AuthService.kt`

- [ ] **Step 1: Добавить исключения**

Внутри `AuthService.kt` (либо в существующем `AuthExceptions.kt`, проверить — если есть, то туда):

```kotlin
class SelfFreezeException : RuntimeException("Нельзя заморозить собственный аккаунт")
class LastActiveAdminException : RuntimeException("Это последний активный администратор")
class InviteNotAcceptedException : RuntimeException("Сотрудник ещё не принял приглашение")
class NotAPendingInviteException : RuntimeException("Это не pending-приглашение")
```

- [ ] **Step 2: Добавить freezeUser**

В `AuthService.kt`:

```kotlin
/**
 * Замораживает сотрудника:
 *  - I1: нельзя замораживать самого себя
 *  - I2: если targetRole == ADMIN, должен остаться ≥1 активный ADMIN после операции
 *  - I5: все refresh-токены target ревокаются
 */
suspend fun freezeUser(actorId: Long, targetId: Long, ip: String?, ua: String?) {
    if (actorId == targetId) throw SelfFreezeException()

    val target = users.findById(targetId)
        ?: throw IllegalArgumentException("User not found")
    if (target.role == UserRole.RESIDENT) {
        throw IllegalArgumentException("Cannot freeze a resident via team API")
    }

    if (target.role == UserRole.ADMIN) {
        val activeAdminsAfter = users.listByTeamStatus(TeamStatus.ACTIVE)
            .count { it.role == UserRole.ADMIN && it.id != targetId }
        if (activeAdminsAfter < 1) throw LastActiveAdminException()
    }

    users.setActive(targetId, false)
    tokens.revokeAllUserRefreshTokens(targetId)
    audit.log(AuditAction.ADMIN_USER_FROZEN, actorId, "user", targetId.toString(), ip, ua)
}
```

Если `UserRepository.setActive(id, value)` не существует — добавить:

```kotlin
fun setActive(userId: Long, value: Boolean) = transaction {
    Users.update({ Users.id eq userId }) { it[Users.isActive] = value }
}
```

- [ ] **Step 3: Компиляция**

```bash
./gradlew :backend:compileKotlin -q
```
Expected: SUCCESS.

- [ ] **Step 4: Commit (без тестов — тесты в Task 11 на route-уровне)**

```bash
git add backend/src/main/kotlin/com/example/cleancity/auth/AuthService.kt
git add -u backend/src/main/kotlin/com/example/cleancity/auth/UserRepository.kt
git commit -m "feat(auth): AuthService.freezeUser с инвариантами I1, I2, I5"
```

---

## Task 9: AuthService.unfreezeUser (I3)

**Files:**
- Modify: `backend/src/main/kotlin/com/example/cleancity/auth/AuthService.kt`

- [ ] **Step 1: Добавить unfreezeUser**

```kotlin
/**
 * Размораживает сотрудника. I3: target должен быть с email_verified=true (frozen, не pending).
 */
suspend fun unfreezeUser(actorId: Long, targetId: Long, ip: String?, ua: String?) {
    val target = users.findById(targetId)
        ?: throw IllegalArgumentException("User not found")
    if (target.role == UserRole.RESIDENT) {
        throw IllegalArgumentException("Cannot unfreeze a resident via team API")
    }
    if (!target.emailVerified) throw InviteNotAcceptedException()

    users.setActive(targetId, true)
    audit.log(AuditAction.ADMIN_USER_UNFROZEN, actorId, "user", targetId.toString(), ip, ua)
}
```

- [ ] **Step 2: Компиляция + commit**

```bash
./gradlew :backend:compileKotlin -q
git add backend/src/main/kotlin/com/example/cleancity/auth/AuthService.kt
git commit -m "feat(auth): AuthService.unfreezeUser с инвариантом I3"
```

---

## Task 10: AuthService.revokeInvitation (I4)

**Files:**
- Modify: `backend/src/main/kotlin/com/example/cleancity/auth/AuthService.kt`
- Modify: `backend/src/main/kotlin/com/example/cleancity/auth/UserRepository.kt` (+ `delete(id)` если нет)

- [ ] **Step 1: Добавить UserRepository.delete если нет**

`backend/.../auth/UserRepository.kt` — если метода нет:

```kotlin
fun delete(userId: Long): Int = transaction {
    Users.deleteWhere { Users.id eq userId }
}
```

Импорт `import org.jetbrains.exposed.sql.deleteWhere` (или совместимый со текущей версией Exposed).

- [ ] **Step 2: Добавить revokeInvitation в AuthService**

```kotlin
/**
 * Отзыв pending-приглашения. I4: target должен быть pending
 * (is_active=false, email_verified=false). После — invalidate токенов + delete row.
 */
suspend fun revokeInvitation(actorId: Long, targetId: Long, ip: String?, ua: String?) {
    val target = users.findById(targetId)
        ?: throw IllegalArgumentException("User not found")
    if (target.isActive || target.emailVerified) throw NotAPendingInviteException()

    tokens.invalidateInviteForUser(targetId)
    users.delete(targetId)
    audit.log(AuditAction.ADMIN_INVITE_REVOKED, actorId, "user", targetId.toString(), ip, ua)
}
```

- [ ] **Step 3: Компиляция + commit**

```bash
./gradlew :backend:compileKotlin -q
git add backend/src/main/kotlin/com/example/cleancity/auth/AuthService.kt \
        backend/src/main/kotlin/com/example/cleancity/auth/UserRepository.kt
git commit -m "feat(auth): AuthService.revokeInvitation с инвариантом I4"
```

---

## Task 11: AuthRoutes — 5 новых ручек + интеграционные тесты

**Files:**
- Modify: `backend/src/main/kotlin/com/example/cleancity/auth/AuthRoutes.kt`
- Create: `backend/src/test/kotlin/com/example/cleancity/auth/AuthAdminTeamRoutesTest.kt`

- [ ] **Step 1: Написать интеграционные тесты**

Создать `backend/src/test/kotlin/com/example/cleancity/auth/AuthAdminTeamRoutesTest.kt`. Структура копирует `AccountDeletionRoutesTest`. Все 14 кейсов:

```kotlin
package com.example.cleancity.auth

import com.example.cleancity.database.tables.AuditAction
import com.example.cleancity.database.tables.AuditLog
import com.example.cleancity.database.tables.EmailTokenPurpose
import com.example.cleancity.database.tables.EmailTokens
import com.example.cleancity.database.tables.RefreshTokens
import com.example.cleancity.database.tables.Users
import com.example.cleancity.email.EmailService
import com.example.cleancity.shared.models.UserRole
import com.example.cleancity.shared.requests.auth.AdminInviteRequest
import com.example.cleancity.shared.responses.admin.TeamMembersResponse
import com.example.cleancity.shared.responses.admin.AuditLogResponse
import com.example.cleancity.shared.responses.admin.TeamStatus
import com.example.cleancity.testutils.installApiErrorHandling
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.*

class AuthAdminTeamRoutesTest {

    private val jwtConfig = JwtConfig(
        secret = "test-secret-at-least-32-bytes-long-for-tests-only-1234567890",
        issuer = "cleancity-test",
        audience = "cleancity-test-api"
    )
    private val json = Json { ignoreUnknownKeys = true }

    private class NoopEmail : EmailService {
        override suspend fun send(to: String, subject: String, htmlBody: String, plainBody: String?) = Unit
    }

    private fun initDb() {
        Database.connect(
            "jdbc:h2:mem:team-${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.drop(RefreshTokens, EmailTokens, AuditLog, Users)
            SchemaUtils.create(Users, EmailTokens, RefreshTokens, AuditLog)
        }
    }

    private fun seedUser(
        email: String,
        role: UserRole,
        isActive: Boolean = true,
        emailVerified: Boolean = true
    ): Long = transaction {
        Users.insert {
            it[Users.email] = email
            it[Users.passwordHash] = "bcrypt-hash"
            it[Users.role] = role.name
            it[Users.fullName] = "Тест Тестов"
            it[Users.emailVerified] = emailVerified
            it[Users.isActive] = isActive
            it[Users.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
            it[Users.passwordChangedAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }[Users.id]
    }

    private fun bearerFor(userId: Long, role: UserRole): String =
        jwtConfig.issueAccessToken(userId = userId, role = role).token

    private fun ApplicationTestBuilder.appWithAuth() {
        val service = AuthService(
            users = UserRepository(),
            tokens = TokenRepository(),
            email = NoopEmail(),
            jwt = jwtConfig,
            baseUrl = "http://localhost:8080",
            termsVersion = "test-v1",
            totp = TotpService(),
            audit = DbAuditLogger()
            // auditLog имеет default = AuditLogRepository()
        )
        application {
            install(ContentNegotiation) { json(json) }
            install(Authentication) {
                jwt("auth-jwt") {
                    verifier(jwtConfig.verifier)
                    validate { c ->
                        if (c.payload.subject != null &&
                            c.payload.getClaim("type").asString() == "access"
                        ) JWTPrincipal(c.payload) else null
                    }
                }
            }
            installApiErrorHandling()
            routing { authRoutes(service, RateLimiter()) }
        }
    }

    @Test
    fun `GET admin users active returns only active staff omitting residents`() {
        initDb()
        val adminId = seedUser("admin@t.local", UserRole.ADMIN)
        seedUser("op@t.local", UserRole.OPERATOR)
        seedUser("frozen@t.local", UserRole.OPERATOR, isActive = false)
        seedUser("resident@t.local", UserRole.RESIDENT)

        testApplication {
            appWithAuth()
            val resp = client.get("/auth/admin/users?status=ACTIVE") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(adminId, UserRole.ADMIN)}")
            }
            assertEquals(HttpStatusCode.OK, resp.status)
            val body = json.decodeFromString<TeamMembersResponse>(resp.bodyAsText())
            val emails = body.items.map { it.email }.toSet()
            assertEquals(setOf("admin@t.local", "op@t.local"), emails)
        }
    }

    @Test
    fun `OPERATOR can list team but cannot freeze`() {
        initDb()
        val opId = seedUser("op@t.local", UserRole.OPERATOR)
        val adminId = seedUser("admin@t.local", UserRole.ADMIN)

        testApplication {
            appWithAuth()
            val list = client.get("/auth/admin/users") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(opId, UserRole.OPERATOR)}")
            }
            assertEquals(HttpStatusCode.OK, list.status)

            val freeze = client.post("/auth/admin/users/$adminId/freeze") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(opId, UserRole.OPERATOR)}")
            }
            assertEquals(HttpStatusCode.Forbidden, freeze.status)
        }
    }

    @Test
    fun `freeze revokes all refresh tokens of target`() {
        initDb()
        val adminId = seedUser("admin@t.local", UserRole.ADMIN)
        seedUser("admin2@t.local", UserRole.ADMIN)  // чтобы I2 не сработало
        val opId = seedUser("op@t.local", UserRole.OPERATOR)

        // выпустить refresh-токен напрямую через TokenRepository
        val rawToken = TokenRepository().createRefreshToken(
            userId = opId,
            ip = "1.1.1.1",
            userAgent = "test",
            ttlSeconds = 3600
        )

        testApplication {
            appWithAuth()
            val resp = client.post("/auth/admin/users/$opId/freeze") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(adminId, UserRole.ADMIN)}")
            }
            assertEquals(HttpStatusCode.NoContent, resp.status)

            // Попытка использовать старый refresh-токен → 401
            val refresh = client.post("/auth/refresh") {
                contentType(ContentType.Application.Json)
                setBody("""{"refreshToken":"$rawToken"}""")
            }
            assertEquals(HttpStatusCode.Unauthorized, refresh.status)
        }
    }

    @Test
    fun `freeze self returns 403`() {
        initDb()
        val adminId = seedUser("admin@t.local", UserRole.ADMIN)
        seedUser("admin2@t.local", UserRole.ADMIN)  // I2 не должно мешать

        testApplication {
            appWithAuth()
            val resp = client.post("/auth/admin/users/$adminId/freeze") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(adminId, UserRole.ADMIN)}")
            }
            assertEquals(HttpStatusCode.Forbidden, resp.status)
        }
    }

    @Test
    fun `freeze last active admin returns 409`() {
        initDb()
        // adminId — actor (JWT валиден, БД считает его неактивным; JWT не проверяет is_active).
        // targetAdminId — единственный активный ADMIN в БД. Freeze его → 0 активных → I2 → 409.
        val adminId = seedUser("admin@t.local", UserRole.ADMIN, isActive = false)
        val targetAdminId = seedUser("admin2@t.local", UserRole.ADMIN, isActive = true)

        testApplication {
            appWithAuth()
            val resp = client.post("/auth/admin/users/$targetAdminId/freeze") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(adminId, UserRole.ADMIN)}")
            }
            assertEquals(HttpStatusCode.Conflict, resp.status)
        }
    }

    @Test
    fun `freeze admin when other admin exists succeeds`() {
        initDb()
        val adminA = seedUser("a@t.local", UserRole.ADMIN)
        val adminB = seedUser("b@t.local", UserRole.ADMIN)

        testApplication {
            appWithAuth()
            val resp = client.post("/auth/admin/users/$adminB/freeze") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(adminA, UserRole.ADMIN)}")
            }
            assertEquals(HttpStatusCode.NoContent, resp.status)
        }
    }

    @Test
    fun `unfreeze pending returns 400`() {
        initDb()
        val adminId = seedUser("admin@t.local", UserRole.ADMIN)
        val pendingId = seedUser("pending@t.local", UserRole.OPERATOR, isActive = false, emailVerified = false)

        testApplication {
            appWithAuth()
            val resp = client.post("/auth/admin/users/$pendingId/unfreeze") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(adminId, UserRole.ADMIN)}")
            }
            assertEquals(HttpStatusCode.BadRequest, resp.status)
        }
    }

    @Test
    fun `unfreeze frozen returns 204 and user becomes active`() {
        initDb()
        val adminId = seedUser("admin@t.local", UserRole.ADMIN)
        val frozenId = seedUser("frozen@t.local", UserRole.OPERATOR, isActive = false, emailVerified = true)

        testApplication {
            appWithAuth()
            val resp = client.post("/auth/admin/users/$frozenId/unfreeze") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(adminId, UserRole.ADMIN)}")
            }
            assertEquals(HttpStatusCode.NoContent, resp.status)
            transaction {
                val active = Users.selectAll().where { Users.id eq frozenId }.first()[Users.isActive]
                assertTrue(active)
            }
        }
    }

    @Test
    fun `revoke invitation deletes pending user and invalidates token`() {
        initDb()
        val adminId = seedUser("admin@t.local", UserRole.ADMIN)
        val pendingId = seedUser("pending@t.local", UserRole.OPERATOR, isActive = false, emailVerified = false)
        TokenRepository().createEmailToken(pendingId, EmailTokenPurpose.ADMIN_INVITE, 3600)

        testApplication {
            appWithAuth()
            val resp = client.delete("/auth/admin/invitations/$pendingId") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(adminId, UserRole.ADMIN)}")
            }
            assertEquals(HttpStatusCode.NoContent, resp.status)
            transaction {
                val gone = Users.selectAll().where { Users.id eq pendingId }.count()
                assertEquals(0L, gone)
                val tokenRows = EmailTokens.selectAll().where { EmailTokens.userId eq pendingId }.toList()
                assertTrue(tokenRows.all { it[EmailTokens.consumedAt] != null })
            }
        }
    }

    @Test
    fun `revoke invitation on active user returns 400`() {
        initDb()
        val adminId = seedUser("admin@t.local", UserRole.ADMIN)
        val activeId = seedUser("active@t.local", UserRole.OPERATOR)

        testApplication {
            appWithAuth()
            val resp = client.delete("/auth/admin/invitations/$activeId") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(adminId, UserRole.ADMIN)}")
            }
            assertEquals(HttpStatusCode.BadRequest, resp.status)
        }
    }

    @Test
    fun `audit log returns recent events with actor email`() {
        initDb()
        val adminId = seedUser("admin@t.local", UserRole.ADMIN)
        val opId = seedUser("op@t.local", UserRole.OPERATOR)
        seedUser("admin2@t.local", UserRole.ADMIN)

        testApplication {
            appWithAuth()
            client.post("/auth/admin/users/$opId/freeze") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(adminId, UserRole.ADMIN)}")
            }
            val resp = client.get("/auth/admin/audit-log") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(adminId, UserRole.ADMIN)}")
            }
            assertEquals(HttpStatusCode.OK, resp.status)
            val body = json.decodeFromString<AuditLogResponse>(resp.bodyAsText())
            val frozen = body.items.firstOrNull { it.action == AuditAction.ADMIN_USER_FROZEN.name }
            assertNotNull(frozen)
            assertEquals("admin@t.local", frozen.actorEmail)
        }
    }

    @Test
    fun `audit log limit max 50`() {
        initDb()
        val adminId = seedUser("admin@t.local", UserRole.ADMIN)
        repeat(60) { i ->
            transaction {
                AuditLog.insert {
                    it[AuditLog.actorUserId] = adminId
                    it[AuditLog.action] = AuditAction.LOGIN_SUCCESS.name
                    it[AuditLog.createdAt] = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(i.toLong())
                }
            }
        }

        testApplication {
            appWithAuth()
            val resp = client.get("/auth/admin/audit-log?limit=999") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(adminId, UserRole.ADMIN)}")
            }
            val body = json.decodeFromString<AuditLogResponse>(resp.bodyAsText())
            assertEquals(50, body.items.size)
        }
    }

    @Test
    fun `invite role inspector returns 400`() {
        initDb()
        val adminId = seedUser("admin@t.local", UserRole.ADMIN)

        testApplication {
            appWithAuth()
            val resp = client.post("/auth/admin/invite") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(adminId, UserRole.ADMIN)}")
                contentType(ContentType.Application.Json)
                setBody("""{"email":"new@t.local","role":"INSPECTOR"}""")
            }
            assertEquals(HttpStatusCode.BadRequest, resp.status)
        }
    }

    @Test
    fun `operator cannot invite`() {
        initDb()
        val opId = seedUser("op@t.local", UserRole.OPERATOR)

        testApplication {
            appWithAuth()
            val resp = client.post("/auth/admin/invite") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(opId, UserRole.OPERATOR)}")
                contentType(ContentType.Application.Json)
                setBody("""{"email":"new@t.local","role":"OPERATOR"}""")
            }
            assertEquals(HttpStatusCode.Forbidden, resp.status)
        }
    }
}
```

- [ ] **Step 2: Запустить — все упадут (роуты ещё не реализованы)**

```bash
./gradlew :backend:test --tests "com.example.cleancity.auth.AuthAdminTeamRoutesTest" -q
```
Expected: 14 FAIL.

- [ ] **Step 3: Реализовать 5 новых ручек + exception mapping**

`backend/.../auth/AuthRoutes.kt` — внутри блока `authenticate("auth-jwt") { ... }`, после существующего `/admin/invite` (после строки 253), добавить:

```kotlin
get("/admin/users") {
    val role = call.requireRole()
    if (role !in setOf(UserRole.ADMIN, UserRole.OPERATOR)) {
        throw ForbiddenException("Только сотрудники", ErrorCodes.AUTH_FORBIDDEN)
    }
    val status = call.parameters["status"]?.let { raw ->
        runCatching { TeamStatus.valueOf(raw.uppercase()) }.getOrNull()
            ?: throw BadRequestException("Invalid status", ErrorCodes.VALIDATION_BAD_FIELD)
    }
    call.respond(HttpStatusCode.OK, TeamMembersResponse(service.listTeamMembers(status)))
}

get("/admin/audit-log") {
    val role = call.requireRole()
    if (role !in setOf(UserRole.ADMIN, UserRole.OPERATOR)) {
        throw ForbiddenException("Только сотрудники", ErrorCodes.AUTH_FORBIDDEN)
    }
    val limit = call.parameters["limit"]?.toIntOrNull()?.coerceIn(1, 50) ?: 50
    call.respond(HttpStatusCode.OK, AuditLogResponse(service.recentAuditEvents(limit)))
}

post("/admin/users/{id}/freeze") {
    val actorId = call.requireUserId()
    val role = call.requireRole()
    if (role != UserRole.ADMIN) throw ForbiddenException("Admins only", ErrorCodes.AUTH_FORBIDDEN)
    val targetId = call.parameters["id"]?.toLongOrNull()
        ?: throw BadRequestException("Invalid user id", ErrorCodes.VALIDATION_BAD_FIELD)
    try {
        service.freezeUser(actorId, targetId, call.clientIp(), call.userAgentSafe())
        call.respond(HttpStatusCode.NoContent)
    } catch (_: SelfFreezeException) {
        throw ForbiddenException("Нельзя заморозить собственный аккаунт", ErrorCodes.ADMIN_CANNOT_FREEZE_SELF)
    } catch (_: LastActiveAdminException) {
        throw ConflictException("Это последний активный администратор", ErrorCodes.LAST_ACTIVE_ADMIN)
    }
}

post("/admin/users/{id}/unfreeze") {
    val actorId = call.requireUserId()
    val role = call.requireRole()
    if (role != UserRole.ADMIN) throw ForbiddenException("Admins only", ErrorCodes.AUTH_FORBIDDEN)
    val targetId = call.parameters["id"]?.toLongOrNull()
        ?: throw BadRequestException("Invalid user id", ErrorCodes.VALIDATION_BAD_FIELD)
    try {
        service.unfreezeUser(actorId, targetId, call.clientIp(), call.userAgentSafe())
        call.respond(HttpStatusCode.NoContent)
    } catch (_: InviteNotAcceptedException) {
        throw BadRequestException("Сотрудник ещё не принял приглашение", ErrorCodes.INVITE_NOT_ACCEPTED)
    }
}

delete("/admin/invitations/{id}") {
    val actorId = call.requireUserId()
    val role = call.requireRole()
    if (role != UserRole.ADMIN) throw ForbiddenException("Admins only", ErrorCodes.AUTH_FORBIDDEN)
    val targetId = call.parameters["id"]?.toLongOrNull()
        ?: throw BadRequestException("Invalid user id", ErrorCodes.VALIDATION_BAD_FIELD)
    try {
        service.revokeInvitation(actorId, targetId, call.clientIp(), call.userAgentSafe())
        call.respond(HttpStatusCode.NoContent)
    } catch (_: NotAPendingInviteException) {
        throw BadRequestException("Это не pending-приглашение", ErrorCodes.NOT_A_PENDING_INVITE)
    }
}
```

Импорты:

```kotlin
import com.example.cleancity.shared.responses.admin.AuditLogResponse
import com.example.cleancity.shared.responses.admin.TeamMembersResponse
import com.example.cleancity.shared.responses.admin.TeamStatus
```

Если `ConflictException` нет в проекте — проверить в существующих файлах (например в обработчиках) и использовать существующий формат, либо добавить аналогично `ForbiddenException`/`BadRequestException`. Должен быть симметричен.

- [ ] **Step 4: Запустить тесты — все должны пройти**

```bash
./gradlew :backend:test --tests "com.example.cleancity.auth.AuthAdminTeamRoutesTest" -q
```
Expected: 14 PASS.

- [ ] **Step 5: Прогнать весь backend test suite — никаких регрессий**

```bash
./gradlew :backend:test -q
```
Expected: ВСЕ зелёные.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/example/cleancity/auth/AuthRoutes.kt \
        backend/src/test/kotlin/com/example/cleancity/auth/AuthAdminTeamRoutesTest.kt
git commit -m "feat(auth): 5 новых ручек /auth/admin/* для управления командой и audit"
```

---

## Task 12: Frontend types и API-клиент

**Files:**
- Modify: `web-admin/src/api/types.ts`
- Create: `web-admin/src/api/admin.ts`

- [ ] **Step 1: Добавить типы**

`web-admin/src/api/types.ts` — добавить в конец файла:

```ts
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

export type TeamMembersResponse = { items: TeamMemberDto[] }

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

export type AuditLogResponse = { items: AuditEntryDto[] }
```

- [ ] **Step 2: Создать api/admin.ts**

`web-admin/src/api/admin.ts`:

```ts
import { apiClient } from './client'
import type {
  TeamMemberDto,
  TeamMembersResponse,
  AuditEntryDto,
  AuditLogResponse,
} from './types'

export async function listTeamMembers(
  status?: 'active' | 'frozen' | 'pending'
): Promise<TeamMemberDto[]> {
  const params = status ? { status: status.toUpperCase() } : undefined
  const { data } = await apiClient.get<TeamMembersResponse>('/auth/admin/users', { params })
  return data.items
}

export async function inviteTeamMember(
  email: string,
  role: 'ADMIN' | 'OPERATOR'
): Promise<void> {
  await apiClient.post('/auth/admin/invite', { email, role })
}

export async function freezeUser(userId: number): Promise<void> {
  await apiClient.post(`/auth/admin/users/${userId}/freeze`)
}

export async function unfreezeUser(userId: number): Promise<void> {
  await apiClient.post(`/auth/admin/users/${userId}/unfreeze`)
}

export async function revokeInvitation(userId: number): Promise<void> {
  await apiClient.delete(`/auth/admin/invitations/${userId}`)
}

export async function recentAuditEvents(limit = 50): Promise<AuditEntryDto[]> {
  const { data } = await apiClient.get<AuditLogResponse>('/auth/admin/audit-log', {
    params: { limit },
  })
  return data.items
}
```

Если `apiClient` называется иначе (проверить в `api/client.ts` — может `api` или `client`) — использовать актуальное имя.

- [ ] **Step 3: Компиляция TS**

```bash
cd web-admin && npx tsc --noEmit
cd ..
```
Expected: 0 ошибок.

- [ ] **Step 4: Commit**

```bash
git add web-admin/src/api/types.ts web-admin/src/api/admin.ts
git commit -m "feat(web-admin): типы и API-клиент для команды и audit-лога"
```

---

## Task 13: actionLabels (русские строки для AuditAction)

**Files:**
- Create: `web-admin/src/pages/settings/actionLabels.ts`

- [ ] **Step 1: Создать словарь**

`web-admin/src/pages/settings/actionLabels.ts`:

```ts
/**
 * Соответствие AuditAction.name → русская строка для UI.
 * Если action отсутствует в словаре — возвращаем сам action.
 */
const LABELS: Record<string, string> = {
  LOGIN_SUCCESS: 'Вход',
  LOGIN_FAIL: 'Неуспешный вход',
  LOGIN_LOCKED: 'Аккаунт заблокирован',
  TWOFA_SETUP_STARTED: 'Начало настройки 2FA',
  TWOFA_ENABLED: '2FA подключено',
  TWOFA_FAIL: 'Неверный 2FA-код',
  TWOFA_LOGIN_SUCCESS: 'Вход с 2FA',
  PASSWORD_RESET: 'Сброс пароля',
  REFRESH_REVOKED: 'Отозван refresh-токен',
  SESSION_REVOKED: 'Завершена сессия',
  ADMIN_INVITE_SENT: 'Отправлено приглашение',
  ADMIN_INVITE_ACCEPTED: 'Приглашение принято',
  ADMIN_INVITE_REVOKED: 'Приглашение отозвано',
  ADMIN_USER_FROZEN: 'Сотрудник заморожен',
  ADMIN_USER_UNFROZEN: 'Сотрудник разморожен',
  COMPLAINT_STATUS_CHANGE: 'Смена статуса жалобы',
  ACCOUNT_DELETED: 'Удаление аккаунта',
}

export function labelForAction(action: string): string {
  return LABELS[action] ?? action
}
```

- [ ] **Step 2: Commit**

```bash
git add web-admin/src/pages/settings/actionLabels.ts
git commit -m "feat(settings): словарь action → русская строка для audit-лога"
```

---

## Task 14: ConfirmActionDialog + InviteMemberDialog

**Files:**
- Create: `web-admin/src/pages/settings/ConfirmActionDialog.tsx`
- Create: `web-admin/src/pages/settings/InviteMemberDialog.tsx`

- [ ] **Step 1: Прочитать паттерн существующих диалогов**

Прочитать `web-admin/src/components/announcements/AnnouncementItem.tsx` или любой существующий компонент с `Dialog` — чтобы взять корректное имя импорта и стиль кнопок.

```bash
ls web-admin/src/components/ui/
```

- [ ] **Step 2: Создать ConfirmActionDialog**

`web-admin/src/pages/settings/ConfirmActionDialog.tsx`:

```tsx
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'

type Props = {
  open: boolean
  title: string
  message: string
  confirmLabel: string
  variant?: 'default' | 'destructive'
  loading?: boolean
  onConfirm: () => void
  onCancel: () => void
}

export function ConfirmActionDialog({
  open,
  title,
  message,
  confirmLabel,
  variant = 'default',
  loading = false,
  onConfirm,
  onCancel,
}: Props) {
  return (
    <Dialog open={open} onOpenChange={(o) => { if (!o) onCancel() }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{message}</DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" onClick={onCancel} disabled={loading}>
            Отмена
          </Button>
          <Button
            variant={variant === 'destructive' ? 'destructive' : 'default'}
            onClick={onConfirm}
            disabled={loading}
          >
            {loading ? 'Подождите…' : confirmLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
```

Если пути алиасов отличаются (`@/components/ui/...` vs `~/...`) — взять как в существующем коде.

- [ ] **Step 3: Создать InviteMemberDialog**

`web-admin/src/pages/settings/InviteMemberDialog.tsx`:

```tsx
import { useState } from 'react'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

type Props = {
  open: boolean
  loading?: boolean
  onSubmit: (email: string, role: 'ADMIN' | 'OPERATOR') => void
  onCancel: () => void
}

export function InviteMemberDialog({ open, loading = false, onSubmit, onCancel }: Props) {
  const [email, setEmail] = useState('')
  const [role, setRole] = useState<'ADMIN' | 'OPERATOR'>('OPERATOR')

  const handleSubmit = () => {
    if (!email.trim()) return
    onSubmit(email.trim(), role)
  }

  const handleOpenChange = (next: boolean) => {
    if (!next) {
      setEmail('')
      setRole('OPERATOR')
      onCancel()
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Пригласить сотрудника</DialogTitle>
          <DialogDescription>
            На указанный email придёт ссылка для активации. Действует 24 часа.
          </DialogDescription>
        </DialogHeader>
        <div className="flex flex-col gap-4">
          <div>
            <Label htmlFor="invite-email">Email</Label>
            <Input
              id="invite-email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="ivan@sochi.gov.ru"
              disabled={loading}
            />
          </div>
          <div>
            <Label>Роль</Label>
            <Select value={role} onValueChange={(v) => setRole(v as 'ADMIN' | 'OPERATOR')} disabled={loading}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="OPERATOR">Оператор (обработка обращений)</SelectItem>
                <SelectItem value="ADMIN">Администратор (полный доступ)</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => handleOpenChange(false)} disabled={loading}>
            Отмена
          </Button>
          <Button onClick={handleSubmit} disabled={loading || !email.trim()}>
            {loading ? 'Отправляем…' : 'Пригласить'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
```

- [ ] **Step 4: Компиляция TS**

```bash
cd web-admin && npx tsc --noEmit
cd ..
```
Expected: 0 ошибок.

- [ ] **Step 5: Commit**

```bash
git add web-admin/src/pages/settings/ConfirmActionDialog.tsx \
        web-admin/src/pages/settings/InviteMemberDialog.tsx
git commit -m "feat(settings): ConfirmActionDialog и InviteMemberDialog"
```

---

## Task 15: TeamTable + тест

**Files:**
- Create: `web-admin/src/pages/settings/TeamTable.tsx`
- Create: `web-admin/src/pages/settings/TeamTable.test.tsx`

- [ ] **Step 1: Написать падающие тесты**

`web-admin/src/pages/settings/TeamTable.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { TeamTable } from './TeamTable'
import type { TeamMemberDto } from '@/api/types'

const member: TeamMemberDto = {
  id: 1,
  email: 'a@b.c',
  fullName: 'Иван Иванов',
  role: 'OPERATOR',
  district: null,
  status: 'ACTIVE',
  createdAt: '2026-05-28T12:00:00Z',
  lastLoginAt: '2026-05-28T11:00:00Z',
  invitedAt: null,
}

describe('TeamTable', () => {
  it('shows empty state when list is empty', () => {
    render(
      <TeamTable
        status="active"
        members={[]}
        currentRole="ADMIN"
        onAction={() => {}}
      />
    )
    expect(screen.getByText(/нет активных сотрудников/i)).toBeInTheDocument()
  })

  it('renders dash for null lastLoginAt', () => {
    const pending: TeamMemberDto = { ...member, status: 'PENDING', lastLoginAt: null }
    render(
      <TeamTable
        status="pending"
        members={[pending]}
        currentRole="ADMIN"
        onAction={() => {}}
      />
    )
    // в строке pending не должно быть даты последнего входа — должен быть прочерк
    expect(screen.getByText('—')).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Запустить — упадёт (компонент не существует)**

```bash
cd web-admin && npm test -- --run TeamTable
cd ..
```
Expected: FAIL.

- [ ] **Step 3: Создать TeamTable**

`web-admin/src/pages/settings/TeamTable.tsx`:

```tsx
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import type { TeamMemberDto, UserRole } from '@/api/types'

type ActionKind = 'freeze' | 'unfreeze' | 'revoke'

type Props = {
  status: 'active' | 'frozen' | 'pending'
  members: TeamMemberDto[]
  currentRole: UserRole
  onAction: (kind: ActionKind, member: TeamMemberDto) => void
}

const EMPTY_LABEL: Record<Props['status'], string> = {
  active: 'Нет активных сотрудников',
  frozen: 'Нет замороженных сотрудников',
  pending: 'Нет ожидающих приглашений',
}

function formatDate(iso: string | null): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('ru-RU', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function actionFor(status: Props['status']): ActionKind | null {
  if (status === 'active') return 'freeze'
  if (status === 'frozen') return 'unfreeze'
  if (status === 'pending') return 'revoke'
  return null
}

function actionLabel(kind: ActionKind): string {
  return kind === 'freeze' ? 'Заморозить' : kind === 'unfreeze' ? 'Разморозить' : 'Отозвать'
}

export function TeamTable({ status, members, currentRole, onAction }: Props) {
  if (members.length === 0) {
    return <div className="py-8 text-center text-sm text-muted-foreground">{EMPTY_LABEL[status]}</div>
  }

  const action = actionFor(status)
  const showActions = currentRole === 'ADMIN' && action !== null

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Email</TableHead>
          <TableHead>ФИО</TableHead>
          <TableHead>Роль</TableHead>
          <TableHead>Район</TableHead>
          <TableHead>{status === 'pending' ? 'Приглашён' : 'Последний вход'}</TableHead>
          {showActions && <TableHead className="text-right">Действия</TableHead>}
        </TableRow>
      </TableHeader>
      <TableBody>
        {members.map((m) => (
          <TableRow key={m.id}>
            <TableCell>{m.email}</TableCell>
            <TableCell>{m.fullName ?? '—'}</TableCell>
            <TableCell>
              <Badge variant={m.role === 'ADMIN' ? 'default' : 'secondary'}>
                {m.role === 'ADMIN' ? 'Админ' : 'Оператор'}
              </Badge>
            </TableCell>
            <TableCell>{m.district ?? 'Все районы'}</TableCell>
            <TableCell>{formatDate(status === 'pending' ? m.invitedAt : m.lastLoginAt)}</TableCell>
            {showActions && (
              <TableCell className="text-right">
                <Button
                  variant={action === 'unfreeze' ? 'default' : 'destructive'}
                  size="sm"
                  onClick={() => onAction(action!, m)}
                >
                  {actionLabel(action!)}
                </Button>
              </TableCell>
            )}
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}
```

- [ ] **Step 4: Запустить тесты — должны пройти**

```bash
cd web-admin && npm test -- --run TeamTable
cd ..
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add web-admin/src/pages/settings/TeamTable.tsx web-admin/src/pages/settings/TeamTable.test.tsx
git commit -m "feat(settings): TeamTable с условными действиями по роли и табу"
```

---

## Task 16: TeamSection (табы + invite + mutations)

**Files:**
- Create: `web-admin/src/pages/settings/TeamSection.tsx`

- [ ] **Step 1: Создать TeamSection**

`web-admin/src/pages/settings/TeamSection.tsx`:

```tsx
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import {
  freezeUser,
  inviteTeamMember,
  listTeamMembers,
  revokeInvitation,
  unfreezeUser,
} from '@/api/admin'
import { extractApiError } from '@/api/errors'
import type { TeamMemberDto, UserRole } from '@/api/types'
import { ConfirmActionDialog } from './ConfirmActionDialog'
import { InviteMemberDialog } from './InviteMemberDialog'
import { TeamTable } from './TeamTable'

type TabKey = 'active' | 'frozen' | 'pending'
type ActionKind = 'freeze' | 'unfreeze' | 'revoke'

type Props = { currentRole: UserRole }

const CONFIRM: Record<ActionKind, { title: string; message: string; confirmLabel: string; variant: 'default' | 'destructive' }> = {
  freeze: {
    title: 'Заморозить сотрудника?',
    message: 'Все активные сессии будут отозваны. Сотрудник не сможет войти.',
    confirmLabel: 'Заморозить',
    variant: 'destructive',
  },
  unfreeze: {
    title: 'Разморозить сотрудника?',
    message: 'Сотрудник снова сможет войти в систему.',
    confirmLabel: 'Разморозить',
    variant: 'default',
  },
  revoke: {
    title: 'Отозвать приглашение?',
    message: 'Ссылка из письма перестанет работать.',
    confirmLabel: 'Отозвать',
    variant: 'destructive',
  },
}

export function TeamSection({ currentRole }: Props) {
  const qc = useQueryClient()
  const [tab, setTab] = useState<TabKey>('active')
  const [inviteOpen, setInviteOpen] = useState(false)
  const [pending, setPending] = useState<{ kind: ActionKind; member: TeamMemberDto } | null>(null)

  const activeQ = useQuery({
    queryKey: ['team', 'active'],
    queryFn: () => listTeamMembers('active'),
  })
  const frozenQ = useQuery({
    queryKey: ['team', 'frozen'],
    queryFn: () => listTeamMembers('frozen'),
  })
  const pendingQ = useQuery({
    queryKey: ['team', 'pending'],
    queryFn: () => listTeamMembers('pending'),
  })

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['team'] })
    qc.invalidateQueries({ queryKey: ['audit-log'] })
  }

  const inviteMut = useMutation({
    mutationFn: (vars: { email: string; role: 'ADMIN' | 'OPERATOR' }) =>
      inviteTeamMember(vars.email, vars.role),
    onSuccess: () => {
      toast.success('Приглашение отправлено')
      setInviteOpen(false)
      invalidate()
    },
    onError: (err) => toast.error(extractApiError(err).message),
  })

  const actionMut = useMutation({
    mutationFn: async (vars: { kind: ActionKind; userId: number }) => {
      if (vars.kind === 'freeze') return freezeUser(vars.userId)
      if (vars.kind === 'unfreeze') return unfreezeUser(vars.userId)
      return revokeInvitation(vars.userId)
    },
    onSuccess: () => {
      toast.success('Готово')
      setPending(null)
      invalidate()
    },
    onError: (err) => {
      toast.error(extractApiError(err).message)
      setPending(null)
    },
  })

  const counts = {
    active: activeQ.data?.length ?? 0,
    frozen: frozenQ.data?.length ?? 0,
    pending: pendingQ.data?.length ?? 0,
  }

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between">
        <CardTitle>Команда</CardTitle>
        {currentRole === 'ADMIN' && (
          <Button onClick={() => setInviteOpen(true)}>Пригласить сотрудника</Button>
        )}
      </CardHeader>
      <CardContent>
        <Tabs value={tab} onValueChange={(v) => setTab(v as TabKey)}>
          <TabsList>
            <TabsTrigger value="active">Активные ({counts.active})</TabsTrigger>
            <TabsTrigger value="frozen">Замороженные ({counts.frozen})</TabsTrigger>
            <TabsTrigger value="pending">Ожидают ({counts.pending})</TabsTrigger>
          </TabsList>
          <TabsContent value="active">
            <TeamTable
              status="active"
              members={activeQ.data ?? []}
              currentRole={currentRole}
              onAction={(kind, member) => setPending({ kind, member })}
            />
          </TabsContent>
          <TabsContent value="frozen">
            <TeamTable
              status="frozen"
              members={frozenQ.data ?? []}
              currentRole={currentRole}
              onAction={(kind, member) => setPending({ kind, member })}
            />
          </TabsContent>
          <TabsContent value="pending">
            <TeamTable
              status="pending"
              members={pendingQ.data ?? []}
              currentRole={currentRole}
              onAction={(kind, member) => setPending({ kind, member })}
            />
          </TabsContent>
        </Tabs>
      </CardContent>

      <InviteMemberDialog
        open={inviteOpen}
        loading={inviteMut.isPending}
        onSubmit={(email, role) => inviteMut.mutate({ email, role })}
        onCancel={() => setInviteOpen(false)}
      />

      {pending && (
        <ConfirmActionDialog
          open
          title={CONFIRM[pending.kind].title}
          message={CONFIRM[pending.kind].message}
          confirmLabel={CONFIRM[pending.kind].confirmLabel}
          variant={CONFIRM[pending.kind].variant}
          loading={actionMut.isPending}
          onConfirm={() => actionMut.mutate({ kind: pending.kind, userId: pending.member.id })}
          onCancel={() => setPending(null)}
        />
      )}
    </Card>
  )
}
```

- [ ] **Step 2: Компиляция TS**

```bash
cd web-admin && npx tsc --noEmit
cd ..
```
Expected: 0 ошибок.

- [ ] **Step 3: Commit**

```bash
git add web-admin/src/pages/settings/TeamSection.tsx
git commit -m "feat(settings): TeamSection с табами, инвайтом и confirm-диалогами"
```

---

## Task 17: AuditTable + AuditLogSection

**Files:**
- Create: `web-admin/src/pages/settings/AuditTable.tsx`
- Create: `web-admin/src/pages/settings/AuditLogSection.tsx`

- [ ] **Step 1: Создать AuditTable**

`web-admin/src/pages/settings/AuditTable.tsx`:

```tsx
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import type { AuditEntryDto } from '@/api/types'
import { labelForAction } from './actionLabels'

type Props = { entries: AuditEntryDto[] }

function formatTime(iso: string): string {
  return new Date(iso).toLocaleString('ru-RU', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}

function describeTarget(targetType: string | null, targetId: string | null): string {
  if (!targetType || !targetId) return '—'
  if (targetType === 'user') return `Пользователь #${targetId}`
  if (targetType === 'complaint') return `Жалоба #${targetId}`
  if (targetType === 'refresh_token') return `Refresh-токен #${targetId}`
  return `${targetType} #${targetId}`
}

export function AuditTable({ entries }: Props) {
  if (entries.length === 0) {
    return (
      <div className="py-8 text-center text-sm text-muted-foreground">
        События будут появляться по мере действий в системе.
      </div>
    )
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Время</TableHead>
          <TableHead>Кто</TableHead>
          <TableHead>Действие</TableHead>
          <TableHead>Объект</TableHead>
          <TableHead>IP</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {entries.map((e) => (
          <TableRow key={e.id}>
            <TableCell className="whitespace-nowrap">{formatTime(e.timestamp)}</TableCell>
            <TableCell>{e.actorEmail ?? 'Система'}</TableCell>
            <TableCell>{labelForAction(e.action)}</TableCell>
            <TableCell>{describeTarget(e.targetType, e.targetId)}</TableCell>
            <TableCell className="text-muted-foreground">{e.ip ?? '—'}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}
```

- [ ] **Step 2: Создать AuditLogSection**

`web-admin/src/pages/settings/AuditLogSection.tsx`:

```tsx
import { useQuery } from '@tanstack/react-query'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { recentAuditEvents } from '@/api/admin'
import { AuditTable } from './AuditTable'

export function AuditLogSection() {
  const { data, isLoading } = useQuery({
    queryKey: ['audit-log'],
    queryFn: () => recentAuditEvents(50),
  })

  return (
    <Card>
      <CardHeader>
        <CardTitle>Журнал событий</CardTitle>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <div className="py-8 text-center text-sm text-muted-foreground">Загрузка…</div>
        ) : (
          <AuditTable entries={data ?? []} />
        )}
      </CardContent>
    </Card>
  )
}
```

- [ ] **Step 3: Компиляция TS**

```bash
cd web-admin && npx tsc --noEmit
cd ..
```
Expected: 0 ошибок.

- [ ] **Step 4: Commit**

```bash
git add web-admin/src/pages/settings/AuditTable.tsx web-admin/src/pages/settings/AuditLogSection.tsx
git commit -m "feat(settings): AuditTable и AuditLogSection"
```

---

## Task 18: SettingsPage + тесты + routing

**Files:**
- Create: `web-admin/src/pages/SettingsPage.tsx`
- Create: `web-admin/src/pages/SettingsPage.test.tsx`
- Modify: `web-admin/src/App.tsx`

- [ ] **Step 1: Создать SettingsPage**

Прежде проверить, как существующие страницы получают `role` текущего пользователя — например, в `useAuth` хуке. Найти:

```bash
grep -rn "useAuth\|currentUser\|UserRole" web-admin/src/auth/ | head -10
```

Использовать ту же абстракцию. Пример (адаптировать по реальному хуку):

`web-admin/src/pages/SettingsPage.tsx`:

```tsx
import { useAuth } from '@/auth/useAuth'  // <- проверить точный путь!
import { TeamSection } from './settings/TeamSection'
import { AuditLogSection } from './settings/AuditLogSection'

export function SettingsPage() {
  const { user } = useAuth()
  // user.role: 'ADMIN' | 'OPERATOR' | 'RESIDENT' — RESIDENT не должен попасть на /settings,
  // но защитные fallback'и не вредят
  const role = user?.role ?? 'OPERATOR'

  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-2xl font-semibold">Настройки</h1>
      <TeamSection currentRole={role} />
      <AuditLogSection />
    </div>
  )
}
```

- [ ] **Step 2: Подключить роут в App.tsx**

`web-admin/src/App.tsx` — было (строка ~29):

```tsx
<Route path="/settings" element={<SectionPlaceholder title="Настройки" />} />
```

Стало:

```tsx
<Route path="/settings" element={<SettingsPage />} />
```

Импорт `import { SettingsPage } from '@/pages/SettingsPage'` (либо относительный — как в соседних импортах).

- [ ] **Step 3: Написать тесты SettingsPage**

`web-admin/src/pages/SettingsPage.test.tsx`:

```tsx
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { Toaster } from 'sonner'
import * as adminApi from '@/api/admin'
import { SettingsPage } from './SettingsPage'

vi.mock('@/api/admin')
vi.mock('@/auth/useAuth', () => ({
  useAuth: vi.fn(),
}))

import { useAuth } from '@/auth/useAuth'

function wrap(ui: React.ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <Toaster />
      {ui}
    </QueryClientProvider>
  )
}

const fakeMember = {
  id: 1, email: 'a@b.c', fullName: 'Иван', role: 'OPERATOR' as const,
  district: null, status: 'ACTIVE' as const, createdAt: '2026-05-28T00:00:00Z',
  lastLoginAt: '2026-05-28T01:00:00Z', invitedAt: null,
}

describe('SettingsPage', () => {
  beforeEach(() => {
    vi.mocked(adminApi.listTeamMembers).mockResolvedValue([fakeMember])
    vi.mocked(adminApi.recentAuditEvents).mockResolvedValue([])
  })

  it('renders team and audit sections', async () => {
    vi.mocked(useAuth).mockReturnValue({ user: { role: 'ADMIN' } } as any)
    wrap(<SettingsPage />)
    expect(screen.getByText('Команда')).toBeInTheDocument()
    expect(screen.getByText('Журнал событий')).toBeInTheDocument()
  })

  it('admin sees invite and action buttons', async () => {
    vi.mocked(useAuth).mockReturnValue({ user: { role: 'ADMIN' } } as any)
    wrap(<SettingsPage />)
    expect(screen.getByText('Пригласить сотрудника')).toBeInTheDocument()
    await waitFor(() => {
      expect(screen.getByText('Заморозить')).toBeInTheDocument()
    })
  })

  it('operator does not see action buttons', async () => {
    vi.mocked(useAuth).mockReturnValue({ user: { role: 'OPERATOR' } } as any)
    wrap(<SettingsPage />)
    expect(screen.queryByText('Пригласить сотрудника')).not.toBeInTheDocument()
    await waitFor(() => {
      expect(screen.getByText('a@b.c')).toBeInTheDocument()
    })
    expect(screen.queryByText('Заморозить')).not.toBeInTheDocument()
  })

  it('freeze action shows confirm dialog with revoke warning', async () => {
    vi.mocked(useAuth).mockReturnValue({ user: { role: 'ADMIN' } } as any)
    wrap(<SettingsPage />)
    await waitFor(() => screen.getByText('Заморозить'))
    await userEvent.click(screen.getByText('Заморозить'))
    expect(screen.getByText(/все активные сессии будут отозваны/i)).toBeInTheDocument()
  })

  it('freeze success invalidates team query and refetches', async () => {
    vi.mocked(useAuth).mockReturnValue({ user: { role: 'ADMIN' } } as any)
    vi.mocked(adminApi.freezeUser).mockResolvedValue(undefined)
    wrap(<SettingsPage />)
    await waitFor(() => screen.getByText('Заморозить'))
    await userEvent.click(screen.getByText('Заморозить'))
    await userEvent.click(screen.getByRole('button', { name: 'Заморозить' }))
    await waitFor(() => {
      expect(adminApi.freezeUser).toHaveBeenCalledWith(1)
    })
    // вторая выборка после invalidate
    expect(adminApi.listTeamMembers).toHaveBeenCalledTimes(6) // 3 таба × 2 раза (initial + invalidate)
  })

  it('last active admin error shows friendly toast', async () => {
    vi.mocked(useAuth).mockReturnValue({ user: { role: 'ADMIN' } } as any)
    vi.mocked(adminApi.freezeUser).mockRejectedValue({
      response: { data: { code: 'LAST_ACTIVE_ADMIN', message: 'Это последний активный администратор. Сначала добавь и активируй другого.' } },
    })
    wrap(<SettingsPage />)
    await waitFor(() => screen.getByText('Заморозить'))
    await userEvent.click(screen.getByText('Заморозить'))
    await userEvent.click(screen.getByRole('button', { name: 'Заморозить' }))
    await waitFor(() => {
      expect(screen.getByText(/последний активный администратор/i)).toBeInTheDocument()
    })
  })

  it('tab counts update', async () => {
    vi.mocked(useAuth).mockReturnValue({ user: { role: 'ADMIN' } } as any)
    vi.mocked(adminApi.listTeamMembers).mockImplementation(async (s) => {
      if (s === 'active') return [fakeMember]
      if (s === 'frozen') return []
      if (s === 'pending') return []
      return []
    })
    wrap(<SettingsPage />)
    await waitFor(() => {
      expect(screen.getByText(/Активные \(1\)/)).toBeInTheDocument()
      expect(screen.getByText(/Замороженные \(0\)/)).toBeInTheDocument()
      expect(screen.getByText(/Ожидают \(0\)/)).toBeInTheDocument()
    })
  })

  it('pending tab shows revoke button only', async () => {
    vi.mocked(useAuth).mockReturnValue({ user: { role: 'ADMIN' } } as any)
    const pending = { ...fakeMember, id: 9, status: 'PENDING' as const, lastLoginAt: null, invitedAt: '2026-05-28T00:00:00Z' }
    vi.mocked(adminApi.listTeamMembers).mockImplementation(async (s) => {
      if (s === 'pending') return [pending]
      return []
    })
    wrap(<SettingsPage />)
    await userEvent.click(screen.getByText(/Ожидают/))
    await waitFor(() => {
      expect(screen.getByText('Отозвать')).toBeInTheDocument()
    })
    expect(screen.queryByText('Заморозить')).not.toBeInTheDocument()
  })
})
```

- [ ] **Step 4: Запустить — должны пройти**

```bash
cd web-admin && npm test -- --run
cd ..
```
Expected: все зелёные. Если 6-й кейс (`freeze_success_invalidates...`) даёт другое число вызовов из-за `useQuery` особенностей — поправить ожидаемое число под фактическое поведение TanStack Query.

- [ ] **Step 5: Commit**

```bash
git add web-admin/src/pages/SettingsPage.tsx \
        web-admin/src/pages/SettingsPage.test.tsx \
        web-admin/src/App.tsx
git commit -m "feat(settings): SettingsPage и роут /settings + 8 unit-тестов"
```

---

## Task 19: Ручной smoke + PLAN.md + финальный коммит

**Files:**
- Modify: `docs/PLAN.md`

- [ ] **Step 1: Поднять backend + Mailpit + web-admin локально**

В трёх терминалах:

```bash
# T1 — backend
docker compose up -d db mailpit
./gradlew :backend:run

# T2 — web-admin
cd web-admin && npm run dev
```

- [ ] **Step 2: Ручной smoke — 10 шагов из спека (§ «Ручной smoke»)**

Открыть `http://localhost:5173`. Прогнать:

1. Логин ADMIN (seed: `admin@cleancity.dev` / `Admin12345!`) → `/settings`
2. Пригласить `invitee1@test.local` с ролью OPERATOR
3. Открыть Mailpit (`http://localhost:8025`) → accept-invite → задать пароль
4. Вернуться в `/settings` под ADMIN → таб «Активные» содержит нового сотрудника
5. Логин под `invitee1@test.local` → `/settings` → список **без** кнопок-действий, audit-лог виден
6. Назад под ADMIN → «Заморозить» нового сотрудника
7. У OPERATOR: F5 → автоматический logout через 401
8. Audit-лог содержит `ADMIN_INVITE_SENT`, `ADMIN_INVITE_ACCEPTED`, `ADMIN_USER_FROZEN` с правильными actorEmail
9. «Разморозить» → OPERATOR заново может зайти
10. Пригласить второго → «Отозвать» из таба «Ожидают» → ссылка из письма не работает (страница accept-invite → ошибка токена)

Если на любом шаге что-то не работает — НЕ комитить, разобраться с багом.

- [ ] **Step 3: Обновить PLAN.md (корень репо)**

`docs/PLAN.md` — найти раздел Day 17 пункт 17C (строки 447-451). Поставить `[x]` на:

- `SettingsPage` — профиль, смена пароля, 2FA, приглашение админов, audit-лог (упрощённо — последние 50 событий)
- **Управление командой в Settings:**
  - Список админов через `GET /auth/admin/users`
  - Модалка редактирования сотрудника: ФИО / роль / районы / кнопки **«Заморозить»** и **«Удалить»**
  - Pending invitations + кнопка отзыва

Дописать примечание в стиле других дней:

```markdown
> **17C закрыт 2026-05-28.** Скоп: только Команда + Audit-лог (профиль/пароль/2FA UI — в backlog).
> Доп.: удалена роль INSPECTOR из enum и кодовой базы (~9 точек).
> Дизайн+план: docs/superpowers/specs/2026-05-28-day17c-settings-team-audit-design.md,
> docs/superpowers/plans/2026-05-28-day17c-settings-team-audit.md.
```

- [ ] **Step 4: Финальный коммит**

```bash
git add docs/PLAN.md
git commit -m "$(cat <<'EOF'
feat(day17c): SettingsPage — команда + audit-лог (закрыт)

Скоп Day 17C сужен: только управление командой
(список/инвайт/заморозка/разморозка/отзыв) и audit-лог
(50 последних событий). Профиль/смена пароля/2FA UI/сессии
UI оставлены в backlog — бэкенд под них уже готов.

Дополнительно: удалена роль INSPECTOR из всей кодовой базы
(никогда не использовалась в seed/миграциях; ~9 точек).

Бэкенд: 5 новых ручек /auth/admin/* (users, audit-log,
freeze, unfreeze, revoke-invitation) + ужесточение
/admin/invite до ADMIN-only. Доменные инварианты I1-I5
enforce-ятся в AuthService. 14 интеграционных тестов.

Frontend: SettingsPage с двумя секциями (Команда + Audit),
8 unit-тестов SettingsPage + 2 TeamTable. ADMIN видит
кнопки-действия, OPERATOR — read-only.

Чек-листы PLAN.md обновлены.
EOF
)"
```

- [ ] **Step 5: Финальная проверка**

```bash
git log --oneline -20
./gradlew :backend:test -q
cd web-admin && npm test -- --run && cd ..
```

Expected: ~19 коммитов на ветке, оба test-suite зелёные.

- [ ] **Step 6: Опционально — push и PR**

Если работаем через GitHub:

```bash
git push -u origin day17c-settings-team-audit
gh pr create --title "Day 17C: SettingsPage — команда + audit-лог" \
  --body "См. docs/superpowers/specs/2026-05-28-day17c-settings-team-audit-design.md"
```

---

## Self-review checklist (заполняется после prog-а реализации)

- [ ] Все ручки `/auth/admin/*` возвращают корректные HTTP-коды на ВСЕХ инвариантах I1-I6
- [ ] OPERATOR никаким способом не может вызвать freeze/unfreeze/revoke/invite (даже прямым curl-ом)
- [ ] Заморозка реально ревокает refresh-токены (проверяется в smoke step 7)
- [ ] INSPECTOR полностью удалён: `grep -rn "INSPECTOR" backend/ shared/ web-admin/` пусто
- [ ] Audit-лог показывает события из smoke-сценария с корректным `actorEmail`
- [ ] На `/settings` не падает при `role=OPERATOR` (нет ошибок в консоли)
- [ ] `npm test -- --run` зелёный (≥ 10 новых тестов на frontend)
- [ ] `./gradlew :backend:test -q` зелёный (≥ 14 новых тестов на backend + UserRoleEnum)
- [ ] `docs/PLAN.md` обновлён, пометка о Day 17C закрытии есть
