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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SPEC § 4.3: жителям и гостям не показывать REJECTED/DUPLICATE на карте и в ленте.
 * Админам — всё. Автор и проголосовавший видят свою/поддержанную REJECTED/DUPLICATE
 * в детальном просмотре, чтобы прочесть комментарий админа.
 */
class ComplaintVisibilityTest {

    private lateinit var complaintRepo: ComplaintRepository
    private lateinit var voteRepo: VoteRepository
    private lateinit var service: ComplaintService

    @BeforeTest
    fun setup() {
        Database.connect(
            "jdbc:h2:mem:vis-${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.drop(Notifications, Votes, StatusChanges, ComplaintPhotos, Complaints, AuditLog, Users)
            SchemaUtils.create(Users, Complaints, ComplaintPhotos, StatusChanges, Votes, AuditLog, Notifications)
        }
        complaintRepo = ComplaintRepository()
        voteRepo = VoteRepository()
        service = ComplaintService(
            repo = complaintRepo,
            storage = LocalStorageService("./uploads", "http://test"),
            voteRepo = voteRepo,
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

    private fun seedComplaint(authorId: Long, status: ComplaintStatus, address: String): Long = transaction {
        val cid = Complaints.insert {
            it[Complaints.authorId] = authorId
            it[Complaints.category] = ProblemCategory.GARBAGE.name
            it[Complaints.title] = "Мусор · $address"
            it[Complaints.description] = "д"
            it[Complaints.latitude] = 43.6
            it[Complaints.longitude] = 39.7
            it[Complaints.address] = address
            it[Complaints.status] = status.name
            it[Complaints.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
            it[Complaints.updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }[Complaints.id]
        Votes.insert {
            it[Votes.complaintId] = cid
            it[Votes.userId] = authorId
            it[Votes.value] = 1
            it[Votes.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }
        cid
    }

    private fun seedSupporter(complaintId: Long, userId: Long) = transaction {
        Votes.insert {
            it[Votes.complaintId] = complaintId
            it[Votes.userId] = userId
            it[Votes.value] = 1
            it[Votes.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }
    }

    private fun seedAllStatuses(authorId: Long): Map<ComplaintStatus, Long> = mapOf(
        ComplaintStatus.NEW to seedComplaint(authorId, ComplaintStatus.NEW, "ул. Новая"),
        ComplaintStatus.IN_PROGRESS to seedComplaint(authorId, ComplaintStatus.IN_PROGRESS, "ул. Рабочая"),
        ComplaintStatus.RESOLVED to seedComplaint(authorId, ComplaintStatus.RESOLVED, "ул. Готовая"),
        ComplaintStatus.REJECTED to seedComplaint(authorId, ComplaintStatus.REJECTED, "ул. Отклонённая"),
        ComplaintStatus.DUPLICATE to seedComplaint(authorId, ComplaintStatus.DUPLICATE, "ул. Дубль")
    )

    @Test
    fun `guest list returns only NEW IN_PROGRESS RESOLVED`() {
        val author = seedUser("a@x.ru")
        seedAllStatuses(author)

        val resp = service.list(Viewer.Guest, PublicListFilter())
        val statuses = resp.items.map { it.status }.toSet()
        assertEquals(
            setOf(ComplaintStatus.NEW, ComplaintStatus.IN_PROGRESS, ComplaintStatus.RESOLVED),
            statuses
        )
        assertEquals(3L, resp.total, "REJECTED/DUPLICATE не учитываются и в total")
    }

    @Test
    fun `resident list does NOT show REJECTED nor DUPLICATE`() {
        val author = seedUser("a@x.ru")
        val randomResident = seedUser("r@x.ru")
        seedAllStatuses(author)

        val viewer = Viewer.Authenticated(randomResident, UserRole.RESIDENT)
        val resp = service.list(viewer, PublicListFilter())
        val statuses = resp.items.map { it.status }.toSet()
        assertFalse(ComplaintStatus.REJECTED in statuses, "REJECTED не должна быть в ленте резидента")
        assertFalse(ComplaintStatus.DUPLICATE in statuses, "DUPLICATE не должна быть в ленте резидента")
        assertEquals(3, resp.items.size)
    }

    @Test
    fun `admin list returns all 5 statuses`() {
        val author = seedUser("a@x.ru")
        val admin = seedUser("adm@x.ru", UserRole.ADMIN)
        seedAllStatuses(author)

        val viewer = Viewer.Authenticated(admin, UserRole.ADMIN)
        val resp = service.list(viewer, PublicListFilter())
        val statuses = resp.items.map { it.status }.toSet()
        assertEquals(5, statuses.size)
        assertTrue(ComplaintStatus.REJECTED in statuses)
        assertTrue(ComplaintStatus.DUPLICATE in statuses)
    }

    @Test
    fun `operator and inspector also see all statuses`() {
        val author = seedUser("a@x.ru")
        val op = seedUser("op@x.ru", UserRole.OPERATOR)
        val ins = seedUser("ins@x.ru", UserRole.INSPECTOR)
        seedAllStatuses(author)

        val opResp = service.list(Viewer.Authenticated(op, UserRole.OPERATOR), PublicListFilter())
        val insResp = service.list(Viewer.Authenticated(ins, UserRole.INSPECTOR), PublicListFilter())
        assertEquals(5, opResp.items.size)
        assertEquals(5, insResp.items.size)
    }

    @Test
    fun `guest map markers do NOT include REJECTED nor DUPLICATE`() {
        val author = seedUser("a@x.ru")
        seedAllStatuses(author)

        val markers = service.listMarkers(
            Viewer.Guest, swLat = 43.0, swLon = 39.0, neLat = 44.0, neLon = 40.0,
            category = null
        )
        val statuses = markers.markers.map { it.status }.toSet()
        assertEquals(
            setOf(ComplaintStatus.NEW, ComplaintStatus.IN_PROGRESS, ComplaintStatus.RESOLVED),
            statuses
        )
    }

    @Test
    fun `resident map markers do NOT include REJECTED nor DUPLICATE`() {
        val author = seedUser("a@x.ru")
        val resident = seedUser("r@x.ru")
        seedAllStatuses(author)

        val markers = service.listMarkers(
            Viewer.Authenticated(resident, UserRole.RESIDENT),
            swLat = 43.0, swLon = 39.0, neLat = 44.0, neLon = 40.0, category = null
        )
        val statuses = markers.markers.map { it.status }.toSet()
        assertFalse(ComplaintStatus.REJECTED in statuses)
        assertFalse(ComplaintStatus.DUPLICATE in statuses)
    }

    @Test
    fun `admin map markers include all statuses`() {
        val author = seedUser("a@x.ru")
        val admin = seedUser("adm@x.ru", UserRole.ADMIN)
        seedAllStatuses(author)

        val markers = service.listMarkers(
            Viewer.Authenticated(admin, UserRole.ADMIN),
            swLat = 43.0, swLon = 39.0, neLat = 44.0, neLon = 40.0, category = null
        )
        assertEquals(5, markers.markers.size)
    }

    @Test
    fun `author can see own REJECTED complaint via getById`() {
        val author = seedUser("a@x.ru")
        val rejectedId = seedComplaint(author, ComplaintStatus.REJECTED, "ул. Отклонённая")

        val viewer = Viewer.Authenticated(author, UserRole.RESIDENT)
        assertNotNull(service.getById(rejectedId, viewer), "автор должен видеть свою REJECTED")
    }

    @Test
    fun `supporter can see REJECTED complaint they voted for`() {
        val author = seedUser("a@x.ru")
        val supporter = seedUser("s@x.ru")
        val rejectedId = seedComplaint(author, ComplaintStatus.REJECTED, "ул. Поддержанная")
        seedSupporter(rejectedId, supporter)

        val viewer = Viewer.Authenticated(supporter, UserRole.RESIDENT)
        assertNotNull(service.getById(rejectedId, viewer), "проголосовавший должен видеть REJECTED")
    }

    @Test
    fun `random resident cannot see REJECTED complaint via getById`() {
        val author = seedUser("a@x.ru")
        val stranger = seedUser("x@x.ru")
        val rejectedId = seedComplaint(author, ComplaintStatus.REJECTED, "ул. Чужая")

        val viewer = Viewer.Authenticated(stranger, UserRole.RESIDENT)
        assertNull(service.getById(rejectedId, viewer), "посторонний резидент не видит REJECTED")
    }

    @Test
    fun `guest cannot see REJECTED complaint via getById`() {
        val author = seedUser("a@x.ru")
        val rejectedId = seedComplaint(author, ComplaintStatus.REJECTED, "ул. Гостевая")
        assertNull(service.getById(rejectedId, Viewer.Guest))
    }
}
