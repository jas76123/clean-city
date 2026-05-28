# Команда: ФИО в инвайт + убрать «Район» — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Сделать ФИО обязательным полем в форме приглашения сотрудника; убрать неинформативную колонку «Район» из таблицы команды и из `TeamMemberDto`.

**Architecture:** Сквозное расширение invite-флоу — `InviteMemberDialog` → `inviteTeamMember(email, fullName, role)` → `POST /auth/admin/invite` → `AuthService.inviteAdmin(... , fullName)` → `users.create(fullName)`. Письмо приглашения персонализируется по имени получателя. Параллельно из `TeamMemberDto` (Kotlin shared + TS) и из `TeamTable.tsx` удаляется поле/колонка `district`; БД-колонка `users.district` остаётся (нужна для рассылки announcements жителям).

**Tech Stack:** Backend — Ktor + Exposed ORM + H2 (test) + kotlin.test. Frontend — React 19 + TypeScript + Vite + Vitest + Testing Library + TanStack Query v5 + shadcn/ui.

**Спека:** `docs/superpowers/specs/2026-05-28-team-fullname-no-district-design.md`

**Рабочая директория:** все пути ниже — от корня репо `~/Desktop/Myapp/cleancity-kmp/`. Backend-команды запускать из корня (`./gradlew ...`); frontend-команды — из `web-admin/` (`npm ...`). Ветка: уже текущая `day17c-settings-team-audit` (follow-up к Day 17C — отдельную ветку не создаём).

---

## Структура файлов

### Backend (Kotlin)

| Файл | Действие | Ответственность |
|---|---|---|
| `shared/src/commonMain/kotlin/com/example/cleancity/shared/requests/auth/AuthRequests.kt` | modify | `AdminInviteRequest`: + `fullName: String` |
| `shared/src/commonMain/kotlin/com/example/cleancity/shared/responses/admin/TeamMemberDto.kt` | modify | удалить `district: String?` |
| `backend/.../auth/AuthService.kt` | modify | + `InvalidFullNameException`; `inviteAdmin` принимает и сохраняет `fullName` |
| `backend/.../email/EmailService.kt` (`EmailTemplates.adminInvite`) | modify | + параметр `recipientName`, использовать в шапке письма |
| `backend/.../auth/UserRepository.kt` | modify | в `toTeamMember`-маппере убрать строку `district = this[Users.district]` |
| `backend/.../auth/AuthRoutes.kt` | modify | `POST /admin/invite` пробрасывает `req.fullName` в сервис |
| `backend/src/test/kotlin/.../auth/AuthSecurityTest.kt` | modify | обновить 3 вызова `service.inviteAdmin` (+ кейс blank fullName) |
| `backend/src/test/kotlin/.../auth/AuthAdminTeamRoutesTest.kt` | modify | JSON-фикстуры с `fullName`; + кейс blank fullName → 400 |

### Frontend (TypeScript / React)

| Файл | Действие | Ответственность |
|---|---|---|
| `web-admin/src/api/types.ts:275-285` | modify | `TeamMemberDto`: убрать `district` |
| `web-admin/src/api/admin.ts:17-22` | modify | `inviteTeamMember(email, fullName, role)`; тело POST с `fullName` |
| `web-admin/src/pages/settings/InviteMemberDialog.tsx` | modify | + поле ФИО (обяз.); `onSubmit(email, fullName, role)` |
| `web-admin/src/pages/settings/InviteMemberDialog.test.tsx` | create | 3 кейса (disabled, submit с fullName, сброс state) |
| `web-admin/src/pages/settings/TeamSection.tsx:73-82, 149-154` | modify | пробросить `fullName` в mutation и в `onSubmit` |
| `web-admin/src/pages/settings/TeamTable.tsx:69, 84` | modify | удалить `<TableHead>Район</TableHead>` и соответствующую `<TableCell>` |
| `web-admin/src/pages/settings/TeamTable.test.tsx:6-16` | modify | убрать `district` из фикстуры; добавить ассерты «нет Района» и «есть ФИО» |

---

## Задачи

### Task 1: `AuthService.inviteAdmin` — принять и сохранить ФИО

**Files:**
- Modify: `backend/src/main/kotlin/com/example/cleancity/auth/AuthService.kt` (строки 28-30, 297-329, validateEmail-блок)
- Modify: `backend/src/test/kotlin/com/example/cleancity/auth/AuthSecurityTest.kt` (строки 256-305)

**Контекст:** `AuthService.inviteAdmin` сейчас принимает `actorId, targetEmail, targetRole, ip, userAgent` и в `users.create()` передаёт `fullName = null`. Стиль кода: валидационные исключения локально в `AuthService.kt`, наследуются от `IllegalArgumentException` (как `WeakPasswordException`, `InvalidEmailException`), маппятся в `Application.kt:85` → `400 BAD_REQUEST`.

- [ ] **Step 1: Добавить failing test в `AuthSecurityTest.kt`**

В конец секции `// ----- Admin invite -----` (после теста `inviteAdmin refuses RESIDENT role`, перед `acceptInvite rejects expired or unknown token`):

```kotlin
@Test
fun `inviteAdmin saves fullName and uses it`() = runBlocking<Unit> {
    val actorId = createAdmin("inviter4@example.com")

    val invited = service.inviteAdmin(
        actorId = actorId,
        targetEmail = "named@example.com",
        targetFullName = "Иван Иванов",
        targetRole = UserRole.OPERATOR,
        ip = null,
        userAgent = null
    )
    val saved = users.findById(invited.id)!!
    assertEquals("Иван Иванов", saved.fullName)
}

@Test
fun `inviteAdmin rejects blank fullName`() = runBlocking<Unit> {
    val actorId = createAdmin("inviter5@example.com")
    assertFailsWith<InvalidFullNameException> {
        service.inviteAdmin(actorId, "x2@example.com", "   ", UserRole.OPERATOR, null, null)
    }
}

@Test
fun `inviteAdmin trims fullName`() = runBlocking<Unit> {
    val actorId = createAdmin("inviter6@example.com")
    val invited = service.inviteAdmin(actorId, "y@example.com", "  Анна Сидорова  ", UserRole.OPERATOR, null, null)
    assertEquals("Анна Сидорова", users.findById(invited.id)!!.fullName)
}
```

Также **обновить 3 существующих вызова** `service.inviteAdmin` в этом файле, добавив `targetFullName` параметром:
- Строка 259-265 (`inviteAdmin creates inactive user...`): добавить `targetFullName = "Test User",` после `targetEmail`.
- Строка 291: `service.inviteAdmin(actorId, "newadmin2@example.com", "Test User", UserRole.ADMIN, null, null)`
- Строка 303: `service.inviteAdmin(actorId, "x@example.com", "Test User", UserRole.RESIDENT, null, null)`

- [ ] **Step 2: Запустить тесты, убедиться что новые фейлят и старые не компилируются**

```bash
./gradlew backend:test --tests 'com.example.cleancity.auth.AuthSecurityTest' -q
```
Expected: компиляция падает с unresolved reference `targetFullName` и `InvalidFullNameException`.

- [ ] **Step 3: Добавить `InvalidFullNameException` в `AuthService.kt`**

В блок объявлений исключений на строке 28-30, после `WeakPasswordException`:

```kotlin
class WeakPasswordException(msg: String) : IllegalArgumentException(msg)
class InvalidEmailException(msg: String = "Invalid email format") : IllegalArgumentException(msg)
class InvalidFullNameException(msg: String = "Full name is required") : IllegalArgumentException(msg)
class EmailAlreadyRegisteredException(msg: String = "Email already registered") : RuntimeException(msg)
```

- [ ] **Step 4: Расширить `inviteAdmin` параметром `fullName` + валидация**

Заменить сигнатуру и тело метода `inviteAdmin` (строки 297-329) на:

```kotlin
suspend fun inviteAdmin(
    actorId: Long,
    targetEmail: String,
    targetFullName: String,
    targetRole: UserRole,
    ip: String?,
    userAgent: String?
): UserResponse {
    if (targetRole == UserRole.RESIDENT) throw IllegalArgumentException("Use registration for residents")
    validateEmail(targetEmail)
    val normalizedName = targetFullName.trim()
    if (normalizedName.isEmpty()) throw InvalidFullNameException()
    val existing = users.findByEmail(targetEmail)
    if (existing != null) throw EmailAlreadyRegisteredException()

    val placeholder = PasswordHasher.hash(java.util.UUID.randomUUID().toString())
    val user = users.create(
        email = targetEmail,
        passwordHash = placeholder,
        role = targetRole,
        fullName = normalizedName,
        isActive = false,
        emailVerified = false,
        mustChangePassword = true
    )

    val token = tokens.createEmailToken(user.id, EmailTokenPurpose.ADMIN_INVITE, INVITE_TOKEN_TTL_SECONDS)
    val link = "$baseUrl/accept-invite?token=$token"
    val invitedBy = users.findById(actorId)?.email ?: "Администратор CleanCity"
    val (subject, html) = EmailTemplates.adminInvite(link, invitedBy, normalizedName)
    email.send(user.email, subject, html)

    audit.log(AuditAction.ADMIN_INVITE_SENT, actorId, "user", user.id.toString(), ip, userAgent, "role=${targetRole.name}")
    return user.toResponse()
}
```

(Обрати внимание: `EmailTemplates.adminInvite` теперь принимает 3-й аргумент `normalizedName` — это обновится в Task 2; пока компиляция упадёт. Это ожидаемо.)

- [ ] **Step 5: Запустить тесты, убедиться что компиляция падает только на `adminInvite`**

```bash
./gradlew backend:compileTestKotlin -q
```
Expected: ошибка `Too many arguments for fun adminInvite(...)`.

- [ ] **Step 6: Закоммитить промежуточное состояние (НЕ закоммитить — фича незаконченная)**

Пропускаем — переходим к Task 2 и коммитим вместе после зелёного backend.

---

### Task 2: `EmailTemplates.adminInvite` — персонализация

**Files:**
- Modify: `backend/src/main/kotlin/com/example/cleancity/email/EmailService.kt:46-58`

**Контекст:** Сейчас `adminInvite(acceptLink: String, invitedBy: String)` возвращает письмо с шапкой "Чистый Город · Сочи · Админ-кабинет" и текстом "$invitedBy пригласил(а) вас...". Добавляем приветствие по имени получателя.

- [ ] **Step 1: Обновить сигнатуру и тело `adminInvite`**

Заменить функцию `adminInvite` (строки 46-58) на:

```kotlin
fun adminInvite(acceptLink: String, invitedBy: String, recipientName: String): Pair<String, String> {
    val subject = "Вас пригласили в админ-кабинет CleanCity"
    val html = """
        <html><body style="font-family:sans-serif;max-width:600px;margin:0 auto;padding:24px">
        <h2 style="color:#0d2b1a">Чистый Город · Сочи · Админ-кабинет</h2>
        <p>Здравствуйте, $recipientName!</p>
        <p>$invitedBy пригласил(а) вас в админ-кабинет CleanCity. Установите пароль, чтобы активировать аккаунт:</p>
        <p><a href="$acceptLink" style="display:inline-block;background:#5DDE8A;color:#0d2b1a;padding:12px 24px;border-radius:8px;text-decoration:none;font-weight:600">Активировать аккаунт</a></p>
        <p style="color:#666;font-size:13px">Или скопируйте ссылку: $acceptLink</p>
        <p style="color:#999;font-size:12px;margin-top:24px">Ссылка действительна 7 дней.</p>
        </body></html>
    """.trimIndent()
    return subject to html
}
```

- [ ] **Step 2: Запустить компиляцию + тесты `AuthSecurityTest`**

```bash
./gradlew backend:test --tests 'com.example.cleancity.auth.AuthSecurityTest' -q
```
Expected: PASS (3 новых invite-теста + 3 обновлённых старых).

- [ ] **Step 3: Закоммитить промежуточно (НЕ коммитим)**

Пропускаем — продолжаем к Task 3.

---

### Task 3: Shared DTO + Route — пробросить `fullName` через HTTP

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/cleancity/shared/requests/auth/AuthRequests.kt:56-60`
- Modify: `backend/src/main/kotlin/com/example/cleancity/auth/AuthRoutes.kt:237-250`
- Modify: `backend/src/test/kotlin/com/example/cleancity/auth/AuthAdminTeamRoutesTest.kt:357-388`

**Контекст:** `AdminInviteRequest` — это DTO в `shared`, который десериализуется из тела `POST /auth/admin/invite`. Сейчас имеет 2 поля: `email`, `role`. Поскольку поле `fullName` будет обязательным, kotlinx.serialization без default-значения будет валить десериализацию запросов без `fullName` → автоматический 400. Это нужно (требование спеки), но также сломает существующие 2 теста в `AuthAdminTeamRoutesTest` (отправляют JSON без `fullName`).

- [ ] **Step 1: Добавить failing-тесты в `AuthAdminTeamRoutesTest.kt`**

В конец файла (после `operator cannot invite`, строка 388, перед `}` класса):

```kotlin
@Test
fun `invite with blank fullName returns 400`() {
    initDb()
    val adminId = seedUser("admin@t.local", UserRole.ADMIN)

    testApplication {
        appWithAuth()
        val resp = client.post("/auth/admin/invite") {
            header(HttpHeaders.Authorization, "Bearer ${bearerFor(adminId, UserRole.ADMIN)}")
            contentType(ContentType.Application.Json)
            setBody("""{"email":"new@t.local","fullName":"   ","role":"OPERATOR"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }
}

@Test
fun `invite with fullName creates pending user with that name`() {
    initDb()
    val adminId = seedUser("admin2@t.local", UserRole.ADMIN)

    testApplication {
        appWithAuth()
        val resp = client.post("/auth/admin/invite") {
            header(HttpHeaders.Authorization, "Bearer ${bearerFor(adminId, UserRole.ADMIN)}")
            contentType(ContentType.Application.Json)
            setBody("""{"email":"named@t.local","fullName":"Пётр Петров","role":"OPERATOR"}""")
        }
        assertEquals(HttpStatusCode.Created, resp.status)
        val invited = transaction {
            Users.selectAll().where { Users.email eq "named@t.local" }.single()
        }
        assertEquals("Пётр Петров", invited[Users.fullName])
    }
}
```

Если в файле ещё нет импортов `Users` / `transaction` / `selectAll` — добавь их в шапку:

```kotlin
import com.example.cleancity.database.tables.Users
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
```

Также **обновить 2 существующих JSON-фикстуры** в этом файле (они без `fullName` будут падать на десериализации):
- Строка 367: `setBody("""{"email":"new@t.local","fullName":"X","role":"INSPECTOR"}""")`
- Строка 383: `setBody("""{"email":"new@t.local","fullName":"X","role":"OPERATOR"}""")`

- [ ] **Step 2: Запустить тесты — убедиться что новые фейлят**

```bash
./gradlew backend:test --tests 'com.example.cleancity.auth.AuthAdminTeamRoutesTest' -q
```
Expected: новые 2 теста фейлят (400 → 500 или unsupported field), либо компиляция fail на route.

- [ ] **Step 3: Расширить `AdminInviteRequest`**

Заменить определение (строки 56-60 в `AuthRequests.kt`) на:

```kotlin
@Serializable
data class AdminInviteRequest(
    val email: String,
    val fullName: String,
    val role: String   // "ADMIN" | "OPERATOR"
)
```

- [ ] **Step 4: Пробросить `fullName` в роуте**

В `AuthRoutes.kt` найти блок `post("/admin/invite") { ... }` (примерно строки 237-251) и заменить вызов сервиса. Контекст блока — `targetRole` уже валидируется выше. Конкретно: внутри `try { ... val invited = service.inviteAdmin(actorId, req.email, targetRole, call.clientIp(), call.userAgentSafe()) ... }` заменить на:

```kotlin
val invited = service.inviteAdmin(
    actorId,
    req.email,
    req.fullName,
    targetRole,
    call.clientIp(),
    call.userAgentSafe()
)
```

- [ ] **Step 5: Запустить тесты — убедиться что зелёные**

```bash
./gradlew backend:test --tests 'com.example.cleancity.auth.AuthAdminTeamRoutesTest' -q
```
Expected: все тесты PASS (включая обновлённые `invite role inspector returns 400`, `operator cannot invite` и 2 новых).

- [ ] **Step 6: Полный backend-suite (на всякий случай)**

```bash
./gradlew backend:test -q
```
Expected: все тесты зелёные. Если что-то ещё ссылается на старый `inviteAdmin` — исправь там же по аналогии (named-параметры или позиционно).

---

### Task 4: Убрать `district` из `TeamMemberDto` и из маппера

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/cleancity/shared/responses/admin/TeamMemberDto.kt:5-16`
- Modify: `backend/src/main/kotlin/com/example/cleancity/auth/UserRepository.kt:~215`

**Контекст:** `TeamMemberDto` сейчас отдаётся в `GET /auth/admin/users` (используется веб-админкой для таблицы команды). Поле `district` для admin/operator всегда null — мёртвый код. БД-колонка `users.district` остаётся (используется в `AnnouncementRepository.recipientIdsForDistricts` для рассылки жителям) — **её не трогаем**.

- [ ] **Step 1: Удалить `district` из DTO**

Заменить `TeamMemberDto.kt` (целиком, строки 1-16) на:

```kotlin
package com.example.cleancity.shared.responses.admin

import kotlinx.serialization.Serializable

@Serializable
data class TeamMemberDto(
    val id: Long,
    val email: String,
    val fullName: String?,
    val role: String,            // "ADMIN" | "OPERATOR"
    val status: TeamStatus,
    val createdAt: String,       // ISO-8601 с offset
    val lastLoginAt: String?,    // null для pending
    val invitedAt: String?       // createdAt для pending, иначе null
)

@Serializable
data class TeamMembersResponse(val items: List<TeamMemberDto>)
```

- [ ] **Step 2: Убрать `district = ...` из маппера в `UserRepository.kt`**

Найти в `UserRepository.kt` строку `district = this[Users.district],` (около 215). Удалить её целиком. Структура конструктора `TeamMemberDto(...)` должна остаться валидной (поле `status` идёт сразу после `role`).

- [ ] **Step 3: Запустить весь backend-suite**

```bash
./gradlew backend:test -q
```
Expected: всё зелёное. Если есть тест, который ассертил `district` в DTO — обнови или удали ассерт.

- [ ] **Step 4: Коммит — бэк готов**

```bash
git add shared/src backend/src
git commit -m "feat(team): инвайт принимает ФИО, DTO команды без district

- AdminInviteRequest +fullName (обяз.); валидация blank → 400.
- AuthService.inviteAdmin сохраняет ФИО, EmailTemplates.adminInvite
  персонализирует приветствие.
- TeamMemberDto без поля district (для admin/operator оно всегда null);
  колонку users.district в БД оставляем — нужна для рассылок жителям.
- Тесты: 3 новых в AuthSecurityTest, 2 новых в AuthAdminTeamRoutesTest,
  существующие фикстуры обновлены."
```

---

### Task 5: `TeamTable.tsx` — убрать колонку «Район»

**Files:**
- Modify: `web-admin/src/pages/settings/TeamTable.test.tsx:6-43`
- Modify: `web-admin/src/pages/settings/TeamTable.tsx:69, 84`
- Modify: `web-admin/src/api/types.ts:275-285`

**Контекст:** `TeamTable` — презентационный компонент таблицы команды. Колонка «Район» (стр. 69, 84 в текущем файле) всегда показывает «Все районы» (`m.district ?? 'Все районы'`). DTO в `types.ts` тоже надо синхронизировать с бэком (Task 4).

- [ ] **Step 1: Обновить `TeamTable.test.tsx` — добавить ассерты для ФИО и отсутствия колонки Район**

Заменить файл целиком на:

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
      />,
    )
    expect(screen.getByText(/нет активных сотрудников/i)).toBeInTheDocument()
  })

  it('renders dash for null lastLoginAt and invitedAt', () => {
    const pending: TeamMemberDto = { ...member, status: 'PENDING', lastLoginAt: null, invitedAt: null }
    render(
      <TeamTable
        status="pending"
        members={[pending]}
        currentRole="ADMIN"
        onAction={() => {}}
      />,
    )
    expect(screen.getByText('—')).toBeInTheDocument()
  })

  it('shows fullName in row', () => {
    render(
      <TeamTable
        status="active"
        members={[member]}
        currentRole="ADMIN"
        onAction={() => {}}
      />,
    )
    expect(screen.getByText('Иван Иванов')).toBeInTheDocument()
  })

  it('does not render district column', () => {
    render(
      <TeamTable
        status="active"
        members={[member]}
        currentRole="ADMIN"
        onAction={() => {}}
      />,
    )
    expect(screen.queryByRole('columnheader', { name: /район/i })).toBeNull()
  })
})
```

- [ ] **Step 2: Запустить тест — убедиться что новые фейлят**

```bash
cd web-admin && npm test -- --run TeamTable.test
```
Expected: «does not render district column» падает (колонка ещё есть); может падать тайп-чек (фикстура без `district`).

- [ ] **Step 3: Убрать `district` из типа `TeamMemberDto` (`web-admin/src/api/types.ts`)**

Заменить определение (строки 275-285) на:

```typescript
export interface TeamMemberDto {
  id: number
  email: string
  fullName: string | null
  role: 'ADMIN' | 'OPERATOR'
  status: TeamStatus
  createdAt: string
  lastLoginAt: string | null
  invitedAt: string | null
}
```

- [ ] **Step 4: Убрать колонку «Район» из `TeamTable.tsx`**

В `TeamTable.tsx` удалить две строки:
- Строка 69: `<TableHead>Район</TableHead>`
- Строка 84: `<TableCell>{m.district ?? 'Все районы'}</TableCell>`

- [ ] **Step 5: Запустить тесты — должны быть зелёные**

```bash
cd web-admin && npm test -- --run TeamTable.test
```
Expected: 4/4 PASS.

---

### Task 6: `InviteMemberDialog` — поле ФИО (обязательное)

**Files:**
- Create: `web-admin/src/pages/settings/InviteMemberDialog.test.tsx`
- Modify: `web-admin/src/pages/settings/InviteMemberDialog.tsx`

**Контекст:** `InviteMemberDialog` — модалка приглашения. Сейчас принимает props `{ open, loading?, onSubmit: (email, role) => void, onCancel }` и имеет поля email + role (Select). Тестов нет (создаём с нуля). Стиль тестов — см. `TeamTable.test.tsx` (vitest + @testing-library/react).

- [ ] **Step 1: Создать failing-test `InviteMemberDialog.test.tsx`**

```tsx
import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { InviteMemberDialog } from './InviteMemberDialog'

describe('InviteMemberDialog', () => {
  it('disables submit while fullName is empty', () => {
    render(
      <InviteMemberDialog
        open
        onSubmit={() => {}}
        onCancel={() => {}}
      />,
    )
    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: 'a@b.c' } })
    const submit = screen.getByRole('button', { name: /пригласить/i })
    expect(submit).toBeDisabled()
  })

  it('disables submit while email is empty', () => {
    render(
      <InviteMemberDialog
        open
        onSubmit={() => {}}
        onCancel={() => {}}
      />,
    )
    fireEvent.change(screen.getByLabelText(/фио/i), { target: { value: 'Иван Иванов' } })
    const submit = screen.getByRole('button', { name: /пригласить/i })
    expect(submit).toBeDisabled()
  })

  it('submits with email, fullName, role', () => {
    const onSubmit = vi.fn()
    render(
      <InviteMemberDialog
        open
        onSubmit={onSubmit}
        onCancel={() => {}}
      />,
    )
    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: 'a@b.c' } })
    fireEvent.change(screen.getByLabelText(/фио/i), { target: { value: '  Иван Иванов  ' } })
    fireEvent.click(screen.getByRole('button', { name: /пригласить/i }))
    expect(onSubmit).toHaveBeenCalledWith('a@b.c', 'Иван Иванов', 'OPERATOR')
  })
})
```

- [ ] **Step 2: Запустить — убедиться что фейлит на компиляции типа**

```bash
cd web-admin && npm test -- --run InviteMemberDialog.test
```
Expected: TS-ошибка или фейл — `onSubmit` сигнатура не та / нет поля «ФИО».

- [ ] **Step 3: Обновить `InviteMemberDialog.tsx` — добавить поле ФИО**

Заменить файл целиком на:

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
  onSubmit: (email: string, fullName: string, role: 'ADMIN' | 'OPERATOR') => void
  onCancel: () => void
}

export function InviteMemberDialog({ open, loading = false, onSubmit, onCancel }: Props) {
  const [email, setEmail] = useState('')
  const [fullName, setFullName] = useState('')
  const [role, setRole] = useState<'ADMIN' | 'OPERATOR'>('OPERATOR')

  const handleSubmit = () => {
    if (!email.trim() || !fullName.trim()) return
    onSubmit(email.trim(), fullName.trim(), role)
  }

  const handleOpenChange = (next: boolean) => {
    if (!next) {
      setEmail('')
      setFullName('')
      setRole('OPERATOR')
      onCancel()
    }
  }

  const canSubmit = email.trim().length > 0 && fullName.trim().length > 0

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
          <div className="flex flex-col gap-1.5">
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
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="invite-fullname">ФИО</Label>
            <Input
              id="invite-fullname"
              type="text"
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              placeholder="Иван Иванов"
              disabled={loading}
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <Label>Роль</Label>
            <Select
              value={role}
              onValueChange={(v) => setRole(v as 'ADMIN' | 'OPERATOR')}
              disabled={loading}
            >
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
          <Button onClick={handleSubmit} disabled={loading || !canSubmit}>
            {loading ? 'Отправляем…' : 'Пригласить'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
```

- [ ] **Step 4: Запустить тест — должно быть зелёное**

```bash
cd web-admin && npm test -- --run InviteMemberDialog.test
```
Expected: 3/3 PASS.

---

### Task 7: `inviteTeamMember` API + `TeamSection.tsx` — пробросить `fullName`

**Files:**
- Modify: `web-admin/src/api/admin.ts:17-22`
- Modify: `web-admin/src/pages/settings/TeamSection.tsx:73-82, 149-154`

**Контекст:** Сейчас `inviteTeamMember(email, role)` отправляет `{ email, role }` в POST. `TeamSection.tsx` оборачивает это в `useMutation` с `vars: { email; role }`. Расширяем сигнатуру на 3 параметра и пробрасываем из `onSubmit` диалога (после Task 6 у диалога уже новая сигнатура с `fullName`).

- [ ] **Step 1: Обновить `web-admin/src/api/admin.ts`**

Заменить функцию `inviteTeamMember` (строки 17-22) на:

```typescript
export async function inviteTeamMember(
  email: string,
  fullName: string,
  role: 'ADMIN' | 'OPERATOR',
): Promise<void> {
  await api.post('/auth/admin/invite', { email, fullName, role })
}
```

- [ ] **Step 2: Обновить `TeamSection.tsx`**

В `web-admin/src/pages/settings/TeamSection.tsx` найти `useMutation` для инвайта (строки 73-82) и заменить тип `vars` и вызов:

```typescript
const inviteMut = useMutation({
  mutationFn: (vars: { email: string; fullName: string; role: 'ADMIN' | 'OPERATOR' }) =>
    inviteTeamMember(vars.email, vars.fullName, vars.role),
  onSuccess: () => {
    toast.success('Приглашение отправлено')
    setInviteOpen(false)
    invalidate()
  },
  onError: (err) => toast.error(extractApiError(err).message),
})
```

И обновить пропс `onSubmit` на `<InviteMemberDialog>` (около строки 152):

```tsx
<InviteMemberDialog
  open={inviteOpen}
  loading={inviteMut.isPending}
  onSubmit={(email, fullName, role) => inviteMut.mutate({ email, fullName, role })}
  onCancel={() => setInviteOpen(false)}
/>
```

- [ ] **Step 3: Прогнать весь фронт-suite + тайпчек**

```bash
cd web-admin && npm test -- --run && npm run typecheck
```
(Если в проекте нет скрипта `typecheck` — `npx tsc --noEmit` из `web-admin/`.)

Expected: всё зелёное. Если ругается на `extractApiError` или прочее — ничего трогать не должно, ошибки только в трёх файлах из Task 5-7.

- [ ] **Step 4: Коммит — фронт готов**

```bash
git add web-admin/src
git commit -m "feat(web-admin): инвайт принимает ФИО, таблица команды без района

- InviteMemberDialog: новое обязательное поле ФИО.
- inviteTeamMember(email, fullName, role); TeamSection пробрасывает.
- TeamTable: убрана колонка «Район» (всегда показывала «Все районы»).
- types.ts: TeamMemberDto без district (синхронно с shared).
- 4 теста TeamTable + 3 новых теста InviteMemberDialog."
```

---

### Task 8: Smoke-проверка на dev-стенде

**Files:** —

**Контекст:** Acceptance criteria из спеки (§ Acceptance criteria) проверяются вручную, т.к. e2e нет. Локальный bootstrap-админ — `admin@cleancity.dev` / `Admin12345!` (DEV-сид V99).

- [ ] **Step 1: Запустить backend и фронт**

В одном терминале:
```bash
docker compose -f compose.dev.yml up -d postgres
./gradlew backend:run
```

В другом:
```bash
cd web-admin && npm run dev
```

- [ ] **Step 2: Зайти в админку и пригласить нового сотрудника**

Открыть `http://localhost:5173`, залогиниться как `admin@cleancity.dev` / `Admin12345!`. Перейти на `/settings`. Нажать «Пригласить сотрудника».

**Проверки в диалоге:**
- Поле «ФИО» отображается между Email и Роль.
- Кнопка «Пригласить» disabled, пока ФИО пустое (даже если email заполнен).
- Кнопка «Пригласить» disabled, пока email пустой.

Ввести `test1@local`, ФИО `Тест Тестов`, роль OPERATOR. Нажать «Пригласить».

- [ ] **Step 3: Проверить таблицу и email**

- В вкладке «Ожидают» новая строка: Email `test1@local`, ФИО **«Тест Тестов»**, роль «Оператор».
- В таблице **нет** колонки «Район» ни в одной вкладке (Активные/Замороженные/Ожидают).
- В логах backend в письме приглашения видно: `<p>Здравствуйте, Тест Тестов!</p>`.

- [ ] **Step 4: Проверить bootstrap-админа (обратная совместимость)**

Открыть вкладку «Активные». В строке `admin@cleancity.dev` в колонке «ФИО» — `—` (не падает, не ломается).

- [ ] **Step 5: Проверить 400 на blank fullName (через curl)**

```bash
TOKEN=$(curl -s -X POST localhost:8080/auth/login -H 'content-type: application/json' \
  -d '{"email":"admin@cleancity.dev","password":"Admin12345!"}' | jq -r .accessToken)

curl -s -o /dev/null -w "%{http_code}\n" -X POST localhost:8080/auth/admin/invite \
  -H "authorization: Bearer $TOKEN" \
  -H 'content-type: application/json' \
  -d '{"email":"x@local","fullName":"   ","role":"OPERATOR"}'
```
Expected: `400`.

- [ ] **Step 6: Проверить рассылку announcements жителям (smoke — что не сломали)**

Если в DEV-сиде есть житель с заполненным `district` — создать announcement в админке с фильтром по этому району и убедиться что житель видит/получает уведомление. Если жителя нет — пропустить, отметить пропуск в коммите Task 9. (БД-колонку не трогали, риск регрессии — низкий, но smoke желателен.)

- [ ] **Step 7: Финальный коммит (только если что-то поправлено в smoke)**

Если по результатам smoke нашлись дефекты — пофиксить, добавить тест, закоммитить отдельным коммитом. Если всё ок — пропускаем.

---

### Task 9: Обновить документацию

**Files:**
- Modify: `docs/PLAN.md` (если в нём есть пункт 17C/follow-up)

**Контекст:** В `docs/PLAN.md` ведётся общий план Day 17C. Этот follow-up закрывает доработку «команда — ФИО + без района».

- [ ] **Step 1: Найти и дополнить запись 17C в `docs/PLAN.md`**

```bash
grep -n "17[Cc]\|Settings\|команд" docs/PLAN.md | head -20
```

Найти раздел Day 17C, добавить под ним пункт-чекбокс:

```markdown
- [x] Follow-up 2026-05-28: ФИО обязательным полем в инвайте, колонка «Район» удалена из таблицы команды и `TeamMemberDto` (БД-колонка остаётся для рассылок). План: `docs/superpowers/plans/2026-05-28-team-fullname-no-district.md`.
```

- [ ] **Step 2: Финальный коммит**

```bash
git add docs/PLAN.md
git commit -m "docs(plan): отметить follow-up по полю ФИО и колонке района"
```

- [ ] **Step 3: Финальный полный прогон тестов перед мёрджем**

```bash
./gradlew backend:test -q
cd web-admin && npm test -- --run
```

Expected: всё зелёное на обоих суитах.

---

## Definition of Done

- [ ] Backend-suite зелёный (`./gradlew backend:test`).
- [ ] Frontend-suite зелёный (`cd web-admin && npm test -- --run`).
- [ ] Все 6 acceptance criteria из спеки § Acceptance criteria закрыты smoke-прогоном (Task 8).
- [ ] 4 коммита на ветке `day17c-settings-team-audit`:
  1. `feat(team): инвайт принимает ФИО, DTO команды без district`
  2. `feat(web-admin): инвайт принимает ФИО, таблица команды без района`
  3. (опц.) фиксы по результатам smoke
  4. `docs(plan): отметить follow-up по полю ФИО и колонке района`
- [ ] `docs/PLAN.md` обновлён.
