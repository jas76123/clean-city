# Day 16 — Web: жалобы + смена статусов — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Экран жалоб в веб-админке CleanCity — таблица с фильтрами, детальная панель, смена статусов; плюс backend-расширение `GET /complaints` (status/SLA-фильтры) и нормализация района.

**Architecture:** Backend-first. Сначала расширяем `GET /complaints` и нормализуем район, покрываем тестами; затем строим `web-admin/` страницу против реального API. Веб использует TanStack Query, native `<select>`/`<button>`-чипы и плоские overlay-модалки (без новых UI-зависимостей).

**Tech Stack:** Kotlin/Ktor/Exposed + H2 (тесты) · React 19 + TypeScript + Vite + TanStack Query v5 + Vitest + MSW · Tailwind v4.

**Дизайн-спека:** `docs/superpowers/specs/2026-05-22-day16-complaints-status-design.md`

---

## Структура файлов

### Backend / shared (изменяемые)
- `shared/.../models/District.kt` — + `fromGeocoderText()`
- `backend/.../complaints/ComplaintService.kt` — нормализация района в `create()`, SLA-поля, status-фильтр
- `backend/.../complaints/ComplaintRepository.kt` — `ComplaintFilter` += поля, `buildCondition` += условия
- `backend/.../complaints/ComplaintRoutes.kt` — `parsePublicFilter` += парсинг `status`/`slaBreached`
- `shared/.../models/ComplaintResponse.kt` — + `slaDeadline`, `slaBreached`
- `backend/src/main/resources/db/migration/V8__normalize_complaint_districts.sql` — новый

### Backend тесты (новые)
- `backend/src/test/.../complaints/DistrictNormalizationTest.kt`
- `backend/src/test/.../complaints/ComplaintFilterTest.kt`

### Web (новые, в `web-admin/src/`)
- `api/types.ts` (дополняем) · `api/complaints.ts` · `api/analytics.ts`
- `lib/complaintMeta.ts` (+ `.test.ts`)
- `hooks/complaintQueries.ts`
- `components/complaints/`: `StatusBadge.tsx` · `ComplaintFilters.tsx` (+ test) · `ComplaintsTable.tsx` · `ComplaintsPagination.tsx` · `PhotoGallery.tsx` · `StatusHistory.tsx` · `DuplicatePicker.tsx` · `StatusChangeDialog.tsx` (+ test) · `ComplaintDetailPanel.tsx`
- `pages/ComplaintsPage.tsx` (+ test) · `App.tsx` (правим маршрут)

---

# ЧАСТЬ A — Backend

## Task 1: `District.fromGeocoderText()`

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/cleancity/shared/models/District.kt`
- Test: `backend/src/test/kotlin/com/example/cleancity/complaints/DistrictNormalizationTest.kt`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/kotlin/com/example/cleancity/complaints/DistrictNormalizationTest.kt`:

```kotlin
package com.example.cleancity.complaints

import com.example.cleancity.shared.models.District
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DistrictNormalizationTest {

    @Test
    fun `fromGeocoderText распознаёт все 4 района по подстроке`() {
        assertEquals(District.CENTRAL, District.fromGeocoderText("Центральный район"))
        assertEquals(District.ADLER, District.fromGeocoderText("Адлерский внутригородской район"))
        assertEquals(District.KHOSTA, District.fromGeocoderText("Хостинский район г. Сочи"))
        assertEquals(District.LAZAREVSKOE, District.fromGeocoderText("Лазаревский район"))
    }

    @Test
    fun `fromGeocoderText регистронезависим`() {
        assertEquals(District.CENTRAL, District.fromGeocoderText("ЦЕНТРАЛЬНЫЙ"))
        assertEquals(District.ADLER, District.fromGeocoderText("адлер"))
    }

    @Test
    fun `fromGeocoderText на нераспознанном и пустом возвращает null`() {
        assertNull(District.fromGeocoderText("Краснодарский край"))
        assertNull(District.fromGeocoderText(""))
        assertNull(District.fromGeocoderText(null))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :backend:test --tests "*DistrictNormalizationTest"`
Expected: FAIL — `fromGeocoderText` не существует (ошибка компиляции).

- [ ] **Step 3: Add `fromGeocoderText` to District companion**

In `District.kt`, внутри `companion object` (после `fromLabelOrNull`):

```kotlin
        /**
         * Нормализует свободный текст геокодера к одному из 4 районов по подстроке.
         * Геокодер отдаёт строки вроде «Адлерский внутригородской район» — точное
         * сравнение по label их не ловит, поэтому матчим по корню слова.
         */
        fun fromGeocoderText(raw: String?): District? {
            if (raw.isNullOrBlank()) return null
            val s = raw.lowercase()
            return when {
                "центральн" in s -> CENTRAL
                "адлер" in s -> ADLER
                "хост" in s -> KHOSTA
                "лазаревск" in s -> LAZAREVSKOE
                else -> null
            }
        }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :backend:test --tests "*DistrictNormalizationTest"`
Expected: PASS (3 теста).

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/cleancity/shared/models/District.kt \
        backend/src/test/kotlin/com/example/cleancity/complaints/DistrictNormalizationTest.kt
git commit -m "feat(shared): District.fromGeocoderText — нормализация района по подстроке"
```

---

## Task 2: `ComplaintService.create()` нормализует район

**Files:**
- Modify: `backend/src/main/kotlin/com/example/cleancity/complaints/ComplaintService.kt`

**Контекст:** в `create()` сейчас `district = req.district?.trim()?.takeIf { it.isNotBlank() }` сохраняет сырой текст геокодера. Меняем на нормализацию. Автотеста нет — `create()` требует валидных JPEG-байтов, а фикстур изображений в проекте нет; логика нормализации полностью покрыта Task 1, корректность связки проверяется ручным чекпоинтом Day 16. Это осознанное отступление.

- [ ] **Step 1: Добавить импорт District**

В блоке импортов `ComplaintService.kt` добавить:

```kotlin
import com.example.cleancity.shared.models.District
```

- [ ] **Step 2: Заменить присвоение district в `create()`**

Найти в `fun create(...)` строку:

```kotlin
                district = req.district?.trim()?.takeIf { it.isNotBlank() }
```

Заменить на:

```kotlin
                // Нормализуем район геокодера к одному из 4 District (храним каноничный
                // label) — иначе фильтр по району в веб-админке не находит совпадений.
                district = District.fromGeocoderText(req.district)?.localizedLabel
```

- [ ] **Step 3: Скомпилировать и прогнать существующие тесты**

Run: `./gradlew :backend:test --tests "*Complaint*"`
Expected: PASS — все существующие complaint-тесты зелёные (они сидят жалобы напрямую через `Complaints.insert`, `create()` не затрагивают).

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/kotlin/com/example/cleancity/complaints/ComplaintService.kt
git commit -m "feat(backend): нормализовать район при создании жалобы"
```

---

## Task 3: Flyway-миграция нормализации старых районов

**Files:**
- Create: `backend/src/main/resources/db/migration/V8__normalize_complaint_districts.sql`

**Контекст:** последняя миграция — `V7`. Миграция выполняется только на Postgres (тесты используют `SchemaUtils`, не Flyway), поэтому `ILIKE` безопасен. Идемпотентна: повторный прогон на уже нормализованных данных — no-op.

- [ ] **Step 1: Создать файл миграции**

```sql
-- V8: нормализация complaints.district к 4 каноничным районам Сочи.
-- District хранился как свободный текст геокодера («Адлерский внутригородской
-- район» и т.п.) — приводим к каноничным label для точного фильтра в веб-админке.
UPDATE complaints
SET district = CASE
    WHEN district ILIKE '%центральн%'  THEN 'Центральный'
    WHEN district ILIKE '%адлер%'      THEN 'Адлерский'
    WHEN district ILIKE '%хост%'       THEN 'Хостинский'
    WHEN district ILIKE '%лазаревск%'  THEN 'Лазаревский'
    ELSE NULL
END
WHERE district IS NOT NULL;
```

- [ ] **Step 2: Проверить, что backend стартует с миграцией**

Run: `./gradlew :backend:compileKotlin`
Expected: BUILD SUCCESSFUL. (Полный прогон Flyway — на старте backend против Postgres, проверяется в ручном чекпоинте.)

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/db/migration/V8__normalize_complaint_districts.sql
git commit -m "feat(backend): миграция V8 — нормализация district существующих жалоб"
```

---

## Task 4: SLA-поля в `ComplaintResponse` (admin-only)

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/cleancity/shared/models/ComplaintResponse.kt`
- Modify: `backend/src/main/kotlin/com/example/cleancity/complaints/ComplaintService.kt`
- Test: `backend/src/test/kotlin/com/example/cleancity/complaints/ComplaintFilterTest.kt`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/kotlin/com/example/cleancity/complaints/ComplaintFilterTest.kt`:

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
import com.example.cleancity.shared.models.ComplaintStatus
import com.example.cleancity.shared.models.ProblemCategory
import com.example.cleancity.shared.models.UserRole
import com.example.cleancity.storage.LocalStorageService
import com.example.cleancity.votes.VoteRepository
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComplaintFilterTest {

    private lateinit var service: ComplaintService

    @BeforeTest
    fun setup() {
        Database.connect(
            "jdbc:h2:mem:cfilter-${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.drop(Notifications, Votes, StatusChanges, ComplaintPhotos, Complaints, AuditLog, Users)
            SchemaUtils.create(Users, Complaints, ComplaintPhotos, StatusChanges, Votes, AuditLog, Notifications)
        }
        service = ComplaintService(
            repo = ComplaintRepository(),
            storage = LocalStorageService("./uploads", "http://test"),
            voteRepo = VoteRepository(),
            notifications = DbNotificationService(NotificationRepository()),
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

    /** Сидит жалобу с заданными статусом/категорией/районом и возрастом (часов назад). */
    private fun seedComplaint(
        authorId: Long,
        status: ComplaintStatus,
        category: ProblemCategory = ProblemCategory.ROADS,
        district: String? = null,
        ageHours: Long = 1
    ): Long = transaction {
        val ts = OffsetDateTime.now(ZoneOffset.UTC).minusHours(ageHours)
        Complaints.insert {
            it[Complaints.authorId] = authorId
            it[Complaints.category] = category.name
            it[Complaints.title] = "T"
            it[Complaints.description] = "d"
            it[Complaints.latitude] = 43.6
            it[Complaints.longitude] = 39.7
            it[Complaints.address] = "addr"
            it[Complaints.district] = district
            it[Complaints.status] = status.name
            it[Complaints.createdAt] = ts
            it[Complaints.updatedAt] = ts
        }[Complaints.id]
    }

    private fun admin() = Viewer.Authenticated(seedUser("adm@x.ru", UserRole.ADMIN), UserRole.ADMIN)

    @Test
    fun `SLA-поля заполнены для админа`() {
        val a = admin()
        // ROADS норматив 72ч; возраст 100ч → просрочена
        seedComplaint(a.userId, ComplaintStatus.NEW, ProblemCategory.ROADS, ageHours = 100)
        val resp = service.list(a, PublicListFilter())
        val item = resp.items.single()
        assertTrue(item.slaBreached, "активная ROADS возрастом 100ч (норматив 72ч) — просрочена")
        assertTrue(item.slaDeadline != null)
    }

    @Test
    fun `SLA-поля пустые для резидента`() {
        val a = admin()
        seedComplaint(a.userId, ComplaintStatus.NEW, ProblemCategory.ROADS, ageHours = 100)
        val resident = Viewer.Authenticated(seedUser("r@x.ru"), UserRole.RESIDENT)
        val item = service.list(resident, PublicListFilter()).items.single()
        assertFalse(item.slaBreached)
        assertNull(item.slaDeadline)
    }

    @Test
    fun `RESOLVED не считается breached даже если стара`() {
        val a = admin()
        seedComplaint(a.userId, ComplaintStatus.RESOLVED, ProblemCategory.ROADS, ageHours = 500)
        val item = service.list(a, PublicListFilter()).items.single()
        assertFalse(item.slaBreached, "терминальные статусы не горят по SLA")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :backend:test --tests "*ComplaintFilterTest"`
Expected: FAIL — `slaBreached`/`slaDeadline` не существуют в `ComplaintResponse`.

- [ ] **Step 3: Добавить поля в `ComplaintResponse`**

В `ComplaintResponse.kt`, в `data class ComplaintResponse(...)`, после `val statusHistory: List<StatusChangeResponse> = emptyList()` — добавить запятую и поля:

```kotlin
    val statusHistory: List<StatusChangeResponse> = emptyList(),
    val slaDeadline: String? = null,
    val slaBreached: Boolean = false
```

- [ ] **Step 4: Реализовать вычисление SLA в `ComplaintService`**

В `ComplaintService.kt`, в `companion object` после `private val ADMIN_ROLES = ...` добавить:

```kotlin
        // Активные статусы, для которых SLA может «гореть» (terminal — никогда).
        private val SLA_ACTIVE = setOf(ComplaintStatus.NEW, ComplaintStatus.IN_PROGRESS)
```

Добавить приватный helper (рядом с другими private-методами, например после `visibleStatusesFor`):

```kotlin
    /**
     * SLA-метка для строки. SPEC §146: жителям SLA не видна нигде — для не-админа
     * возвращаем (null, false), поле не утекает в JSON.
     */
    private fun slaFor(row: ComplaintRow, viewer: Viewer): Pair<String?, Boolean> {
        val isAdmin = (viewer as? Viewer.Authenticated)?.role in ADMIN_ROLES
        if (!isAdmin) return null to false
        val deadline = row.createdAt.plusHours(CategorySla.hoursFor(row.category).toLong())
        val breached = row.status in SLA_ACTIVE &&
            OffsetDateTime.now(ZoneOffset.UTC).isAfter(deadline)
        return deadline.toString() to breached
    }
```

Добавить импорт в начало файла:

```kotlin
import com.example.cleancity.shared.models.CategorySla
```

(`OffsetDateTime`/`ZoneOffset` уже импортированы.)

- [ ] **Step 5: Прокинуть SLA в `toResponse`**

В `ComplaintService.kt` изменить сигнатуру `private fun ComplaintRow.toResponse(...)` — добавить два параметра и передать их в конструктор:

```kotlin
    private fun ComplaintRow.toResponse(
        photos: List<ComplaintPhotoResponse>,
        votesCount: Int,
        userVoted: Boolean,
        statusHistory: List<StatusChangeResponse>,
        slaDeadline: String? = null,
        slaBreached: Boolean = false
    ): ComplaintResponse =
        ComplaintResponse(
```

В теле конструктора `ComplaintResponse(...)` после `statusHistory = statusHistory` добавить:

```kotlin
            statusHistory = statusHistory,
            slaDeadline = slaDeadline,
            slaBreached = slaBreached
```

- [ ] **Step 6: Заполнить SLA в `enrichList` и `getById`**

В `enrichList`, в `rows.map { it.toResponse(...) }` добавить аргументы:

```kotlin
        val items = rows.map {
            val (deadline, breached) = slaFor(it, viewer)
            it.toResponse(
                photos = (photos[it.id] ?: emptyList()).toResponses(),
                votesCount = countsByComplaint[it.id] ?: 0,
                userVoted = it.id in votedSet,
                statusHistory = emptyList(),
                slaDeadline = deadline,
                slaBreached = breached
            )
        }
```

В `getById`, в `return row.toResponse(photos, votesCount, userVoted, history)` — заменить на:

```kotlin
        val (slaDeadline, slaBreached) = slaFor(row, viewer)
        return row.toResponse(photos, votesCount, userVoted, history, slaDeadline, slaBreached)
```

(`create()` оставляем как есть — там автор-резидент, SLA дефолтно null/false.)

- [ ] **Step 7: Run test to verify it passes**

Run: `./gradlew :backend:test --tests "*ComplaintFilterTest"`
Expected: PASS (3 теста).

- [ ] **Step 8: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/cleancity/shared/models/ComplaintResponse.kt \
        backend/src/main/kotlin/com/example/cleancity/complaints/ComplaintService.kt \
        backend/src/test/kotlin/com/example/cleancity/complaints/ComplaintFilterTest.kt
git commit -m "feat(backend): SLA-поля slaDeadline/slaBreached в ответе жалобы (admin-only)"
```

---

## Task 5: Фильтры `status` и `slaBreached` в `GET /complaints`

**Files:**
- Modify: `backend/src/main/kotlin/com/example/cleancity/complaints/ComplaintRepository.kt`
- Modify: `backend/src/main/kotlin/com/example/cleancity/complaints/ComplaintService.kt`
- Modify: `backend/src/main/kotlin/com/example/cleancity/complaints/ComplaintRoutes.kt`
- Test: `backend/src/test/kotlin/com/example/cleancity/complaints/ComplaintFilterTest.kt` (дополняем)

- [ ] **Step 1: Write the failing tests**

В `ComplaintFilterTest.kt` добавить тесты в конец класса (перед закрывающей `}`):

```kotlin
    @Test
    fun `фильтр по статусу возвращает только этот статус`() {
        val a = admin()
        seedComplaint(a.userId, ComplaintStatus.NEW)
        seedComplaint(a.userId, ComplaintStatus.IN_PROGRESS)
        seedComplaint(a.userId, ComplaintStatus.RESOLVED)
        val resp = service.list(a, PublicListFilter(status = ComplaintStatus.IN_PROGRESS))
        assertEquals(1, resp.items.size)
        assertEquals(ComplaintStatus.IN_PROGRESS, resp.items.single().status)
    }

    @Test
    fun `резидент с фильтром REJECTED получает пусто`() {
        val a = admin()
        seedComplaint(a.userId, ComplaintStatus.REJECTED)
        val resident = Viewer.Authenticated(seedUser("r@x.ru"), UserRole.RESIDENT)
        val resp = service.list(resident, PublicListFilter(status = ComplaintStatus.REJECTED))
        assertEquals(0, resp.items.size, "REJECTED не входит в visibleStatuses резидента")
    }

    @Test
    fun `админ с фильтром REJECTED видит REJECTED`() {
        val a = admin()
        seedComplaint(a.userId, ComplaintStatus.REJECTED)
        seedComplaint(a.userId, ComplaintStatus.NEW)
        val resp = service.list(a, PublicListFilter(status = ComplaintStatus.REJECTED))
        assertEquals(1, resp.items.size)
        assertEquals(ComplaintStatus.REJECTED, resp.items.single().status)
    }

    @Test
    fun `slaBreached фильтрует только просроченные активные`() {
        val a = admin()
        // ROADS норматив 72ч
        seedComplaint(a.userId, ComplaintStatus.NEW, ProblemCategory.ROADS, ageHours = 100)  // просрочена
        seedComplaint(a.userId, ComplaintStatus.NEW, ProblemCategory.ROADS, ageHours = 1)    // свежая
        seedComplaint(a.userId, ComplaintStatus.RESOLVED, ProblemCategory.ROADS, ageHours = 500) // не активна
        val resp = service.list(a, PublicListFilter(slaBreached = true))
        assertEquals(1, resp.items.size, "только просроченная активная жалоба")
        assertTrue(resp.items.single().slaBreached)
    }

    @Test
    fun `slaBreached учитывает норматив категории`() {
        val a = admin()
        // GARBAGE норматив 24ч; возраст 30ч → просрочена
        seedComplaint(a.userId, ComplaintStatus.NEW, ProblemCategory.GARBAGE, ageHours = 30)
        // OTHER норматив 120ч; возраст 30ч → НЕ просрочена
        seedComplaint(a.userId, ComplaintStatus.NEW, ProblemCategory.OTHER, ageHours = 30)
        val resp = service.list(a, PublicListFilter(slaBreached = true))
        assertEquals(1, resp.items.size)
        assertEquals(ProblemCategory.GARBAGE, resp.items.single().category)
    }

    @Test
    fun `фильтр по нормализованному району находит жалобу`() {
        val a = admin()
        seedComplaint(a.userId, ComplaintStatus.NEW, district = "Центральный")
        seedComplaint(a.userId, ComplaintStatus.NEW, district = "Адлерский")
        val resp = service.list(a, PublicListFilter(district = "Центральный"))
        assertEquals(1, resp.items.size)
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :backend:test --tests "*ComplaintFilterTest"`
Expected: FAIL — `PublicListFilter` не имеет параметров `status`/`slaBreached` (ошибка компиляции).

- [ ] **Step 3: Расширить `PublicListFilter`**

В `ComplaintService.kt` найти `data class PublicListFilter(...)` (в конце файла) и заменить на:

```kotlin
data class PublicListFilter(
    val category: ProblemCategory? = null,
    val district: String? = null,
    val sort: ComplaintSort = ComplaintSort.DATE,
    val page: Int = 0,
    val size: Int = 20,
    val status: ComplaintStatus? = null,
    val slaBreached: Boolean = false
)
```

- [ ] **Step 4: Расширить `ComplaintFilter`**

В `ComplaintRepository.kt` найти `data class ComplaintFilter(...)` и заменить на:

```kotlin
data class ComplaintFilter(
    val visibleStatuses: Set<ComplaintStatus>,
    val category: ProblemCategory? = null,
    val district: String? = null,
    val sort: ComplaintSort = ComplaintSort.DATE,
    val page: Int = 0,
    val size: Int = 20,
    val authorId: Long? = null,
    val status: ComplaintStatus? = null,
    val slaBreached: Boolean = false
)
```

- [ ] **Step 5: Прокинуть фильтры в `ComplaintService.list()`**

В `ComplaintService.kt`, в `fun list(...)`, в конструктор `ComplaintFilter(...)` добавить два аргумента:

```kotlin
        val (rows, total) = repo.list(
            ComplaintFilter(
                visibleStatuses = visibleStatusesFor(viewer),
                category = filter.category,
                district = filter.district,
                sort = filter.sort,
                page = filter.page,
                size = filter.size,
                status = filter.status,
                slaBreached = filter.slaBreached
            )
        )
```

- [ ] **Step 6: Расширить `buildCondition` + добавить SLA-условие**

В `ComplaintRepository.kt` заменить `buildCondition` на:

```kotlin
    private fun buildCondition(filter: ComplaintFilter): Op<Boolean> = with(SqlExpressionBuilder) {
        var op: Op<Boolean> = Complaints.status inList filter.visibleStatuses.map { it.name }
        if (filter.category != null) op = op and (Complaints.category eq filter.category.name)
        if (filter.district != null) op = op and (Complaints.district eq filter.district)
        if (filter.authorId != null) op = op and (Complaints.authorId eq filter.authorId)
        if (filter.status != null) op = op and (Complaints.status eq filter.status.name)
        if (filter.slaBreached) op = op and slaBreachedCondition()
        op
    }

    /**
     * Просроченные активные жалобы: статус NEW/IN_PROGRESS и created_at раньше
     * норматива §4.8. Пороги времени считаем в Kotlin (не raw SQL INTERVAL/EXTRACT) —
     * портабельно к H2-PostgreSQL-mode в тестах.
     */
    private fun slaBreachedCondition(): Op<Boolean> = with(SqlExpressionBuilder) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val active: Op<Boolean> = Complaints.status inList
            listOf(ComplaintStatus.NEW.name, ComplaintStatus.IN_PROGRESS.name)
        var overdue: Op<Boolean> = Op.FALSE
        ProblemCategory.entries.groupBy { CategorySla.hoursFor(it) }.forEach { (hours, cats) ->
            val cutoff = now.minusHours(hours.toLong())
            overdue = overdue or (
                (Complaints.category inList cats.map { it.name }) and
                    (Complaints.createdAt less cutoff)
                )
        }
        active and overdue
    }
```

Добавить импорты в начало `ComplaintRepository.kt` (если ещё нет):

```kotlin
import com.example.cleancity.shared.models.CategorySla
import com.example.cleancity.shared.models.ProblemCategory
import java.time.OffsetDateTime
import java.time.ZoneOffset
```

(Проверь существующие импорты — `ProblemCategory`/`OffsetDateTime`/`ZoneOffset` уже могут быть.)

- [ ] **Step 7: Парсить query-параметры в `ComplaintRoutes.kt`**

В `ComplaintRoutes.kt` заменить `parsePublicFilter()` на:

```kotlin
private fun ApplicationCall.parsePublicFilter(): PublicListFilter {
    val category = queryEnum<ProblemCategory>("category")
    val sort = (request.queryParameters["sort"]?.uppercase()?.let {
        runCatching { ComplaintSort.valueOf(it) }.getOrNull()
    }) ?: ComplaintSort.DATE
    val district = request.queryParameters["district"]?.takeIf { it.isNotBlank() }
    val page = (queryInt("page") ?: 0).coerceAtLeast(0)
    val size = (queryInt("size") ?: 20).coerceIn(1, MAX_PAGE_SIZE)
    val status = queryEnum<ComplaintStatus>("status")
    val slaBreached = request.queryParameters["slaBreached"]?.toBoolean() ?: false
    return PublicListFilter(category, district, sort, page, size, status, slaBreached)
}
```

Добавить импорт в `ComplaintRoutes.kt`:

```kotlin
import com.example.cleancity.shared.models.ComplaintStatus
```

(`queryEnum` уже бросает `BadRequestException` → 400 на невалидном `status`; глобальный exception handler это покрывает.)

- [ ] **Step 8: Run tests to verify they pass**

Run: `./gradlew :backend:test --tests "*ComplaintFilterTest"`
Expected: PASS (все 9 тестов класса).

- [ ] **Step 9: Прогнать весь backend-suite (регрессия)**

Run: `./gradlew :backend:test`
Expected: PASS — все тесты зелёные (новые фильтры опциональны, дефолты не меняют поведение).

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/kotlin/com/example/cleancity/complaints/ \
        backend/src/test/kotlin/com/example/cleancity/complaints/ComplaintFilterTest.kt
git commit -m "feat(backend): фильтры status и slaBreached в GET /complaints"
```

---

# ЧАСТЬ B — Web (`web-admin/`)

> Все web-команды запускать из `cd web-admin`. Тесты: `npm run test`. Сборка: `npm run build`.

## Task 6: API-типы жалоб

**Files:**
- Modify: `web-admin/src/api/types.ts`

- [ ] **Step 1: Добавить типы в конец `types.ts`**

В конец `web-admin/src/api/types.ts` добавить:

```typescript
// --- Жалобы (Day 16) ---

export type ComplaintStatus = 'NEW' | 'IN_PROGRESS' | 'RESOLVED' | 'REJECTED' | 'DUPLICATE'

export type ProblemCategory =
  | 'GARBAGE' | 'ROADS' | 'SIDEWALKS' | 'LIGHTING' | 'GREENERY' | 'LANDSCAPING'
  | 'PLAYGROUNDS' | 'PARKS' | 'BEACHES' | 'SAFETY' | 'VANDALISM' | 'WATER_SUPPLY'
  | 'SEWAGE' | 'ELECTRICITY' | 'ECOLOGY' | 'ACCESSIBILITY' | 'TRADE' | 'OTHER'

export type ComplaintSort = 'date' | 'votes' | 'priority'

export interface ComplaintPhoto {
  id: number
  photoUrl: string
  thumbUrl: string
  sortOrder: number
}

export interface StatusChange {
  fromStatus?: ComplaintStatus | null
  toStatus: ComplaintStatus
  comment: string
  changedByName?: string | null
  createdAt: string
}

export interface Complaint {
  id: number
  authorId: number
  authorName?: string | null
  category: ProblemCategory
  title: string
  description: string
  latitude: number
  longitude: number
  address: string
  district?: string | null
  status: ComplaintStatus
  photos: ComplaintPhoto[]
  votesCount: number
  userVoted: boolean
  duplicateOfId?: number | null
  createdAt: string
  updatedAt: string
  resolvedAt?: string | null
  statusHistory: StatusChange[]
  slaDeadline?: string | null
  slaBreached: boolean
}

export interface ComplaintListResponse {
  items: Complaint[]
  page: number
  size: number
  total: number
}

export interface DuplicateCandidate {
  id: number
  title: string
  category: ProblemCategory
  status: ComplaintStatus
  address: string
  votesCount: number
  distanceMeters: number
  createdAt: string
}

export interface DuplicateCandidatesResponse {
  items: DuplicateCandidate[]
}

export interface AnalyticsOverview {
  total: number
  new: number
  inProgress: number
  resolved: number
  rejected: number
  duplicate: number
  today: number
  week: number
  slaBreachCount: number
}

export interface ChangeStatusRequest {
  toStatus: ComplaintStatus
  comment: string
  duplicateOfId?: number | null
}

export interface ComplaintFilter {
  status: ComplaintStatus | null
  slaBreached: boolean
  category: ProblemCategory | null
  district: string | null
  sort: ComplaintSort
  page: number
}
```

- [ ] **Step 2: Проверить компиляцию типов**

Run: `cd web-admin && npx tsc -b`
Expected: без ошибок.

- [ ] **Step 3: Commit**

```bash
git add web-admin/src/api/types.ts
git commit -m "feat(web): типы жалоб, дубликатов и аналитики"
```

---

## Task 7: `lib/complaintMeta.ts` — справочники и карта переходов

**Files:**
- Create: `web-admin/src/lib/complaintMeta.ts`
- Test: `web-admin/src/lib/complaintMeta.test.ts`

- [ ] **Step 1: Write the failing test**

Create `web-admin/src/lib/complaintMeta.test.ts`:

```typescript
import { describe, it, expect } from 'vitest'
import { allowedActions, STATUS_META, CATEGORY_META } from './complaintMeta'

describe('complaintMeta', () => {
  it('NEW допускает 3 действия', () => {
    const actions = allowedActions('NEW').map((a) => a.toStatus)
    expect(actions).toEqual(['IN_PROGRESS', 'REJECTED', 'DUPLICATE'])
  })

  it('IN_PROGRESS допускает решить/отклонить/дубликат', () => {
    const actions = allowedActions('IN_PROGRESS').map((a) => a.toStatus)
    expect(actions).toEqual(['RESOLVED', 'REJECTED', 'DUPLICATE'])
  })

  it('терминальные статусы не дают действий', () => {
    expect(allowedActions('RESOLVED')).toEqual([])
    expect(allowedActions('REJECTED')).toEqual([])
    expect(allowedActions('DUPLICATE')).toEqual([])
  })

  it('у каждого статуса есть label и цвет', () => {
    expect(STATUS_META.NEW.label).toBeTruthy()
    expect(STATUS_META.NEW.className).toBeTruthy()
  })

  it('18 категорий с label', () => {
    expect(Object.keys(CATEGORY_META)).toHaveLength(18)
    expect(CATEGORY_META.GARBAGE.label).toBe('Мусор')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd web-admin && npm run test -- complaintMeta`
Expected: FAIL — модуль `./complaintMeta` не существует.

- [ ] **Step 3: Создать `lib/complaintMeta.ts`**

```typescript
import type { ComplaintStatus, ProblemCategory, ComplaintSort } from '@/api/types'

export interface StatusAction {
  toStatus: ComplaintStatus
  label: string
}

/** Карта допустимых переходов — зеркало backend ALLOWED_TRANSITIONS (SPEC §3.2). */
const TRANSITIONS: Record<ComplaintStatus, StatusAction[]> = {
  NEW: [
    { toStatus: 'IN_PROGRESS', label: 'Принять в работу' },
    { toStatus: 'REJECTED', label: 'Отклонить' },
    { toStatus: 'DUPLICATE', label: 'Дубликат' },
  ],
  IN_PROGRESS: [
    { toStatus: 'RESOLVED', label: 'Решить' },
    { toStatus: 'REJECTED', label: 'Отклонить' },
    { toStatus: 'DUPLICATE', label: 'Дубликат' },
  ],
  RESOLVED: [],
  REJECTED: [],
  DUPLICATE: [],
}

export function allowedActions(status: ComplaintStatus): StatusAction[] {
  return TRANSITIONS[status]
}

export interface StatusMeta {
  label: string
  className: string
}

export const STATUS_META: Record<ComplaintStatus, StatusMeta> = {
  NEW: { label: 'В обработке', className: 'bg-amber-100 text-amber-800' },
  IN_PROGRESS: { label: 'В работе', className: 'bg-blue-100 text-blue-800' },
  RESOLVED: { label: 'Решено', className: 'bg-emerald-100 text-emerald-800' },
  REJECTED: { label: 'Отклонена', className: 'bg-slate-200 text-slate-700' },
  DUPLICATE: { label: 'Дубликат', className: 'bg-slate-200 text-slate-700' },
}

export interface CategoryMeta {
  label: string
  icon: string
}

export const CATEGORY_META: Record<ProblemCategory, CategoryMeta> = {
  GARBAGE: { label: 'Мусор', icon: '🗑' },
  ROADS: { label: 'Дороги', icon: '🛣' },
  SIDEWALKS: { label: 'Тротуары', icon: '🚶' },
  LIGHTING: { label: 'Освещение', icon: '💡' },
  GREENERY: { label: 'Озеленение', icon: '🌳' },
  LANDSCAPING: { label: 'Благоустройство', icon: '🏗' },
  PLAYGROUNDS: { label: 'Площадки', icon: '🛝' },
  PARKS: { label: 'Парки', icon: '🏞' },
  BEACHES: { label: 'Пляжи', icon: '🏖' },
  SAFETY: { label: 'Безопасность', icon: '🚨' },
  VANDALISM: { label: 'Вандализм', icon: '🎨' },
  WATER_SUPPLY: { label: 'Водоснабжение', icon: '🚰' },
  SEWAGE: { label: 'Канализация', icon: '🌊' },
  ELECTRICITY: { label: 'Электроснабжение', icon: '⚡' },
  ECOLOGY: { label: 'Экология', icon: '☣' },
  ACCESSIBILITY: { label: 'Доступная среда', icon: '♿' },
  TRADE: { label: 'Торговля', icon: '🏪' },
  OTHER: { label: 'Прочее', icon: '❓' },
}

export const CATEGORY_ORDER = Object.keys(CATEGORY_META) as ProblemCategory[]

/** 4 каноничных района Сочи — значение = нормализованный label из backend. */
export const DISTRICTS: string[] = ['Центральный', 'Адлерский', 'Хостинский', 'Лазаревский']

export const SORT_OPTIONS: { value: ComplaintSort; label: string }[] = [
  { value: 'date', label: 'По дате' },
  { value: 'priority', label: 'По приоритету' },
  { value: 'votes', label: 'По голосам' },
]
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd web-admin && npm run test -- complaintMeta`
Expected: PASS (5 тестов).

- [ ] **Step 5: Commit**

```bash
git add web-admin/src/lib/complaintMeta.ts web-admin/src/lib/complaintMeta.test.ts
git commit -m "feat(web): справочники категорий/статусов + карта переходов"
```

---

## Task 8: API-функции жалоб и аналитики

**Files:**
- Create: `web-admin/src/api/complaints.ts`
- Create: `web-admin/src/api/analytics.ts`

- [ ] **Step 1: Создать `api/complaints.ts`**

```typescript
import { api } from './client'
import type {
  Complaint,
  ComplaintFilter,
  ComplaintListResponse,
  ChangeStatusRequest,
  DuplicateCandidatesResponse,
  ProblemCategory,
} from './types'

const PAGE_SIZE = 20

export async function listComplaints(filter: ComplaintFilter): Promise<ComplaintListResponse> {
  const params: Record<string, string | number> = {
    sort: filter.sort,
    page: filter.page,
    size: PAGE_SIZE,
  }
  if (filter.status) params.status = filter.status
  if (filter.slaBreached) params.slaBreached = 'true'
  if (filter.category) params.category = filter.category
  if (filter.district) params.district = filter.district
  const res = await api.get<ComplaintListResponse>('/complaints', { params })
  return res.data
}

export async function getComplaint(id: number): Promise<Complaint> {
  const res = await api.get<Complaint>(`/complaints/${id}`)
  return res.data
}

export async function changeStatus(id: number, req: ChangeStatusRequest): Promise<Complaint> {
  const res = await api.patch<Complaint>(`/complaints/${id}/status`, req)
  return res.data
}

export async function findDuplicates(
  lat: number,
  lon: number,
  category: ProblemCategory,
): Promise<DuplicateCandidatesResponse> {
  const res = await api.get<DuplicateCandidatesResponse>('/complaints/duplicates', {
    params: { lat, lon, category },
  })
  return res.data
}
```

- [ ] **Step 2: Создать `api/analytics.ts`**

```typescript
import { api } from './client'
import type { AnalyticsOverview } from './types'

export async function getOverview(): Promise<AnalyticsOverview> {
  const res = await api.get<AnalyticsOverview>('/analytics/overview')
  return res.data
}
```

- [ ] **Step 3: Проверить компиляцию**

Run: `cd web-admin && npx tsc -b`
Expected: без ошибок.

- [ ] **Step 4: Commit**

```bash
git add web-admin/src/api/complaints.ts web-admin/src/api/analytics.ts
git commit -m "feat(web): API-клиент жалоб и аналитики"
```

---

## Task 9: Query-хуки

**Files:**
- Create: `web-admin/src/hooks/complaintQueries.ts`

- [ ] **Step 1: Создать `hooks/complaintQueries.ts`**

```typescript
import { useMutation, useQuery, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import { listComplaints, getComplaint, changeStatus, findDuplicates } from '@/api/complaints'
import { getOverview } from '@/api/analytics'
import type { ChangeStatusRequest, ComplaintFilter, ProblemCategory } from '@/api/types'

export function useComplaintsQuery(filter: ComplaintFilter) {
  return useQuery({
    queryKey: ['complaints', filter],
    queryFn: () => listComplaints(filter),
    placeholderData: keepPreviousData,
  })
}

export function useComplaintQuery(id: number | null) {
  return useQuery({
    queryKey: ['complaint', id],
    queryFn: () => getComplaint(id as number),
    enabled: id != null,
  })
}

export function useOverviewQuery() {
  return useQuery({
    queryKey: ['analytics', 'overview'],
    queryFn: getOverview,
  })
}

export function useDuplicatesQuery(
  enabled: boolean,
  lat: number,
  lon: number,
  category: ProblemCategory,
) {
  return useQuery({
    queryKey: ['duplicates', lat, lon, category],
    queryFn: () => findDuplicates(lat, lon, category),
    enabled,
  })
}

export function useChangeStatusMutation() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, req }: { id: number; req: ChangeStatusRequest }) => changeStatus(id, req),
    onSuccess: (updated) => {
      qc.invalidateQueries({ queryKey: ['complaints'] })
      qc.invalidateQueries({ queryKey: ['complaint', updated.id] })
      qc.invalidateQueries({ queryKey: ['analytics', 'overview'] })
    },
  })
}
```

- [ ] **Step 2: Проверить компиляцию**

Run: `cd web-admin && npx tsc -b`
Expected: без ошибок.

- [ ] **Step 3: Commit**

```bash
git add web-admin/src/hooks/complaintQueries.ts
git commit -m "feat(web): TanStack Query хуки жалоб"
```

---

## Task 10: `StatusBadge`

**Files:**
- Create: `web-admin/src/components/complaints/StatusBadge.tsx`

- [ ] **Step 1: Создать компонент**

```tsx
import type { ComplaintStatus } from '@/api/types'
import { STATUS_META } from '@/lib/complaintMeta'

const BASE = 'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium'

export function StatusBadge({ status, slaBreached }: { status: ComplaintStatus; slaBreached?: boolean }) {
  if (slaBreached) {
    return <span className={`${BASE} bg-red-100 text-red-700`}>⚠ SLA</span>
  }
  const meta = STATUS_META[status]
  return <span className={`${BASE} ${meta.className}`}>{meta.label}</span>
}
```

- [ ] **Step 2: Проверить компиляцию**

Run: `cd web-admin && npx tsc -b`
Expected: без ошибок.

- [ ] **Step 3: Commit**

```bash
git add web-admin/src/components/complaints/StatusBadge.tsx
git commit -m "feat(web): StatusBadge — бейдж статуса/SLA"
```

---

## Task 11: `ComplaintFilters`

**Files:**
- Create: `web-admin/src/components/complaints/ComplaintFilters.tsx`
- Test: `web-admin/src/components/complaints/ComplaintFilters.test.tsx`

- [ ] **Step 1: Write the failing test**

Create `web-admin/src/components/complaints/ComplaintFilters.test.tsx`:

```tsx
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ComplaintFilters } from './ComplaintFilters'
import type { ComplaintFilter, AnalyticsOverview } from '@/api/types'

const baseFilter: ComplaintFilter = {
  status: null, slaBreached: false, category: null, district: null, sort: 'date', page: 0,
}

const overview: AnalyticsOverview = {
  total: 247, new: 47, inProgress: 63, resolved: 137, rejected: 8, duplicate: 4,
  today: 5, week: 30, slaBreachCount: 8,
}

describe('ComplaintFilters', () => {
  it('клик по чипу статуса вызывает onChange со статусом и page=0', async () => {
    const onChange = vi.fn()
    render(<ComplaintFilters filter={{ ...baseFilter, page: 3 }} overview={overview} onChange={onChange} />)
    await userEvent.click(screen.getByRole('button', { name: /В работе/ }))
    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({ status: 'IN_PROGRESS', slaBreached: false, page: 0 }),
    )
  })

  it('клик по чипу SLA выставляет slaBreached и сбрасывает status', async () => {
    const onChange = vi.fn()
    render(<ComplaintFilters filter={{ ...baseFilter, status: 'NEW' }} overview={overview} onChange={onChange} />)
    await userEvent.click(screen.getByRole('button', { name: /SLA/ }))
    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({ status: null, slaBreached: true }),
    )
  })

  it('счётчики из overview отрисованы', () => {
    render(<ComplaintFilters filter={baseFilter} overview={overview} onChange={vi.fn()} />)
    expect(screen.getByRole('button', { name: /Все 247/ })).toBeInTheDocument()
  })

  it('без overview счётчики не ломают рендер', () => {
    render(<ComplaintFilters filter={baseFilter} overview={undefined} onChange={vi.fn()} />)
    expect(screen.getByRole('button', { name: /Все/ })).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd web-admin && npm run test -- ComplaintFilters`
Expected: FAIL — модуль `./ComplaintFilters` не существует.

- [ ] **Step 3: Создать `ComplaintFilters.tsx`**

```tsx
import type { AnalyticsOverview, ComplaintFilter, ComplaintStatus } from '@/api/types'
import { CATEGORY_META, CATEGORY_ORDER, DISTRICTS, SORT_OPTIONS } from '@/lib/complaintMeta'

interface Props {
  filter: ComplaintFilter
  overview: AnalyticsOverview | undefined
  onChange: (next: ComplaintFilter) => void
}

interface Chip {
  key: string
  label: string
  count: number | undefined
  active: boolean
  apply: (f: ComplaintFilter) => ComplaintFilter
}

const CHIP_BASE =
  'rounded-full border px-3 py-1 text-sm transition-colors disabled:opacity-50'

export function ComplaintFilters({ filter, overview, onChange }: Props) {
  const statusChip = (
    status: ComplaintStatus,
    label: string,
    count: number | undefined,
  ): Chip => ({
    key: status,
    label,
    count,
    active: filter.status === status && !filter.slaBreached,
    apply: (f) => ({ ...f, status, slaBreached: false, page: 0 }),
  })

  const chips: Chip[] = [
    {
      key: 'ALL',
      label: 'Все',
      count: overview?.total,
      active: filter.status === null && !filter.slaBreached,
      apply: (f) => ({ ...f, status: null, slaBreached: false, page: 0 }),
    },
    statusChip('NEW', 'В обработке', overview?.new),
    statusChip('IN_PROGRESS', 'В работе', overview?.inProgress),
    statusChip('RESOLVED', 'Решено', overview?.resolved),
    statusChip('REJECTED', 'Отклонена', overview?.rejected),
    statusChip('DUPLICATE', 'Дубликат', overview?.duplicate),
    {
      key: 'SLA',
      label: '⚠ SLA',
      count: overview?.slaBreachCount,
      active: filter.slaBreached,
      apply: (f) => ({ ...f, status: null, slaBreached: true, page: 0 }),
    },
  ]

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap gap-2">
        {chips.map((c) => (
          <button
            key={c.key}
            type="button"
            onClick={() => onChange(c.apply(filter))}
            className={`${CHIP_BASE} ${
              c.active
                ? 'border-slate-800 bg-slate-800 text-white'
                : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-50'
            }`}
          >
            {c.label}
            {c.count !== undefined ? ` ${c.count}` : ''}
          </button>
        ))}
      </div>
      <div className="flex flex-wrap gap-3">
        <select
          aria-label="Категория"
          className="rounded border border-slate-300 px-2 py-1 text-sm"
          value={filter.category ?? ''}
          onChange={(e) =>
            onChange({
              ...filter,
              category: (e.target.value || null) as ComplaintFilter['category'],
              page: 0,
            })
          }
        >
          <option value="">Все категории</option>
          {CATEGORY_ORDER.map((c) => (
            <option key={c} value={c}>
              {CATEGORY_META[c].icon} {CATEGORY_META[c].label}
            </option>
          ))}
        </select>
        <select
          aria-label="Район"
          className="rounded border border-slate-300 px-2 py-1 text-sm"
          value={filter.district ?? ''}
          onChange={(e) => onChange({ ...filter, district: e.target.value || null, page: 0 })}
        >
          <option value="">Все районы</option>
          {DISTRICTS.map((d) => (
            <option key={d} value={d}>
              {d}
            </option>
          ))}
        </select>
        <select
          aria-label="Сортировка"
          className="rounded border border-slate-300 px-2 py-1 text-sm"
          value={filter.sort}
          onChange={(e) =>
            onChange({ ...filter, sort: e.target.value as ComplaintFilter['sort'], page: 0 })
          }
        >
          {SORT_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
      </div>
    </div>
  )
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd web-admin && npm run test -- ComplaintFilters`
Expected: PASS (4 теста).

- [ ] **Step 5: Commit**

```bash
git add web-admin/src/components/complaints/ComplaintFilters.tsx \
        web-admin/src/components/complaints/ComplaintFilters.test.tsx
git commit -m "feat(web): ComplaintFilters — чипы статуса/SLA + селекты"
```

---

## Task 12: `ComplaintsTable` + `ComplaintsPagination`

**Files:**
- Create: `web-admin/src/components/complaints/ComplaintsTable.tsx`
- Create: `web-admin/src/components/complaints/ComplaintsPagination.tsx`
- Test: `web-admin/src/components/complaints/ComplaintsTable.test.tsx`

- [ ] **Step 1: Write the failing test**

Create `web-admin/src/components/complaints/ComplaintsTable.test.tsx`:

```tsx
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ComplaintsTable } from './ComplaintsTable'
import type { Complaint } from '@/api/types'

function complaint(id: number, over: Partial<Complaint> = {}): Complaint {
  return {
    id, authorId: 1, category: 'GARBAGE', title: `Жалоба ${id}`, description: 'd',
    latitude: 43.6, longitude: 39.7, address: 'ул. Тест', district: 'Центральный',
    status: 'NEW', photos: [], votesCount: 12, userVoted: false,
    createdAt: '2026-05-20T09:00:00Z', updatedAt: '2026-05-20T09:00:00Z',
    statusHistory: [], slaBreached: false, ...over,
  }
}

describe('ComplaintsTable', () => {
  it('рендерит строки и клик вызывает onSelect', async () => {
    const onSelect = vi.fn()
    render(
      <ComplaintsTable items={[complaint(1), complaint(2)]} selectedId={null} onSelect={onSelect} />,
    )
    await userEvent.click(screen.getByText('Жалоба 1'))
    expect(onSelect).toHaveBeenCalledWith(1)
  })

  it('показывает empty state на пустом списке', () => {
    render(<ComplaintsTable items={[]} selectedId={null} onSelect={vi.fn()} />)
    expect(screen.getByText(/ничего не нашлось/i)).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd web-admin && npm run test -- ComplaintsTable`
Expected: FAIL — модуль не существует.

- [ ] **Step 3: Создать `ComplaintsTable.tsx`**

```tsx
import type { Complaint } from '@/api/types'
import { CATEGORY_META } from '@/lib/complaintMeta'
import { StatusBadge } from './StatusBadge'

interface Props {
  items: Complaint[]
  selectedId: number | null
  onSelect: (id: number) => void
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit' })
}

export function ComplaintsTable({ items, selectedId, onSelect }: Props) {
  if (items.length === 0) {
    return (
      <div className="py-12 text-center text-sm text-slate-400">
        Под выбранные фильтры ничего не нашлось
      </div>
    )
  }
  return (
    <table className="w-full text-sm">
      <thead>
        <tr className="border-b text-left text-xs uppercase text-slate-400">
          <th className="px-3 py-2">Проблема</th>
          <th className="px-3 py-2">Район</th>
          <th className="px-3 py-2">Категория</th>
          <th className="px-3 py-2">Дата</th>
          <th className="px-3 py-2">Голоса</th>
          <th className="px-3 py-2">Статус</th>
        </tr>
      </thead>
      <tbody>
        {items.map((c) => (
          <tr
            key={c.id}
            onClick={() => onSelect(c.id)}
            className={`cursor-pointer border-b hover:bg-slate-50 ${
              c.id === selectedId ? 'bg-slate-100' : ''
            }`}
          >
            <td className="px-3 py-2 font-medium text-slate-800">{c.title}</td>
            <td className="px-3 py-2 text-slate-600">{c.district ?? '—'}</td>
            <td className="px-3 py-2 text-slate-600">
              {CATEGORY_META[c.category].icon} {CATEGORY_META[c.category].label}
            </td>
            <td className="px-3 py-2 text-slate-600">{formatDate(c.createdAt)}</td>
            <td className="px-3 py-2 text-slate-600">{c.votesCount}</td>
            <td className="px-3 py-2">
              <StatusBadge status={c.status} slaBreached={c.slaBreached} />
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
```

- [ ] **Step 4: Создать `ComplaintsPagination.tsx`**

```tsx
interface Props {
  page: number
  size: number
  total: number
  onPage: (page: number) => void
}

export function ComplaintsPagination({ page, size, total, onPage }: Props) {
  const lastPage = Math.max(0, Math.ceil(total / size) - 1)
  if (total === 0) return null
  return (
    <div className="flex items-center justify-end gap-3 py-3 text-sm text-slate-600">
      <span>
        Стр. {page + 1} из {lastPage + 1} · всего {total}
      </span>
      <button
        type="button"
        disabled={page <= 0}
        onClick={() => onPage(page - 1)}
        className="rounded border border-slate-300 px-2 py-1 disabled:opacity-40"
      >
        Назад
      </button>
      <button
        type="button"
        disabled={page >= lastPage}
        onClick={() => onPage(page + 1)}
        className="rounded border border-slate-300 px-2 py-1 disabled:opacity-40"
      >
        Вперёд
      </button>
    </div>
  )
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd web-admin && npm run test -- ComplaintsTable`
Expected: PASS (2 теста).

- [ ] **Step 6: Commit**

```bash
git add web-admin/src/components/complaints/ComplaintsTable.tsx \
        web-admin/src/components/complaints/ComplaintsPagination.tsx \
        web-admin/src/components/complaints/ComplaintsTable.test.tsx
git commit -m "feat(web): таблица жалоб + пагинация"
```

---

## Task 13: `PhotoGallery` + `StatusHistory`

**Files:**
- Create: `web-admin/src/components/complaints/PhotoGallery.tsx`
- Create: `web-admin/src/components/complaints/StatusHistory.tsx`

- [ ] **Step 1: Создать `PhotoGallery.tsx`**

```tsx
import { useState } from 'react'
import type { ComplaintPhoto } from '@/api/types'

const BROKEN = 'data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22/%3E'

export function PhotoGallery({ photos }: { photos: ComplaintPhoto[] }) {
  const [zoom, setZoom] = useState<string | null>(null)
  if (photos.length === 0) {
    return <div className="text-sm text-slate-400">Фото нет</div>
  }
  return (
    <>
      <div className="flex flex-wrap gap-2">
        {photos.map((p) => (
          <img
            key={p.id}
            src={p.thumbUrl}
            alt="Фото жалобы"
            onClick={() => setZoom(p.photoUrl)}
            onError={(e) => {
              ;(e.target as HTMLImageElement).src = BROKEN
              ;(e.target as HTMLImageElement).title = 'фото недоступно'
            }}
            className="h-20 w-20 cursor-pointer rounded object-cover"
          />
        ))}
      </div>
      {zoom && (
        <div
          onClick={() => setZoom(null)}
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-8"
        >
          <img src={zoom} alt="Фото жалобы" className="max-h-full max-w-full rounded" />
        </div>
      )}
    </>
  )
}
```

- [ ] **Step 2: Создать `StatusHistory.tsx`**

```tsx
import type { StatusChange } from '@/api/types'
import { STATUS_META } from '@/lib/complaintMeta'

function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('ru-RU', {
    day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit',
  })
}

export function StatusHistory({ history }: { history: StatusChange[] }) {
  if (history.length === 0) {
    return <div className="text-sm text-slate-400">История пуста</div>
  }
  return (
    <ul className="space-y-2">
      {history.map((h, i) => (
        <li key={i} className="border-l-2 border-slate-200 pl-3 text-sm">
          <div className="font-medium text-slate-700">
            {h.fromStatus ? `${STATUS_META[h.fromStatus].label} → ` : ''}
            {STATUS_META[h.toStatus].label}
          </div>
          <div className="text-slate-600">{h.comment}</div>
          <div className="text-xs text-slate-400">
            {h.changedByName ?? 'Администратор'} · {formatDateTime(h.createdAt)}
          </div>
        </li>
      ))}
    </ul>
  )
}
```

- [ ] **Step 3: Проверить компиляцию**

Run: `cd web-admin && npx tsc -b`
Expected: без ошибок.

- [ ] **Step 4: Commit**

```bash
git add web-admin/src/components/complaints/PhotoGallery.tsx \
        web-admin/src/components/complaints/StatusHistory.tsx
git commit -m "feat(web): галерея фото + лента истории статусов"
```

---

## Task 14: `DuplicatePicker`

**Files:**
- Create: `web-admin/src/components/complaints/DuplicatePicker.tsx`

- [ ] **Step 1: Создать `DuplicatePicker.tsx`**

```tsx
import { useDuplicatesQuery } from '@/hooks/complaintQueries'
import { CATEGORY_META } from '@/lib/complaintMeta'
import type { ProblemCategory } from '@/api/types'

interface Props {
  lat: number
  lon: number
  category: ProblemCategory
  excludeId: number
  selectedId: number | null
  onSelect: (id: number) => void
}

export function DuplicatePicker({ lat, lon, category, excludeId, selectedId, onSelect }: Props) {
  const { data, isLoading, isError } = useDuplicatesQuery(true, lat, lon, category)

  if (isLoading) return <div className="text-sm text-slate-400">Поиск кандидатов…</div>
  if (isError) return <div className="text-sm text-red-600">Не удалось загрузить кандидатов</div>

  const items = (data?.items ?? []).filter((c) => c.id !== excludeId)
  if (items.length === 0) {
    return (
      <div className="text-sm text-slate-500">
        Поблизости активных жалоб категории «{CATEGORY_META[category].label}» нет
      </div>
    )
  }
  return (
    <ul className="max-h-48 space-y-1 overflow-y-auto">
      {items.map((c) => (
        <li key={c.id}>
          <button
            type="button"
            onClick={() => onSelect(c.id)}
            className={`w-full rounded border px-2 py-1 text-left text-sm ${
              c.id === selectedId
                ? 'border-slate-800 bg-slate-100'
                : 'border-slate-200 hover:bg-slate-50'
            }`}
          >
            <span className="font-medium">#{c.id}</span> {c.title}
            <span className="text-slate-400"> · {Math.round(c.distanceMeters)} м</span>
          </button>
        </li>
      ))}
    </ul>
  )
}
```

- [ ] **Step 2: Проверить компиляцию**

Run: `cd web-admin && npx tsc -b`
Expected: без ошибок.

- [ ] **Step 3: Commit**

```bash
git add web-admin/src/components/complaints/DuplicatePicker.tsx
git commit -m "feat(web): DuplicatePicker — выбор оригинала для DUPLICATE"
```

---

## Task 15: `StatusChangeDialog`

**Files:**
- Create: `web-admin/src/components/complaints/StatusChangeDialog.tsx`
- Test: `web-admin/src/components/complaints/StatusChangeDialog.test.tsx`

- [ ] **Step 1: Write the failing test**

Create `web-admin/src/components/complaints/StatusChangeDialog.test.tsx`:

```tsx
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StatusChangeDialog } from './StatusChangeDialog'
import type { Complaint } from '@/api/types'

const complaint: Complaint = {
  id: 7, authorId: 1, category: 'GARBAGE', title: 'Свалка', description: 'd',
  latitude: 43.6, longitude: 39.7, address: 'ул. Тест', district: 'Центральный',
  status: 'NEW', photos: [], votesCount: 3, userVoted: false,
  createdAt: '2026-05-20T09:00:00Z', updatedAt: '2026-05-20T09:00:00Z',
  statusHistory: [], slaBreached: false,
}

function renderDialog(props: Partial<React.ComponentProps<typeof StatusChangeDialog>> = {}) {
  const qc = new QueryClient()
  return render(
    <QueryClientProvider client={qc}>
      <StatusChangeDialog
        complaint={complaint}
        toStatus="IN_PROGRESS"
        onClose={vi.fn()}
        onSubmit={vi.fn()}
        submitting={false}
        {...props}
      />
    </QueryClientProvider>,
  )
}

describe('StatusChangeDialog', () => {
  it('кнопка подтверждения disabled при пустом комментарии', () => {
    renderDialog()
    expect(screen.getByRole('button', { name: /подтвердить/i })).toBeDisabled()
  })

  it('после ввода комментария submit шлёт верный body', async () => {
    const onSubmit = vi.fn()
    renderDialog({ onSubmit })
    await userEvent.type(screen.getByLabelText(/комментарий/i), 'Приняли в работу')
    await userEvent.click(screen.getByRole('button', { name: /подтвердить/i }))
    expect(onSubmit).toHaveBeenCalledWith({
      toStatus: 'IN_PROGRESS',
      comment: 'Приняли в работу',
      duplicateOfId: undefined,
    })
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd web-admin && npm run test -- StatusChangeDialog`
Expected: FAIL — модуль не существует.

- [ ] **Step 3: Создать `StatusChangeDialog.tsx`**

```tsx
import { useState } from 'react'
import type { ChangeStatusRequest, Complaint, ComplaintStatus } from '@/api/types'
import { STATUS_META } from '@/lib/complaintMeta'
import { Button } from '@/components/ui/button'
import { DuplicatePicker } from './DuplicatePicker'

const MAX_COMMENT = 2000

interface Props {
  complaint: Complaint
  toStatus: ComplaintStatus
  submitting: boolean
  onClose: () => void
  onSubmit: (req: ChangeStatusRequest) => void
}

export function StatusChangeDialog({ complaint, toStatus, submitting, onClose, onSubmit }: Props) {
  const [comment, setComment] = useState('')
  const [duplicateOfId, setDuplicateOfId] = useState<number | null>(null)
  const isDuplicate = toStatus === 'DUPLICATE'

  const tooLong = comment.length > MAX_COMMENT
  const canSubmit =
    comment.trim().length > 0 && !tooLong && (!isDuplicate || duplicateOfId != null) && !submitting

  function submit() {
    if (!canSubmit) return
    onSubmit({
      toStatus,
      comment: comment.trim(),
      duplicateOfId: isDuplicate ? (duplicateOfId as number) : undefined,
    })
  }

  return (
    <div
      onClick={onClose}
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
    >
      <div
        role="dialog"
        onClick={(e) => e.stopPropagation()}
        className="w-full max-w-md rounded-lg bg-white p-5 shadow-xl"
      >
        <h3 className="text-lg font-semibold text-slate-800">
          Сменить статус → {STATUS_META[toStatus].label}
        </h3>
        <p className="mt-1 text-sm text-slate-500">«{complaint.title}»</p>

        {isDuplicate && (
          <div className="mt-3">
            <div className="mb-1 text-sm font-medium text-slate-700">Оригинал жалобы</div>
            <DuplicatePicker
              lat={complaint.latitude}
              lon={complaint.longitude}
              category={complaint.category}
              excludeId={complaint.id}
              selectedId={duplicateOfId}
              onSelect={setDuplicateOfId}
            />
          </div>
        )}

        <div className="mt-3">
          <label htmlFor="status-comment" className="mb-1 block text-sm font-medium text-slate-700">
            Комментарий (обязательно)
          </label>
          <textarea
            id="status-comment"
            value={comment}
            maxLength={MAX_COMMENT}
            onChange={(e) => setComment(e.target.value)}
            rows={4}
            className="w-full rounded border border-slate-300 p-2 text-sm"
          />
          <div className="text-right text-xs text-slate-400">
            {comment.length} / {MAX_COMMENT}
          </div>
        </div>

        <div className="mt-4 flex justify-end gap-2">
          <Button variant="outline" onClick={onClose} disabled={submitting}>
            Отмена
          </Button>
          <Button onClick={submit} disabled={!canSubmit}>
            {submitting ? 'Отправка…' : 'Подтвердить'}
          </Button>
        </div>
      </div>
    </div>
  )
}
```

> **Примечание для исполнителя:** проверь экспорт `Button` в `@/components/ui/button` — поддерживает ли он `variant="outline"`. Если variant API отличается, используй доступный вариант или `className`. Не добавляй новые UI-зависимости.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd web-admin && npm run test -- StatusChangeDialog`
Expected: PASS (2 теста).

- [ ] **Step 5: Commit**

```bash
git add web-admin/src/components/complaints/StatusChangeDialog.tsx \
        web-admin/src/components/complaints/StatusChangeDialog.test.tsx
git commit -m "feat(web): StatusChangeDialog — модалка смены статуса"
```

---

## Task 16: `ComplaintDetailPanel`

**Files:**
- Create: `web-admin/src/components/complaints/ComplaintDetailPanel.tsx`

- [ ] **Step 1: Создать `ComplaintDetailPanel.tsx`**

```tsx
import type { ChangeStatusRequest, Complaint, ComplaintStatus } from '@/api/types'
import { CATEGORY_META } from '@/lib/complaintMeta'
import { allowedActions } from '@/lib/complaintMeta'
import { Button } from '@/components/ui/button'
import { StatusBadge } from './StatusBadge'
import { PhotoGallery } from './PhotoGallery'
import { StatusHistory } from './StatusHistory'

interface Props {
  complaint: Complaint | undefined
  isLoading: boolean
  isError: boolean
  onAction: (toStatus: ComplaintStatus) => void
}

function yandexMapsUrl(lat: number, lon: number): string {
  return `https://yandex.ru/maps/?pt=${lon},${lat}&z=17&l=map`
}

export function ComplaintDetailPanel({ complaint, isLoading, isError, onAction }: Props) {
  if (isError) {
    return <div className="p-6 text-sm text-red-600">Не удалось загрузить детали жалобы</div>
  }
  if (isLoading || !complaint) {
    return (
      <div className="flex h-full items-center justify-center p-6 text-center text-sm text-slate-400">
        {isLoading ? 'Загрузка…' : 'Кликни по строке — справа откроется детальная карточка'}
      </div>
    )
  }

  const actions = allowedActions(complaint.status)

  return (
    <div className="space-y-4 p-5">
      <div>
        <div className="flex items-center gap-2">
          <StatusBadge status={complaint.status} />
          {complaint.slaBreached && <StatusBadge status={complaint.status} slaBreached />}
        </div>
        <h2 className="mt-2 text-lg font-semibold text-slate-800">{complaint.title}</h2>
        <div className="text-sm text-slate-500">
          {CATEGORY_META[complaint.category].icon} {CATEGORY_META[complaint.category].label}
        </div>
      </div>

      <PhotoGallery photos={complaint.photos} />

      <div className="text-sm text-slate-700">{complaint.description}</div>

      <div className="text-sm text-slate-600">
        <div className="font-medium text-slate-700">Местоположение</div>
        <div>
          {complaint.address}
          {complaint.district ? ` · ${complaint.district} р-н` : ''}
        </div>
        <div className="text-xs text-slate-400">
          {complaint.latitude.toFixed(5)}, {complaint.longitude.toFixed(5)}
        </div>
        <a
          href={yandexMapsUrl(complaint.latitude, complaint.longitude)}
          target="_blank"
          rel="noreferrer"
          className="text-xs text-blue-600 underline"
        >
          Открыть в Яндекс.Картах
        </a>
      </div>

      <div className="text-sm text-slate-600">
        <span className="font-medium text-slate-700">Голоса:</span> {complaint.votesCount}
      </div>

      <div>
        <div className="mb-1 text-sm font-medium text-slate-700">История статусов</div>
        <StatusHistory history={complaint.statusHistory} />
      </div>

      {actions.length > 0 && (
        <div className="flex flex-wrap gap-2 border-t pt-3">
          {actions.map((a) => (
            <Button key={a.toStatus} onClick={() => onAction(a.toStatus)}>
              {a.label}
            </Button>
          ))}
        </div>
      )}
    </div>
  )
}
```

- [ ] **Step 2: Проверить компиляцию**

Run: `cd web-admin && npx tsc -b`
Expected: без ошибок.

- [ ] **Step 3: Commit**

```bash
git add web-admin/src/components/complaints/ComplaintDetailPanel.tsx
git commit -m "feat(web): ComplaintDetailPanel — детальная карточка жалобы"
```

---

## Task 17: `ComplaintsPage` + маршрут + интеграционный тест

**Files:**
- Create: `web-admin/src/pages/ComplaintsPage.tsx`
- Modify: `web-admin/src/App.tsx`
- Test: `web-admin/src/pages/ComplaintsPage.test.tsx`

- [ ] **Step 1: Создать `ComplaintsPage.tsx`**

```tsx
import { useState } from 'react'
import { toast } from 'sonner'
import type { ChangeStatusRequest, ComplaintFilter, ComplaintStatus } from '@/api/types'
import {
  useComplaintsQuery,
  useComplaintQuery,
  useOverviewQuery,
  useChangeStatusMutation,
} from '@/hooks/complaintQueries'
import { extractApiError } from '@/api/errors'
import { ComplaintFilters } from '@/components/complaints/ComplaintFilters'
import { ComplaintsTable } from '@/components/complaints/ComplaintsTable'
import { ComplaintsPagination } from '@/components/complaints/ComplaintsPagination'
import { ComplaintDetailPanel } from '@/components/complaints/ComplaintDetailPanel'
import { StatusChangeDialog } from '@/components/complaints/StatusChangeDialog'

const INITIAL_FILTER: ComplaintFilter = {
  status: null, slaBreached: false, category: null, district: null, sort: 'date', page: 0,
}
const PAGE_SIZE = 20

export function ComplaintsPage() {
  const [filter, setFilter] = useState<ComplaintFilter>(INITIAL_FILTER)
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [actionStatus, setActionStatus] = useState<ComplaintStatus | null>(null)

  const list = useComplaintsQuery(filter)
  const overview = useOverviewQuery()
  const detail = useComplaintQuery(selectedId)
  const mutation = useChangeStatusMutation()

  function submitStatusChange(req: ChangeStatusRequest) {
    if (selectedId == null) return
    mutation.mutate(
      { id: selectedId, req },
      {
        onSuccess: () => {
          toast.success('Статус изменён')
          setActionStatus(null)
        },
        onError: (err) => {
          const { code, message } = extractApiError(err)
          toast.error(message)
          if (code !== 'UNKNOWN') setActionStatus(null)
        },
      },
    )
  }

  return (
    <div className="flex h-full gap-4 p-4">
      <div className="flex flex-1 flex-col">
        <ComplaintFilters filter={filter} overview={overview.data} onChange={setFilter} />
        <div className="mt-3 flex-1 overflow-auto rounded border bg-white">
          {list.isError ? (
            <div className="p-6 text-center text-sm text-red-600">
              Не удалось загрузить список.{' '}
              <button onClick={() => list.refetch()} className="underline">
                Повторить
              </button>
            </div>
          ) : list.isLoading ? (
            <div className="p-6 text-center text-sm text-slate-400">Загрузка…</div>
          ) : (
            <ComplaintsTable
              items={list.data?.items ?? []}
              selectedId={selectedId}
              onSelect={setSelectedId}
            />
          )}
        </div>
        <ComplaintsPagination
          page={filter.page}
          size={PAGE_SIZE}
          total={list.data?.total ?? 0}
          onPage={(page) => setFilter((f) => ({ ...f, page }))}
        />
      </div>

      <div className="w-[420px] shrink-0 self-start rounded border bg-white">
        <ComplaintDetailPanel
          complaint={detail.data}
          isLoading={detail.isLoading && selectedId != null}
          isError={detail.isError}
          onAction={setActionStatus}
        />
      </div>

      {actionStatus && detail.data && (
        <StatusChangeDialog
          complaint={detail.data}
          toStatus={actionStatus}
          submitting={mutation.isPending}
          onClose={() => setActionStatus(null)}
          onSubmit={submitStatusChange}
        />
      )}
    </div>
  )
}
```

- [ ] **Step 2: Подключить маршрут в `App.tsx`**

В `web-admin/src/App.tsx` добавить импорт после импорта `SectionPlaceholder`:

```tsx
import { ComplaintsPage } from '@/pages/ComplaintsPage'
```

Заменить строку:

```tsx
            <Route path="/complaints" element={<SectionPlaceholder title="Жалобы" />} />
```

на:

```tsx
            <Route path="/complaints" element={<ComplaintsPage />} />
```

- [ ] **Step 3: Write the integration test**

Create `web-admin/src/pages/ComplaintsPage.test.tsx`:

```tsx
import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { ComplaintsPage } from './ComplaintsPage'
import type { Complaint } from '@/api/types'

const BASE = 'http://localhost:8081'

function complaint(id: number, over: Partial<Complaint> = {}): Complaint {
  return {
    id, authorId: 1, category: 'GARBAGE', title: `Жалоба ${id}`, description: 'описание',
    latitude: 43.6, longitude: 39.7, address: 'ул. Тест', district: 'Центральный',
    status: 'NEW', photos: [], votesCount: 5, userVoted: false,
    createdAt: '2026-05-20T09:00:00Z', updatedAt: '2026-05-20T09:00:00Z',
    statusHistory: [], slaBreached: false, ...over,
  }
}

const overview = {
  total: 2, new: 2, inProgress: 0, resolved: 0, rejected: 0, duplicate: 0,
  today: 0, week: 0, slaBreachCount: 0,
}

const server = setupServer(
  http.get(`${BASE}/complaints`, () =>
    HttpResponse.json({ items: [complaint(1), complaint(2)], page: 0, size: 20, total: 2 }),
  ),
  http.get(`${BASE}/complaints/:id`, ({ params }) =>
    HttpResponse.json(complaint(Number(params.id))),
  ),
  http.get(`${BASE}/analytics/overview`, () => HttpResponse.json(overview)),
  http.patch(`${BASE}/complaints/:id/status`, ({ params }) =>
    HttpResponse.json(complaint(Number(params.id), { status: 'IN_PROGRESS' })),
  ),
)

beforeAll(() => server.listen())
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <ComplaintsPage />
    </QueryClientProvider>,
  )
}

describe('ComplaintsPage', () => {
  it('загружает и показывает список жалоб', async () => {
    renderPage()
    expect(await screen.findByText('Жалоба 1')).toBeInTheDocument()
    expect(screen.getByText('Жалоба 2')).toBeInTheDocument()
  })

  it('клик по строке открывает деталь-панель с действиями', async () => {
    renderPage()
    await userEvent.click(await screen.findByText('Жалоба 1'))
    expect(await screen.findByRole('button', { name: /Принять в работу/ })).toBeInTheDocument()
  })

  it('смена статуса с комментарием показывает toast успеха', async () => {
    renderPage()
    await userEvent.click(await screen.findByText('Жалоба 1'))
    await userEvent.click(await screen.findByRole('button', { name: /Принять в работу/ }))
    await userEvent.type(screen.getByLabelText(/комментарий/i), 'Беру в работу')
    await userEvent.click(screen.getByRole('button', { name: /подтвердить/i }))
    await waitFor(() => expect(screen.getByText('Статус изменён')).toBeInTheDocument())
  })
})
```

> **Примечание:** тост рендерится через `sonner` `<Toaster>`. Если тест-проверка тоста нестабильна (Toaster монтируется в `main.tsx`, не в тесте) — обернуть `renderPage` в `<><ComplaintsPage/><Toaster/></>`, импортировав `Toaster` из `@/components/ui/sonner`.

- [ ] **Step 4: Run integration test**

Run: `cd web-admin && npm run test -- ComplaintsPage`
Expected: PASS (3 теста). При нестабильности проверки тоста — применить примечание из Step 3.

- [ ] **Step 5: Commit**

```bash
git add web-admin/src/pages/ComplaintsPage.tsx web-admin/src/pages/ComplaintsPage.test.tsx \
        web-admin/src/App.tsx
git commit -m "feat(web): ComplaintsPage — экран жалоб + смена статусов"
```

---

## Task 18: Финальная верификация

**Files:** нет изменений кода — только прогон проверок.

- [ ] **Step 1: Весь backend-suite**

Run: `./gradlew :backend:test`
Expected: BUILD SUCCESSFUL, все тесты зелёные.

- [ ] **Step 2: Весь web-suite**

Run: `cd web-admin && npm run test`
Expected: все тесты зелёные (20 от Day 15 + новые из Task 7, 11, 12, 15, 17).

- [ ] **Step 3: Web-сборка**

Run: `cd web-admin && npm run build`
Expected: `tsc -b` без ошибок типов + `vite build` успешно.

- [ ] **Step 4: Обновить PLAN.md — закрыть Day 16**

В `docs/PLAN.md` в секции «### День 16» проставить `[x]` у выполненных пунктов и добавить строку-резюме после заголофка (по образцу Day 15):

```markdown
> **Закрыт 2026-05-22.** Backend: фильтры status/slaBreached + SLA-поля в ответе
> (admin-only) + нормализация района при создании + миграция V8. Web: ComplaintsPage
> (таблица + фильтры + деталь-панель + смена статусов). Дизайн+план:
> docs/superpowers/specs/2026-05-22-day16-complaints-status-design.md,
> docs/superpowers/plans/2026-05-22-day16-complaints-status.md.
> Карта в деталях отложена (нужен Yandex JS API ключ).
```

- [ ] **Step 5: Commit**

```bash
git add docs/PLAN.md
git commit -m "docs: закрыть Day 16 в PLAN.md"
```

- [ ] **Step 6: Ручной чекпоинт Day 16** (не автоматизируется — выполняет человек)

1. Поднять backend локально (Postgres) + `cd web-admin && npm run dev` (:5173); войти сид-админом `admin@cleancity.dev` / `Admin12345!`.
2. Телефон Samsung A33 (`adb reverse tcp:8081`) — резидент создаёт жалобу.
3. В веб-админке открыть жалобу → «Принять в работу» с комментарием → toast успеха, статус строки обновился.
4. На телефоне приходит уведомление (polling-канал) с текстом комментария.
5. Проверить DUPLICATE: «Дубликат» → выбрать оригинал из пикера → подтвердить; голоса смержились на оригинал.
6. Проверить фильтры: чип статуса, чип «⚠ SLA», селект района (нормализованные значения), категория, сортировка, пагинация.

---

## Self-Review (выполнено при написании плана)

**1. Покрытие спеки:**
- §1 Backend-контракт (status/slaBreached параметры, SLA-поля, admin-only) → Tasks 4, 5 ✓
- §1 Нормализация района (`fromGeocoderText`, `create()`, миграция V8) → Tasks 1, 2, 3 ✓
- §2 Структура веб-страницы (все файлы) → Tasks 6–17 ✓
- §3 Поток данных (query-ключи, инвалидация, поток смены статуса) → Tasks 9, 17 ✓
- §4 Обработка ошибок (ошибки запросов, коды мутации, edge-cases) → Tasks 15, 16, 17 ✓
- §5 Тестирование (backend + web тесты, verification, чекпоинт) → распределено по задачам + Task 18 ✓

**2. Отступления от спеки (осознанные):**
- Спека §5 упоминала отдельный `ComplaintRoutesTest`. Заменён: 400 на невалидном `status` обеспечивается существующим `queryEnum` + глобальным exception handler (изменений в этой логике нет), фильтры покрыты service-level тестами `ComplaintFilterTest`. Полноценный route-тест с `testApplication` не добавлен — низкая ценность относительно объёма DI-обвязки.
- Нормализация в `ComplaintService.create()` (Task 2) не имеет автотеста — в проекте нет фикстур изображений для прогона `create()` end-to-end; логика покрыта unit-тестом `fromGeocoderText` (Task 1) и ручным чекпоинтом.

**3. Консистентность типов:** `ComplaintFilter` (web) — единый источник формы фильтра, используется в `listComplaints`, хуках и компонентах. `ChangeStatusRequest` — единый body мутации. `Complaint` — единый DTO для списка и деталей (backend отдаёт один `ComplaintResponse`). Имена backend (`slaDeadline`/`slaBreached`/`status`/`slaBreached`-параметр) совпадают между Task 4, 5 и web-типами Task 6.
