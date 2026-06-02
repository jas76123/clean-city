# Resident Moderation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Дать сотрудникам (ADMIN/OPERATOR) инструмент модерации жителей — авто-флаг по 3 отклонённым жалобам, ручное предупреждение и бан/разбан прямо на детали жалобы, всё в audit_log.

**Architecture:** Новый изолированный модуль `moderation` на backend (`ModerationService` + `ModerationRoutes`), который композирует существующие репозитории (`UserRepository`, `ComplaintRepository`, `TokenRepository`) и `NotificationService`/`AuditLogger`. Подсчёт отклонений и флаг считает backend и отдаёт сводку отдельным endpoint'ом; web-admin показывает её и кнопки на панели детали жалобы.

**Tech Stack:** Kotlin/Ktor, Exposed ORM, PostgreSQL + Flyway (прод) / H2 (тесты), React + TypeScript + TanStack Query + Vitest/Testing-Library (web-admin).

**Источник правды (spec):** `docs/superpowers/specs/2026-06-02-resident-moderation-design.md`

**Отклонение от spec (осознанное):** spec предлагал «расширить ответ детали жалобы» полями модерации. План вместо этого вводит отдельный endpoint `GET /auth/admin/residents/{id}/moderation`. Причина — декаплинг: не тянуть `UserRepository` и логику модерации в `ComplaintService`. Поведение и UX идентичны.

**Замечание про DB CHECK-констрейнт:** юнит-тесты идут на H2 через `SchemaUtils.create` (без Flyway и без CHECK-констрейнтов notifications). Инвариант «warning всегда привязан к жалобе» обеспечивается в коде (`ModerationService.warn` всегда передаёт `complaintId`) и тестируется на уровне сервиса. Сам CHECK в Postgres — defense-in-depth, проверяется вручную в финальной задаче.

---

## File Structure

**Backend (создать):**
- `backend/src/main/resources/db/migration/V9__resident_moderation.sql` — колонка `users.warned_at` + расширение CHECK-констрейнтов notifications.
- `backend/src/main/kotlin/com/example/cleancity/moderation/ModerationService.kt` — бизнес-логика (summary/warn/ban/unban + исключения).
- `backend/src/main/kotlin/com/example/cleancity/moderation/ModerationRoutes.kt` — 4 HTTP-endpoint'а под `/auth/admin/residents/...`.
- `shared/src/commonMain/kotlin/com/example/cleancity/shared/responses/admin/Moderation.kt` — DTO (запросы/ответ).

**Backend (изменить):**
- `backend/src/main/kotlin/com/example/cleancity/database/tables/Users.kt` — добавить `warnedAt`.
- `backend/src/main/kotlin/com/example/cleancity/database/tables/AuditLog.kt` — 3 значения `AuditAction`.
- `shared/src/commonMain/kotlin/com/example/cleancity/shared/models/NotificationKind.kt` — `MODERATION_WARNING`.
- `backend/src/main/kotlin/com/example/cleancity/auth/UserRepository.kt` — `setWarnedAt`, `getWarnedAt`.
- `backend/src/main/kotlin/com/example/cleancity/complaints/ComplaintRepository.kt` — `countRejectedSince`.
- `backend/src/main/kotlin/com/example/cleancity/ApiExceptions.kt` — новые коды ошибок.
- `backend/src/main/kotlin/com/example/cleancity/Application.kt` — конструирование `ModerationService` + регистрация роутов.

**Backend (тесты):**
- `backend/src/test/kotlin/com/example/cleancity/complaints/CountRejectedSinceTest.kt`
- `backend/src/test/kotlin/com/example/cleancity/moderation/ModerationServiceTest.kt`
- `backend/src/test/kotlin/com/example/cleancity/moderation/ModerationRoutesTest.kt`

**Web-admin (создать):**
- `web-admin/src/api/moderation.ts` — API-клиент.
- `web-admin/src/hooks/moderationQueries.ts` — TanStack-хуки.
- `web-admin/src/components/complaints/ModerationPanel.tsx` — UI (бейдж + кнопки + модалка причины).
- `web-admin/src/components/complaints/ModerationPanel.test.tsx`

**Web-admin (изменить):**
- `web-admin/src/api/types.ts` — типы сводки/запросов.
- `web-admin/src/components/complaints/ComplaintDetailPanel.tsx` — вставить `<ModerationPanel/>`.

---

## Task 1: Foundation — миграция, колонка, enum'ы

**Files:**
- Create: `backend/src/main/resources/db/migration/V9__resident_moderation.sql`
- Modify: `backend/src/main/kotlin/com/example/cleancity/database/tables/Users.kt`
- Modify: `backend/src/main/kotlin/com/example/cleancity/database/tables/AuditLog.kt`
- Modify: `shared/src/commonMain/kotlin/com/example/cleancity/shared/models/NotificationKind.kt`

- [ ] **Step 1: Написать миграцию V9**

Создать `backend/src/main/resources/db/migration/V9__resident_moderation.sql`:

```sql
-- Модерация жителей: дата последнего предупреждения (для обнуления счётчика
-- отклонений) + новый вид уведомления MODERATION_WARNING.
ALTER TABLE users ADD COLUMN warned_at TIMESTAMPTZ;

-- Расширяем CHECK-констрейнты notifications под новый kind.
-- MODERATION_WARNING привязан к жалобе-нарушению (complaint_id NOT NULL).
ALTER TABLE notifications DROP CONSTRAINT notifications_kind_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_kind_check
    CHECK (kind IN ('COMPLAINT_STATUS', 'ANNOUNCEMENT', 'MODERATION_WARNING'));

ALTER TABLE notifications DROP CONSTRAINT notifications_target_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_target_check CHECK (
    (kind = 'COMPLAINT_STATUS'   AND complaint_id IS NOT NULL AND announcement_id IS NULL)
    OR (kind = 'ANNOUNCEMENT'        AND announcement_id IS NOT NULL AND complaint_id IS NULL)
    OR (kind = 'MODERATION_WARNING'  AND complaint_id IS NOT NULL AND announcement_id IS NULL)
);
```

- [ ] **Step 2: Добавить колонку в Exposed-таблицу Users**

В `Users.kt` после строки `val acceptedTermsVersion = ...` добавить:

```kotlin
    val warnedAt = timestampWithTimeZone("warned_at").nullable()
```

- [ ] **Step 3: Добавить значения AuditAction**

В `AuditLog.kt` в `enum class AuditAction`, после `ADMIN_INVITE_REVOKED`, добавить:

```kotlin
    RESIDENT_WARNED,
    RESIDENT_BANNED,
    RESIDENT_UNBANNED
```

(не забыть запятую после `ADMIN_INVITE_REVOKED`)

- [ ] **Step 4: Добавить вид уведомления**

В `NotificationKind.kt` в enum добавить:

```kotlin
    COMPLAINT_STATUS,
    ANNOUNCEMENT,
    MODERATION_WARNING
```

- [ ] **Step 5: Проверить компиляцию**

Run: `./gradlew :backend:compileKotlin :shared:compileKotlinJvm`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/db/migration/V9__resident_moderation.sql \
        backend/src/main/kotlin/com/example/cleancity/database/tables/Users.kt \
        backend/src/main/kotlin/com/example/cleancity/database/tables/AuditLog.kt \
        shared/src/commonMain/kotlin/com/example/cleancity/shared/models/NotificationKind.kt
git commit -m "feat(moderation): schema + enums (warned_at, AuditAction, MODERATION_WARNING)"
```

---

## Task 2: ComplaintRepository.countRejectedSince

**Files:**
- Modify: `backend/src/main/kotlin/com/example/cleancity/complaints/ComplaintRepository.kt`
- Test: `backend/src/test/kotlin/com/example/cleancity/complaints/CountRejectedSinceTest.kt`

Считает число РАЗНЫХ жалоб автора, у которых есть переход в `REJECTED` (`status_changes.to_status='REJECTED'`) с `created_at > since`. `DUPLICATE` сюда не попадает по определению. `since = null` → без фильтра по времени.

- [ ] **Step 1: Написать падающий тест**

Создать `backend/src/test/kotlin/com/example/cleancity/complaints/CountRejectedSinceTest.kt`:

```kotlin
package com.example.cleancity.complaints

import com.example.cleancity.database.tables.Complaints
import com.example.cleancity.database.tables.StatusChanges
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

class CountRejectedSinceTest {

    private fun initDb() {
        Database.connect(
            "jdbc:h2:mem:cnt-rej-${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.drop(StatusChanges, Complaints, Users)
            SchemaUtils.create(Users, Complaints, StatusChanges)
        }
    }

    private fun seedResident(): Long = transaction {
        Users.insert {
            it[Users.email] = "r@test.local"
            it[Users.passwordHash] = "x"
            it[Users.role] = UserRole.RESIDENT.name
            it[Users.isActive] = true
            it[Users.emailVerified] = true
            it[Users.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
            it[Users.passwordChangedAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }[Users.id]
    }

    private fun seedComplaint(authorId: Long): Long = transaction {
        Complaints.insert {
            it[Complaints.authorId] = authorId
            it[Complaints.category] = "GARBAGE"
            it[Complaints.title] = "t"
            it[Complaints.description] = "d"
            it[Complaints.latitude] = 43.6
            it[Complaints.longitude] = 39.7
            it[Complaints.address] = "addr"
            it[Complaints.status] = "NEW"
            it[Complaints.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
            it[Complaints.updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }[Complaints.id]
    }

    private fun seedStatusChange(complaintId: Long, to: String, at: OffsetDateTime, actorId: Long) = transaction {
        StatusChanges.insert {
            it[StatusChanges.complaintId] = complaintId
            it[StatusChanges.fromStatus] = "NEW"
            it[StatusChanges.toStatus] = to
            it[StatusChanges.comment] = "c"
            it[StatusChanges.changedById] = actorId
            it[StatusChanges.createdAt] = at
        }
    }

    @Test
    fun `counts only REJECTED, ignores DUPLICATE, dedups per complaint`() {
        initDb()
        val resident = seedResident()
        val now = OffsetDateTime.now(ZoneOffset.UTC)

        val c1 = seedComplaint(resident)
        val c2 = seedComplaint(resident)
        val c3 = seedComplaint(resident)
        seedStatusChange(c1, "REJECTED", now, resident)
        seedStatusChange(c2, "REJECTED", now, resident)
        seedStatusChange(c2, "REJECTED", now, resident) // дубль перехода той же жалобы
        seedStatusChange(c3, "DUPLICATE", now, resident) // не считается

        val repo = ComplaintRepository()
        assertEquals(2, repo.countRejectedSince(resident, null))
    }

    @Test
    fun `since filter excludes older rejections`() {
        initDb()
        val resident = seedResident()
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val c1 = seedComplaint(resident)
        val c2 = seedComplaint(resident)
        seedStatusChange(c1, "REJECTED", now.minusDays(10), resident) // до предупреждения
        seedStatusChange(c2, "REJECTED", now, resident)               // после

        val repo = ComplaintRepository()
        assertEquals(1, repo.countRejectedSince(resident, now.minusDays(1)))
    }
}
```

- [ ] **Step 2: Запустить — убедиться, что не компилируется/падает**

Run: `./gradlew :backend:test --tests "com.example.cleancity.complaints.CountRejectedSinceTest"`
Expected: FAIL (метод `countRejectedSince` не существует)

- [ ] **Step 3: Реализовать метод**

В `ComplaintRepository.kt` добавить метод (внутри класса). Убедиться, что в импортах есть `org.jetbrains.exposed.sql.JoinType`, `org.jetbrains.exposed.sql.and`, `org.jetbrains.exposed.sql.selectAll`, `com.example.cleancity.shared.models.ComplaintStatus`, `java.time.OffsetDateTime` — добавить недостающие:

```kotlin
    /**
     * Число РАЗНЫХ жалоб автора с переходом в REJECTED после [since]
     * (null = за всё время). DUPLICATE не учитывается.
     */
    fun countRejectedSince(authorId: Long, since: OffsetDateTime?): Int = transaction {
        StatusChanges
            .join(Complaints, JoinType.INNER, onColumn = StatusChanges.complaintId, otherColumn = Complaints.id)
            .selectAll()
            .where {
                val base = (Complaints.authorId eq authorId) and
                    (StatusChanges.toStatus eq ComplaintStatus.REJECTED.name)
                if (since != null) base and (StatusChanges.createdAt greater since) else base
            }
            .map { it[StatusChanges.complaintId] }
            .distinct()
            .size
    }
```

- [ ] **Step 4: Запустить тест — должен пройти**

Run: `./gradlew :backend:test --tests "com.example.cleancity.complaints.CountRejectedSinceTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/example/cleancity/complaints/ComplaintRepository.kt \
        backend/src/test/kotlin/com/example/cleancity/complaints/CountRejectedSinceTest.kt
git commit -m "feat(moderation): ComplaintRepository.countRejectedSince"
```

---

## Task 3: UserRepository.setWarnedAt / getWarnedAt

**Files:**
- Modify: `backend/src/main/kotlin/com/example/cleancity/auth/UserRepository.kt`
- Test: `backend/src/test/kotlin/com/example/cleancity/auth/WarnedAtTest.kt`

- [ ] **Step 1: Написать падающий тест**

Создать `backend/src/test/kotlin/com/example/cleancity/auth/WarnedAtTest.kt`:

```kotlin
package com.example.cleancity.auth

import com.example.cleancity.database.tables.Users
import com.example.cleancity.shared.models.UserRole
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WarnedAtTest {

    private fun initDb() {
        Database.connect(
            "jdbc:h2:mem:warned-${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.drop(Users)
            SchemaUtils.create(Users)
        }
    }

    private fun seedUser(): Long = transaction {
        Users.insert {
            it[Users.email] = "r@test.local"
            it[Users.passwordHash] = "x"
            it[Users.role] = UserRole.RESIDENT.name
            it[Users.isActive] = true
            it[Users.emailVerified] = true
            it[Users.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
            it[Users.passwordChangedAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }[Users.id]
    }

    @Test
    fun `warned_at is null by default and set after setWarnedAt`() {
        initDb()
        val id = seedUser()
        val repo = UserRepository()
        assertNull(repo.getWarnedAt(id))

        repo.setWarnedAt(id)
        assertNotNull(repo.getWarnedAt(id))
    }
}
```

- [ ] **Step 2: Запустить — должен упасть**

Run: `./gradlew :backend:test --tests "com.example.cleancity.auth.WarnedAtTest"`
Expected: FAIL (методов нет)

- [ ] **Step 3: Реализовать методы**

В `UserRepository.kt` добавить (рядом с `setActive`):

```kotlin
    fun setWarnedAt(userId: Long, at: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)) = transaction {
        Users.update({ Users.id eq userId }) { it[Users.warnedAt] = at }
    }

    fun getWarnedAt(userId: Long): OffsetDateTime? = transaction {
        Users.selectAll().where { Users.id eq userId }.firstOrNull()?.get(Users.warnedAt)
    }
```

- [ ] **Step 4: Запустить — должен пройти**

Run: `./gradlew :backend:test --tests "com.example.cleancity.auth.WarnedAtTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/example/cleancity/auth/UserRepository.kt \
        backend/src/test/kotlin/com/example/cleancity/auth/WarnedAtTest.kt
git commit -m "feat(moderation): UserRepository setWarnedAt/getWarnedAt"
```

---

## Task 4: ModerationService — каркас + getSummary

**Files:**
- Create: `backend/src/main/kotlin/com/example/cleancity/moderation/ModerationService.kt`
- Test: `backend/src/test/kotlin/com/example/cleancity/moderation/ModerationServiceTest.kt`

`ModerationServiceTest` будет наполняться в Task 4–6. Для общих фикстур (init H2, seed резидента/сотрудника/жалоб) используется хелпер-класс в этом же файле.

- [ ] **Step 1: Написать падающий тест getSummary**

Создать `backend/src/test/kotlin/com/example/cleancity/moderation/ModerationServiceTest.kt`:

```kotlin
package com.example.cleancity.moderation

import com.example.cleancity.auth.TokenRepository
import com.example.cleancity.auth.UserRepository
import com.example.cleancity.complaints.ComplaintRepository
import com.example.cleancity.database.tables.Complaints
import com.example.cleancity.database.tables.EmailTokens
import com.example.cleancity.database.tables.Notifications
import com.example.cleancity.database.tables.RefreshTokens
import com.example.cleancity.database.tables.StatusChanges
import com.example.cleancity.database.tables.Users
import com.example.cleancity.notifications.NotificationRepository
import com.example.cleancity.notifications.DbNotificationService
import com.example.cleancity.shared.models.UserRole
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModerationServiceTest {

    private fun initDb() {
        Database.connect(
            "jdbc:h2:mem:mod-${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.drop(Notifications, StatusChanges, Complaints, RefreshTokens, EmailTokens, Users)
            SchemaUtils.create(Users, EmailTokens, RefreshTokens, Complaints, StatusChanges, Notifications)
        }
    }

    private fun service(): ModerationService =
        ModerationService(
            users = UserRepository(),
            complaints = ComplaintRepository(),
            tokens = TokenRepository(),
            notifications = DbNotificationService(NotificationRepository()),
        )

    private fun seedUser(role: UserRole, active: Boolean = true): Long = transaction {
        Users.insert {
            it[Users.email] = "u${System.nanoTime()}@test.local"
            it[Users.passwordHash] = "x"
            it[Users.role] = role.name
            it[Users.isActive] = active
            it[Users.emailVerified] = true
            it[Users.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
            it[Users.passwordChangedAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }[Users.id]
    }

    private fun seedRejectedComplaints(authorId: Long, count: Int, at: OffsetDateTime) = transaction {
        repeat(count) {
            val cid = Complaints.insert {
                it[Complaints.authorId] = authorId
                it[Complaints.category] = "GARBAGE"
                it[Complaints.title] = "t"
                it[Complaints.description] = "d"
                it[Complaints.latitude] = 43.6
                it[Complaints.longitude] = 39.7
                it[Complaints.address] = "addr"
                it[Complaints.status] = "REJECTED"
                it[Complaints.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
                it[Complaints.updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
            }[Complaints.id]
            StatusChanges.insert {
                it[StatusChanges.complaintId] = cid
                it[StatusChanges.fromStatus] = "NEW"
                it[StatusChanges.toStatus] = "REJECTED"
                it[StatusChanges.comment] = "c"
                it[StatusChanges.changedById] = authorId
                it[StatusChanges.createdAt] = at
            }
        }
    }

    @Test
    fun `summary flags resident at threshold of 3`() {
        initDb()
        val svc = service()
        val resident = seedUser(UserRole.RESIDENT)
        seedRejectedComplaints(resident, 3, OffsetDateTime.now(ZoneOffset.UTC))

        val summary = svc.getSummary(resident)
        assertEquals(3, summary.rejectedCountSinceWarning)
        assertTrue(summary.flagged)
        assertFalse(summary.isWarned)
        assertFalse(summary.isBanned)
    }

    @Test
    fun `summary not flagged below threshold`() {
        initDb()
        val svc = service()
        val resident = seedUser(UserRole.RESIDENT)
        seedRejectedComplaints(resident, 2, OffsetDateTime.now(ZoneOffset.UTC))

        val summary = svc.getSummary(resident)
        assertEquals(2, summary.rejectedCountSinceWarning)
        assertFalse(summary.flagged)
    }
}
```

- [ ] **Step 2: Запустить — должен упасть**

Run: `./gradlew :backend:test --tests "com.example.cleancity.moderation.ModerationServiceTest"`
Expected: FAIL (нет класса `ModerationService`)

- [ ] **Step 3: Реализовать ModerationService с getSummary**

Создать `backend/src/main/kotlin/com/example/cleancity/moderation/ModerationService.kt`:

```kotlin
package com.example.cleancity.moderation

import com.example.cleancity.auth.TokenRepository
import com.example.cleancity.auth.UserRepository
import com.example.cleancity.auth.UserRow
import com.example.cleancity.complaints.ComplaintRepository
import com.example.cleancity.auth.AuditLogger
import com.example.cleancity.auth.NoopAuditLogger
import com.example.cleancity.database.tables.AuditAction
import com.example.cleancity.notifications.NotificationService
import com.example.cleancity.shared.models.NotificationKind
import com.example.cleancity.shared.models.UserRole

class ResidentNotFoundException : RuntimeException()
class NotAResidentException : RuntimeException()
class ReasonRequiredException : RuntimeException()

data class ModerationSummary(
    val rejectedCountSinceWarning: Int,
    val flagged: Boolean,
    val isWarned: Boolean,
    val isBanned: Boolean,
)

class ModerationService(
    private val users: UserRepository,
    private val complaints: ComplaintRepository,
    private val tokens: TokenRepository,
    private val notifications: NotificationService,
    private val audit: AuditLogger = NoopAuditLogger,
) {
    companion object {
        const val REJECTED_FLAG_THRESHOLD = 3
    }

    fun getSummary(residentId: Long): ModerationSummary {
        val user = users.findById(residentId) ?: throw ResidentNotFoundException()
        val warnedAt = users.getWarnedAt(residentId)
        val count = complaints.countRejectedSince(residentId, warnedAt)
        return ModerationSummary(
            rejectedCountSinceWarning = count,
            flagged = count >= REJECTED_FLAG_THRESHOLD,
            isWarned = warnedAt != null,
            isBanned = !user.isActive,
        )
    }

    private fun requireResident(residentId: Long): UserRow {
        val user = users.findById(residentId) ?: throw ResidentNotFoundException()
        if (user.role != UserRole.RESIDENT) throw NotAResidentException()
        return user
    }
}
```

- [ ] **Step 4: Запустить — должен пройти**

Run: `./gradlew :backend:test --tests "com.example.cleancity.moderation.ModerationServiceTest"`
Expected: PASS (2 теста)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/example/cleancity/moderation/ModerationService.kt \
        backend/src/test/kotlin/com/example/cleancity/moderation/ModerationServiceTest.kt
git commit -m "feat(moderation): ModerationService.getSummary + flag threshold"
```

---

## Task 5: ModerationService.warn

**Files:**
- Modify: `backend/src/main/kotlin/com/example/cleancity/moderation/ModerationService.kt`
- Modify: `backend/src/test/kotlin/com/example/cleancity/moderation/ModerationServiceTest.kt`

`warn` шлёт уведомление `MODERATION_WARNING` (привязанное к жалобе), ставит `warned_at`, пишет аудит, и тем самым обнуляет счётчик (отклонения до предупреждения больше не считаются).

- [ ] **Step 1: Дописать падающие тесты warn**

Добавить в `ModerationServiceTest.kt` (импорты `com.example.cleancity.database.tables.AuditLog`, `org.jetbrains.exposed.sql.selectAll`, `kotlin.test.assertFailsWith`):

```kotlin
    @Test
    fun `warn sends MODERATION_WARNING notification, sets warned_at, resets count, audits`() {
        initDb()
        val svc = service()
        val admin = seedUser(UserRole.ADMIN)
        val resident = seedUser(UserRole.RESIDENT)
        val before = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1)
        seedRejectedComplaints(resident, 3, before)

        // создать жалобу-нарушение для привязки уведомления
        val complaintId = transaction {
            Complaints.insert {
                it[Complaints.authorId] = resident
                it[Complaints.category] = "GARBAGE"
                it[Complaints.title] = "bad"
                it[Complaints.description] = "d"
                it[Complaints.latitude] = 43.6
                it[Complaints.longitude] = 39.7
                it[Complaints.address] = "addr"
                it[Complaints.status] = "NEW"
                it[Complaints.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
                it[Complaints.updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
            }[Complaints.id]
        }

        svc.warn(admin, resident, complaintId, "Не оскорбляйте", "1.1.1.1", "UA")

        // уведомление создано нужного вида и привязано к жалобе
        val notif = transaction {
            Notifications.selectAll().where { Notifications.userId eq resident }.single()
        }
        assertEquals(NotificationKind.MODERATION_WARNING.name, notif[Notifications.kind])
        assertEquals(complaintId, notif[Notifications.complaintId])

        // счётчик обнулён: старые отклонения (до warned_at) больше не считаются
        val summary = svc.getSummary(resident)
        assertTrue(summary.isWarned)
        assertEquals(0, summary.rejectedCountSinceWarning)
        assertFalse(summary.flagged)

        // аудит записан
        val audited = transaction {
            AuditLog.selectAll().where { AuditLog.action eq "RESIDENT_WARNED" }.count()
        }
        assertEquals(1L, audited)
    }

    @Test
    fun `warn on non-resident throws`() {
        initDb()
        val svc = service()
        val admin = seedUser(UserRole.ADMIN)
        val operator = seedUser(UserRole.OPERATOR)
        assertFailsWith<NotAResidentException> {
            svc.warn(admin, operator, 1L, "x", null, null)
        }
    }

    @Test
    fun `warn with blank reason throws`() {
        initDb()
        val svc = service()
        val admin = seedUser(UserRole.ADMIN)
        val resident = seedUser(UserRole.RESIDENT)
        assertFailsWith<ReasonRequiredException> {
            svc.warn(admin, resident, 1L, "   ", null, null)
        }
    }
```

- [ ] **Step 2: Запустить — должны упасть**

Run: `./gradlew :backend:test --tests "com.example.cleancity.moderation.ModerationServiceTest"`
Expected: FAIL (нет метода `warn`)

- [ ] **Step 3: Реализовать warn**

Добавить метод в `ModerationService` (после `getSummary`):

```kotlin
    fun warn(actorId: Long, residentId: Long, complaintId: Long, reason: String, ip: String?, ua: String?) {
        val target = requireResident(residentId)
        val cleanReason = reason.trim()
        if (cleanReason.isEmpty()) throw ReasonRequiredException()
        notifications.notify(
            recipientUserIds = listOf(target.id),
            kind = NotificationKind.MODERATION_WARNING,
            title = "Предупреждение модерации",
            body = cleanReason,
            iconStyle = "WARNING",
            complaintId = complaintId,
        )
        users.setWarnedAt(residentId)
        audit.log(AuditAction.RESIDENT_WARNED, actorId, "user", residentId.toString(), ip, ua, cleanReason)
    }
```

Примечание: проверка `requireResident` выполняется ДО проверки причины — поэтому тест `warn on non-resident` (operator) бросит `NotAResidentException` даже с непустой причиной. Тест `blank reason` использует резидента, поэтому дойдёт до проверки причины.

- [ ] **Step 4: Запустить — должны пройти**

Run: `./gradlew :backend:test --tests "com.example.cleancity.moderation.ModerationServiceTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/example/cleancity/moderation/ModerationService.kt \
        backend/src/test/kotlin/com/example/cleancity/moderation/ModerationServiceTest.kt
git commit -m "feat(moderation): ModerationService.warn (notify + warned_at + audit)"
```

---

## Task 6: ModerationService.ban / unban

**Files:**
- Modify: `backend/src/main/kotlin/com/example/cleancity/moderation/ModerationService.kt`
- Modify: `backend/src/test/kotlin/com/example/cleancity/moderation/ModerationServiceTest.kt`

`ban` ставит `is_active=false` + отзывает все refresh-токены + аудит. `unban` возвращает `is_active=true` + аудит.

- [ ] **Step 1: Дописать падающие тесты ban/unban**

Добавить в `ModerationServiceTest.kt` (импорт `com.example.cleancity.database.tables.RefreshTokens`):

```kotlin
    @Test
    fun `ban deactivates resident, revokes refresh tokens, audits`() {
        initDb()
        val svc = service()
        val admin = seedUser(UserRole.ADMIN)
        val resident = seedUser(UserRole.RESIDENT)
        // активный refresh-токен жителя
        TokenRepository().createRefreshToken(resident, "raw-token", null, null, 3600)

        svc.ban(admin, resident, "Спам 18+", "1.1.1.1", "UA")

        val summary = svc.getSummary(resident)
        assertTrue(summary.isBanned)

        val activeTokens = TokenRepository().listActiveRefreshTokens(resident)
        assertTrue(activeTokens.isEmpty())

        val audited = transaction {
            AuditLog.selectAll().where { AuditLog.action eq "RESIDENT_BANNED" }.count()
        }
        assertEquals(1L, audited)
    }

    @Test
    fun `unban reactivates resident and audits`() {
        initDb()
        val svc = service()
        val admin = seedUser(UserRole.ADMIN)
        val resident = seedUser(UserRole.RESIDENT, active = false)

        svc.unban(admin, resident, "1.1.1.1", "UA")

        assertFalse(svc.getSummary(resident).isBanned)
        val audited = transaction {
            AuditLog.selectAll().where { AuditLog.action eq "RESIDENT_UNBANNED" }.count()
        }
        assertEquals(1L, audited)
    }

    @Test
    fun `ban with blank reason throws`() {
        initDb()
        val svc = service()
        val admin = seedUser(UserRole.ADMIN)
        val resident = seedUser(UserRole.RESIDENT)
        assertFailsWith<ReasonRequiredException> {
            svc.ban(admin, resident, "", null, null)
        }
    }
```

- [ ] **Step 2: Запустить — должны упасть**

Run: `./gradlew :backend:test --tests "com.example.cleancity.moderation.ModerationServiceTest"`
Expected: FAIL (нет методов `ban`/`unban`)

- [ ] **Step 3: Реализовать ban/unban**

Добавить в `ModerationService`:

```kotlin
    fun ban(actorId: Long, residentId: Long, reason: String, ip: String?, ua: String?) {
        requireResident(residentId)
        val cleanReason = reason.trim()
        if (cleanReason.isEmpty()) throw ReasonRequiredException()
        users.setActive(residentId, false)
        tokens.revokeAllUserRefreshTokens(residentId)
        audit.log(AuditAction.RESIDENT_BANNED, actorId, "user", residentId.toString(), ip, ua, cleanReason)
    }

    fun unban(actorId: Long, residentId: Long, ip: String?, ua: String?) {
        requireResident(residentId)
        users.setActive(residentId, true)
        audit.log(AuditAction.RESIDENT_UNBANNED, actorId, "user", residentId.toString(), ip, ua)
    }
```

- [ ] **Step 4: Запустить весь ModerationServiceTest — должен пройти**

Run: `./gradlew :backend:test --tests "com.example.cleancity.moderation.ModerationServiceTest"`
Expected: PASS (все тесты)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/example/cleancity/moderation/ModerationService.kt \
        backend/src/test/kotlin/com/example/cleancity/moderation/ModerationServiceTest.kt
git commit -m "feat(moderation): ModerationService.ban/unban (deactivate + revoke + audit)"
```

---

## Task 7: DTO + HTTP-роуты + wiring

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/cleancity/shared/responses/admin/Moderation.kt`
- Modify: `backend/src/main/kotlin/com/example/cleancity/ApiExceptions.kt`
- Create: `backend/src/main/kotlin/com/example/cleancity/moderation/ModerationRoutes.kt`
- Modify: `backend/src/main/kotlin/com/example/cleancity/Application.kt`
- Test: `backend/src/test/kotlin/com/example/cleancity/moderation/ModerationRoutesTest.kt`

- [ ] **Step 1: Добавить DTO**

Создать `shared/src/commonMain/kotlin/com/example/cleancity/shared/responses/admin/Moderation.kt`:

```kotlin
package com.example.cleancity.shared.responses.admin

import kotlinx.serialization.Serializable

@Serializable
data class ModerationSummaryResponse(
    val rejectedCountSinceWarning: Int,
    val flagged: Boolean,
    val isWarned: Boolean,
    val isBanned: Boolean,
)

@Serializable
data class WarnResidentRequest(
    val reason: String,
    val complaintId: Long,
)

@Serializable
data class BanResidentRequest(
    val reason: String,
)
```

- [ ] **Step 2: Добавить коды ошибок**

В `ApiExceptions.kt`, в `object ErrorCodes` (после `NOT_A_PENDING_INVITE`), добавить:

```kotlin
    const val MODERATION_NOT_RESIDENT = "MODERATION_NOT_RESIDENT"
    const val MODERATION_REASON_REQUIRED = "MODERATION_REASON_REQUIRED"
```

- [ ] **Step 3: Написать роуты**

Создать `backend/src/main/kotlin/com/example/cleancity/moderation/ModerationRoutes.kt`:

```kotlin
package com.example.cleancity.moderation

import com.example.cleancity.BadRequestException
import com.example.cleancity.ErrorCodes
import com.example.cleancity.ForbiddenException
import com.example.cleancity.NotFoundException
import com.example.cleancity.UnauthorizedException
import com.example.cleancity.auth.clientIp
import com.example.cleancity.auth.userAgentSafe
import com.example.cleancity.shared.models.UserRole
import com.example.cleancity.shared.responses.admin.BanResidentRequest
import com.example.cleancity.shared.responses.admin.ModerationSummaryResponse
import com.example.cleancity.shared.responses.admin.WarnResidentRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.moderationRoutes(service: ModerationService) {
    route("/auth") {
        authenticate("auth-jwt") {

            get("/admin/residents/{id}/moderation") {
                requireStaff(call)
                val id = pathId(call)
                try {
                    val s = service.getSummary(id)
                    call.respond(
                        HttpStatusCode.OK,
                        ModerationSummaryResponse(s.rejectedCountSinceWarning, s.flagged, s.isWarned, s.isBanned)
                    )
                } catch (_: ResidentNotFoundException) {
                    throw NotFoundException("Пользователь не найден")
                }
            }

            post("/admin/residents/{id}/warn") {
                val actorId = requireStaff(call)
                val id = pathId(call)
                val req = call.receive<WarnResidentRequest>()
                try {
                    service.warn(actorId, id, req.complaintId, req.reason, call.clientIp(), call.userAgentSafe())
                    call.respond(HttpStatusCode.NoContent)
                } catch (e: Exception) { mapModerationError(e) }
            }

            post("/admin/residents/{id}/ban") {
                val actorId = requireStaff(call)
                val id = pathId(call)
                val req = call.receive<BanResidentRequest>()
                try {
                    service.ban(actorId, id, req.reason, call.clientIp(), call.userAgentSafe())
                    call.respond(HttpStatusCode.NoContent)
                } catch (e: Exception) { mapModerationError(e) }
            }

            post("/admin/residents/{id}/unban") {
                val actorId = requireStaff(call)
                val id = pathId(call)
                try {
                    service.unban(actorId, id, call.clientIp(), call.userAgentSafe())
                    call.respond(HttpStatusCode.NoContent)
                } catch (_: ResidentNotFoundException) {
                    throw NotFoundException("Пользователь не найден")
                } catch (_: NotAResidentException) {
                    throw ForbiddenException("Это не житель", ErrorCodes.MODERATION_NOT_RESIDENT)
                }
            }
        }
    }
}

private fun mapModerationError(e: Exception): Nothing = when (e) {
    is ResidentNotFoundException -> throw NotFoundException("Пользователь не найден")
    is NotAResidentException -> throw ForbiddenException("Это не житель", ErrorCodes.MODERATION_NOT_RESIDENT)
    is ReasonRequiredException -> throw BadRequestException("Нужна причина", ErrorCodes.MODERATION_REASON_REQUIRED)
    else -> throw e
}

private fun requireStaff(call: ApplicationCall): Long {
    val principal = call.principal<JWTPrincipal>() ?: throw UnauthorizedException("Not authenticated")
    val role = runCatching { principal.payload.getClaim("role").asString()?.let { UserRole.valueOf(it) } }.getOrNull()
        ?: throw UnauthorizedException("Not authenticated")
    if (role != UserRole.ADMIN && role != UserRole.OPERATOR) {
        throw ForbiddenException("Только сотрудники", ErrorCodes.FORBIDDEN)
    }
    return principal.payload.subject?.toLongOrNull() ?: throw UnauthorizedException("Not authenticated")
}

private fun pathId(call: ApplicationCall): Long =
    call.parameters["id"]?.toLongOrNull()
        ?: throw BadRequestException("Invalid user id", ErrorCodes.VALIDATION_BAD_FIELD)
```

- [ ] **Step 4: Подключить сервис и роуты в Application.kt**

В `Application.kt` после блока создания `complaintService` (около строки 145–154) добавить:

```kotlin
    val moderationService = com.example.cleancity.moderation.ModerationService(
        users = userRepository,
        complaints = complaintRepository,
        tokens = tokenRepository,
        notifications = notificationService,
        audit = auditLogger,
    )
```

> Точные имена переменных уточнить рядом в файле: репозитории/логгер уже создаются для `authService`/`complaintService`. Если переменная аудита называется иначе (напр. `dbAuditLogger`), использовать её. `notificationService` уже есть (строка 143).

В блоке `routing { ... }` после `complaintRoutes(complaintService)` (около строки 181) добавить:

```kotlin
        com.example.cleancity.moderation.moderationRoutes(moderationService)
```

- [ ] **Step 5: Написать тест роутов (guard + happy path)**

Создать `backend/src/test/kotlin/com/example/cleancity/moderation/ModerationRoutesTest.kt`. Скопировать тестовую обвязку (init H2, `appWithAuth()`, `bearerFor(userId, role)`, `seedUser(...)`) из существующего `backend/src/test/kotlin/com/example/cleancity/auth/AuthAdminTeamRoutesTest.kt` (та же схема настройки JWT + Ktor `testApplication`). Зарегистрировать в `appWithAuth()` `moderationRoutes(moderationService)` вместо/вместе с auth-роутами. Тело тестов:

```kotlin
    @Test
    fun `resident cannot access moderation summary (403)`() = testApplication {
        appWithAuth()
        val residentId = seedUser("res@t.local", UserRole.RESIDENT)
        val targetId = seedUser("res2@t.local", UserRole.RESIDENT)
        val resp = client.get("/auth/admin/residents/$targetId/moderation") {
            header(HttpHeaders.Authorization, "Bearer ${bearerFor(residentId, UserRole.RESIDENT)}")
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `operator can fetch moderation summary (200)`() = testApplication {
        appWithAuth()
        val operatorId = seedUser("op@t.local", UserRole.OPERATOR)
        val targetId = seedUser("res@t.local", UserRole.RESIDENT)
        val resp = client.get("/auth/admin/residents/$targetId/moderation") {
            header(HttpHeaders.Authorization, "Bearer ${bearerFor(operatorId, UserRole.OPERATOR)}")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `banning a non-resident returns 403 MODERATION_NOT_RESIDENT`() = testApplication {
        appWithAuth()
        val adminId = seedUser("adm@t.local", UserRole.ADMIN)
        val otherOperator = seedUser("op2@t.local", UserRole.OPERATOR)
        val resp = client.post("/auth/admin/residents/$otherOperator/ban") {
            header(HttpHeaders.Authorization, "Bearer ${bearerFor(adminId, UserRole.ADMIN)}")
            contentType(ContentType.Application.Json)
            setBody("""{"reason":"x"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }
```

(Импорты из ktor-client-test: `io.ktor.client.request.*`, `io.ktor.http.*`, как в `AuthAdminTeamRoutesTest.kt`.)

- [ ] **Step 6: Запустить тест роутов + компиляцию**

Run: `./gradlew :backend:test --tests "com.example.cleancity.moderation.ModerationRoutesTest"`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/cleancity/shared/responses/admin/Moderation.kt \
        backend/src/main/kotlin/com/example/cleancity/ApiExceptions.kt \
        backend/src/main/kotlin/com/example/cleancity/moderation/ModerationRoutes.kt \
        backend/src/main/kotlin/com/example/cleancity/Application.kt \
        backend/src/test/kotlin/com/example/cleancity/moderation/ModerationRoutesTest.kt
git commit -m "feat(moderation): REST endpoints /auth/admin/residents/{id}/{moderation,warn,ban,unban}"
```

---

## Task 8: Web-admin — API-клиент и типы

**Files:**
- Modify: `web-admin/src/api/types.ts`
- Create: `web-admin/src/api/moderation.ts`

- [ ] **Step 1: Добавить типы**

В `web-admin/src/api/types.ts` добавить:

```typescript
export interface ModerationSummary {
  rejectedCountSinceWarning: number
  flagged: boolean
  isWarned: boolean
  isBanned: boolean
}
```

- [ ] **Step 2: Написать API-клиент**

Создать `web-admin/src/api/moderation.ts` (зеркалит стиль `api/admin.ts`):

```typescript
import { api } from './client'
import type { ModerationSummary } from './types'

export async function getModerationSummary(residentId: number): Promise<ModerationSummary> {
  const res = await api.get<ModerationSummary>(`/auth/admin/residents/${residentId}/moderation`)
  return res.data
}

export async function warnResident(
  residentId: number,
  reason: string,
  complaintId: number,
): Promise<void> {
  await api.post(`/auth/admin/residents/${residentId}/warn`, { reason, complaintId })
}

export async function banResident(residentId: number, reason: string): Promise<void> {
  await api.post(`/auth/admin/residents/${residentId}/ban`, { reason })
}

export async function unbanResident(residentId: number): Promise<void> {
  await api.post(`/auth/admin/residents/${residentId}/unban`)
}
```

- [ ] **Step 3: Проверить сборку типов**

Run: `cd web-admin && npx tsc --noEmit`
Expected: без ошибок

- [ ] **Step 4: Commit**

```bash
git add web-admin/src/api/types.ts web-admin/src/api/moderation.ts
git commit -m "feat(moderation): web-admin API client + types"
```

---

## Task 9: Web-admin — TanStack-хуки

**Files:**
- Create: `web-admin/src/hooks/moderationQueries.ts`

- [ ] **Step 1: Написать хуки**

Создать `web-admin/src/hooks/moderationQueries.ts` (зеркалит `hooks/complaintQueries.ts`):

```typescript
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  getModerationSummary,
  warnResident,
  banResident,
  unbanResident,
} from '@/api/moderation'

export function useModerationSummaryQuery(residentId: number | null) {
  return useQuery({
    queryKey: ['moderation', residentId],
    queryFn: () => getModerationSummary(residentId as number),
    enabled: residentId != null,
  })
}

export function useWarnMutation(residentId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ reason, complaintId }: { reason: string; complaintId: number }) =>
      warnResident(residentId, reason, complaintId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['moderation', residentId] }),
  })
}

export function useBanMutation(residentId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ reason }: { reason: string }) => banResident(residentId, reason),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['moderation', residentId] }),
  })
}

export function useUnbanMutation(residentId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => unbanResident(residentId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['moderation', residentId] }),
  })
}
```

- [ ] **Step 2: Проверить сборку**

Run: `cd web-admin && npx tsc --noEmit`
Expected: без ошибок

- [ ] **Step 3: Commit**

```bash
git add web-admin/src/hooks/moderationQueries.ts
git commit -m "feat(moderation): web-admin TanStack hooks"
```

---

## Task 10: Web-admin — ModerationPanel + интеграция

**Files:**
- Create: `web-admin/src/components/complaints/ModerationPanel.tsx`
- Create: `web-admin/src/components/complaints/ModerationPanel.test.tsx`
- Modify: `web-admin/src/components/complaints/ComplaintDetailPanel.tsx`

- [ ] **Step 1: Написать падающий тест компонента**

Создать `web-admin/src/components/complaints/ModerationPanel.test.tsx`. Хуки модерации мокаются, чтобы тест проверял только отрисовку состояний:

```tsx
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ModerationPanel } from './ModerationPanel'

vi.mock('@/hooks/moderationQueries', () => ({
  useModerationSummaryQuery: () => ({
    data: { rejectedCountSinceWarning: 3, flagged: true, isWarned: false, isBanned: false },
    isLoading: false,
  }),
  useWarnMutation: () => ({ mutate: vi.fn(), isPending: false }),
  useBanMutation: () => ({ mutate: vi.fn(), isPending: false }),
  useUnbanMutation: () => ({ mutate: vi.fn(), isPending: false }),
}))

describe('ModerationPanel', () => {
  it('показывает бейдж флага при flagged и кнопки модерации', () => {
    render(<ModerationPanel authorId={7} complaintId={42} />)
    expect(screen.getByText(/3 отклонённ/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Предупредить/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Забанить/ })).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Запустить — должен упасть**

Run: `cd web-admin && npx vitest run src/components/complaints/ModerationPanel.test.tsx`
Expected: FAIL (нет файла `ModerationPanel.tsx`)

- [ ] **Step 3: Написать компонент**

Создать `web-admin/src/components/complaints/ModerationPanel.tsx`:

```tsx
import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import {
  useModerationSummaryQuery,
  useWarnMutation,
  useBanMutation,
  useUnbanMutation,
} from '@/hooks/moderationQueries'

interface Props {
  authorId: number
  complaintId: number
}

export function ModerationPanel({ authorId, complaintId }: Props) {
  const summary = useModerationSummaryQuery(authorId)
  const warn = useWarnMutation(authorId)
  const ban = useBanMutation(authorId)
  const unban = useUnbanMutation(authorId)
  const [reason, setReason] = useState('')

  if (summary.isLoading || !summary.data) return null
  const s = summary.data

  return (
    <div className="space-y-2 border-t pt-3">
      <div className="flex items-center gap-2 text-sm font-medium text-slate-700">
        Модерация автора
        {s.flagged && (
          <Badge className="bg-amber-100 text-amber-800">
            ⚠ {s.rejectedCountSinceWarning} отклонённых
          </Badge>
        )}
        {s.isWarned && <Badge className="bg-slate-100 text-slate-600">предупреждён</Badge>}
        {s.isBanned && <Badge className="bg-red-100 text-red-700">забанен</Badge>}
      </div>

      {!s.isBanned && (
        <textarea
          className="w-full rounded border p-2 text-sm"
          rows={2}
          placeholder="Причина (обязательно для предупреждения и бана)"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
        />
      )}

      <div className="flex flex-wrap gap-2">
        {!s.isBanned && (
          <>
            <Button
              variant="outline"
              disabled={!reason.trim() || warn.isPending}
              onClick={() => warn.mutate({ reason, complaintId }, { onSuccess: () => setReason('') })}
            >
              Предупредить
            </Button>
            <Button
              variant="destructive"
              disabled={!reason.trim() || ban.isPending}
              onClick={() => ban.mutate({ reason }, { onSuccess: () => setReason('') })}
            >
              Забанить
            </Button>
          </>
        )}
        {s.isBanned && (
          <Button variant="outline" disabled={unban.isPending} onClick={() => unban.mutate()}>
            Разбанить
          </Button>
        )}
      </div>
    </div>
  )
}
```

> Если у `Button` нет варианта `destructive`/`outline` в `components/ui/button.tsx` — посмотреть доступные `variant` в этом файле и подставить существующий (напр. для «Забанить» использовать класс через `className="bg-red-600 ..."`). Проверить перед запуском.

- [ ] **Step 4: Запустить тест — должен пройти**

Run: `cd web-admin && npx vitest run src/components/complaints/ModerationPanel.test.tsx`
Expected: PASS

- [ ] **Step 5: Вставить панель в деталь жалобы**

В `web-admin/src/components/complaints/ComplaintDetailPanel.tsx`:

В импорты добавить:
```tsx
import { ModerationPanel } from './ModerationPanel'
```

Перед закрывающим блоком действий (`{actions.length > 0 && ( ... )}`) — то есть сразу после блока `История статусов` — вставить:
```tsx
      <ModerationPanel authorId={complaint.authorId} complaintId={complaint.id} />
```

- [ ] **Step 6: Прогнать связанные тесты + сборку**

Run: `cd web-admin && npx vitest run src/components/complaints && npx tsc --noEmit`
Expected: PASS, без ошибок типов

- [ ] **Step 7: Commit**

```bash
git add web-admin/src/components/complaints/ModerationPanel.tsx \
        web-admin/src/components/complaints/ModerationPanel.test.tsx \
        web-admin/src/components/complaints/ComplaintDetailPanel.tsx
git commit -m "feat(moderation): ModerationPanel on complaint detail (warn/ban/unban + flag badge)"
```

---

## Task 11: Финальная проверка

**Files:** нет изменений кода — только верификация.

- [ ] **Step 1: Весь backend-набор зелёный**

Run: `./gradlew :backend:test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Весь web-admin-набор зелёный + типы**

Run: `cd web-admin && npx vitest run && npx tsc --noEmit && npm run build`
Expected: все тесты PASS, сборка успешна

- [ ] **Step 3: Проверка миграции и CHECK-констрейнта на реальном Postgres (ручная)**

> Юнит-тесты на H2 не проверяют CHECK-констрейнты notifications. Эту проверку делаем против Postgres dev-окружения.

1. Поднять backend локально (см. `reference_cleancity_dev_seed`): миграция V9 должна примениться без ошибок.
2. Через psql проверить, что вставка `MODERATION_WARNING` без `complaint_id` падает, а с `complaint_id` — проходит:

```sql
-- должна упасть (нарушение notifications_target_check)
INSERT INTO notifications (user_id, kind, title, body, created_at)
VALUES (1, 'MODERATION_WARNING', 't', 'b', NOW());

-- должна пройти
INSERT INTO notifications (user_id, kind, title, body, complaint_id, created_at)
VALUES (1, 'MODERATION_WARNING', 't', 'b', 1, NOW());
```

- [ ] **Step 4: Ручной e2e-смоук (по желанию, на dev-стенде)**

1. Залогиниться в web-admin как OPERATOR/ADMIN, открыть жалобу резидента.
2. Убедиться, что у автора с ≥3 REJECTED виден бейдж «⚠ N отклонённых».
3. Ввести причину → «Предупредить» → проверить, что бейдж сменился на «предупреждён», счётчик сбросился; у жителя появилось in-app уведомление.
4. «Забанить» с причиной → бейдж «забанен», кнопка «Разбанить»; вход забаненного жителя в мобильном отклоняется.
5. «Разбанить» → доступ возвращается.
6. Открыть `GET /auth/admin/audit-log` (или раскрыть скрытый блок) — увидеть записи `RESIDENT_WARNED` / `RESIDENT_BANNED` / `RESIDENT_UNBANNED`.

- [ ] **Step 5: Финальный коммит (если были мелкие правки в Step 3 / Step 4)**

```bash
git add -A
git commit -m "chore(moderation): финальная верификация resident moderation"
```

---

## Самопроверка плана (для автора)

- **Покрытие spec:** две дорожки (авто-флаг + ручной бан) — Task 4/5/6/10; лестница warn→ban→unban — Task 5/6/10; порог 3 — `REJECTED_FLAG_THRESHOLD` Task 4; `DUPLICATE` не считается — Task 2; доступ ADMIN+OPERATOR — Task 7 `requireStaff`; действия на детали жалобы — Task 10; данные (`warned_at`, AuditAction, MODERATION_WARNING) — Task 1; обязательная причина + аудит — Task 5/6/7; разбан — Task 6/10; миграция CHECK — Task 1 + ручная проверка Task 11.
- **Краевые случаи spec:** не-резидент → 403 (Task 7 тест); пустая причина → 400 (Task 5/6 тесты); забаненный не входит — уже покрыто `!isActive` в логине, проверяется в e2e (Task 11).
- **Согласованность типов:** `ModerationSummary`(Kotlin)/`ModerationSummaryResponse`(DTO)/`ModerationSummary`(TS) имеют одинаковые поля `rejectedCountSinceWarning/flagged/isWarned/isBanned`. `warn(actorId, residentId, complaintId, reason, ip, ua)` — одинаковая сигнатура в Task 5 и вызове из роута Task 7. `WarnResidentRequest{reason, complaintId}` совпадает с web-admin `warnResident(residentId, reason, complaintId)` Task 8.
