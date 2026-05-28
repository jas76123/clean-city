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
            // Жалоба ровно на границе from (входит)
            insertComplaint(authorId, april1, status = ComplaintStatus.RESOLVED, resolvedAt = april1.plusDays(1))
            // Жалоба в середине апреля (входит)
            insertComplaint(authorId, april1.plusDays(10), status = ComplaintStatus.NEW)
            // Жалоба ровно на границе to (НЕ входит)
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
            it[Complaints.latitude] = 43.6
            it[Complaints.longitude] = 39.7
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
        val central = result.first { it.district == District.CENTRAL }
        assertEquals(2, central.count, "Только две апрельские жалобы в Центральном")
    }

    @Test
    fun `slaRange counts only resolved within range`() {
        val result = service.slaRange(april1, may1)
        val garbage = result.first { it.category == ProblemCategory.GARBAGE }
        assertEquals(1, garbage.resolvedCount, "Одна resolved-жалоба в категории GARBAGE")
    }
}
