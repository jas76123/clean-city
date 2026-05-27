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
        seedComplaint(a.userId, ComplaintStatus.NEW, ProblemCategory.ROADS, ageHours = 150)
        val resp = service.list(a, PublicListFilter())
        val item = resp.items.single()
        assertTrue(item.slaBreached, "активная ROADS возрастом 150ч (норматив 120ч) — просрочена")
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
        seedComplaint(a.userId, ComplaintStatus.NEW, ProblemCategory.ROADS, ageHours = 150)
        seedComplaint(a.userId, ComplaintStatus.IN_PROGRESS, ProblemCategory.ROADS, ageHours = 150)
        seedComplaint(a.userId, ComplaintStatus.NEW, ProblemCategory.ROADS, ageHours = 1)
        seedComplaint(a.userId, ComplaintStatus.RESOLVED, ProblemCategory.ROADS, ageHours = 500)
        val resp = service.list(a, PublicListFilter(slaBreached = true))
        assertEquals(2, resp.items.size, "просроченные активные жалобы (NEW и IN_PROGRESS)")
        assertTrue(resp.items.all { it.slaBreached })
        assertEquals(
            setOf(ComplaintStatus.NEW, ComplaintStatus.IN_PROGRESS),
            resp.items.map { it.status }.toSet()
        )
    }

    @Test
    fun `slaBreached учитывает норматив категории`() {
        val a = admin()
        seedComplaint(a.userId, ComplaintStatus.NEW, ProblemCategory.GARBAGE, ageHours = 30)
        seedComplaint(a.userId, ComplaintStatus.NEW, ProblemCategory.OTHER, ageHours = 30)
        val resp = service.list(a, PublicListFilter(slaBreached = true))
        assertEquals(1, resp.items.size)
        assertEquals(ProblemCategory.GARBAGE, resp.items.single().category)
    }

    @Test
    fun `фильтр по району возвращает только совпадающий район`() {
        val a = admin()
        seedComplaint(a.userId, ComplaintStatus.NEW, district = "Центральный")
        seedComplaint(a.userId, ComplaintStatus.NEW, district = "Адлерский")
        val resp = service.list(a, PublicListFilter(district = "Центральный"))
        assertEquals(1, resp.items.size)
    }
}
