# Day 17D — PDF «Сводный отчёт за месяц» Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Добавить бэкенд-эндпоинт и UI-карточку, через которые админ скачивает PDF-сводку за предыдущий полный календарный месяц. Закрывает последний пункт Дня 17 CleanCity.

**Architecture:**
- Бэкенд: новый подпакет `analytics/pdf/` с `MonthlyReportPdfService` (оркестрация) и `MonthlyReportLayout` (вёрстка OpenPDF). Range-методы добавляются в существующий `AnalyticsService`. Кириллица — через DejaVuSans, положенный в resources.
- Фронт: новый `ExportSection` в SettingsPage с 4 карточками; 1 активная (скачивает PDF blob), 3 disabled с `title="Скоро"`. API-функция `downloadMonthlyReport()` в `src/api/analytics.ts`.

**Tech Stack:** Kotlin + Ktor (бэкенд), OpenPDF 1.3.30, JUnit + H2; React + TypeScript + Vite (фронт), Vitest + RTL, axios, sonner.

**Spec:** `docs/superpowers/specs/2026-05-28-day17d-monthly-report-pdf-design.md`

---

## Task 1: Добавить шрифты DejaVuSans в resources

**Files:**
- Create: `backend/src/main/resources/fonts/DejaVuSans.ttf` (бинарный, ~700 KB)
- Create: `backend/src/main/resources/fonts/DejaVuSans-Bold.ttf` (бинарный, ~700 KB)
- Modify: `.gitattributes` (если есть; иначе создать) — `*.ttf binary`

- [ ] **Step 1: Скачать шрифты DejaVuSans**

```bash
mkdir -p backend/src/main/resources/fonts
cd backend/src/main/resources/fonts
curl -L -o DejaVuSans.zip "https://sourceforge.net/projects/dejavu/files/dejavu/2.37/dejavu-fonts-ttf-2.37.zip/download"
unzip -j DejaVuSans.zip "dejavu-fonts-ttf-2.37/ttf/DejaVuSans.ttf" "dejavu-fonts-ttf-2.37/ttf/DejaVuSans-Bold.ttf"
rm DejaVuSans.zip
ls -la DejaVuSans*.ttf
```

Expected: два файла, каждый ~700 KB.

- [ ] **Step 2: Проверить .gitattributes**

```bash
cat ../../../.gitattributes 2>/dev/null | grep -i ttf || echo "ttf не настроен"
```

Если `ttf не настроен` — добавить в корневой `.gitattributes` (или создать его):

```
*.ttf binary
```

- [ ] **Step 3: Закоммитить шрифты**

```bash
git add backend/src/main/resources/fonts/DejaVuSans.ttf backend/src/main/resources/fonts/DejaVuSans-Bold.ttf .gitattributes
git commit -m "chore(day17d): добавить шрифты DejaVuSans для PDF-отчётов"
```

---

## Task 2: Добавить зависимость OpenPDF

**Files:**
- Modify: `backend/build.gradle.kts` (секция `dependencies { ... }`)

- [ ] **Step 1: Найти место для добавления**

```bash
grep -n "dependencies {" backend/build.gradle.kts | head -3
grep -n "implementation(\"com" backend/build.gradle.kts | head -10
```

Expected: видим существующий блок `dependencies { ... }` и список существующих implementations.

- [ ] **Step 2: Добавить строку**

В `backend/build.gradle.kts` в блок `dependencies { ... }` (рядом с другими `implementation(...)`):

```kotlin
implementation("com.github.librepdf:openpdf:1.3.30")
```

- [ ] **Step 3: Проверить сборку**

```bash
cd backend && ../gradlew :backend:compileKotlin
```

Expected: BUILD SUCCESSFUL. Если PASS — зависимость скачалась и компилируется.

- [ ] **Step 4: Закоммитить**

```bash
git add backend/build.gradle.kts
git commit -m "feat(day17d): добавить OpenPDF 1.3.30 для генерации PDF-отчётов"
```

---

## Task 3: Range-методы в AnalyticsService — тесты + имплементация

**Files:**
- Create: `backend/src/test/kotlin/com/example/cleancity/analytics/AnalyticsServiceRangeTest.kt`
- Modify: `backend/src/main/kotlin/com/example/cleancity/analytics/AnalyticsService.kt`

Добавляем три перегрузки: `overviewRange(from, to)`, `byDistrictRange(from, to)`, `slaRange(from, to)`. Существующие методы НЕ трогаем.

- [ ] **Step 1: Написать failing тест**

Создать `backend/src/test/kotlin/com/example/cleancity/analytics/AnalyticsServiceRangeTest.kt`:

```kotlin
package com.example.cleancity.analytics

import com.example.cleancity.database.tables.Complaints
import com.example.cleancity.database.tables.Users
import com.example.cleancity.shared.models.ComplaintStatus
import com.example.cleancity.shared.models.District
import com.example.cleancity.shared.models.ProblemCategory
import com.example.cleancity.shared.models.UserRole
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AnalyticsServiceRangeTest {

    private lateinit var service: AnalyticsService

    private val april1 = OffsetDateTime.of(2026, 4, 1, 0, 0, 0, 0, ZoneOffset.ofHours(3))
    private val may1 = OffsetDateTime.of(2026, 5, 1, 0, 0, 0, 0, ZoneOffset.ofHours(3))

    @BeforeTest
    fun setup() {
        Database.connect(
            "jdbc:h2:mem:range-${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(Complaints, Users)
            SchemaUtils.create(Users, Complaints)
            val authorId = Users.insert {
                it[Users.email] = "a@x.ru"; it[Users.passwordHash] = "x"
                it[Users.role] = UserRole.RESIDENT.name
                it[Users.emailVerified] = true; it[Users.isActive] = true
                it[Users.createdAt] = april1
                it[Users.passwordChangedAt] = april1
            }[Users.id]
            // Одна жалоба ровно на границе from (входит)
            insertComplaint(authorId, april1, status = ComplaintStatus.RESOLVED, resolvedAt = april1.plusDays(1))
            // Одна — в середине апреля (входит)
            insertComplaint(authorId, april1.plusDays(10), status = ComplaintStatus.NEW)
            // Одна — ровно на границе to (НЕ входит)
            insertComplaint(authorId, may1, status = ComplaintStatus.NEW)
        }
        service = AnalyticsService(AnalyticsRepository())
    }

    private fun insertComplaint(
        authorId: Long,
        createdAt: OffsetDateTime,
        status: ComplaintStatus,
        resolvedAt: OffsetDateTime? = null,
        district: District = District.CENTRAL,
        category: ProblemCategory = ProblemCategory.GARBAGE,
    ) {
        Complaints.insert {
            it[Complaints.authorId] = authorId
            it[Complaints.title] = "test"
            it[Complaints.description] = "desc"
            it[Complaints.category] = category.name
            it[Complaints.district] = district.localizedLabel
            it[Complaints.address] = "addr"
            it[Complaints.lat] = 43.6
            it[Complaints.lon] = 39.7
            it[Complaints.status] = status.name
            it[Complaints.createdAt] = createdAt
            it[Complaints.updatedAt] = createdAt
            if (resolvedAt != null) it[Complaints.resolvedAt] = resolvedAt
        }
    }

    @Test
    fun `overviewRange counts complaint with createdAt equal to from but excludes complaint with createdAt equal to to`() {
        val result = service.overviewRange(april1, may1)
        assertEquals(2, result.total, "Должны попасть 2 жалобы (1 апреля и 10 апреля); жалоба 1 мая не входит")
        assertEquals(1, result.new)
        assertEquals(1, result.resolved)
    }

    @Test
    fun `byDistrictRange aggregates only within range`() {
        val result = service.byDistrictRange(april1, may1)
        val central = result.firstOrNull { it.district == District.CENTRAL }
        assertEquals(2, central?.count, "Только две апрельские жалобы в Центральном")
    }

    @Test
    fun `slaRange counts only resolved within range`() {
        val result = service.slaRange(april1, may1)
        val garbage = result.firstOrNull { it.category == ProblemCategory.GARBAGE }
        assertEquals(1, garbage?.resolvedCount, "Одна resolved-жалоба в категории GARBAGE")
    }
}
```

- [ ] **Step 2: Запустить тест — должен упасть**

```bash
cd backend && ../gradlew :backend:test --tests "com.example.cleancity.analytics.AnalyticsServiceRangeTest"
```

Expected: COMPILATION ERROR — методы `overviewRange`, `byDistrictRange`, `slaRange` не существуют.

- [ ] **Step 3: Имплементировать `overviewRange`**

В `backend/src/main/kotlin/com/example/cleancity/analytics/AnalyticsService.kt` после метода `overview(...)` (примерно строка 56) добавить:

```kotlin
/** Сводка за произвольный диапазон [from, to). Используется PDF-отчётом. */
fun overviewRange(from: OffsetDateTime, to: OffsetDateTime): AnalyticsOverview {
    val rows = repo.loadComplaints(periodStart = from)
        .filter { it.createdAt < to }
    val byStatus = rows.groupingBy { it.status }.eachCount()

    val slaBreachCount = rows.count { row ->
        val active = row.status == ComplaintStatus.NEW || row.status == ComplaintStatus.IN_PROGRESS
        if (!active) return@count false
        val ageHours = Duration.between(row.createdAt, to).toHours()
        ageHours > CategorySla.hoursFor(row.category)
    }

    return AnalyticsOverview(
        total = rows.size,
        new = byStatus[ComplaintStatus.NEW] ?: 0,
        inProgress = byStatus[ComplaintStatus.IN_PROGRESS] ?: 0,
        resolved = byStatus[ComplaintStatus.RESOLVED] ?: 0,
        rejected = byStatus[ComplaintStatus.REJECTED] ?: 0,
        duplicate = byStatus[ComplaintStatus.DUPLICATE] ?: 0,
        today = 0,
        week = 0,
        slaBreachCount = slaBreachCount,
        monthlyKpis = null,
    )
}
```

Поля `today`, `week`, `monthlyKpis` не имеют смысла для произвольного диапазона — ставим 0/null.

- [ ] **Step 4: Имплементировать `byDistrictRange`**

В тот же файл после `byDistrict(...)` (строка 95):

```kotlin
/** По районам за произвольный диапазон [from, to). */
fun byDistrictRange(from: OffsetDateTime, to: OffsetDateTime): List<DistrictStat> {
    val rows = repo.loadComplaints(periodStart = from).filter { it.createdAt < to }
    return District.entries.map { d ->
        val districtRows = rows.filter { it.district == d.localizedLabel }
        DistrictStat(
            district = d,
            label = d.localizedLabel,
            count = districtRows.size,
            newCount = districtRows.count { it.status == ComplaintStatus.NEW },
            resolvedCount = districtRows.count { it.status == ComplaintStatus.RESOLVED },
            medianResolutionHours = null,
            slaCompliancePct = null,
        )
    }
}
```

Поле `district` в `Row` — это название района (текст), а не enum. Сверка через `.localizedLabel`. Возвращаем все 4 района, даже с 0 жалоб (требование макета PDF).

- [ ] **Step 5: Имплементировать `slaRange`**

В тот же файл после `sla(...)` (строка 178):

```kotlin
/** SLA по категориям за произвольный диапазон [from, to). */
fun slaRange(from: OffsetDateTime, to: OffsetDateTime): List<SlaStat> {
    val rows = repo.loadComplaints(periodStart = from).filter { it.createdAt < to }
    return ProblemCategory.entries.map { cat ->
        val slaHours = CategorySla.hoursFor(cat)
        val resolved = rows.filter {
            it.category == cat && it.status == ComplaintStatus.RESOLVED && it.resolvedAt != null
        }
        val breach = resolved.count { Duration.between(it.createdAt, it.resolvedAt!!).toHours() > slaHours }
        val breachPct = if (resolved.isEmpty()) 0.0 else round1(breach * 100.0 / resolved.size)
        SlaStat(
            category = cat,
            label = cat.localizedLabel,
            slaHours = slaHours,
            avgResolutionHours = avgResolutionHours(resolved),
            breachPct = breachPct,
            resolvedCount = resolved.size,
        )
    }
        .filter { it.resolvedCount > 0 }
        .sortedBy { it.category.ordinal }
}
```

Примечание: `round1` и `avgResolutionHours` — приватные методы того же класса, доступны.

- [ ] **Step 6: Запустить тест — должен пройти**

```bash
cd backend && ../gradlew :backend:test --tests "com.example.cleancity.analytics.AnalyticsServiceRangeTest"
```

Expected: 3 теста PASS.

- [ ] **Step 7: Запустить весь analytics-test-suite — убедиться что ничего не сломали**

```bash
cd backend && ../gradlew :backend:test --tests "com.example.cleancity.analytics.*"
```

Expected: все существующие тесты PASS + 3 новых PASS.

- [ ] **Step 8: Закоммитить**

```bash
git add backend/src/main/kotlin/com/example/cleancity/analytics/AnalyticsService.kt backend/src/test/kotlin/com/example/cleancity/analytics/AnalyticsServiceRangeTest.kt
git commit -m "feat(day17d): range-методы overviewRange/byDistrictRange/slaRange в AnalyticsService"
```

---

## Task 4: MonthlyReportLayout — вёрстка PDF

**Files:**
- Create: `backend/src/main/kotlin/com/example/cleancity/analytics/pdf/MonthlyReportLayout.kt`
- Create: `backend/src/test/kotlin/com/example/cleancity/analytics/pdf/MonthlyReportLayoutTest.kt`

Чистая вёрстка: принимает уже готовые DTO и пишет PDF в `OutputStream`. Никаких запросов к БД.

- [ ] **Step 1: Написать failing тест**

Создать `backend/src/test/kotlin/com/example/cleancity/analytics/pdf/MonthlyReportLayoutTest.kt`:

```kotlin
package com.example.cleancity.analytics.pdf

import com.lowagie.text.pdf.PdfReader
import com.lowagie.text.pdf.parser.PdfTextExtractor
import com.example.cleancity.shared.models.AnalyticsOverview
import com.example.cleancity.shared.models.District
import com.example.cleancity.shared.models.DistrictStat
import com.example.cleancity.shared.models.ProblemCategory
import com.example.cleancity.shared.models.SlaStat
import java.io.ByteArrayOutputStream
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MonthlyReportLayoutTest {

    private fun render(
        overview: AnalyticsOverview = sampleOverview(slaBreach = 0),
        districts: List<DistrictStat> = sampleDistricts(),
        sla: List<SlaStat> = sampleSla(),
        generatedAt: OffsetDateTime = OffsetDateTime.of(2026, 5, 28, 14, 32, 0, 0, ZoneOffset.ofHours(3)),
        monthStart: OffsetDateTime = OffsetDateTime.of(2026, 4, 1, 0, 0, 0, 0, ZoneOffset.ofHours(3)),
    ): String {
        val out = ByteArrayOutputStream()
        MonthlyReportLayout().render(
            output = out,
            monthStart = monthStart,
            generatedAt = generatedAt,
            overview = overview,
            districts = districts,
            sla = sla,
        )
        val bytes = out.toByteArray()
        assertTrue(bytes.size > 1000, "PDF должен быть не пустой, размер: ${bytes.size}")
        assertEquals("%PDF-".toByteArray().toList(), bytes.take(5), "PDF magic bytes")
        return PdfTextExtractor(PdfReader(bytes)).getTextFromPage(1) +
            (if (PdfReader(bytes).numberOfPages > 1) PdfTextExtractor(PdfReader(bytes)).getTextFromPage(2) else "")
    }

    @Test
    fun `header contains brand and russian month title`() {
        val text = render()
        assertTrue(text.contains("Чистый Город"), "Бренд: $text")
        assertTrue(text.contains("Сводный отчёт за апрель 2026"), "Заголовок: $text")
        assertTrue(text.contains("г. Сочи"), "Город: $text")
    }

    @Test
    fun `kpi block shows all 6 rows even when zero`() {
        val text = render(overview = sampleOverview(total = 0, new = 0, inProgress = 0, resolved = 0, rejected = 0, duplicate = 0))
        listOf("Всего жалоб", "Новых", "В работе", "Решено", "Отклонено", "Дубликаты").forEach {
            assertTrue(text.contains(it), "Должна быть строка '$it'")
        }
    }

    @Test
    fun `sla banner appears only when breach count is positive`() {
        val zero = render(overview = sampleOverview(slaBreach = 0))
        assertTrue(!zero.contains("Нарушено SLA"), "При 0 нарушений баннера быть не должно")

        val twelve = render(overview = sampleOverview(slaBreach = 12))
        assertTrue(twelve.contains("Нарушено SLA: 12"), "При 12 нарушений показываем счётчик")
    }

    @Test
    fun `district table always shows 4 rows in fixed order`() {
        val text = render(
            districts = listOf(
                DistrictStat(District.CENTRAL, "Центральный", 0, 0, 0, null, null),
                DistrictStat(District.ADLER, "Адлерский", 0, 0, 0, null, null),
                DistrictStat(District.KHOSTA, "Хостинский", 0, 0, 0, null, null),
                DistrictStat(District.LAZAREVSKOE, "Лазаревский", 0, 0, 0, null, null),
            )
        )
        assertTrue(text.contains("Центральный"))
        assertTrue(text.contains("Адлерский"))
        assertTrue(text.contains("Хостинский"))
        assertTrue(text.contains("Лазаревский"))
    }

    @Test
    fun `sla table shows fallback when empty`() {
        val text = render(sla = emptyList())
        assertTrue(text.contains("За отчётный период данных нет"), "Должен быть fallback: $text")
    }

    private fun sampleOverview(
        total: Int = 142, new: Int = 18, inProgress: Int = 31, resolved: Int = 87,
        rejected: Int = 4, duplicate: Int = 2, slaBreach: Int = 0,
    ) = AnalyticsOverview(
        total = total, new = new, inProgress = inProgress, resolved = resolved,
        rejected = rejected, duplicate = duplicate,
        today = 0, week = 0, slaBreachCount = slaBreach, monthlyKpis = null,
    )

    private fun sampleDistricts() = listOf(
        DistrictStat(District.CENTRAL, "Центральный", 62, 8, 38, null, null),
        DistrictStat(District.ADLER, "Адлерский", 41, 5, 28, null, null),
        DistrictStat(District.KHOSTA, "Хостинский", 24, 3, 15, null, null),
        DistrictStat(District.LAZAREVSKOE, "Лазаревский", 15, 2, 6, null, null),
    )

    private fun sampleSla() = listOf(
        SlaStat(ProblemCategory.GARBAGE, "Свалка", 48, 31.0, 8.0, 10),
        SlaStat(ProblemCategory.ROAD_HOLE, "Яма на дороге", 72, 68.0, 22.0, 5),
    )
}
```

- [ ] **Step 2: Запустить тест — должен упасть**

```bash
cd backend && ../gradlew :backend:test --tests "com.example.cleancity.analytics.pdf.MonthlyReportLayoutTest"
```

Expected: COMPILATION ERROR — класс `MonthlyReportLayout` не существует.

- [ ] **Step 3: Имплементировать MonthlyReportLayout**

Создать `backend/src/main/kotlin/com/example/cleancity/analytics/pdf/MonthlyReportLayout.kt`:

```kotlin
package com.example.cleancity.analytics.pdf

import com.example.cleancity.shared.models.AnalyticsOverview
import com.example.cleancity.shared.models.DistrictStat
import com.example.cleancity.shared.models.SlaStat
import com.lowagie.text.Document
import com.lowagie.text.Element
import com.lowagie.text.Font
import com.lowagie.text.FontFactory
import com.lowagie.text.PageSize
import com.lowagie.text.Paragraph
import com.lowagie.text.Phrase
import com.lowagie.text.Rectangle
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfWriter
import java.awt.Color
import java.io.OutputStream
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class MonthlyReportLayout {

    private companion object {
        const val FONT_REGULAR = "DejaVuSans"
        const val FONT_BOLD = "DejaVuSans-Bold"

        // Месяцы в родительном падеже («за апрель», «за май», …)
        val MONTHS_GENITIVE = listOf(
            "января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря",
        )

        val SLA_RED = Color(0xE8, 0x44, 0x3C)
        val GREY = Color(0x6B, 0x70, 0x80)

        val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm")
    }

    init {
        // Регистрация шрифтов — лениво, идемпотентно.
        if (!FontFactory.isRegistered(FONT_REGULAR)) {
            val regular = javaClass.classLoader.getResource("fonts/DejaVuSans.ttf")
                ?: error("DejaVuSans.ttf not found in resources/fonts/")
            val bold = javaClass.classLoader.getResource("fonts/DejaVuSans-Bold.ttf")
                ?: error("DejaVuSans-Bold.ttf not found in resources/fonts/")
            FontFactory.register(regular.toString(), FONT_REGULAR)
            FontFactory.register(bold.toString(), FONT_BOLD)
        }
    }

    private fun font(bold: Boolean, size: Float, color: Color = Color.BLACK): Font {
        val name = if (bold) FONT_BOLD else FONT_REGULAR
        return FontFactory.getFont(name, "Identity-H", true, size, Font.NORMAL, color)
    }

    fun render(
        output: OutputStream,
        monthStart: OffsetDateTime,
        generatedAt: OffsetDateTime,
        overview: AnalyticsOverview,
        districts: List<DistrictStat>,
        sla: List<SlaStat>,
    ) {
        val doc = Document(PageSize.A4, 36f, 36f, 36f, 36f)
        PdfWriter.getInstance(doc, output)
        doc.open()
        writeHeader(doc, monthStart, generatedAt)
        writeKpiBlock(doc, overview)
        if (overview.slaBreachCount > 0) writeSlaBanner(doc, overview.slaBreachCount)
        writeDistrictTable(doc, districts)
        writeSlaTable(doc, sla)
        doc.close()
    }

    private fun writeHeader(doc: Document, monthStart: OffsetDateTime, generatedAt: OffsetDateTime) {
        doc.add(Paragraph("Чистый Город", font(bold = true, size = 18f)))
        val month = MONTHS_GENITIVE[monthStart.monthValue - 1]
        doc.add(Paragraph("Сводный отчёт за $month ${monthStart.year} г.", font(bold = true, size = 13f)))
        doc.add(
            Paragraph(
                "г. Сочи · сформирован ${generatedAt.format(DATE_FMT)} MSK",
                font(bold = false, size = 9f, color = GREY),
            )
        )
        doc.add(Paragraph(" ", font(bold = false, size = 6f)))
    }

    private fun writeKpiBlock(doc: Document, o: AnalyticsOverview) {
        doc.add(Paragraph("Сводка за месяц", font(bold = true, size = 13f)))
        val table = PdfPTable(floatArrayOf(3f, 1f)).apply {
            widthPercentage = 60f
            horizontalAlignment = Element.ALIGN_LEFT
            spacingBefore = 4f
            spacingAfter = 12f
        }
        listOf(
            "Всего жалоб" to o.total,
            "Новых" to o.new,
            "В работе" to o.inProgress,
            "Решено" to o.resolved,
            "Отклонено" to o.rejected,
            "Дубликаты" to o.duplicate,
        ).forEach { (label, value) ->
            table.addCell(textCell(label, font(bold = false, size = 10f), align = Element.ALIGN_LEFT))
            table.addCell(textCell(value.toString(), font(bold = true, size = 10f), align = Element.ALIGN_RIGHT))
        }
        doc.add(table)
    }

    private fun writeSlaBanner(doc: Document, count: Int) {
        val p = Paragraph(
            Phrase("⚠ Нарушено SLA: $count жалоб", font(bold = true, size = 11f, color = SLA_RED))
        )
        p.spacingAfter = 12f
        doc.add(p)
    }

    private fun writeDistrictTable(doc: Document, districts: List<DistrictStat>) {
        doc.add(Paragraph("По районам Сочи", font(bold = true, size = 13f)))
        val table = PdfPTable(floatArrayOf(3f, 1f, 1f, 1f)).apply {
            widthPercentage = 80f
            horizontalAlignment = Element.ALIGN_LEFT
            spacingBefore = 4f
            spacingAfter = 12f
        }
        listOf("Район", "Всего", "Новых", "Решено").forEachIndexed { idx, h ->
            table.addCell(headerCell(h, if (idx == 0) Element.ALIGN_LEFT else Element.ALIGN_RIGHT))
        }
        districts.forEach { d ->
            table.addCell(textCell(d.label, font(bold = false, size = 10f), align = Element.ALIGN_LEFT))
            table.addCell(textCell(d.count.toString(), font(bold = false, size = 10f), align = Element.ALIGN_RIGHT))
            table.addCell(textCell(d.newCount.toString(), font(bold = false, size = 10f), align = Element.ALIGN_RIGHT))
            table.addCell(textCell(d.resolvedCount.toString(), font(bold = false, size = 10f), align = Element.ALIGN_RIGHT))
        }
        doc.add(table)
    }

    private fun writeSlaTable(doc: Document, sla: List<SlaStat>) {
        doc.add(Paragraph("SLA по категориям", font(bold = true, size = 13f)))
        if (sla.isEmpty()) {
            doc.add(Paragraph("За отчётный период данных нет.", font(bold = false, size = 10f, color = GREY)))
            return
        }
        val table = PdfPTable(floatArrayOf(3f, 1f, 1f, 1f)).apply {
            widthPercentage = 90f
            horizontalAlignment = Element.ALIGN_LEFT
            spacingBefore = 4f
        }
        listOf("Категория", "Норматив", "Среднее", "Нарушено").forEachIndexed { idx, h ->
            table.addCell(headerCell(h, if (idx == 0) Element.ALIGN_LEFT else Element.ALIGN_RIGHT))
        }
        sla.forEach { s ->
            table.addCell(textCell(s.label, font(bold = false, size = 10f), align = Element.ALIGN_LEFT))
            table.addCell(textCell("${s.slaHours} ч", font(bold = false, size = 10f), align = Element.ALIGN_RIGHT))
            val avg = s.avgResolutionHours
            table.addCell(textCell(if (avg == null) "—" else "${avg.toInt()} ч", font(bold = false, size = 10f), align = Element.ALIGN_RIGHT))
            table.addCell(textCell("${s.breachPct.toInt()}%", font(bold = false, size = 10f), align = Element.ALIGN_RIGHT))
        }
        doc.add(table)
    }

    private fun textCell(text: String, font: Font, align: Int): PdfPCell =
        PdfPCell(Phrase(text, font)).apply {
            horizontalAlignment = align
            border = Rectangle.BOTTOM
            borderColorBottom = Color(0xE5, 0xE7, 0xEB)
            paddingTop = 4f
            paddingBottom = 4f
        }

    private fun headerCell(text: String, align: Int): PdfPCell =
        PdfPCell(Phrase(text, font(bold = true, size = 10f))).apply {
            horizontalAlignment = align
            border = Rectangle.BOTTOM
            borderColorBottom = Color.BLACK
            paddingTop = 4f
            paddingBottom = 4f
            backgroundColor = Color(0xF3, 0xF4, 0xF6)
        }
}
```

- [ ] **Step 4: Запустить тест — должен пройти**

```bash
cd backend && ../gradlew :backend:test --tests "com.example.cleancity.analytics.pdf.MonthlyReportLayoutTest"
```

Expected: 5 тестов PASS.

Если падает на «Сводный отчёт за апрель 2026» — проверить что DateTimeFormatter правильно форматирует, и что родительный падеж корректно подставляется.

- [ ] **Step 5: Закоммитить**

```bash
git add backend/src/main/kotlin/com/example/cleancity/analytics/pdf/MonthlyReportLayout.kt backend/src/test/kotlin/com/example/cleancity/analytics/pdf/MonthlyReportLayoutTest.kt
git commit -m "feat(day17d): MonthlyReportLayout — рендер PDF через OpenPDF"
```

---

## Task 5: MonthlyReportPdfService — оркестрация

**Files:**
- Create: `backend/src/main/kotlin/com/example/cleancity/analytics/pdf/MonthlyReportPdfService.kt`
- Create: `backend/src/test/kotlin/com/example/cleancity/analytics/pdf/MonthlyReportPdfServiceTest.kt`

Принимает `Clock` (для тестируемости) и `AnalyticsService`, считает «прошлый месяц по MSK», тянет данные, отдаёт байты.

- [ ] **Step 1: Написать failing тест**

Создать `backend/src/test/kotlin/com/example/cleancity/analytics/pdf/MonthlyReportPdfServiceTest.kt`:

```kotlin
package com.example.cleancity.analytics.pdf

import com.example.cleancity.analytics.AnalyticsService
import com.example.cleancity.shared.models.AnalyticsOverview
import com.example.cleancity.shared.models.District
import com.example.cleancity.shared.models.DistrictStat
import com.example.cleancity.shared.models.SlaStat
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MonthlyReportPdfServiceTest {

    // Clock зафиксирован на 28 мая 2026, 11:32 UTC = 14:32 MSK
    private val fixedClock = Clock.fixed(Instant.parse("2026-05-28T11:32:00Z"), ZoneId.of("Europe/Moscow"))

    private fun newService(svc: AnalyticsService): MonthlyReportPdfService =
        MonthlyReportPdfService(analyticsService = svc, clock = fixedClock)

    private fun mockAnalytics(
        overview: AnalyticsOverview = AnalyticsOverview(142, 18, 31, 87, 4, 2, 0, 0, 0, null),
        districts: List<DistrictStat> = District.entries.map { DistrictStat(it, it.localizedLabel, 0, 0, 0, null, null) },
        sla: List<SlaStat> = emptyList(),
    ): AnalyticsService {
        val svc: AnalyticsService = mockk()
        every { svc.overviewRange(any(), any()) } returns overview
        every { svc.byDistrictRange(any(), any()) } returns districts
        every { svc.slaRange(any(), any()) } returns sla
        return svc
    }

    @Test
    fun `generates non-empty PDF starting with magic bytes`() {
        val bytes = newService(mockAnalytics()).generate()
        assertTrue(bytes.size > 1000, "size = ${bytes.size}")
        assertEquals("%PDF-".toByteArray().toList(), bytes.take(5))
    }

    @Test
    fun `filename includes previous month in YYYY-MM`() {
        val name = newService(mockAnalytics()).filename()
        assertEquals("cleancity-monthly-report-2026-04.pdf", name)
    }

    @Test
    fun `range computed for previous full month in MSK`() {
        val svc = mockk<AnalyticsService>(relaxed = true)
        every { svc.overviewRange(any(), any()) } returns AnalyticsOverview(0, 0, 0, 0, 0, 0, 0, 0, 0, null)
        every { svc.byDistrictRange(any(), any()) } returns emptyList()
        every { svc.slaRange(any(), any()) } returns emptyList()

        newService(svc).generate()

        // Проверяем что в svc.* был вызван с from = 1 апреля 00:00 MSK, to = 1 мая 00:00 MSK
        io.mockk.verify {
            svc.overviewRange(
                io.mockk.match { it.year == 2026 && it.monthValue == 4 && it.dayOfMonth == 1 },
                io.mockk.match { it.year == 2026 && it.monthValue == 5 && it.dayOfMonth == 1 },
            )
        }
    }
}
```

- [ ] **Step 2: Проверить mockk доступен в test classpath**

```bash
grep -n "mockk" backend/build.gradle.kts
```

Если отсутствует — добавить `testImplementation("io.mockk:mockk:1.13.10")` в `dependencies`. Если уже есть (вероятно — проект уже использует mockk) — пропустить.

- [ ] **Step 3: Запустить тест — должен упасть**

```bash
cd backend && ../gradlew :backend:test --tests "com.example.cleancity.analytics.pdf.MonthlyReportPdfServiceTest"
```

Expected: COMPILATION ERROR — `MonthlyReportPdfService` не существует.

- [ ] **Step 4: Имплементировать MonthlyReportPdfService**

Создать `backend/src/main/kotlin/com/example/cleancity/analytics/pdf/MonthlyReportPdfService.kt`:

```kotlin
package com.example.cleancity.analytics.pdf

import com.example.cleancity.analytics.AnalyticsService
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

class MonthlyReportPdfService(
    private val analyticsService: AnalyticsService,
    private val clock: Clock = Clock.system(ZoneId.of("Europe/Moscow")),
) {
    private val msk: ZoneId = ZoneId.of("Europe/Moscow")
    private val layout = MonthlyReportLayout()

    fun generate(): ByteArray {
        val (monthStart, monthEnd) = computeRange()
        val overview = analyticsService.overviewRange(monthStart, monthEnd)
        val districts = analyticsService.byDistrictRange(monthStart, monthEnd)
        val sla = analyticsService.slaRange(monthStart, monthEnd)
        val out = ByteArrayOutputStream()
        layout.render(
            output = out,
            monthStart = monthStart,
            generatedAt = OffsetDateTime.now(clock),
            overview = overview,
            districts = districts,
            sla = sla,
        )
        return out.toByteArray()
    }

    fun filename(): String {
        val (start, _) = computeRange()
        return "cleancity-monthly-report-%04d-%02d.pdf".format(start.year, start.monthValue)
    }

    private fun computeRange(): Pair<OffsetDateTime, OffsetDateTime> {
        val nowMsk = OffsetDateTime.now(clock).atZoneSameInstant(msk)
        val firstOfCurrent = LocalDate.of(nowMsk.year, nowMsk.month, 1).atStartOfDay(msk)
        val firstOfPrev = firstOfCurrent.minusMonths(1)
        return firstOfPrev.toOffsetDateTime() to firstOfCurrent.toOffsetDateTime()
    }
}
```

- [ ] **Step 5: Запустить тест — должен пройти**

```bash
cd backend && ../gradlew :backend:test --tests "com.example.cleancity.analytics.pdf.MonthlyReportPdfServiceTest"
```

Expected: 3 теста PASS.

- [ ] **Step 6: Закоммитить**

```bash
git add backend/src/main/kotlin/com/example/cleancity/analytics/pdf/MonthlyReportPdfService.kt backend/src/test/kotlin/com/example/cleancity/analytics/pdf/MonthlyReportPdfServiceTest.kt
git commit -m "feat(day17d): MonthlyReportPdfService — оркестрация прошлого месяца + рендер"
```

---

## Task 6: Route `/analytics/export/monthly-report.pdf`

**Files:**
- Modify: `backend/src/main/kotlin/com/example/cleancity/analytics/AnalyticsRoutes.kt`
- Modify: `backend/src/main/kotlin/com/example/cleancity/Application.kt`
- Modify: `backend/src/test/kotlin/com/example/cleancity/analytics/AnalyticsRoutesTest.kt` (новые тесты)

- [ ] **Step 1: Написать failing тесты для route**

В `backend/src/test/kotlin/com/example/cleancity/analytics/AnalyticsRoutesTest.kt` в конец класса (перед `}` закрывающим класс) добавить:

```kotlin
@Test
fun `guest gets 401 on monthly report pdf`() = testApplication {
    initDb()
    appWith()

    val resp = client.get("/analytics/export/monthly-report.pdf")
    assertEquals(HttpStatusCode.Unauthorized, resp.status)
}

@Test
fun `resident gets 403 on monthly report pdf`() = testApplication {
    val ctx = initDb()
    appWith()

    val resp = client.get("/analytics/export/monthly-report.pdf") {
        header("Authorization", "Bearer ${bearerFor(ctx.residentId, UserRole.RESIDENT)}")
    }
    assertEquals(HttpStatusCode.Forbidden, resp.status)
}

@Test
fun `admin gets 200 with pdf headers and magic bytes`() = testApplication {
    val ctx = initDb()
    appWith()

    val resp = client.get("/analytics/export/monthly-report.pdf") {
        header("Authorization", "Bearer ${bearerFor(ctx.adminId, UserRole.ADMIN)}")
    }
    assertEquals(HttpStatusCode.OK, resp.status)
    assertEquals("application/pdf", resp.headers["Content-Type"])
    val disposition = resp.headers["Content-Disposition"] ?: ""
    assertEquals(
        true,
        disposition.startsWith("attachment; filename=\"cleancity-monthly-report-") &&
            disposition.endsWith(".pdf\""),
        "Content-Disposition: $disposition",
    )
    val bytes = resp.readBytes()
    assertEquals("%PDF-".toByteArray().toList(), bytes.take(5))
}
```

Также проверить `imports` в файле — нужно добавить `import io.ktor.client.statement.readBytes` если отсутствует.

И **обновить** функцию `appWith()` чтобы прокидывать `MonthlyReportPdfService`. Заменить строку `routing { analyticsRoutes(AnalyticsService(AnalyticsRepository())) }` на:

```kotlin
routing {
    val analyticsSvc = AnalyticsService(AnalyticsRepository())
    analyticsRoutes(analyticsSvc, com.example.cleancity.analytics.pdf.MonthlyReportPdfService(analyticsSvc))
}
```

- [ ] **Step 2: Запустить тесты — должны упасть**

```bash
cd backend && ../gradlew :backend:test --tests "com.example.cleancity.analytics.AnalyticsRoutesTest"
```

Expected: COMPILATION ERROR — сигнатура `analyticsRoutes(...)` не принимает второй параметр.

- [ ] **Step 3: Изменить сигнатуру `analyticsRoutes` + добавить route**

В `backend/src/main/kotlin/com/example/cleancity/analytics/AnalyticsRoutes.kt`:

- В начало файла добавить:

```kotlin
import com.example.cleancity.analytics.pdf.MonthlyReportPdfService
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
```

- Изменить сигнатуру:

```kotlin
fun Route.analyticsRoutes(service: AnalyticsService, pdfService: MonthlyReportPdfService) {
```

- В конец блока `route("/analytics") { ... }` (перед закрывающим `}`) добавить:

```kotlin
get("/export/monthly-report.pdf") {
    call.requireAdmin()
    val bytes = pdfService.generate()
    call.response.header(
        HttpHeaders.ContentDisposition,
        "attachment; filename=\"${pdfService.filename()}\"",
    )
    call.respondBytes(bytes, ContentType.Application.Pdf)
}
```

- [ ] **Step 4: Обновить wiring в Application.kt**

В `backend/src/main/kotlin/com/example/cleancity/Application.kt`:

- В импорты добавить:

```kotlin
import com.example.cleancity.analytics.pdf.MonthlyReportPdfService
```

- После строки `val analyticsService = AnalyticsService(analyticsRepository)` (примерно 156) добавить:

```kotlin
val monthlyReportPdfService = MonthlyReportPdfService(analyticsService)
```

- Заменить `analyticsRoutes(analyticsService)` (примерно 181) на:

```kotlin
analyticsRoutes(analyticsService, monthlyReportPdfService)
```

- [ ] **Step 5: Запустить тесты — должны пройти**

```bash
cd backend && ../gradlew :backend:test --tests "com.example.cleancity.analytics.AnalyticsRoutesTest"
```

Expected: все существующие тесты PASS + 3 новых (`guest gets 401`, `resident gets 403`, `admin gets 200`) PASS.

- [ ] **Step 6: Запустить весь backend test-suite**

```bash
cd backend && ../gradlew :backend:test
```

Expected: BUILD SUCCESSFUL, ничего из существующего не сломано.

- [ ] **Step 7: Закоммитить**

```bash
git add backend/src/main/kotlin/com/example/cleancity/analytics/AnalyticsRoutes.kt backend/src/main/kotlin/com/example/cleancity/Application.kt backend/src/test/kotlin/com/example/cleancity/analytics/AnalyticsRoutesTest.kt
git commit -m "feat(day17d): эндпоинт GET /analytics/export/monthly-report.pdf"
```

---

## Task 7: Фронт — API-функция `downloadMonthlyReport`

**Files:**
- Modify: `web-admin/src/api/analytics.ts` (добавить функцию)
- Create: `web-admin/src/api/contentDisposition.ts` (утилита парсинга)
- Create: `web-admin/src/api/contentDisposition.test.ts`

- [ ] **Step 1: Написать failing тест для parseContentDisposition**

Создать `web-admin/src/api/contentDisposition.test.ts`:

```ts
import { describe, it, expect } from 'vitest'
import { parseContentDisposition } from './contentDisposition'

describe('parseContentDisposition', () => {
  it('returns filename for attachment with quotes', () => {
    expect(parseContentDisposition('attachment; filename="report.pdf"')).toBe('report.pdf')
  })

  it('returns filename without quotes', () => {
    expect(parseContentDisposition('attachment; filename=report.pdf')).toBe('report.pdf')
  })

  it('returns null when header missing', () => {
    expect(parseContentDisposition(undefined)).toBeNull()
    expect(parseContentDisposition('')).toBeNull()
  })

  it('returns null when filename absent', () => {
    expect(parseContentDisposition('attachment')).toBeNull()
  })
})
```

- [ ] **Step 2: Запустить тест — должен упасть**

```bash
cd web-admin && npx vitest run src/api/contentDisposition.test.ts
```

Expected: FAIL — модуль не найден.

- [ ] **Step 3: Имплементировать утилиту**

Создать `web-admin/src/api/contentDisposition.ts`:

```ts
export function parseContentDisposition(header: string | undefined | null): string | null {
  if (!header) return null
  const match = header.match(/filename="?([^";]+)"?/i)
  return match ? match[1] : null
}
```

- [ ] **Step 4: Запустить тест — должен пройти**

```bash
cd web-admin && npx vitest run src/api/contentDisposition.test.ts
```

Expected: 4 теста PASS.

- [ ] **Step 5: Добавить API-функцию в analytics.ts**

В `web-admin/src/api/analytics.ts` в конец файла:

```ts
export async function downloadMonthlyReport(): Promise<{ blob: Blob; filename: string }> {
  const res = await api.get<Blob>('/analytics/export/monthly-report.pdf', {
    responseType: 'blob',
  })
  const filename =
    parseContentDisposition(res.headers['content-disposition']) ??
    'cleancity-monthly-report.pdf'
  return { blob: res.data, filename }
}
```

И в импорты в начале файла добавить:

```ts
import { parseContentDisposition } from './contentDisposition'
```

- [ ] **Step 6: Прогнать существующие тесты analytics.ts**

```bash
cd web-admin && npx vitest run src/api/analytics.test.ts
```

Expected: PASS (ничего не сломали).

- [ ] **Step 7: Закоммитить**

```bash
git add web-admin/src/api/analytics.ts web-admin/src/api/contentDisposition.ts web-admin/src/api/contentDisposition.test.ts
git commit -m "feat(day17d): API-функция downloadMonthlyReport + парсинг Content-Disposition"
```

---

## Task 8: Компонент ExportSection

**Files:**
- Create: `web-admin/src/pages/settings/ExportSection.tsx`
- Create: `web-admin/src/pages/settings/ExportSection.test.tsx`

- [ ] **Step 1: Написать failing тест**

Создать `web-admin/src/pages/settings/ExportSection.test.tsx`:

```tsx
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ExportSection } from './ExportSection'
import * as analyticsApi from '@/api/analytics'

// Глобальные моки toast и URL.createObjectURL
vi.mock('sonner', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}))

describe('ExportSection', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    global.URL.createObjectURL = vi.fn(() => 'blob:test-url')
    global.URL.revokeObjectURL = vi.fn()
  })

  it('renders 4 export cards', () => {
    render(<ExportSection />)
    expect(screen.getByText(/Сводный отчёт за месяц/i)).toBeInTheDocument()
    expect(screen.getByText(/Реестр жалоб/i)).toBeInTheDocument()
    expect(screen.getByText(/Отчёт по SLA/i)).toBeInTheDocument()
    expect(screen.getByText(/Голосование жителей/i)).toBeInTheDocument()
  })

  it('marks 3 cards as disabled with "Скоро" badge', () => {
    render(<ExportSection />)
    expect(screen.getAllByText('Скоро')).toHaveLength(3)
  })

  it('calls downloadMonthlyReport on active button click', async () => {
    const spy = vi
      .spyOn(analyticsApi, 'downloadMonthlyReport')
      .mockResolvedValue({ blob: new Blob(['x']), filename: 'cleancity-monthly-report-2026-04.pdf' })
    render(<ExportSection />)
    fireEvent.click(screen.getByRole('button', { name: /Скачать PDF/i }))
    await waitFor(() => expect(spy).toHaveBeenCalledOnce())
  })

  it('disables button while loading and re-enables after', async () => {
    let resolveFn: (v: { blob: Blob; filename: string }) => void = () => {}
    vi.spyOn(analyticsApi, 'downloadMonthlyReport').mockReturnValue(
      new Promise((res) => {
        resolveFn = res
      }),
    )
    render(<ExportSection />)
    const btn = screen.getByRole('button', { name: /Скачать PDF/i })
    fireEvent.click(btn)
    await waitFor(() => expect(btn).toBeDisabled())
    resolveFn({ blob: new Blob(['x']), filename: 'x.pdf' })
    await waitFor(() => expect(btn).not.toBeDisabled())
  })

  it('shows error toast on failure', async () => {
    vi.spyOn(analyticsApi, 'downloadMonthlyReport').mockRejectedValue(new Error('boom'))
    const { toast } = await import('sonner')
    render(<ExportSection />)
    fireEvent.click(screen.getByRole('button', { name: /Скачать PDF/i }))
    await waitFor(() => expect(toast.error).toHaveBeenCalled())
  })
})
```

- [ ] **Step 2: Запустить тест — должен упасть**

```bash
cd web-admin && npx vitest run src/pages/settings/ExportSection.test.tsx
```

Expected: FAIL — компонент не найден.

- [ ] **Step 3: Имплементировать ExportSection**

Создать `web-admin/src/pages/settings/ExportSection.tsx`:

```tsx
import { useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { downloadMonthlyReport } from '@/api/analytics'

const MONTHS_GENITIVE = [
  'январь', 'февраль', 'март', 'апрель', 'май', 'июнь',
  'июль', 'август', 'сентябрь', 'октябрь', 'ноябрь', 'декабрь',
]

function previousMonthLabel(now: Date = new Date()): string {
  const prev = new Date(now.getFullYear(), now.getMonth() - 1, 1)
  return `${MONTHS_GENITIVE[prev.getMonth()]} ${prev.getFullYear()} г.`
}

const DISABLED_CARDS: Array<{ icon: string; title: string; subtitle: string }> = [
  { icon: '📋', title: 'Реестр жалоб', subtitle: 'Все жалобы с фильтрами' },
  { icon: '⏱', title: 'Отчёт по SLA', subtitle: 'Анализ соблюдения сроков' },
  { icon: '🗳', title: 'Голосование жителей', subtitle: 'Активность по поддержке жалоб' },
]

export function ExportSection() {
  const [loading, setLoading] = useState(false)

  const onDownload = async () => {
    setLoading(true)
    try {
      const { blob, filename } = await downloadMonthlyReport()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = filename
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)
      toast.success('Отчёт скачан')
    } catch {
      toast.error('Не удалось сформировать отчёт. Попробуйте ещё раз.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <section className="flex flex-col gap-3">
      <div>
        <h2 className="text-lg font-semibold">Экспорт отчётов в PDF</h2>
        <p className="text-sm text-muted-foreground">Готовые шаблоны для отчётности</p>
      </div>
      <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
        <Card className="p-4">
          <div className="text-sm font-semibold">📄 Сводный отчёт за месяц</div>
          <div className="mt-1 text-xs text-muted-foreground">
            KPI, районы, SLA · Для отчёта мэрии
          </div>
          <div className="mt-2 text-xs text-muted-foreground">За {previousMonthLabel()}</div>
          <Button
            className="mt-3"
            size="sm"
            disabled={loading}
            onClick={onDownload}
          >
            {loading ? 'Готовим…' : 'Скачать PDF'}
          </Button>
        </Card>
        {DISABLED_CARDS.map((c) => (
          <Card
            key={c.title}
            className="cursor-not-allowed p-4 opacity-50"
            title="Появится в следующих обновлениях"
            aria-disabled="true"
          >
            <div className="text-sm font-semibold">
              {c.icon} {c.title}
            </div>
            <div className="mt-1 text-xs text-muted-foreground">{c.subtitle}</div>
            <div className="mt-3 inline-block rounded bg-muted px-2 py-0.5 text-xs">Скоро</div>
          </Card>
        ))}
      </div>
    </section>
  )
}
```

Примечание о `Card` импорте: проверить `web-admin/src/components/ui/card.tsx` или эквивалент. Если такого нет в проекте — заменить `<Card>` на `<div className="rounded-lg border bg-card p-4">` (см. как `TeamSection` делает свои карточки).

- [ ] **Step 4: Если `Card` не существует — адаптировать**

```bash
ls web-admin/src/components/ui/card.tsx 2>/dev/null && echo "Card OK" || echo "NO Card"
```

Если `NO Card` — заменить все `<Card …>` на `<div className="rounded-lg border bg-card …">` с теми же остальными классами и убрать импорт `Card`.

- [ ] **Step 5: Запустить тест — должен пройти**

```bash
cd web-admin && npx vitest run src/pages/settings/ExportSection.test.tsx
```

Expected: 5 тестов PASS.

- [ ] **Step 6: Закоммитить**

```bash
git add web-admin/src/pages/settings/ExportSection.tsx web-admin/src/pages/settings/ExportSection.test.tsx
git commit -m "feat(day17d): ExportSection — 4 карточки (1 активная + 3 disabled)"
```

---

## Task 9: Подключить ExportSection в SettingsPage

**Files:**
- Modify: `web-admin/src/pages/SettingsPage.tsx`

- [ ] **Step 1: Добавить импорт и рендер**

В `web-admin/src/pages/SettingsPage.tsx` заменить:

```tsx
import { useAuth } from '@/auth/AuthContext'
import { TeamSection } from './settings/TeamSection'
// Журнал событий временно скрыт — секция готова, но решено не показывать
// до отдельной договорённости. Раскомментировать импорт + render ниже.
// import { AuditLogSection } from './settings/AuditLogSection'

export function SettingsPage() {
  const { user } = useAuth()
  const role = user?.role ?? 'OPERATOR'

  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-2xl font-semibold">Настройки</h1>
      <TeamSection currentRole={role} />
      {/* <AuditLogSection /> */}
    </div>
  )
}
```

на:

```tsx
import { useAuth } from '@/auth/AuthContext'
import { ExportSection } from './settings/ExportSection'
import { TeamSection } from './settings/TeamSection'
// Журнал событий временно скрыт — секция готова, но решено не показывать
// до отдельной договорённости. Раскомментировать импорт + render ниже.
// import { AuditLogSection } from './settings/AuditLogSection'

export function SettingsPage() {
  const { user } = useAuth()
  const role = user?.role ?? 'OPERATOR'

  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-2xl font-semibold">Настройки</h1>
      <TeamSection currentRole={role} />
      <ExportSection />
      {/* <AuditLogSection /> */}
    </div>
  )
}
```

- [ ] **Step 2: Запустить тесты SettingsPage**

```bash
cd web-admin && npx vitest run src/pages/SettingsPage.test.tsx
```

Expected: PASS. Если падает на «не найдено такое-то» — может оказаться, что тест ассертит количество секций. Тогда обновить тест чтобы он учитывал новую `ExportSection`.

- [ ] **Step 3: Прогнать весь фронт-test-suite**

```bash
cd web-admin && npx vitest run
```

Expected: все тесты PASS.

- [ ] **Step 4: Запустить typecheck**

```bash
cd web-admin && npx tsc --noEmit
```

Expected: ноль ошибок.

- [ ] **Step 5: Закоммитить**

```bash
git add web-admin/src/pages/SettingsPage.tsx
git commit -m "feat(day17d): подключить ExportSection в SettingsPage"
```

---

## Task 10: Ручная проверка (Checkpoint Day 17)

**Files:** нет правок кода — только верификация.

Цель — убедиться, что вся фича работает end-to-end на dev-окружении.

- [ ] **Step 1: Поднять backend + web-admin локально**

```bash
# Терминал 1: backend
cd backend && ../gradlew :backend:run
```

Expected: Ktor стартует на :8081 без ошибок.

```bash
# Терминал 2: web-admin
cd web-admin && npm run dev
```

Expected: Vite стартует на :5173.

- [ ] **Step 2: Залогиниться админом**

Открыть `http://localhost:5173/login`, ввести `admin@cleancity.dev` / `Admin12345!` (DEV-сид V99, см. CLAUDE.md / memory).

Expected: попадаем в Overview.

- [ ] **Step 3: Перейти в Настройки и проверить ExportSection**

Открыть `/settings`. Скроллнуть вниз — должна быть секция «Экспорт отчётов в PDF».

Проверить:
- Видны все 4 карточки.
- 3 карточки с opacity-50, без CTA, с бейджем «Скоро».
- 1-я карточка активна, отображает «За <предыдущий-месяц> 2026 г.» (на момент 28.05.2026 — «За апрель 2026 г.»).
- На hover у disabled карточек появляется браузерный tooltip «Появится в следующих обновлениях».

- [ ] **Step 4: Скачать PDF**

Нажать «Скачать PDF» в активной карточке.

Expected:
- Кнопка превращается в «Готовим…» и disabled.
- Браузер скачивает `cleancity-monthly-report-2026-04.pdf`.
- Toast «Отчёт скачан».

- [ ] **Step 5: Открыть PDF в Preview**

Открыть скачанный файл.

Проверить визуально:
- Кириллица читается без вопросиков / квадратиков.
- Шапка: «Чистый Город / Сводный отчёт за апрель 2026 г. / г. Сочи · сформирован дд.мм.гггг, ЧЧ:ММ MSK».
- KPI-блок: 6 строк.
- Если есть SLA-нарушения — красный баннер «⚠ Нарушено SLA: N жалоб».
- Таблица «По районам Сочи»: ровно 4 строки в фиксированном порядке.
- Таблица «SLA по категориям»: либо строки активных категорий, либо строка «За отчётный период данных нет.» если категорий не было.

- [ ] **Step 6: Проверить кейс «нет данных»**

Можно либо:
- Сбросить БД на чистый сид (`docker compose down -v && docker compose up -d && wait`) — DEV-сид V99 даёт админа, жалоб может не быть.
- Либо подключиться к боевой dev-БД где жалоб мало.

Скачать PDF ещё раз. Проверить, что:
- Эндпоинт всё равно отдаёт 200.
- В PDF все KPI = 0.
- SLA-таблица — fallback «За отчётный период данных нет.»
- Нет SLA-баннера.

- [ ] **Step 7: Проверить роль RESIDENT**

В DevTools открыть Network. Залогиниться резидентом (если есть seed) или вручную сгенерить токен. Запросить `GET /analytics/export/monthly-report.pdf`.

Expected: 403 Forbidden.

Если в DEV нет резидента — пропустить (integration-тесты уже это покрывают).

---

## Task 11: Закрытие Day 17 — обновление PLAN.md и memory

**Files:**
- Modify: `docs/PLAN.md` — отметить чекбоксы 17D
- (Memory — обновляется через инструмент, не через файл)

- [ ] **Step 1: Поставить чекбоксы в PLAN.md**

В `docs/PLAN.md`, секция «День 17» (строки 443–446), заменить:

```markdown
- [ ] **PDF «Сводный отчёт за месяц»** (один реальный из 4 в мокапе):
  - [ ] Бэкенд: `GET /analytics/export/monthly-report.pdf` — генерация через OpenPDF (`com.github.librepdf:openpdf:1.3.30`, Apache 2.0). Шаблон: KPI + графики (можно как картинки из data-URL) + топ районов + SLA-таблица.
  - [ ] Фронт: 1-я карточка в Settings → Export — кликабельная, скачивает PDF.
  - [ ] Остальные 3 карточки (Реестр / SLA / Голосование) — рендерятся с состоянием `disabled` + tooltip «Скоро» (см. SPEC § 12).
```

на:

```markdown
- [x] **PDF «Сводный отчёт за месяц»** (один реальный из 4 в мокапе):
  - [x] Бэкенд: `GET /analytics/export/monthly-report.pdf` — генерация через OpenPDF (`com.github.librepdf:openpdf:1.3.30`, Apache 2.0). Шаблон: KPI + топ районов + SLA-таблица. **Без графиков** (решение при brainstorming).
  - [x] Фронт: 1-я карточка в Settings → Export — кликабельная, скачивает PDF.
  - [x] Остальные 3 карточки (Реестр / SLA / Голосование) — рендерятся с состоянием `disabled` + tooltip «Скоро» (см. SPEC § 12).
```

И в комментарий-блок сверху Day 17 (строки 426–430) добавить запись о закрытии 17D, по аналогии с предыдущими — либо новой строкой, либо переписать первую блок-цитату:

```markdown
> **17A (Дашборд) закрыт.** OverviewPage + AnalyticsPage реализованы.
> **17B (Объявления) закрыт.** AnnouncementsPage реализован.
> **17C (Настройки) закрыт 2026-05-28.** TeamSection + Audit + следствия.
> **17D (PDF-отчёт) закрыт 2026-05-28.** GET /analytics/export/monthly-report.pdf + ExportSection.
> Дизайн/планы: docs/superpowers/specs/2026-05-28-day17d-monthly-report-pdf-design.md,
> docs/superpowers/plans/2026-05-28-day17d-monthly-report-pdf.md.
> День 17 закрыт полностью.
```

(Точный текст блок-цитаты подогнать под существующий стиль того файла — если 17B-формулировка отличается, оставить как есть.)

- [ ] **Step 2: Закоммитить обновление плана**

```bash
git add docs/PLAN.md
git commit -m "docs(day17d): закрыть чекбоксы 17D в PLAN.md"
```

- [ ] **Step 3: Сохранить memory**

Через memory-инструмент создать `project_cleancity_day17d_done.md` со ссылкой в `MEMORY.md`. Содержимое:

```markdown
---
name: project-cleancity-day17d-done
description: CleanCity Day 17D закрыт 2026-05-28 — PDF «Сводный отчёт за месяц» работает e2e; День 17 закрыт полностью
metadata:
  type: project
---

Day 17D закрыт 2026-05-28. PDF «Сводный отчёт за месяц» работает end-to-end:
- бэкенд: GET /analytics/export/monthly-report.pdf, OpenPDF 1.3.30, DejaVuSans для кириллицы, ADMIN+OPERATOR;
- фронт: ExportSection в SettingsPage — 4 карточки (1 активная скачивает PDF, 3 disabled «Скоро»);
- содержимое PDF: KPI + 4 района + SLA-таблица. Без графиков, без логотипа.

День 17 целиком (17A+17B+17C+17D) закрыт. Следующий этап — Day 18 (деплой на Yandex Cloud).

Дизайн/план: [[project-cleancity-day17c-done]] → продолжение
- docs/superpowers/specs/2026-05-28-day17d-monthly-report-pdf-design.md
- docs/superpowers/plans/2026-05-28-day17d-monthly-report-pdf.md
```

В `MEMORY.md` добавить строку:

```
- [CleanCity — Day 17D закрыт 2026-05-28](project_cleancity_day17d_done.md) — PDF «Сводный отчёт за месяц» работает; День 17 закрыт полностью
```

- [ ] **Step 4: Финальный коммит**

(если что-то ещё неподтверждено)

```bash
git status
```

Expected: working tree clean.

---

## Self-Review Notes

После имплементации проверить:
- Все 11 задач закрыты, у каждого таска все steps отмечены.
- Все 3 backend-тестовых файла (`AnalyticsServiceRangeTest`, `MonthlyReportLayoutTest`, `MonthlyReportPdfServiceTest`) + расширенный `AnalyticsRoutesTest` зелёные.
- `ExportSection.test.tsx` + `contentDisposition.test.ts` зелёные.
- Полный фронтовый `vitest run` зелёный.
- Полный backend `gradlew :backend:test` зелёный.
- Ручная проверка из Task 10 выполнена.
- PLAN.md обновлён, memory обновлено.
