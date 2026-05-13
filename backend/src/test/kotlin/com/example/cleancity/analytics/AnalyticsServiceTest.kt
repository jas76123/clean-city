package com.example.cleancity.analytics

import com.example.cleancity.database.tables.Complaints
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
            SchemaUtils.drop(Votes, Complaints, Users)
            SchemaUtils.create(Users, Complaints, Votes)
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
        district: String? = null
    ): Long = transaction {
        Complaints.insert {
            it[Complaints.authorId] = authorId
            it[Complaints.category] = category.name
            it[Complaints.title] = "t"
            it[Complaints.description] = "d"
            it[Complaints.latitude] = 43.6
            it[Complaints.longitude] = 39.7
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
}
