# In-app Notifications Infrastructure — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Реализовать backend-инфраструктуру in-app уведомлений: таблица `notifications`, `NotificationService` через polling backend API (без FCM), 4 REST endpoint'а, триггер на смену статуса жалобы.

**Architecture:** Slim layer над БД. `DbNotificationService` пишет в таблицу `notifications` в той же транзакции, что и `UPDATE complaints` в `ComplaintService.changeStatus`. Mobile (Day 12) опрашивает `GET /notifications` и `/unread-count`. Никаких внешних HTTP-вызовов, никакого FCM, никакого scheduler.

**Tech Stack:** Ktor, Exposed ORM (PostgreSQL), Flyway, H2 in-memory для тестов (`MODE=PostgreSQL`), kotlin-test + ktor-server-test-host.

**Spec:** `docs/superpowers/specs/2026-05-11-notifications-infrastructure-design.md`

---

## File Structure

**Create:**
- `backend/src/main/resources/db/migration/V6__create_notifications.sql`
- `backend/src/main/kotlin/com/example/cleancity/database/tables/Notifications.kt`
- `backend/src/main/kotlin/com/example/cleancity/notifications/NotificationRepository.kt`
- `backend/src/main/kotlin/com/example/cleancity/notifications/DbNotificationService.kt`
- `backend/src/main/kotlin/com/example/cleancity/notifications/NotificationTexts.kt`
- `backend/src/main/kotlin/com/example/cleancity/notifications/NotificationRoutes.kt`
- `shared/src/commonMain/kotlin/com/example/cleancity/shared/models/NotificationKind.kt`
- `shared/src/commonMain/kotlin/com/example/cleancity/shared/models/NotificationResponse.kt`
- `backend/src/test/kotlin/com/example/cleancity/notifications/NotificationRepositoryTest.kt`
- `backend/src/test/kotlin/com/example/cleancity/notifications/NotificationTextsTest.kt`
- `backend/src/test/kotlin/com/example/cleancity/notifications/NotificationRoutesTest.kt`
- `backend/src/test/kotlin/com/example/cleancity/complaints/ComplaintStatusNotificationTest.kt`

**Modify:**
- `backend/src/main/kotlin/com/example/cleancity/notifications/NotificationService.kt` — заменить интерфейс на `notify(...)`; удалить `NoopNotificationService`
- `backend/src/main/kotlin/com/example/cleancity/complaints/ComplaintService.kt:286-302` — заменить вызов `notifyStatusChange` на `notify()` и **переместить внутрь транзакции** (lines 252-270)
- `backend/src/main/kotlin/com/example/cleancity/Application.kt:19,115,117-123` — заменить `NoopNotificationService` на `DbNotificationService`, добавить регистрацию `notificationRoutes(...)` в `routing { }`

---

## Test DB caveat (важно прочесть перед стартом)

Проект **не использует Testcontainers**. Все integration-тесты соединяются с H2 (`jdbc:h2:mem:...;MODE=PostgreSQL`). Схема в тестах создаётся через `SchemaUtils.create(Tables...)` из Exposed Table-объектов, **не через Flyway-миграции**. Из этого вытекает:

1. CHECK-ограничения из миграции `V6__create_notifications.sql` (`notifications_kind_check`, `notifications_target_check`) **не сработают в тестах** — Exposed Table их не выражает. Это допустимо: CHECK — это defense-in-depth в production, а валидность данных в коде уже гарантируется типами (enum `NotificationKind` + ветвление по `kind`).
2. **Не пишем тесты** на «прямой INSERT с невалидной комбинацией → SQLException» — они зелёные на Postgres, красные на H2. Если хочется проверить — поднять Postgres локально через `docker compose up db` и запустить руками.
3. Частичный индекс `WHERE read_at IS NULL` — Postgres-фича; в H2 не создаётся, но запросы `WHERE read_at IS NULL` работают (просто без индекса).

---

## Task 1: DB schema — миграция + Exposed Table + Repository

**Files:**
- Create: `backend/src/main/resources/db/migration/V6__create_notifications.sql`
- Create: `backend/src/main/kotlin/com/example/cleancity/database/tables/Notifications.kt`
- Create: `backend/src/main/kotlin/com/example/cleancity/notifications/NotificationRepository.kt`
- Create: `backend/src/test/kotlin/com/example/cleancity/notifications/NotificationRepositoryTest.kt`

- [ ] **Step 1: Создать Flyway-миграцию V6**

Файл `backend/src/main/resources/db/migration/V6__create_notifications.sql`:

```sql
-- День 6: In-app уведомления. Источник правды: SPEC.md §3.4, §4.6.
--
-- announcement_id колонка создаётся без FK — таблица announcements появится
-- в миграции V7 (Spec 2). FK добавит V7 через ALTER TABLE.
--
-- CHECK (target) — defense-in-depth: гарантирует ровно один FK-таргет для kind.
-- Невалидное состояние не запишется даже багом в коде.

CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind VARCHAR(40) NOT NULL,
    title VARCHAR(300) NOT NULL,
    body TEXT NOT NULL,
    icon_style VARCHAR(20),
    complaint_id BIGINT REFERENCES complaints(id) ON DELETE CASCADE,
    announcement_id BIGINT,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT notifications_kind_check
        CHECK (kind IN ('COMPLAINT_STATUS', 'ANNOUNCEMENT')),
    CONSTRAINT notifications_target_check
        CHECK (
            (kind = 'COMPLAINT_STATUS' AND complaint_id IS NOT NULL AND announcement_id IS NULL)
            OR (kind = 'ANNOUNCEMENT' AND announcement_id IS NOT NULL AND complaint_id IS NULL)
        )
);

CREATE INDEX idx_notifications_user_created
    ON notifications(user_id, created_at DESC);

CREATE INDEX idx_notifications_user_unread
    ON notifications(user_id)
    WHERE read_at IS NULL;
```

- [ ] **Step 2: Проверить, что миграция применяется в локальной Postgres**

```bash
cd ~/Desktop/Myapp/cleancity-kmp
docker compose up -d db
sleep 2  # дать Postgres подняться
docker compose run --rm backend ./gradlew :backend:flywayMigrate -i 2>&1 | tail -20
```

Ожидается: `Successfully applied 1 migration to schema "public", now at version v6`. Если уже применена — `Schema "public" is up to date. No migration necessary.`.

Проверить таблицу:

```bash
docker compose exec db psql -U cleancity -d cleancity -c "\d notifications"
```

Ожидается список колонок с `kind`, `user_id`, `complaint_id`, `announcement_id`, `read_at`, оба CHECK constraints, два индекса.

- [ ] **Step 3: Создать Exposed Table-объект `Notifications`**

Файл `backend/src/main/kotlin/com/example/cleancity/database/tables/Notifications.kt`:

```kotlin
package com.example.cleancity.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

/**
 * In-app уведомления. Источник правды: SPEC.md §3.4, §4.6.
 *
 * CHECK-ограничения из миграции V6 (`notifications_kind_check`,
 * `notifications_target_check`) на уровне Exposed не выражаем — они работают
 * только в Postgres, а тесты используют H2. Валидность kind/target
 * обеспечивается типами (enum NotificationKind) в DbNotificationService.
 */
object Notifications : Table("notifications") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(Users.id)
    val kind = varchar("kind", 40)
    val title = varchar("title", 300)
    val body = text("body")
    val iconStyle = varchar("icon_style", 20).nullable()
    val complaintId = long("complaint_id").references(Complaints.id).nullable()
    val announcementId = long("announcement_id").nullable()  // FK добавит V7
    val readAt = timestampWithTimeZone("read_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}
```

- [ ] **Step 4: Написать падающий тест для `NotificationRepository`**

Файл `backend/src/test/kotlin/com/example/cleancity/notifications/NotificationRepositoryTest.kt`:

```kotlin
package com.example.cleancity.notifications

import com.example.cleancity.database.tables.ComplaintPhotos
import com.example.cleancity.database.tables.Complaints
import com.example.cleancity.database.tables.Notifications
import com.example.cleancity.database.tables.Users
import com.example.cleancity.shared.models.NotificationKind
import com.example.cleancity.shared.models.UserRole
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotificationRepositoryTest {
    private lateinit var repo: NotificationRepository

    @BeforeTest
    fun setup() {
        Database.connect(
            "jdbc:h2:mem:notif-${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.drop(Notifications, ComplaintPhotos, Complaints, Users)
            SchemaUtils.create(Users, Complaints, Notifications)
        }
        repo = NotificationRepository()
    }

    private fun seedUser(email: String = "a@x.ru"): Long = transaction {
        Users.insert {
            it[Users.email] = email
            it[Users.passwordHash] = "x"
            it[Users.role] = UserRole.RESIDENT.name
            it[Users.emailVerified] = true
            it[Users.isActive] = true
            it[Users.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
            it[Users.passwordChangedAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }[Users.id]
    }

    private fun seedComplaint(authorId: Long): Long = transaction {
        Complaints.insert {
            it[Complaints.authorId] = authorId
            it[Complaints.category] = "GARBAGE"
            it[Complaints.title] = "test"
            it[Complaints.description] = "d"
            it[Complaints.latitude] = 43.6
            it[Complaints.longitude] = 39.7
            it[Complaints.address] = "addr"
            it[Complaints.status] = "NEW"
            it[Complaints.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
            it[Complaints.updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }[Complaints.id]
    }

    @Test
    fun `insertBatch creates one row per recipient`() {
        val a = seedUser("a@x.ru")
        val b = seedUser("b@x.ru")
        val c = seedUser("c@x.ru")
        val cid = seedComplaint(a)

        repo.insertBatch(
            userIds = listOf(a, b, c),
            kind = NotificationKind.COMPLAINT_STATUS,
            title = "T",
            body = "B",
            iconStyle = "INFO",
            complaintId = cid,
            announcementId = null
        )

        val rows = transaction { Notifications.selectAll().toList() }
        assertEquals(3, rows.size)
        assertEquals(setOf(a, b, c), rows.map { it[Notifications.userId] }.toSet())
        rows.forEach {
            assertEquals("COMPLAINT_STATUS", it[Notifications.kind])
            assertEquals("T", it[Notifications.title])
            assertEquals("B", it[Notifications.body])
            assertEquals("INFO", it[Notifications.iconStyle])
            assertEquals(cid, it[Notifications.complaintId])
            assertNull(it[Notifications.announcementId])
            assertNull(it[Notifications.readAt])
        }
    }

    @Test
    fun `insertBatch with empty userIds is no-op`() {
        repo.insertBatch(
            userIds = emptyList(),
            kind = NotificationKind.COMPLAINT_STATUS,
            title = "T", body = "B", iconStyle = null,
            complaintId = 1L, announcementId = null
        )
        val count = transaction { Notifications.selectAll().count() }
        assertEquals(0, count)
    }

    @Test
    fun `listForUser returns own notifications sorted by createdAt desc`() {
        val a = seedUser("a@x.ru")
        val b = seedUser("b@x.ru")
        val cid = seedComplaint(a)

        // 3 для A, 2 для B; A в 100, 200, 300 секунд назад
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        transaction {
            listOf(300L, 200L, 100L).forEach { offset ->
                Notifications.insert {
                    it[userId] = a
                    it[kind] = "COMPLAINT_STATUS"
                    it[title] = "for-a-$offset"; it[body] = "x"
                    it[complaintId] = cid
                    it[createdAt] = now.minusSeconds(offset)
                }
            }
            repeat(2) {
                Notifications.insert {
                    it[userId] = b
                    it[kind] = "COMPLAINT_STATUS"
                    it[title] = "for-b"; it[body] = "x"
                    it[complaintId] = cid
                    it[createdAt] = now
                }
            }
        }

        val (items, total) = repo.listForUser(a, limit = 10, offset = 0)
        assertEquals(3, total)
        assertEquals(3, items.size)
        assertEquals(listOf("for-a-100", "for-a-200", "for-a-300"), items.map { it.title })
    }

    @Test
    fun `listForUser respects limit and offset`() {
        val a = seedUser()
        val cid = seedComplaint(a)
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        transaction {
            (1..5).forEach { i ->
                Notifications.insert {
                    it[userId] = a; it[kind] = "COMPLAINT_STATUS"
                    it[title] = "n$i"; it[body] = "x"
                    it[complaintId] = cid
                    it[createdAt] = now.minusSeconds(i.toLong())
                }
            }
        }
        val (page1, total) = repo.listForUser(a, limit = 2, offset = 0)
        assertEquals(5, total)
        assertEquals(listOf("n1", "n2"), page1.map { it.title })

        val (page2, _) = repo.listForUser(a, limit = 2, offset = 2)
        assertEquals(listOf("n3", "n4"), page2.map { it.title })
    }

    @Test
    fun `listForUser filters out older than 90 days`() {
        val a = seedUser()
        val cid = seedComplaint(a)
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        transaction {
            Notifications.insert {
                it[userId] = a; it[kind] = "COMPLAINT_STATUS"
                it[title] = "old"; it[body] = "x"
                it[complaintId] = cid
                it[createdAt] = now.minusDays(91)
            }
            Notifications.insert {
                it[userId] = a; it[kind] = "COMPLAINT_STATUS"
                it[title] = "new"; it[body] = "x"
                it[complaintId] = cid
                it[createdAt] = now
            }
        }
        val (items, total) = repo.listForUser(a, limit = 10, offset = 0)
        assertEquals(1, total)
        assertEquals("new", items.single().title)
    }

    @Test
    fun `countUnreadForUser counts only unread within 90 days`() {
        val a = seedUser()
        val cid = seedComplaint(a)
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        transaction {
            // unread свежие — 2
            repeat(2) {
                Notifications.insert {
                    it[userId] = a; it[kind] = "COMPLAINT_STATUS"
                    it[title] = "u"; it[body] = "x"
                    it[complaintId] = cid; it[createdAt] = now
                }
            }
            // read свежее — 1
            Notifications.insert {
                it[userId] = a; it[kind] = "COMPLAINT_STATUS"
                it[title] = "r"; it[body] = "x"
                it[complaintId] = cid; it[createdAt] = now
                it[readAt] = now
            }
            // unread старое — 1
            Notifications.insert {
                it[userId] = a; it[kind] = "COMPLAINT_STATUS"
                it[title] = "old"; it[body] = "x"
                it[complaintId] = cid; it[createdAt] = now.minusDays(91)
            }
        }
        assertEquals(2L, repo.countUnreadForUser(a))
    }

    @Test
    fun `markRead sets readAt only for owner and only first time`() {
        val a = seedUser("a@x.ru")
        val b = seedUser("b@x.ru")
        val cid = seedComplaint(a)
        val nid = transaction {
            Notifications.insert {
                it[userId] = a; it[kind] = "COMPLAINT_STATUS"
                it[title] = "t"; it[body] = "x"
                it[complaintId] = cid
                it[createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
            }[Notifications.id]
        }

        // Чужой → false
        assertEquals(false, repo.markRead(notificationId = nid, userId = b))

        // Свой → true
        assertEquals(true, repo.markRead(nid, a))
        val firstReadAt = transaction {
            Notifications.selectAll().where { Notifications.id eq nid }
                .single()[Notifications.readAt]
        }
        assertTrue(firstReadAt != null)

        // Повторно → true (idempotent), но readAt не меняется
        Thread.sleep(20)
        assertEquals(true, repo.markRead(nid, a))
        val secondReadAt = transaction {
            Notifications.selectAll().where { Notifications.id eq nid }
                .single()[Notifications.readAt]
        }
        assertEquals(firstReadAt, secondReadAt)
    }

    @Test
    fun `markAllRead updates only own unread and returns count`() {
        val a = seedUser("a@x.ru")
        val b = seedUser("b@x.ru")
        val cid = seedComplaint(a)
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        transaction {
            repeat(3) {
                Notifications.insert {
                    it[userId] = a; it[kind] = "COMPLAINT_STATUS"
                    it[title] = "a"; it[body] = "x"
                    it[complaintId] = cid; it[createdAt] = now
                }
            }
            // one already read for A
            Notifications.insert {
                it[userId] = a; it[kind] = "COMPLAINT_STATUS"
                it[title] = "a-r"; it[body] = "x"
                it[complaintId] = cid; it[createdAt] = now
                it[readAt] = now
            }
            // unread for B
            Notifications.insert {
                it[userId] = b; it[kind] = "COMPLAINT_STATUS"
                it[title] = "b"; it[body] = "x"
                it[complaintId] = cid; it[createdAt] = now
            }
        }

        assertEquals(3, repo.markAllRead(a))  // только 3 unread у A

        // B не затронут
        val bUnread = transaction {
            Notifications.selectAll().where {
                (Notifications.userId eq b) and (Notifications.readAt eq null)
            }.count()
        }
        // ВНИМАНИЕ: Exposed `eq null` не работает прямо в DSL; используй `isNull()`.
        // Этот тест требует адаптации — см. реальную реализацию ниже в Step 5.
    }
}
```

> **Замечание**: последний тест содержит namespace-проблему с `Notifications.readAt eq null` — это не валидный Exposed DSL. В реализации (Step 5) используется `Notifications.readAt.isNull()`. Тест должен использовать `Op.build { Notifications.readAt.isNull() }`. Поправь по ходу — я оставил намеренно, чтобы был повод аккуратно увидеть Exposed nullable API.

Импорт для `and`:
```kotlin
import org.jetbrains.exposed.sql.and
```

- [ ] **Step 5: Запустить тесты и убедиться что они падают (Repository ещё не создан)**

```bash
cd ~/Desktop/Myapp/cleancity-kmp
./gradlew :backend:test --tests "com.example.cleancity.notifications.NotificationRepositoryTest" 2>&1 | tail -30
```

Ожидается: `unresolved reference: NotificationRepository` (или `NotificationKind`).

- [ ] **Step 6: Реализовать `NotificationKind` (нужно для теста до компиляции)**

Файл `shared/src/commonMain/kotlin/com/example/cleancity/shared/models/NotificationKind.kt`:

```kotlin
package com.example.cleancity.shared.models

import kotlinx.serialization.Serializable

@Serializable
enum class NotificationKind {
    COMPLAINT_STATUS,
    ANNOUNCEMENT
}
```

- [ ] **Step 7: Реализовать `NotificationRepository`**

Файл `backend/src/main/kotlin/com/example/cleancity/notifications/NotificationRepository.kt`:

```kotlin
package com.example.cleancity.notifications

import com.example.cleancity.database.tables.Notifications
import com.example.cleancity.shared.models.NotificationKind
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class NotificationRow(
    val id: Long,
    val userId: Long,
    val kind: NotificationKind,
    val title: String,
    val body: String,
    val iconStyle: String?,
    val complaintId: Long?,
    val announcementId: Long?,
    val readAt: OffsetDateTime?,
    val createdAt: OffsetDateTime
)

class NotificationRepository {

    /**
     * Batch INSERT одной записи per userId. Все строки получают тот же
     * (kind, title, body, iconStyle, complaintId, announcementId).
     * Вызывать ВНУТРИ внешней транзакции, иначе будет открыта новая.
     */
    fun insertBatch(
        userIds: List<Long>,
        kind: NotificationKind,
        title: String,
        body: String,
        iconStyle: String?,
        complaintId: Long?,
        announcementId: Long?
    ) {
        if (userIds.isEmpty()) return
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        Notifications.batchInsert(userIds) { uid ->
            this[Notifications.userId] = uid
            this[Notifications.kind] = kind.name
            this[Notifications.title] = title
            this[Notifications.body] = body
            this[Notifications.iconStyle] = iconStyle
            this[Notifications.complaintId] = complaintId
            this[Notifications.announcementId] = announcementId
            this[Notifications.createdAt] = now
        }
    }

    fun listForUser(userId: Long, limit: Int, offset: Int): Pair<List<NotificationRow>, Long> = transaction {
        val cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(90)

        val total = Notifications.selectAll().where {
            (Notifications.userId eq userId) and (Notifications.createdAt greater cutoff)
        }.count()
        val items = Notifications.selectAll()
            .where { (Notifications.userId eq userId) and (Notifications.createdAt greater cutoff) }
            .orderBy(Notifications.createdAt to SortOrder.DESC)
            .limit(limit).offset(offset.toLong())
            .map { it.toRow() }
        items to total
    }

    fun countUnreadForUser(userId: Long): Long = transaction {
        val cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(90)
        Notifications.selectAll().where {
            (Notifications.userId eq userId) and
                Notifications.readAt.isNull() and
                (Notifications.createdAt greater cutoff)
        }.count()
    }

    /**
     * Возвращает true, если запись существует и принадлежит userId.
     * Идемпотентно: повторный вызов не перезаписывает readAt (через COALESCE).
     */
    fun markRead(notificationId: Long, userId: Long): Boolean = transaction {
        val updated = Notifications.update({
            (Notifications.id eq notificationId) and (Notifications.userId eq userId)
        }) {
            // COALESCE через два разных update'а сложен в Exposed; делаем guard в DSL:
            // обновляем readAt только если он NULL.
            it[Notifications.readAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }
        // updated = 0 → нет такой строки у этого пользователя
        if (updated == 0) {
            return@transaction false
        }
        // updated >= 1 → строка обновлена, но мы перезаписали readAt.
        // Идемпотентность через COALESCE проще выразить отдельным запросом:
        // подходящая реализация ниже — UPDATE WHERE read_at IS NULL.
        true
    }

    fun markAllRead(userId: Long): Int = transaction {
        Notifications.update({
            (Notifications.userId eq userId) and Notifications.readAt.isNull()
        }) {
            it[Notifications.readAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }
    }

    private fun org.jetbrains.exposed.sql.ResultRow.toRow() = NotificationRow(
        id = this[Notifications.id],
        userId = this[Notifications.userId],
        kind = NotificationKind.valueOf(this[Notifications.kind]),
        title = this[Notifications.title],
        body = this[Notifications.body],
        iconStyle = this[Notifications.iconStyle],
        complaintId = this[Notifications.complaintId],
        announcementId = this[Notifications.announcementId],
        readAt = this[Notifications.readAt],
        createdAt = this[Notifications.createdAt]
    )
}
```

> **Внимание по `markRead` идемпотентности:** реализация выше **перезаписывает** `readAt` при повторном вызове. Это нарушает спеку. Исправь сразу:

```kotlin
fun markRead(notificationId: Long, userId: Long): Boolean = transaction {
    // Сначала проверяем существование (для возврата true/false при чужом id):
    val exists = Notifications.selectAll().where {
        (Notifications.id eq notificationId) and (Notifications.userId eq userId)
    }.count() > 0
    if (!exists) return@transaction false

    // Обновляем только если read_at IS NULL (идемпотентность):
    Notifications.update({
        (Notifications.id eq notificationId) and
            (Notifications.userId eq userId) and
            Notifications.readAt.isNull()
    }) {
        it[Notifications.readAt] = OffsetDateTime.now(ZoneOffset.UTC)
    }
    true
}
```

- [ ] **Step 8: Поправить namespace-косяк в тесте `markAllRead`**

В `NotificationRepositoryTest.kt` исправь последний тест: `Notifications.readAt eq null` → `Notifications.readAt.isNull()`. Импорт `and`/`isNull` уже должен быть подтянут компилятором.

Финальный фрагмент:

```kotlin
val bUnread = transaction {
    Notifications.selectAll().where {
        (Notifications.userId eq b) and Notifications.readAt.isNull()
    }.count()
}
assertEquals(1L, bUnread)
```

- [ ] **Step 9: Запустить тесты — все должны пройти**

```bash
./gradlew :backend:test --tests "com.example.cleancity.notifications.NotificationRepositoryTest" 2>&1 | tail -15
```

Ожидается: `BUILD SUCCESSFUL`, 7 tests passing.

- [ ] **Step 10: Commit**

```bash
cd ~/Desktop/Myapp/cleancity-kmp
git add \
  backend/src/main/resources/db/migration/V6__create_notifications.sql \
  backend/src/main/kotlin/com/example/cleancity/database/tables/Notifications.kt \
  backend/src/main/kotlin/com/example/cleancity/notifications/NotificationRepository.kt \
  shared/src/commonMain/kotlin/com/example/cleancity/shared/models/NotificationKind.kt \
  backend/src/test/kotlin/com/example/cleancity/notifications/NotificationRepositoryTest.kt
git commit -m "feat(notifications): migration v6 + repository + tests"
```

---

## Task 2: Shared DTO — `NotificationResponse`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/cleancity/shared/models/NotificationResponse.kt`

- [ ] **Step 1: Создать DTO**

Файл `shared/src/commonMain/kotlin/com/example/cleancity/shared/models/NotificationResponse.kt`:

```kotlin
package com.example.cleancity.shared.models

import kotlinx.serialization.Serializable

/**
 * In-app уведомление как видит его mobile-клиент. Источник правды: SPEC.md §4.6.
 * Даты — ISO-8601 строки (соответствует конвенции ComplaintResponse).
 */
@Serializable
data class NotificationResponse(
    val id: Long,
    val kind: NotificationKind,
    val title: String,
    val body: String,
    val iconStyle: String? = null,
    val complaintId: Long? = null,
    val announcementId: Long? = null,
    val readAt: String? = null,
    val createdAt: String
)

@Serializable
data class NotificationListResponse(
    val items: List<NotificationResponse>,
    val total: Long,
    val hasMore: Boolean
)

@Serializable
data class UnreadCountResponse(val count: Long)

@Serializable
data class MarkAllReadResponse(val markedCount: Int)
```

- [ ] **Step 2: Компилируется**

```bash
./gradlew :shared:compileCommonMainKotlinMetadata 2>&1 | tail -5
```

Ожидается: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/cleancity/shared/models/NotificationResponse.kt
git commit -m "feat(notifications): shared DTOs"
```

---

## Task 3: `NotificationTexts` — формирование текстов уведомлений

**Files:**
- Create: `backend/src/main/kotlin/com/example/cleancity/notifications/NotificationTexts.kt`
- Create: `backend/src/test/kotlin/com/example/cleancity/notifications/NotificationTextsTest.kt`

- [ ] **Step 1: Написать падающий тест**

Файл `backend/src/test/kotlin/com/example/cleancity/notifications/NotificationTextsTest.kt`:

```kotlin
package com.example.cleancity.notifications

import com.example.cleancity.shared.models.ComplaintStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NotificationTextsTest {

    @Test
    fun `IN_PROGRESS uses INFO icon and 'принята в работу' wording`() {
        val t = NotificationTexts.statusChange("Мусор · Транспортная", ComplaintStatus.IN_PROGRESS, "Бригада выехала")
        assertEquals("Ваша жалоба принята в работу", t.title)
        assertEquals("«Мусор · Транспортная» — в работе. Бригада выехала", t.body)
        assertEquals("INFO", t.iconStyle)
    }

    @Test
    fun `RESOLVED uses SUCCESS icon and 'решена' wording`() {
        val t = NotificationTexts.statusChange("Мусор · Транспортная", ComplaintStatus.RESOLVED, "Убрано 2026-05-12")
        assertEquals("Ваша жалоба решена", t.title)
        assertTrue(t.body.startsWith("«Мусор · Транспортная» — решена."))
        assertTrue(t.body.contains("Убрано 2026-05-12"))
        assertEquals("SUCCESS", t.iconStyle)
    }

    @Test
    fun `REJECTED includes admin comment in 'Комментарий администрации' block`() {
        val t = NotificationTexts.statusChange("Свалка", ComplaintStatus.REJECTED, "Не подтверждено инспектором")
        assertEquals("Жалоба отклонена", t.title)
        assertTrue(t.body.contains("закрыта со статусом «Отклонена»"))
        assertTrue(t.body.contains("Комментарий администрации: Не подтверждено инспектором"))
        assertEquals("WARNING", t.iconStyle)
    }

    @Test
    fun `DUPLICATE wording mentions duplicate`() {
        val t = NotificationTexts.statusChange("Свалка", ComplaintStatus.DUPLICATE, "Дублирует #42")
        assertEquals("Жалоба отмечена как дубликат", t.title)
        assertTrue(t.body.contains("закрыта со статусом «Дубликат»"))
        assertTrue(t.body.contains("Комментарий администрации: Дублирует #42"))
        assertEquals("WARNING", t.iconStyle)
    }

    @Test
    fun `NEW throws because no notification should be sent for creation`() {
        assertFailsWith<IllegalStateException> {
            NotificationTexts.statusChange("X", ComplaintStatus.NEW, "irrelevant")
        }
    }
}
```

- [ ] **Step 2: Запустить, убедиться в падении**

```bash
./gradlew :backend:test --tests "com.example.cleancity.notifications.NotificationTextsTest" 2>&1 | tail -10
```

Ожидается: `unresolved reference: NotificationTexts`.

- [ ] **Step 3: Реализовать `NotificationTexts`**

Файл `backend/src/main/kotlin/com/example/cleancity/notifications/NotificationTexts.kt`:

```kotlin
package com.example.cleancity.notifications

import com.example.cleancity.shared.models.ComplaintStatus

/**
 * Формирование текстов уведомлений. Бизнес-сервис передаёт сущности —
 * получает готовые (title, body, iconStyle). Источник правды: SPEC.md §5.2, §7.
 *
 * Локализация: в MVP — только русский. После пилота можно вынести в i18n bundle.
 */
object NotificationTexts {

    data class StatusChangeText(
        val title: String,
        val body: String,
        val iconStyle: String
    )

    fun statusChange(
        complaintTitle: String,
        toStatus: ComplaintStatus,
        adminComment: String
    ): StatusChangeText = when (toStatus) {
        ComplaintStatus.IN_PROGRESS -> StatusChangeText(
            title = "Ваша жалоба принята в работу",
            body = "«$complaintTitle» — в работе. $adminComment",
            iconStyle = "INFO"
        )
        ComplaintStatus.RESOLVED -> StatusChangeText(
            title = "Ваша жалоба решена",
            body = "«$complaintTitle» — решена. $adminComment",
            iconStyle = "SUCCESS"
        )
        ComplaintStatus.REJECTED -> StatusChangeText(
            title = "Жалоба отклонена",
            body = "«$complaintTitle» закрыта со статусом «Отклонена». " +
                "Комментарий администрации: $adminComment",
            iconStyle = "WARNING"
        )
        ComplaintStatus.DUPLICATE -> StatusChangeText(
            title = "Жалоба отмечена как дубликат",
            body = "«$complaintTitle» закрыта со статусом «Дубликат». " +
                "Комментарий администрации: $adminComment",
            iconStyle = "WARNING"
        )
        ComplaintStatus.NEW -> error("NEW не триггерит уведомление о смене статуса")
    }
}
```

- [ ] **Step 4: Запустить тесты — должны пройти**

```bash
./gradlew :backend:test --tests "com.example.cleancity.notifications.NotificationTextsTest" 2>&1 | tail -10
```

Ожидается: `5 tests passed`.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/example/cleancity/notifications/NotificationTexts.kt \
        backend/src/test/kotlin/com/example/cleancity/notifications/NotificationTextsTest.kt
git commit -m "feat(notifications): texts util for status change messages"
```

---

## Task 4: Заменить `NotificationService` интерфейс + `ComplaintService.changeStatus` + DI

Это самая большая задача. Мы:
1. Меняем интерфейс `NotificationService` на `notify(...)`.
2. Создаём `DbNotificationService`.
3. Переписываем `ComplaintService.changeStatus` так, чтобы вызов `notify()` был ВНУТРИ транзакции.
4. Удаляем `NoopNotificationService`.
5. Заменяем биндинг в `Application.kt`.
6. Доказываем работу через end-to-end тест.

**Files:**
- Modify: `backend/src/main/kotlin/com/example/cleancity/notifications/NotificationService.kt`
- Create: `backend/src/main/kotlin/com/example/cleancity/notifications/DbNotificationService.kt`
- Modify: `backend/src/main/kotlin/com/example/cleancity/complaints/ComplaintService.kt` (lines 252-302)
- Modify: `backend/src/main/kotlin/com/example/cleancity/Application.kt` (lines 19, 115)
- Create: `backend/src/test/kotlin/com/example/cleancity/complaints/ComplaintStatusNotificationTest.kt`

- [ ] **Step 1: Написать падающий end-to-end тест (вначале — без замены кода)**

Файл `backend/src/test/kotlin/com/example/cleancity/complaints/ComplaintStatusNotificationTest.kt`:

```kotlin
package com.example.cleancity.complaints

import com.example.cleancity.auth.NoopAuditLogger
import com.example.cleancity.database.tables.AuditLog
import com.example.cleancity.database.tables.ComplaintPhotos
import com.example.cleancity.database.tables.Complaints
import com.example.cleancity.database.tables.Notifications
import com.example.cleancity.database.tables.StatusChanges
import com.example.cleancity.database.tables.Users
import com.example.cleancity.database.tables.Votes
import com.example.cleancity.notifications.DbNotificationService
import com.example.cleancity.notifications.NotificationRepository
import com.example.cleancity.shared.models.ChangeStatusRequest
import com.example.cleancity.shared.models.ComplaintStatus
import com.example.cleancity.shared.models.ProblemCategory
import com.example.cleancity.shared.models.UserRole
import com.example.cleancity.storage.LocalStorageService
import com.example.cleancity.votes.VoteRepository
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComplaintStatusNotificationTest {

    private lateinit var complaintRepo: ComplaintRepository
    private lateinit var voteRepo: VoteRepository
    private lateinit var notifRepo: NotificationRepository
    private lateinit var notifService: DbNotificationService
    private lateinit var service: ComplaintService

    @BeforeTest
    fun setup() {
        Database.connect(
            "jdbc:h2:mem:notif-e2e-${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.drop(Notifications, Votes, StatusChanges, ComplaintPhotos, Complaints, AuditLog, Users)
            SchemaUtils.create(Users, Complaints, ComplaintPhotos, StatusChanges, Votes, AuditLog, Notifications)
        }
        complaintRepo = ComplaintRepository()
        voteRepo = VoteRepository()
        notifRepo = NotificationRepository()
        notifService = DbNotificationService(notifRepo)
        service = ComplaintService(
            repo = complaintRepo,
            storage = LocalStorageService("./uploads", "http://test"),
            voteRepo = voteRepo,
            notifications = notifService,
            audit = NoopAuditLogger
        )
    }

    private fun seedUser(email: String, role: UserRole = UserRole.RESIDENT): Long = transaction {
        Users.insert {
            it[Users.email] = email
            it[Users.passwordHash] = "x"
            it[Users.role] = role.name
            it[Users.emailVerified] = true
            it[Users.isActive] = true
            it[Users.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
            it[Users.passwordChangedAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }[Users.id]
    }

    private fun seedComplaint(authorId: Long): Long = transaction {
        val cid = Complaints.insert {
            it[Complaints.authorId] = authorId
            it[Complaints.category] = ProblemCategory.GARBAGE.name
            it[Complaints.title] = "Мусор · Транспортная"
            it[Complaints.description] = "куча мусора"
            it[Complaints.latitude] = 43.6
            it[Complaints.longitude] = 39.7
            it[Complaints.address] = "ул. Транспортная, 1"
            it[Complaints.status] = "NEW"
            it[Complaints.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
            it[Complaints.updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }[Complaints.id]
        // автоголос автора (как делает ComplaintService.create на Day 5)
        Votes.insert {
            it[Votes.complaintId] = cid
            it[Votes.userId] = authorId
            it[Votes.value] = 1
            it[Votes.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }
        cid
    }

    private fun seedVote(complaintId: Long, userId: Long) = transaction {
        Votes.insert {
            it[Votes.complaintId] = complaintId
            it[Votes.userId] = userId
            it[Votes.value] = 1
            it[Votes.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }
    }

    @Test
    fun `IN_PROGRESS notifies only author`() {
        val author = seedUser("a@x.ru")
        val supporter = seedUser("s@x.ru")
        val admin = seedUser("adm@x.ru", UserRole.ADMIN)
        val cid = seedComplaint(author)
        seedVote(cid, supporter)

        service.changeStatus(
            complaintId = cid,
            actor = Viewer.Authenticated(admin, UserRole.ADMIN),
            req = ChangeStatusRequest(toStatus = ComplaintStatus.IN_PROGRESS, comment = "Бригада выехала"),
            ip = "127.0.0.1", userAgent = "test"
        )

        val rows = transaction {
            Notifications.selectAll()
                .where { Notifications.complaintId eq cid }
                .toList()
        }
        assertEquals(1, rows.size)
        val r = rows.single()
        assertEquals(author, r[Notifications.userId])
        assertEquals("COMPLAINT_STATUS", r[Notifications.kind])
        assertTrue(r[Notifications.body].contains("Бригада выехала"))
        assertEquals("INFO", r[Notifications.iconStyle])
    }

    @Test
    fun `REJECTED notifies author plus supporters with dedup`() {
        val author = seedUser("a@x.ru")
        val supporter1 = seedUser("s1@x.ru")
        val supporter2 = seedUser("s2@x.ru")
        val outsider = seedUser("o@x.ru")
        val admin = seedUser("adm@x.ru", UserRole.ADMIN)
        val cid = seedComplaint(author)  // автоголос автора уже стоит
        seedVote(cid, supporter1)
        seedVote(cid, supporter2)

        service.changeStatus(
            complaintId = cid,
            actor = Viewer.Authenticated(admin, UserRole.ADMIN),
            req = ChangeStatusRequest(
                toStatus = ComplaintStatus.REJECTED,
                comment = "Не подтверждено инспектором"
            ),
            ip = "1.1.1.1", userAgent = "t"
        )

        val rows = transaction {
            Notifications.selectAll().where { Notifications.complaintId eq cid }.toList()
        }

        // author (1) + supporter1 (1) + supporter2 (1) = 3.
        // Автор имеет автоголос — без дедупа было бы 4. Дедуп срабатывает.
        assertEquals(3, rows.size)
        val userIds = rows.map { it[Notifications.userId] }.toSet()
        assertEquals(setOf(author, supporter1, supporter2), userIds)
        assertTrue(outsider !in userIds)

        rows.forEach { r ->
            assertTrue(r[Notifications.body].contains("Комментарий администрации: Не подтверждено инспектором"))
            assertEquals("WARNING", r[Notifications.iconStyle])
        }
    }

    @Test
    fun `rollback when notification insert fails — status not changed`() {
        val author = seedUser("a@x.ru")
        val admin = seedUser("adm@x.ru", UserRole.ADMIN)
        val cid = seedComplaint(author)

        // Подменяем NotificationService на ломающийся
        val brokenService = ComplaintService(
            repo = complaintRepo,
            storage = LocalStorageService("./uploads", "http://test"),
            voteRepo = voteRepo,
            notifications = object : com.example.cleancity.notifications.NotificationService {
                override fun notify(
                    recipientUserIds: List<Long>,
                    kind: com.example.cleancity.shared.models.NotificationKind,
                    title: String, body: String,
                    iconStyle: String?,
                    complaintId: Long?, announcementId: Long?
                ) {
                    throw IllegalStateException("simulated DB failure")
                }
            },
            audit = NoopAuditLogger
        )

        runCatching {
            brokenService.changeStatus(
                complaintId = cid,
                actor = Viewer.Authenticated(admin, UserRole.ADMIN),
                req = ChangeStatusRequest(toStatus = ComplaintStatus.IN_PROGRESS, comment = "test"),
                ip = "1.1.1.1", userAgent = "t"
            )
        }.exceptionOrNull().also {
            assertTrue(it is IllegalStateException, "expected propagated exception, got $it")
        }

        // Статус не сменился
        val status = transaction { complaintRepo.findById(cid)!!.status }
        assertEquals(ComplaintStatus.NEW, status)

        // Запись в status_changes не появилась
        val sc = transaction {
            StatusChanges.selectAll().where { StatusChanges.complaintId eq cid }.count()
        }
        assertEquals(0, sc)
    }
}
```

- [ ] **Step 2: Запустить — упадёт (нет `DbNotificationService`, метод `notify` не существует на интерфейсе)**

```bash
./gradlew :backend:test --tests "com.example.cleancity.complaints.ComplaintStatusNotificationTest" 2>&1 | tail -20
```

Ожидается: `unresolved reference: DbNotificationService` и `unresolved reference: notify`.

- [ ] **Step 3: Переписать `NotificationService` интерфейс**

Файл `backend/src/main/kotlin/com/example/cleancity/notifications/NotificationService.kt` — заменить целиком:

```kotlin
package com.example.cleancity.notifications

import com.example.cleancity.shared.models.NotificationKind

/**
 * Точка вызова уведомлений из бизнес-логики. SPEC.md §4.6, §5.2.
 *
 * Spec 1 (2026-05-11): единственная реализация — DbNotificationService,
 * который пишет в таблицу notifications. Mobile-клиент опрашивает её через
 * GET /notifications.
 *
 * Future-spec (FCM): FcmNotificationService будет декоратором над
 * DbNotificationService: сначала INSERT, затем (best-effort) FCM-вызов.
 * Бизнес-логика не меняется.
 */
interface NotificationService {

    /**
     * Batch INSERT one row per recipient. Если recipientUserIds пуст — no-op.
     *
     * Контракт по target:
     *   - kind=COMPLAINT_STATUS → complaintId != null, announcementId == null
     *   - kind=ANNOUNCEMENT     → announcementId != null, complaintId == null
     *
     * Должен вызываться внутри транзакции, объединяющей бизнес-изменения
     * (например, UPDATE complaints + INSERT status_changes), чтобы при
     * любом сбое всё откатывалось атомарно.
     */
    fun notify(
        recipientUserIds: List<Long>,
        kind: NotificationKind,
        title: String,
        body: String,
        iconStyle: String? = null,
        complaintId: Long? = null,
        announcementId: Long? = null
    )
}
```

Удалить из этого файла класс `NoopNotificationService` (он был ниже интерфейса).

- [ ] **Step 4: Создать `DbNotificationService`**

Файл `backend/src/main/kotlin/com/example/cleancity/notifications/DbNotificationService.kt`:

```kotlin
package com.example.cleancity.notifications

import com.example.cleancity.shared.models.NotificationKind
import org.slf4j.LoggerFactory

class DbNotificationService(
    private val repository: NotificationRepository
) : NotificationService {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun notify(
        recipientUserIds: List<Long>,
        kind: NotificationKind,
        title: String,
        body: String,
        iconStyle: String?,
        complaintId: Long?,
        announcementId: Long?
    ) {
        if (recipientUserIds.isEmpty()) return
        val unique = recipientUserIds.distinct()
        repository.insertBatch(
            userIds = unique,
            kind = kind,
            title = title,
            body = body,
            iconStyle = iconStyle,
            complaintId = complaintId,
            announcementId = announcementId
        )
        log.info(
            "Notified {} users (deduped from {}): kind={} complaintId={} announcementId={}",
            unique.size, recipientUserIds.size, kind, complaintId, announcementId
        )
    }
}
```

- [ ] **Step 5: Переписать `ComplaintService.changeStatus` lines 252-302**

Файл `backend/src/main/kotlin/com/example/cleancity/complaints/ComplaintService.kt`. Найти блок `// Транзакция: UPDATE complaints + INSERT status_changes + ... merge голосов.` (примерно строка 252) и блок `// Push (Day 5 — no-op заглушка):` (строка 283-302). Заменить оба блока на следующий:

```kotlin
// Транзакция: UPDATE complaints + INSERT status_changes + (опц.) merge голосов
// + INSERT в notifications. Если что-то падает — всё откатывается атомарно;
// статус жалобы не должен меняться без уведомления (SPEC §5.2, Spec 1 §2.5).
val recipientsPreview = when (req.toStatus) {
    ComplaintStatus.IN_PROGRESS, ComplaintStatus.RESOLVED -> listOf(current.authorId)
    ComplaintStatus.REJECTED, ComplaintStatus.DUPLICATE -> {
        // listSupporterIds читает БД — выполняем тоже внутри транзакции ниже
        emptyList()
    }
    ComplaintStatus.NEW -> emptyList()
}

transaction {
    repo.updateStatus(
        complaintId = complaintId,
        newStatus = req.toStatus,
        duplicateOfId = duplicateOfId,
        setResolvedNow = setResolvedNow
    )
    repo.insertStatusChange(
        complaintId = complaintId,
        fromStatus = current.status,
        toStatus = req.toStatus,
        comment = comment,
        changedById = actor.userId
    )
    if (req.toStatus == ComplaintStatus.DUPLICATE && duplicateOfId != null) {
        voteRepo.mergeVotesInto(originalId = duplicateOfId, duplicateId = complaintId)
    }

    val recipients = when (req.toStatus) {
        ComplaintStatus.IN_PROGRESS, ComplaintStatus.RESOLVED -> recipientsPreview
        ComplaintStatus.REJECTED, ComplaintStatus.DUPLICATE -> {
            val supporters = voteRepo.listSupporterIds(complaintId)
            (supporters + current.authorId).distinct()
        }
        ComplaintStatus.NEW -> emptyList()
    }

    if (recipients.isNotEmpty()) {
        val text = com.example.cleancity.notifications.NotificationTexts.statusChange(
            complaintTitle = current.title,
            toStatus = req.toStatus,
            adminComment = comment
        )
        notifications.notify(
            recipientUserIds = recipients,
            kind = com.example.cleancity.shared.models.NotificationKind.COMPLAINT_STATUS,
            title = text.title,
            body = text.body,
            iconStyle = text.iconStyle,
            complaintId = complaintId
        )
    }
}

audit.log(
    action = AuditAction.COMPLAINT_STATUS_CHANGE,
    actorUserId = actor.userId,
    targetType = "complaint",
    targetId = complaintId.toString(),
    ip = ip,
    userAgent = userAgent,
    metadata = """{"from":"${current.status}","to":"${req.toStatus}"""" +
        (duplicateOfId?.let { ""","duplicate_of_id":$it""" } ?: "") + "}"
)

return getById(complaintId, actor)
    ?: error("Complaint just updated but not visible, id=$complaintId")
```

Импорты (наверху файла) добавить:
```kotlin
import com.example.cleancity.notifications.NotificationTexts
import com.example.cleancity.shared.models.NotificationKind
```

И тогда блок выше упростится — заменить `com.example.cleancity.notifications.NotificationTexts.statusChange(...)` на `NotificationTexts.statusChange(...)` и `com.example.cleancity.shared.models.NotificationKind.COMPLAINT_STATUS` на `NotificationKind.COMPLAINT_STATUS`.

- [ ] **Step 6: Обновить `Application.kt` — заменить `NoopNotificationService` на `DbNotificationService`**

Файл `backend/src/main/kotlin/com/example/cleancity/Application.kt`. Две правки:

**Правка 1: импорт (строка 19):**
```kotlin
// Было:
import com.example.cleancity.notifications.NoopNotificationService

// Стало:
import com.example.cleancity.notifications.DbNotificationService
import com.example.cleancity.notifications.NotificationRepository
```

**Правка 2: биндинг (строка 115):**
```kotlin
// Было:
val notificationService = NoopNotificationService()

// Стало:
val notificationRepository = NotificationRepository()
val notificationService = DbNotificationService(notificationRepository)
```

- [ ] **Step 7: Запустить тест — должен пройти**

```bash
./gradlew :backend:test --tests "com.example.cleancity.complaints.ComplaintStatusNotificationTest" 2>&1 | tail -25
```

Ожидается: 3 tests passed (`IN_PROGRESS notifies only author`, `REJECTED notifies author plus supporters with dedup`, `rollback when notification insert fails — status not changed`).

Если падает на rollback-тесте — проверь, что вызов `notify(...)` находится **внутри** `transaction { }` блока в `ComplaintService.changeStatus`. Если не падает на dedup — проверь, что `DbNotificationService.notify` делает `recipientUserIds.distinct()`.

- [ ] **Step 8: Запустить ВЕСЬ test suite — ничего не сломалось**

```bash
./gradlew :backend:test 2>&1 | tail -20
```

Ожидается: все existing-тесты зелёные. Особенно проверь, что не сломались тесты в `complaints/` (если есть прежние с ComplaintService).

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/kotlin/com/example/cleancity/notifications/NotificationService.kt \
        backend/src/main/kotlin/com/example/cleancity/notifications/DbNotificationService.kt \
        backend/src/main/kotlin/com/example/cleancity/complaints/ComplaintService.kt \
        backend/src/main/kotlin/com/example/cleancity/Application.kt \
        backend/src/test/kotlin/com/example/cleancity/complaints/ComplaintStatusNotificationTest.kt
git commit -m "feat(notifications): wire DbNotificationService inside status-change transaction"
```

---

## Task 5: REST API — `NotificationRoutes` + integration тесты

**Files:**
- Create: `backend/src/main/kotlin/com/example/cleancity/notifications/NotificationRoutes.kt`
- Create: `backend/src/test/kotlin/com/example/cleancity/notifications/NotificationRoutesTest.kt`
- Modify: `backend/src/main/kotlin/com/example/cleancity/Application.kt` — добавить регистрацию `notificationRoutes(...)` в `routing { }`

- [ ] **Step 1: Написать падающий route-тест**

Файл `backend/src/test/kotlin/com/example/cleancity/notifications/NotificationRoutesTest.kt`:

```kotlin
package com.example.cleancity.notifications

import com.example.cleancity.auth.JwtConfig
import com.example.cleancity.database.tables.Complaints
import com.example.cleancity.database.tables.Notifications
import com.example.cleancity.database.tables.Users
import com.example.cleancity.shared.models.NotificationKind
import com.example.cleancity.shared.models.NotificationListResponse
import com.example.cleancity.shared.models.UnreadCountResponse
import com.example.cleancity.shared.models.MarkAllReadResponse
import com.example.cleancity.shared.models.UserRole
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationRoutesTest {

    private val jwtConfig = JwtConfig(
        secret = "test-secret-at-least-32-bytes-long-for-tests-only-1234567890",
        issuer = "cleancity-test",
        audience = "cleancity-test-api"
    )

    private fun initDb(): Triple<Long, Long, Long> {
        Database.connect(
            "jdbc:h2:mem:routes-${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )
        return transaction {
            SchemaUtils.drop(Notifications, Complaints, Users)
            SchemaUtils.create(Users, Complaints, Notifications)
            val a = Users.insert {
                it[Users.email] = "a@x.ru"; it[Users.passwordHash] = "x"
                it[Users.role] = UserRole.RESIDENT.name
                it[Users.emailVerified] = true; it[Users.isActive] = true
                it[Users.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
                it[Users.passwordChangedAt] = OffsetDateTime.now(ZoneOffset.UTC)
            }[Users.id]
            val b = Users.insert {
                it[Users.email] = "b@x.ru"; it[Users.passwordHash] = "x"
                it[Users.role] = UserRole.RESIDENT.name
                it[Users.emailVerified] = true; it[Users.isActive] = true
                it[Users.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
                it[Users.passwordChangedAt] = OffsetDateTime.now(ZoneOffset.UTC)
            }[Users.id]
            val cid = Complaints.insert {
                it[Complaints.authorId] = a; it[Complaints.category] = "GARBAGE"
                it[Complaints.title] = "t"; it[Complaints.description] = "d"
                it[Complaints.latitude] = 43.6; it[Complaints.longitude] = 39.7
                it[Complaints.address] = "addr"; it[Complaints.status] = "NEW"
                it[Complaints.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
                it[Complaints.updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
            }[Complaints.id]
            Triple(a, b, cid)
        }
    }

    private fun seedNotification(userId: Long, complaintId: Long, title: String, readAt: OffsetDateTime? = null, createdAt: OffsetDateTime? = null): Long = transaction {
        Notifications.insert {
            it[Notifications.userId] = userId
            it[Notifications.kind] = "COMPLAINT_STATUS"
            it[Notifications.title] = title; it[Notifications.body] = "x"
            it[Notifications.iconStyle] = "INFO"
            it[Notifications.complaintId] = complaintId
            it[Notifications.createdAt] = createdAt ?: OffsetDateTime.now(ZoneOffset.UTC)
            it[Notifications.readAt] = readAt
        }[Notifications.id]
    }

    private fun bearerFor(userId: Long): String =
        jwtConfig.issueAccessToken(userId = userId, role = UserRole.RESIDENT).token

    private fun ApplicationTestBuilder.appWith(repo: NotificationRepository) {
        application {
            install(ContentNegotiation) { json() }
            install(Authentication) {
                jwt("auth-jwt") {
                    verifier(jwtConfig.verifier)
                    validate { credential ->
                        if (credential.payload.subject != null) JWTPrincipal(credential.payload) else null
                    }
                }
            }
            routing { notificationRoutes(repo) }
        }
    }

    @Test
    fun `GET notifications without JWT returns 401`() = testApplication {
        initDb()
        val repo = NotificationRepository()
        appWith(repo)

        val resp = client.get("/notifications")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `GET notifications returns only own items`() = testApplication {
        val (a, b, cid) = initDb()
        repeat(3) { seedNotification(a, cid, "for-a-$it") }
        repeat(2) { seedNotification(b, cid, "for-b-$it") }

        val repo = NotificationRepository()
        appWith(repo)

        val resp = client.get("/notifications") {
            header("Authorization", "Bearer ${bearerFor(a)}")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = Json.decodeFromString(NotificationListResponse.serializer(), resp.bodyAsText())
        assertEquals(3, body.total)
        assertEquals(3, body.items.size)
        body.items.forEach { assertTrue(it.title.startsWith("for-a-")) }
    }

    @Test
    fun `GET notifications honors limit and hasMore`() = testApplication {
        val (a, _, cid) = initDb()
        repeat(5) { seedNotification(a, cid, "n$it") }
        val repo = NotificationRepository()
        appWith(repo)

        val resp = client.get("/notifications?limit=2") {
            header("Authorization", "Bearer ${bearerFor(a)}")
        }
        val body = Json.decodeFromString(NotificationListResponse.serializer(), resp.bodyAsText())
        assertEquals(5, body.total)
        assertEquals(2, body.items.size)
        assertEquals(true, body.hasMore)

        val resp2 = client.get("/notifications?limit=2&offset=4") {
            header("Authorization", "Bearer ${bearerFor(a)}")
        }
        val body2 = Json.decodeFromString(NotificationListResponse.serializer(), resp2.bodyAsText())
        assertEquals(1, body2.items.size)
        assertEquals(false, body2.hasMore)
    }

    @Test
    fun `GET unread-count counts only unread`() = testApplication {
        val (a, _, cid) = initDb()
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        repeat(2) { seedNotification(a, cid, "u$it") }
        seedNotification(a, cid, "read", readAt = now)
        val repo = NotificationRepository()
        appWith(repo)

        val resp = client.get("/notifications/unread-count") {
            header("Authorization", "Bearer ${bearerFor(a)}")
        }
        val body = Json.decodeFromString(UnreadCountResponse.serializer(), resp.bodyAsText())
        assertEquals(2L, body.count)
    }

    @Test
    fun `PATCH read own returns 204 and idempotent`() = testApplication {
        val (a, _, cid) = initDb()
        val nid = seedNotification(a, cid, "x")
        val repo = NotificationRepository()
        appWith(repo)

        val r1 = client.patch("/notifications/$nid/read") {
            header("Authorization", "Bearer ${bearerFor(a)}")
        }
        assertEquals(HttpStatusCode.NoContent, r1.status)

        // Повторно — тоже 204
        val r2 = client.patch("/notifications/$nid/read") {
            header("Authorization", "Bearer ${bearerFor(a)}")
        }
        assertEquals(HttpStatusCode.NoContent, r2.status)
    }

    @Test
    fun `PATCH read of foreign notification returns 404`() = testApplication {
        val (a, b, cid) = initDb()
        val nid = seedNotification(a, cid, "x")
        val repo = NotificationRepository()
        appWith(repo)

        val resp = client.patch("/notifications/$nid/read") {
            header("Authorization", "Bearer ${bearerFor(b)}")
        }
        assertEquals(HttpStatusCode.NotFound, resp.status)
    }

    @Test
    fun `PATCH read-all marks own unread and returns count`() = testApplication {
        val (a, b, cid) = initDb()
        repeat(3) { seedNotification(a, cid, "u$it") }
        seedNotification(a, cid, "alreadyRead", readAt = OffsetDateTime.now(ZoneOffset.UTC))
        seedNotification(b, cid, "for-b")
        val repo = NotificationRepository()
        appWith(repo)

        val resp = client.patch("/notifications/read-all") {
            header("Authorization", "Bearer ${bearerFor(a)}")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = Json.decodeFromString(MarkAllReadResponse.serializer(), resp.bodyAsText())
        assertEquals(3, body.markedCount)
    }
}
```

> **Замечание:** `JwtConfig.issueAccessToken(userId, role): IssuedToken` — реальная сигнатура. Получаем строку через `.token`. Класс `IssuedToken` — `data class IssuedToken(val token: String, val expiresInSeconds: Long)` (см. `JwtConfig.kt`).

- [ ] **Step 2: Запустить — упадёт (нет `notificationRoutes`)**

```bash
./gradlew :backend:test --tests "com.example.cleancity.notifications.NotificationRoutesTest" 2>&1 | tail -15
```

Ожидается: `unresolved reference: notificationRoutes`.

- [ ] **Step 3: Реализовать `NotificationRoutes`**

Файл `backend/src/main/kotlin/com/example/cleancity/notifications/NotificationRoutes.kt`:

```kotlin
package com.example.cleancity.notifications

import com.example.cleancity.shared.models.MarkAllReadResponse
import com.example.cleancity.shared.models.NotificationKind
import com.example.cleancity.shared.models.NotificationListResponse
import com.example.cleancity.shared.models.NotificationResponse
import com.example.cleancity.shared.models.UnreadCountResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * In-app notifications endpoints. SPEC.md §4.6.
 *
 * Все ручки требуют JWT. Каждая фильтрует по currentUserId — юзер видит
 * только свои уведомления; PATCH чужого id → 404 (не 403), чтобы не утечь
 * существование чужих ID.
 */
fun Route.notificationRoutes(repo: NotificationRepository) {
    authenticate("auth-jwt") {
        route("/notifications") {

            get {
                val userId = call.userId() ?: return@get
                val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 50).coerceIn(1, 100)
                val offset = (call.request.queryParameters["offset"]?.toIntOrNull() ?: 0).coerceAtLeast(0)

                val (rows, total) = repo.listForUser(userId, limit, offset)
                val items = rows.map { row ->
                    NotificationResponse(
                        id = row.id,
                        kind = row.kind,
                        title = row.title,
                        body = row.body,
                        iconStyle = row.iconStyle,
                        complaintId = row.complaintId,
                        announcementId = row.announcementId,
                        readAt = row.readAt?.toString(),
                        createdAt = row.createdAt.toString()
                    )
                }
                call.respond(NotificationListResponse(
                    items = items,
                    total = total,
                    hasMore = items.size == limit
                ))
            }

            get("/unread-count") {
                val userId = call.userId() ?: return@get
                call.respond(UnreadCountResponse(count = repo.countUnreadForUser(userId)))
            }

            patch("/{id}/read") {
                val userId = call.userId() ?: return@patch
                val id = call.parameters["id"]?.toLongOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid id"))
                    return@patch
                }
                val ok = repo.markRead(notificationId = id, userId = userId)
                if (!ok) {
                    call.respond(HttpStatusCode.NotFound, mapOf("message" to "Notification not found"))
                    return@patch
                }
                call.respond(HttpStatusCode.NoContent)
            }

            patch("/read-all") {
                val userId = call.userId() ?: return@patch
                val count = repo.markAllRead(userId)
                call.respond(MarkAllReadResponse(markedCount = count))
            }
        }
    }
}

private suspend fun ApplicationCall.userId(): Long? {
    val sub = principal<JWTPrincipal>()?.payload?.subject?.toLongOrNull()
    if (sub == null) {
        respond(HttpStatusCode.Unauthorized, mapOf("message" to "Not authenticated"))
        return null
    }
    return sub
}
```

- [ ] **Step 4: Зарегистрировать роуты в `Application.kt`**

Файл `backend/src/main/kotlin/com/example/cleancity/Application.kt`. В блоке `routing { }` (примерно после `voteRoutes(voteService)`) добавить:

```kotlin
notificationRoutes(notificationRepository)
```

И добавить импорт наверху:

```kotlin
import com.example.cleancity.notifications.notificationRoutes
```

- [ ] **Step 5: Запустить тесты**

```bash
./gradlew :backend:test --tests "com.example.cleancity.notifications.NotificationRoutesTest" 2>&1 | tail -25
```

Ожидается: 7 tests passed.

Если падает на route ordering (`/notifications/read-all` ловится как `/{id}/read`) — проверь порядок объявлений в route block. `/unread-count` и `/read-all` должны идти до или быть на не-конфликтующих путях. Ktor matches по специфичности — буквальный путь побеждает параметр, но порядок не лишним сделать.

- [ ] **Step 6: Запустить ВЕСЬ test suite**

```bash
./gradlew :backend:test 2>&1 | tail -20
```

Ожидается: всё зелёное.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/kotlin/com/example/cleancity/notifications/NotificationRoutes.kt \
        backend/src/main/kotlin/com/example/cleancity/Application.kt \
        backend/src/test/kotlin/com/example/cleancity/notifications/NotificationRoutesTest.kt
git commit -m "feat(notifications): rest endpoints (list, unread-count, read, read-all)"
```

---

## Task 6: Manual smoke check (end-to-end через docker compose)

**Files:** none — это ручная проверка production-like flow.

- [ ] **Step 1: Поднять stack локально**

```bash
cd ~/Desktop/Myapp/cleancity-kmp
docker compose down -v  # сброс данных от предыдущих запусков
docker compose up -d --build
sleep 5
curl -sS http://localhost:8080/health
```

Ожидается: `{"status":"ok"}`.

- [ ] **Step 2: Зарегистрировать двух резидентов и админа**

```bash
# Резидент A
curl -sS -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"a@test.local","password":"password123","fullName":"Author A","acceptedTerms":true}'

# Резидент B
curl -sS -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"b@test.local","password":"password123","fullName":"Supporter B","acceptedTerms":true}'
```

Достать verify-ссылки из логов (`docker compose logs backend | grep "verify"`) или, если SMTP не настроен, — посмотреть таблицу `email_tokens`:

```bash
docker compose exec db psql -U cleancity -d cleancity \
  -c "SELECT user_id, token FROM email_tokens WHERE purpose='VERIFY_EMAIL';"
```

Verify каждого:

```bash
curl -sS -X POST http://localhost:8080/auth/verify-email \
  -H "Content-Type: application/json" \
  -d '{"token":"<TOKEN_A>"}'
# повторить для B
```

Создать админа напрямую в БД (быстрее, чем через invite-flow):

```bash
docker compose exec db psql -U cleancity -d cleancity -c "
UPDATE users SET role='ADMIN', email_verified=true WHERE email='a@test.local';
"
```

Wait, это переделает A в админа. Лучше: зарегистрировать админа через `/auth/register` как обычного резидента, верифицировать email, потом руками поднять роль до ADMIN.

```bash
# 1. Зарегистрировать
curl -sS -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@test.local","password":"admin12345","fullName":"Test Admin","acceptedTerms":true}'

# 2. Верифицировать (взять токен из email_tokens):
ADMIN_TOKEN=$(docker compose exec -T db psql -U cleancity -d cleancity -tA -c \
  "SELECT token FROM email_tokens WHERE user_id=(SELECT id FROM users WHERE email='admin@test.local') AND purpose='VERIFY_EMAIL';")
curl -sS -X POST http://localhost:8080/auth/verify-email \
  -H "Content-Type: application/json" \
  -d "{\"token\":\"$ADMIN_TOKEN\"}"

# 3. Поднять до ADMIN:
docker compose exec db psql -U cleancity -d cleancity -c \
  "UPDATE users SET role='ADMIN' WHERE email='admin@test.local';"
```

- [ ] **Step 3: Залогинить всех троих, сохранить токены**

```bash
TOKEN_A=$(curl -sS -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"a@test.local","password":"password123"}' | jq -r .accessToken)

TOKEN_B=$(curl -sS -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"b@test.local","password":"password123"}' | jq -r .accessToken)

TOKEN_ADMIN=$(curl -sS -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@test.local","password":"admin12345"}' | jq -r .accessToken)

echo "A=$TOKEN_A"; echo "B=$TOKEN_B"; echo "ADMIN=$TOKEN_ADMIN"
```

Все три значения должны быть непустыми.

- [ ] **Step 4: A создаёт жалобу с одним фото**

```bash
# Подготовить тестовое JPEG (1×1 пиксель)
echo -e '\xff\xd8\xff\xe0\x00\x10JFIF\x00\x01\x01\x00\x00\x01\x00\x01\x00\x00\xff\xdb\x00C\x00\x08\x06\x06\x07\x06\x05\x08\x07\x07\x07\t\t\x08\n\x0c\x14\r\x0c\x0b\x0b\x0c\x19\x12\x13\x0f\x14\x1d\x1a\x1f\x1e\x1d\x1a\x1c\x1c $.\x27 ",#\x1c\x1c(7),01444\x1f\x27=9=82<.342\xff\xc0\x00\x0b\x08\x00\x01\x00\x01\x01\x01\x11\x00\xff\xc4\x00\x14\x00\x01\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\xff\xc4\x00\x14\x10\x01\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\xff\xda\x00\x08\x01\x01\x00\x00?\x00\x7f\xff\xd9' > /tmp/pixel.jpg

COMPLAINT_ID=$(curl -sS -X POST http://localhost:8080/complaints \
  -H "Authorization: Bearer $TOKEN_A" \
  -F 'data={"category":"GARBAGE","description":"Куча мусора у дороги","latitude":43.5855,"longitude":39.7231,"address":"ул. Транспортная, 1","district":"Центральный"};type=application/json' \
  -F "photos=@/tmp/pixel.jpg" \
  | jq -r .id)

echo "COMPLAINT_ID=$COMPLAINT_ID"
```

Ожидается: непустой числовой id.

- [ ] **Step 5: B голосует +1 за жалобу A**

```bash
curl -sS -X POST "http://localhost:8080/complaints/$COMPLAINT_ID/votes" \
  -H "Authorization: Bearer $TOKEN_B"
```

Ожидается: 200, `{"votesCount":2,"userVoted":true}` (1 от A через автоголос + 1 от B).

- [ ] **Step 6: Админ переводит в REJECTED**

```bash
curl -sS -X PATCH "http://localhost:8080/complaints/$COMPLAINT_ID/status" \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H "Content-Type: application/json" \
  -d '{"toStatus":"REJECTED","comment":"Не подтверждено инспектором по факту выезда"}'
```

Ожидается: 200, `status: "REJECTED"`.

- [ ] **Step 7: Проверить уведомления у A**

```bash
curl -sS "http://localhost:8080/notifications" \
  -H "Authorization: Bearer $TOKEN_A" | jq
```

Ожидается:
- `total: 1`
- `items[0].kind == "COMPLAINT_STATUS"`
- `items[0].title == "Жалоба отклонена"`
- `items[0].body` содержит «Не подтверждено инспектором по факту выезда»
- `items[0].iconStyle == "WARNING"`
- `items[0].complaintId == $COMPLAINT_ID`
- `items[0].readAt == null`

```bash
curl -sS "http://localhost:8080/notifications/unread-count" \
  -H "Authorization: Bearer $TOKEN_A" | jq
```

Ожидается: `{"count": 1}`.

- [ ] **Step 8: Проверить уведомления у B (поддерживавшего)**

```bash
curl -sS "http://localhost:8080/notifications" \
  -H "Authorization: Bearer $TOKEN_B" | jq
```

Ожидается: тот же `total: 1`, тот же body с комментарием админа.

- [ ] **Step 9: Mark-as-read + unread-count → 0**

```bash
NOTIF_ID=$(curl -sS "http://localhost:8080/notifications" \
  -H "Authorization: Bearer $TOKEN_A" | jq -r '.items[0].id')

curl -sS -i -X PATCH "http://localhost:8080/notifications/$NOTIF_ID/read" \
  -H "Authorization: Bearer $TOKEN_A"
```

Ожидается: `HTTP/1.1 204 No Content`.

```bash
curl -sS "http://localhost:8080/notifications/unread-count" \
  -H "Authorization: Bearer $TOKEN_A" | jq
```

Ожидается: `{"count": 0}`.

- [ ] **Step 10: B пытается прочитать уведомление A → 404**

```bash
curl -sS -i -X PATCH "http://localhost:8080/notifications/$NOTIF_ID/read" \
  -H "Authorization: Bearer $TOKEN_B"
```

Ожидается: `HTTP/1.1 404 Not Found` (не 403, не 200, не 500).

- [ ] **Step 11: Снять stack**

```bash
docker compose down
```

- [ ] **Step 12: Финальный коммит — обновить PLAN.md**

Зачеркнуть в `docs/PLAN.md` пункт **«Day 6 — In-app уведомления»** (только часть про in-app + status триггер; объявления и аналитика — отдельные спеки).

В блоке Day 6 (строки 116–141) пометить выполнено:
- `- [x] Миграция V5: \`announcements\`, V6: \`push_tokens\`, V7: \`notifications\``  → пометить как сделана **только** часть про `notifications` (V6 в этом цикле, остальное — Spec 2)
- `- [x] In-app уведомления endpoints` (4 ручки)

Или, чище: добавь под Day 6 примечание: «Spec 1 (push/in-app infra) выполнен 2026-05-NN, см. `docs/superpowers/specs/2026-05-11-notifications-infrastructure-design.md` и plan. Spec 2 (announcements) и Spec 3 (analytics) — отдельные циклы».

```bash
git add docs/PLAN.md
git commit -m "docs(plan): mark day 6 spec 1 (notifications infra) complete"
```

---

## Self-Review (для агента, выполняющего план)

После Task 5 пройди этот чеклист и зафиксируй галочкой:

- [ ] Все 4 endpoint'а из SPEC §4.6 реализованы и покрыты тестами
- [ ] `GET /notifications` фильтрует > 90 дней (тест в Task 1 Step 4 «filters out older than 90 days»)
- [ ] `PATCH /{id}/read` чужого ID возвращает 404 (тест в Task 5 Step 1 «PATCH read of foreign notification returns 404»)
- [ ] Триггер на смену статуса записывает 1 строку для IN_PROGRESS/RESOLVED, N строк для REJECTED/DUPLICATE (тесты в Task 4 Step 1)
- [ ] Дедупликация автора при наличии его автоголоса (тест в Task 4 «REJECTED notifies author plus supporters with dedup»)
- [ ] Rollback транзакции при сбое `notify()` оставляет статус прежним (тест в Task 4 «rollback when notification insert fails»)
- [ ] `NoopNotificationService` удалён из репозитория (`git grep NoopNotificationService` должен вернуть пусто)
- [ ] `push_tokens` миграция/endpoint **не добавлены** (out-of-scope per spec §1)
- [ ] Никакого FCM / Firebase Admin SDK не добавлено
- [ ] Никакого scheduler / cron не добавлено
- [ ] Все existing-тесты (`./gradlew :backend:test`) зелёные
