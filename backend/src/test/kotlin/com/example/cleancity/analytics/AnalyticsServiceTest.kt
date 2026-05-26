package com.example.cleancity.analytics

import com.example.cleancity.database.tables.Complaints
import com.example.cleancity.database.tables.StatusChanges
import com.example.cleancity.database.tables.Users
import com.example.cleancity.database.tables.Votes
import com.example.cleancity.shared.models.AnalyticsPeriod
import com.example.cleancity.shared.models.ComplaintStatus
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnalyticsServiceTest {
    private lateinit var service: AnalyticsService
    private val now: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)

    @BeforeTest
    fun setup() {
        Database.connect(
            "jdbc:h2:mem:analytics-${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.drop(StatusChanges, Votes, Complaints, Users)
            SchemaUtils.create(Users, Complaints, Votes, StatusChanges)
        }
        service = AnalyticsService(AnalyticsRepository())
    }

    private fun seedUser(email: String = "a@x.ru"): Long = transaction {
        Users.insert {
            it[Users.email] = email
            it[Users.passwordHash] = "x"
            it[Users.role] = UserRole.RESIDENT.name
            it[Users.emailVerified] = true
            it[Users.isActive] = true
            it[Users.createdAt] = now
            it[Users.passwordChangedAt] = now
        }[Users.id]
    }

    /**
     * Создаёт жалобу с заданным createdAt/resolvedAt. status — строкой
     * (для удобства задавать историю).
     */
    private fun seedComplaint(
        authorId: Long,
        category: ProblemCategory,
        status: ComplaintStatus,
        createdAt: OffsetDateTime,
        resolvedAt: OffsetDateTime? = null,
        district: String? = null,
        title: String = "t",
        latitude: Double = 43.6,
        longitude: Double = 39.7,
    ): Long = transaction {
        Complaints.insert {
            it[Complaints.authorId] = authorId
            it[Complaints.category] = category.name
            it[Complaints.title] = title
            it[Complaints.description] = "d"
            it[Complaints.latitude] = latitude
            it[Complaints.longitude] = longitude
            it[Complaints.address] = "addr"
            it[Complaints.district] = district
            it[Complaints.status] = status.name
            it[Complaints.createdAt] = createdAt
            it[Complaints.updatedAt] = createdAt
            it[Complaints.resolvedAt] = resolvedAt
        }[Complaints.id]
    }

    private fun seedVote(complaintId: Long, userId: Long) = transaction {
        Votes.insert {
            it[Votes.complaintId] = complaintId
            it[Votes.userId] = userId
            it[Votes.value] = 1
            it[Votes.createdAt] = now
        }
    }

    private fun seedStatusChange(
        complaintId: Long,
        fromStatus: String?,
        toStatus: String,
        changedById: Long,
        createdAt: OffsetDateTime,
    ): Long = transaction {
        StatusChanges.insert {
            it[StatusChanges.complaintId] = complaintId
            it[StatusChanges.fromStatus] = fromStatus
            it[StatusChanges.toStatus] = toStatus
            it[StatusChanges.comment] = "test"
            it[StatusChanges.changedById] = changedById
            it[StatusChanges.createdAt] = createdAt
        }[StatusChanges.id]
    }

    @Test
    fun `overview counts by status, today, week, and active SLA breach`() {
        val author = seedUser()
        // 3 NEW, одна старая (нарушила SLA для GARBAGE = 24ч)
        seedComplaint(author, ProblemCategory.GARBAGE, ComplaintStatus.NEW, now.minusHours(48))
        seedComplaint(author, ProblemCategory.GARBAGE, ComplaintStatus.NEW, now.minusHours(2))
        seedComplaint(author, ProblemCategory.ROADS, ComplaintStatus.NEW, now.minusHours(1))
        // 1 IN_PROGRESS, не нарушает (ROADS = 72ч)
        seedComplaint(author, ProblemCategory.ROADS, ComplaintStatus.IN_PROGRESS, now.minusHours(10))
        // 2 RESOLVED — не учитываются в active breach
        seedComplaint(author, ProblemCategory.GARBAGE, ComplaintStatus.RESOLVED, now.minusDays(2), now.minusDays(1))
        seedComplaint(author, ProblemCategory.ROADS, ComplaintStatus.RESOLVED, now.minusDays(10), now.minusDays(5))
        // 1 REJECTED, 1 DUPLICATE
        seedComplaint(author, ProblemCategory.OTHER, ComplaintStatus.REJECTED, now.minusDays(8))
        seedComplaint(author, ProblemCategory.OTHER, ComplaintStatus.DUPLICATE, now.minusDays(6))

        val o = service.overview(now)
        assertEquals(8, o.total)
        assertEquals(3, o.new)
        assertEquals(1, o.inProgress)
        assertEquals(2, o.resolved)
        assertEquals(1, o.rejected)
        assertEquals(1, o.duplicate)
        assertEquals(1, o.slaBreachCount, "только активная GARBAGE-жалоба старше 24ч в breach")
        assertTrue(o.week >= 4)
    }

    @Test
    fun `byCategory returns counts and average resolution hours`() {
        val author = seedUser()
        seedComplaint(author, ProblemCategory.GARBAGE, ComplaintStatus.RESOLVED, now.minusDays(2), now.minusDays(2).plusHours(10))
        seedComplaint(author, ProblemCategory.GARBAGE, ComplaintStatus.RESOLVED, now.minusDays(3), now.minusDays(3).plusHours(20))
        seedComplaint(author, ProblemCategory.ROADS, ComplaintStatus.NEW, now)

        val stats = service.byCategory(AnalyticsPeriod.ALL)
        val garbage = stats.first { it.category == ProblemCategory.GARBAGE }
        assertEquals(2, garbage.count)
        assertNotNull(garbage.avgResolutionHours)
        assertEquals(15.0, garbage.avgResolutionHours!!, 0.5)

        val roads = stats.first { it.category == ProblemCategory.ROADS }
        assertEquals(1, roads.count)
        assertNull(roads.avgResolutionHours, "ROADS без resolved — null")
    }

    @Test
    fun `byCategory period=week filters out older`() {
        val author = seedUser()
        seedComplaint(author, ProblemCategory.GARBAGE, ComplaintStatus.NEW, now.minusDays(30))
        seedComplaint(author, ProblemCategory.GARBAGE, ComplaintStatus.NEW, now.minusHours(2))

        val weekStats = service.byCategory(AnalyticsPeriod.WEEK)
        assertEquals(1, weekStats.single { it.category == ProblemCategory.GARBAGE }.count)

        val allStats = service.byCategory(AnalyticsPeriod.ALL)
        assertEquals(2, allStats.single { it.category == ProblemCategory.GARBAGE }.count)
    }

    @Test
    fun `byDistrict aggregates by localized label`() {
        val author = seedUser()
        seedComplaint(author, ProblemCategory.GARBAGE, ComplaintStatus.NEW, now, district = "Центральный")
        seedComplaint(author, ProblemCategory.GARBAGE, ComplaintStatus.RESOLVED, now, now, district = "Центральный")
        seedComplaint(author, ProblemCategory.ROADS, ComplaintStatus.NEW, now, district = "Адлерский")
        seedComplaint(author, ProblemCategory.ROADS, ComplaintStatus.NEW, now, district = null)

        val stats = service.byDistrict(AnalyticsPeriod.ALL)
        val central = stats.single { it.label == "Центральный" }
        assertEquals(2, central.count)
        assertEquals(1, central.newCount)
        assertEquals(1, central.resolvedCount)

        val adler = stats.single { it.label == "Адлерский" }
        assertEquals(1, adler.count)
    }

    @Test
    fun `sla calculates breach percentage for resolved`() {
        val author = seedUser()
        // GARBAGE SLA = 24ч: 1 в срок (10ч), 1 с нарушением (30ч), 1 NEW (не считается)
        seedComplaint(author, ProblemCategory.GARBAGE, ComplaintStatus.RESOLVED, now.minusDays(2), now.minusDays(2).plusHours(10))
        seedComplaint(author, ProblemCategory.GARBAGE, ComplaintStatus.RESOLVED, now.minusDays(3), now.minusDays(3).plusHours(30))
        seedComplaint(author, ProblemCategory.GARBAGE, ComplaintStatus.NEW, now)

        val stats = service.sla(AnalyticsPeriod.ALL)
        val garbage = stats.single { it.category == ProblemCategory.GARBAGE }
        assertEquals(24, garbage.slaHours)
        assertEquals(2, garbage.resolvedCount)
        assertEquals(50.0, garbage.breachPct, 0.1)
    }

    @Test
    fun `votesImpact buckets are stable order`() {
        val author = seedUser()
        val voter1 = seedUser("v1@x.ru")
        val voter2 = seedUser("v2@x.ru")

        // RESOLVED жалобы с 0 / 2 / 12 голосами +1
        val c0 = seedComplaint(author, ProblemCategory.GARBAGE, ComplaintStatus.RESOLVED, now.minusDays(2), now.minusDays(2).plusHours(5))
        val c1 = seedComplaint(author, ProblemCategory.GARBAGE, ComplaintStatus.RESOLVED, now.minusDays(2), now.minusDays(2).plusHours(10))
        val c2 = seedComplaint(author, ProblemCategory.GARBAGE, ComplaintStatus.RESOLVED, now.minusDays(2), now.minusDays(2).plusHours(20))

        // c1: 2 голоса
        seedVote(c1, voter1)
        seedVote(c1, voter2)
        // c2: 12 голосов (берём отдельных юзеров)
        repeat(12) { i -> seedVote(c2, seedUser("vc2-$i@x.ru")) }
        // NEW не учитывается
        val notResolved = seedComplaint(author, ProblemCategory.ROADS, ComplaintStatus.NEW, now)
        seedVote(notResolved, voter1)

        val buckets = service.votesImpact(AnalyticsPeriod.ALL)
        assertEquals(listOf("0", "1-9", "10-49", "50+"), buckets.map { it.bucket })
        assertEquals(1, buckets.single { it.bucket == "0" }.count)
        assertEquals(1, buckets.single { it.bucket == "1-9" }.count)
        assertEquals(1, buckets.single { it.bucket == "10-49" }.count)
        assertEquals(0, buckets.single { it.bucket == "50+" }.count)
        assertEquals(0, c0.compareTo(c0))   // suppress unused warning
    }

    @Test
    fun `votesImpact returns empty buckets when no resolved`() {
        val author = seedUser()
        seedComplaint(author, ProblemCategory.GARBAGE, ComplaintStatus.NEW, now)

        val buckets = service.votesImpact(AnalyticsPeriod.ALL)
        assertEquals(4, buckets.size)
        assertTrue(buckets.all { it.count == 0 })
    }

    @Test
    fun `overview monthlyKpis compares current 30d window with previous`() {
        val author = seedUser()
        // Текущее окно (0-30 дней назад): 2 создано, 1 решена за 5 дней (в пределах 7д)
        seedComplaint(author, ProblemCategory.GARBAGE, ComplaintStatus.NEW, now.minusDays(3))
        seedComplaint(
            author, ProblemCategory.ROADS, ComplaintStatus.RESOLVED,
            now.minusDays(10), resolvedAt = now.minusDays(5),
        )
        // Предыдущее окно (30-60 дней назад): 1 создано, 1 решена за 20 дней (вне 7д)
        seedComplaint(
            author, ProblemCategory.GARBAGE, ComplaintStatus.RESOLVED,
            now.minusDays(50), resolvedAt = now.minusDays(40),
        )

        val k = service.overview(now).monthlyKpis
        assertEquals(2, k.total, "в текущем окне создано 2")
        assertEquals(1, k.prevTotal, "в предыдущем окне создано 1")
        assertEquals(100.0, k.resolvedWithin7dPct, "1 из 1 решена в пределах 7д")
        assertEquals(0.0, k.prevResolvedWithin7dPct, "решена за 20д — вне 7д")
        assertNotNull(k.avgResolutionHours)
        assertEquals(120.0, k.avgResolutionHours!!, 1.0, "5 суток = 120ч")
        // Фикстура текущего окна: 1 NEW + 1 RESOLVED = 2
        assertEquals(1, k.newCount)
        assertEquals(0, k.inProgressCount)
        assertEquals(1, k.resolvedCount)
        assertEquals(0, k.rejectedCount)
        assertEquals(0, k.duplicateCount)
    }

    @Test
    fun `overview monthlyKpis on empty dataset returns zeros and nulls`() {
        val k = service.overview(now).monthlyKpis
        assertEquals(0, k.total)
        assertEquals(0, k.prevTotal)
        assertNull(k.avgResolutionHours)
        assertNull(k.prevAvgResolutionHours)
        assertNull(k.resolvedWithin7dPct, "пустой период — нет базы для расчёта %")
        assertNull(k.prevResolvedWithin7dPct, "пустой предыдущий период — нет базы для расчёта %")
        assertEquals(0, k.newCount)
        assertEquals(0, k.inProgressCount)
        assertEquals(0, k.resolvedCount)
        assertEquals(0, k.rejectedCount)
        assertEquals(0, k.duplicateCount)
    }

    @Test
    fun `overview monthlyKpis includes status breakdown for current 30d window`() {
        val author = seedUser()
        // В текущем окне: 2 NEW, 1 IN_PROGRESS, 1 RESOLVED, 1 REJECTED, 1 DUPLICATE = 6
        seedComplaint(author, ProblemCategory.GARBAGE, ComplaintStatus.NEW, now.minusDays(3))
        seedComplaint(author, ProblemCategory.GARBAGE, ComplaintStatus.NEW, now.minusDays(5))
        seedComplaint(author, ProblemCategory.ROADS, ComplaintStatus.IN_PROGRESS, now.minusDays(7))
        seedComplaint(
            author, ProblemCategory.ROADS, ComplaintStatus.RESOLVED,
            now.minusDays(10), resolvedAt = now.minusDays(8),
        )
        seedComplaint(author, ProblemCategory.LIGHTING, ComplaintStatus.REJECTED, now.minusDays(15))
        seedComplaint(author, ProblemCategory.SAFETY, ComplaintStatus.DUPLICATE, now.minusDays(20))
        // Вне окна (>30д): не должна учитываться
        seedComplaint(author, ProblemCategory.OTHER, ComplaintStatus.NEW, now.minusDays(45))

        val k = service.overview(now).monthlyKpis
        assertEquals(6, k.total, "в окне создано 6")
        assertEquals(2, k.newCount)
        assertEquals(1, k.inProgressCount)
        assertEquals(1, k.resolvedCount)
        assertEquals(1, k.rejectedCount)
        assertEquals(1, k.duplicateCount)
        assertEquals(
            k.total,
            k.newCount + k.inProgressCount + k.resolvedCount + k.rejectedCount + k.duplicateCount,
            "сумма пятёрки == total",
        )
    }

    @Test
    fun `trends returns 30 daily points with created and resolved counts`() {
        val author = seedUser()
        // 2 жалобы созданы сегодня, 1 решена сегодня
        seedComplaint(author, ProblemCategory.GARBAGE, ComplaintStatus.NEW, now)
        seedComplaint(
            author, ProblemCategory.ROADS, ComplaintStatus.RESOLVED,
            now, resolvedAt = now,
        )
        // 1 жалоба создана 100 дней назад — вне окна 30 дней
        seedComplaint(author, ProblemCategory.OTHER, ComplaintStatus.NEW, now.minusDays(100))

        val t = service.trends(now)
        assertEquals(30, t.days.size, "ряд за 30 дней")
        val today = t.days.last()
        assertEquals(now.toLocalDate().toString(), today.date)
        assertEquals(2, today.created, "2 жалобы созданы сегодня")
        assertEquals(1, today.resolved, "1 жалоба решена сегодня")
        assertEquals(
            t.days.map { it.date }.sorted(), t.days.map { it.date },
            "дни идут в хронологическом порядке",
        )
        assertTrue(
            t.days.dropLast(1).all { it.created == 0 },
            "жалобы вне 30-дневного окна не должны попасть в ряд",
        )
    }

    @Test
    fun `trends on empty dataset returns 30 zero points`() {
        val t = service.trends(now)
        assertEquals(30, t.days.size)
        assertTrue(t.days.all { it.created == 0 && it.resolved == 0 })
    }

    @Test
    fun `operational snapshot returns backlog overdue and dta`() {
        val author = seedUser()
        // backlog = 2 (1 NEW + 1 IN_PROGRESS); RESOLVED не в backlog
        val c1 = seedComplaint(author, ProblemCategory.GARBAGE, ComplaintStatus.NEW, now.minusHours(2))
        val c2 = seedComplaint(author, ProblemCategory.GARBAGE, ComplaintStatus.IN_PROGRESS, now.minusHours(48))
        val c3 = seedComplaint(
            author, ProblemCategory.GARBAGE, ComplaintStatus.RESOLVED,
            now.minusHours(72), resolvedAt = now.minusHours(24)
        )
        // status change для c2: первый IN_PROGRESS 36ч назад → ВНЕ 24ч окна DTA → avgDtaHours24h = null
        seedStatusChange(c2, fromStatus = "NEW", toStatus = "IN_PROGRESS", changedById = author, createdAt = now.minusHours(36))

        val snapshot = AnalyticsRepository().operationalSnapshot(now)

        assertEquals(2, snapshot.backlog)
        assertEquals(1, snapshot.overdueNow, "только c2 (48ч >= 24ч SLA для GARBAGE) — overdue")
        assertEquals(mapOf("NEW" to 1, "IN_PROGRESS" to 1, "RESOLVED" to 1), snapshot.statusBreakdown)
        assertNull(snapshot.avgDtaHours24h, "first IN_PROGRESS 36ч назад — вне 24ч окна")
        // c3 не имеет «c3» использования — подавить warning
        assertTrue(c3 > 0)
    }

    @Test
    fun `operational snapshot DTA averages first IN_PROGRESS events in last 24h`() {
        val author = seedUser()
        val c1 = seedComplaint(author, ProblemCategory.GARBAGE, ComplaintStatus.IN_PROGRESS, now.minusHours(20))
        val c2 = seedComplaint(author, ProblemCategory.GARBAGE, ComplaintStatus.IN_PROGRESS, now.minusHours(15))
        // c1: первый IN_PROGRESS 10ч назад (10ч от создания → 10ч DTA), В окне 24ч
        seedStatusChange(c1, "NEW", "IN_PROGRESS", author, now.minusHours(10))
        // c2: первый IN_PROGRESS 5ч назад (10ч от создания → 10ч DTA), В окне 24ч
        seedStatusChange(c2, "NEW", "IN_PROGRESS", author, now.minusHours(5))

        val snapshot = AnalyticsRepository().operationalSnapshot(now)

        assertNotNull(snapshot.avgDtaHours24h)
        assertEquals(10.0, snapshot.avgDtaHours24h!!, 0.5)
    }

    @Test
    fun `operational snapshot DTA ignores complaints whose first IN_PROGRESS event is outside 24h window`() {
        val author = seedUser()
        // Жалоба: создана 50ч назад, IN_PROGRESS поставлена 40ч назад → первое событие вне 24ч окна
        val c1 = seedComplaint(author, ProblemCategory.GARBAGE, ComplaintStatus.IN_PROGRESS, now.minusHours(50))
        seedStatusChange(c1, "NEW", "IN_PROGRESS", author, now.minusHours(40))
        // Та же жалоба позже снова поставлена в IN_PROGRESS (например, после возврата из RESOLVED) — внутри окна
        seedStatusChange(c1, "RESOLVED", "IN_PROGRESS", author, now.minusHours(5))

        val snapshot = AnalyticsRepository().operationalSnapshot(now)
        assertNull(snapshot.avgDtaHours24h, "первый IN_PROGRESS был 40ч назад — жалоба не должна попадать в DTA-24h")
    }

    @Test
    fun `burning queue sorts by slaDueAt and excludes terminal statuses`() {
        val author = seedUser()
        // c1: NEW, SAFETY (SLA 24ч), создана 30ч назад → overdue, slaDueAt = -6ч
        val c1 = seedComplaint(author, ProblemCategory.SAFETY, ComplaintStatus.NEW, now.minusHours(30))
        // c2: IN_PROGRESS, GARBAGE (SLA 24ч), создана 20ч назад → ещё не overdue, slaDueAt = +4ч
        val c2 = seedComplaint(author, ProblemCategory.GARBAGE, ComplaintStatus.IN_PROGRESS, now.minusHours(20))
        // c3: RESOLVED — не должна попасть
        seedComplaint(author, ProblemCategory.GARBAGE, ComplaintStatus.RESOLVED,
                      now.minusHours(48), resolvedAt = now.minusHours(2))

        val items = AnalyticsRepository().burningQueue(now, limit = 10)

        assertEquals(2, items.size, "RESOLVED исключена")
        assertEquals(c1, items[0].id, "overdue c1 первая (более раннее slaDueAt)")
        assertEquals(c2, items[1].id)
        assertTrue(items[0].secondsToDeadline < 0, "c1 overdue")
        assertTrue(items[1].secondsToDeadline > 0, "c2 ещё в сроке")
    }

    @Test
    fun `burning queue respects limit`() {
        val author = seedUser()
        repeat(15) { i ->
            seedComplaint(author, ProblemCategory.GARBAGE, ComplaintStatus.NEW, now.minusHours((i + 1).toLong()))
        }
        val items = AnalyticsRepository().burningQueue(now, limit = 5)
        assertEquals(5, items.size)
    }

    @Test
    fun `burning queue returns title and districtCode and category name`() {
        val author = seedUser()
        seedComplaint(
            author, ProblemCategory.GARBAGE, ComplaintStatus.NEW, now.minusHours(2),
            district = "Центральный", title = "Сломанная урна"
        )
        val items = AnalyticsRepository().burningQueue(now, limit = 10)
        assertEquals(1, items.size)
        assertEquals("Сломанная урна", items[0].title)
        assertEquals("Центральный", items[0].districtCode)
        assertEquals("GARBAGE", items[0].category)
    }

    @Test
    fun `strategic kpis median p90 sla throughput`() {
        val author = seedUser()
        val periodStart = now.minusDays(30)
        val periodEnd = now.plusDays(1)
        // 4 RESOLVED GARBAGE (SLA 24ч): 12ч (within), 18ч (within), 24ч (within — boundary), 100ч (breach)
        seedComplaint(
            author, ProblemCategory.GARBAGE, ComplaintStatus.RESOLVED,
            now.minusDays(10), resolvedAt = now.minusDays(10).plusHours(12)
        )
        seedComplaint(
            author, ProblemCategory.GARBAGE, ComplaintStatus.RESOLVED,
            now.minusDays(9), resolvedAt = now.minusDays(9).plusHours(18)
        )
        seedComplaint(
            author, ProblemCategory.GARBAGE, ComplaintStatus.RESOLVED,
            now.minusDays(8), resolvedAt = now.minusDays(8).plusHours(24)
        )
        seedComplaint(
            author, ProblemCategory.GARBAGE, ComplaintStatus.RESOLVED,
            now.minusDays(7), resolvedAt = now.minusDays(7).plusHours(100)
        )

        val kpis = AnalyticsRepository().strategicKpis(periodStart, periodEnd)

        assertEquals(4, kpis.throughput)
        // SLA compliance: 3 of 4 within (≤24h) = 75%
        assertEquals(75.0, kpis.slaCompliancePct, absoluteTolerance = 0.5)
        // median of [12, 18, 24, 100]: avg of 18 and 24 = 21
        assertEquals(21.0, kpis.medianResolutionHours!!, absoluteTolerance = 0.5)
        // p90 = linear interp at idx 0.9*(4-1)=2.7: sorted[2] + 0.7*(sorted[3]-sorted[2]) = 24 + 0.7*(100-24) = 77.2
        assertTrue(kpis.p90ResolutionHours!! > 50.0, "p90 should not be the average; it should be near the upper tail")
        assertTrue(kpis.p90ResolutionHours!! < 100.0, "p90 should be < max")
    }

    @Test
    fun `strategic kpis empty period returns zeros`() {
        val periodStart = now.minusDays(7)
        val periodEnd = now
        val kpis = AnalyticsRepository().strategicKpis(periodStart, periodEnd)
        assertEquals(0, kpis.throughput)
        assertEquals(0.0, kpis.slaCompliancePct)
        assertNull(kpis.medianResolutionHours)
        assertNull(kpis.p90ResolutionHours)
    }

    @Test
    fun `strategic kpis excludes resolved outside window`() {
        val author = seedUser()
        // Внутри окна: 1 RESOLVED 10ч
        val periodStart = now.minusDays(7)
        val periodEnd = now.plusDays(1)
        seedComplaint(
            author, ProblemCategory.GARBAGE, ComplaintStatus.RESOLVED,
            now.minusDays(3), resolvedAt = now.minusDays(3).plusHours(10)
        )
        // Вне окна (resolvedAt раньше periodStart): не должна попасть
        seedComplaint(
            author, ProblemCategory.GARBAGE, ComplaintStatus.RESOLVED,
            now.minusDays(40), resolvedAt = now.minusDays(35)
        )
        // NEW — не RESOLVED, не должна попасть
        seedComplaint(author, ProblemCategory.GARBAGE, ComplaintStatus.NEW, now.minusDays(1))

        val kpis = AnalyticsRepository().strategicKpis(periodStart, periodEnd)
        assertEquals(1, kpis.throughput)
        assertEquals(100.0, kpis.slaCompliancePct, absoluteTolerance = 0.5)
    }

    @Test
    fun `reopen rate counts pairs within radius and window`() {
        val author = seedUser()
        val periodStart = now.minusDays(30)
        val periodEnd = now.plusDays(1)
        // Точка A: 43.5856, 39.7231 (центр Сочи)
        val latA = 43.5856; val lonA = 39.7231
        // Точка B: ~50м к северу от A (1 градус широты ≈ 111км → 50м ≈ 0.00045°)
        val latB = 43.586049; val lonB = 39.7231
        // Точка C: ~200м к северу от A (вне 50м радиуса)
        val latC = 43.587397; val lonC = 39.7231

        // R1: resolved A, GARBAGE — в начале окна
        seedComplaint(
            author, ProblemCategory.GARBAGE, ComplaintStatus.RESOLVED,
            now.minusDays(20), resolvedAt = now.minusDays(20).plusHours(2),
            latitude = latA, longitude = lonA,
        )
        // reopen для R1: B, GARBAGE, через 5 дней после resolvedAt → попадает
        seedComplaint(
            author, ProblemCategory.GARBAGE, ComplaintStatus.NEW,
            now.minusDays(15), latitude = latB, longitude = lonB,
        )
        // not-reopen: C (200м), GARBAGE — НЕ попадает по радиусу
        seedComplaint(
            author, ProblemCategory.GARBAGE, ComplaintStatus.NEW,
            now.minusDays(14), latitude = latC, longitude = lonC,
        )
        // not-reopen: A, LIGHTING — НЕ попадает по категории
        seedComplaint(
            author, ProblemCategory.LIGHTING, ComplaintStatus.NEW,
            now.minusDays(13), latitude = latA, longitude = lonA,
        )
        // R2: ещё одна resolved в окне без reopen-пары
        seedComplaint(
            author, ProblemCategory.GARBAGE, ComplaintStatus.RESOLVED,
            now.minusDays(5), resolvedAt = now.minusDays(5).plusHours(3),
            latitude = latA, longitude = lonA,
        )

        val stat = AnalyticsRepository().reopenStat(periodStart, periodEnd)

        assertEquals(2, stat.resolvedCount)
        assertEquals(1, stat.reopenCount)
        assertEquals(0.5, stat.reopenRate, absoluteTolerance = 0.01)
    }

    @Test
    fun `reopen rate ignores reopens outside 30 day window`() {
        val author = seedUser()
        val periodStart = now.minusDays(60)
        val periodEnd = now.plusDays(1)
        // R: resolved 50 дней назад
        seedComplaint(
            author, ProblemCategory.GARBAGE, ComplaintStatus.RESOLVED,
            now.minusDays(55), resolvedAt = now.minusDays(50),
            latitude = 43.5856, longitude = 39.7231,
        )
        // C: создана через 40 дней после resolvedAt (>30д окно) — НЕ reopen
        seedComplaint(
            author, ProblemCategory.GARBAGE, ComplaintStatus.NEW,
            now.minusDays(10), latitude = 43.5856, longitude = 39.7231,
        )

        val stat = AnalyticsRepository().reopenStat(periodStart, periodEnd)
        assertEquals(1, stat.resolvedCount)
        assertEquals(0, stat.reopenCount)
        assertEquals(0.0, stat.reopenRate)
    }

    @Test
    fun `reopen rate empty resolved returns zeros`() {
        val stat = AnalyticsRepository().reopenStat(now.minusDays(30), now)
        assertEquals(0, stat.resolvedCount)
        assertEquals(0, stat.reopenCount)
        assertEquals(0.0, stat.reopenRate)
    }

    @Test
    fun `operational snapshot counts createdToday and createdYesterday in Europe Moscow zone`() {
        val author = seedUser()
        val mskZone = java.time.ZoneId.of("Europe/Moscow")
        val todayMskStart = now.atZoneSameInstant(mskZone).toLocalDate().atStartOfDay(mskZone).toOffsetDateTime()
        val yesterdayMskStart = todayMskStart.minusDays(1)

        // Сегодня (в MSK) — 3 жалобы
        seedComplaint(author, ProblemCategory.GARBAGE, ComplaintStatus.NEW, todayMskStart.plusHours(1))
        seedComplaint(author, ProblemCategory.GARBAGE, ComplaintStatus.NEW, todayMskStart.plusHours(5))
        seedComplaint(author, ProblemCategory.GARBAGE, ComplaintStatus.NEW, todayMskStart.plusHours(20))
        // Вчера (в MSK) — 2 жалобы
        seedComplaint(author, ProblemCategory.GARBAGE, ComplaintStatus.NEW, yesterdayMskStart.plusHours(2))
        seedComplaint(author, ProblemCategory.GARBAGE, ComplaintStatus.NEW, yesterdayMskStart.plusHours(23))
        // Позавчера (за пределами today/yesterday) — 1 жалоба, не должна учитываться
        seedComplaint(author, ProblemCategory.GARBAGE, ComplaintStatus.NEW, yesterdayMskStart.minusHours(2))

        val snapshot = AnalyticsRepository().operationalSnapshot(now)
        assertEquals(3, snapshot.createdToday)
        assertEquals(2, snapshot.createdYesterday)
    }
}
