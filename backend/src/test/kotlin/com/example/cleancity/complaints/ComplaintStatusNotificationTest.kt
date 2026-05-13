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
import com.example.cleancity.notifications.NotificationService
import com.example.cleancity.shared.models.ComplaintStatus
import com.example.cleancity.shared.models.NotificationKind
import com.example.cleancity.shared.models.ProblemCategory
import com.example.cleancity.shared.models.UserRole
import com.example.cleancity.shared.requests.ChangeStatusRequest
import com.example.cleancity.storage.LocalStorageService
import com.example.cleancity.votes.VoteRepository
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
        assertEquals(NotificationKind.COMPLAINT_STATUS.name, r[Notifications.kind])
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
        val cid = seedComplaint(author)
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

    // Заметка: DUPLICATE-сценарий имеет идентичную recipients-ветку, что и REJECTED
    // (см. ComplaintService.kt: `REJECTED, DUPLICATE -> ...`). Отдельный тест на
    // DUPLICATE здесь не делаем, потому что mergeVotesInto использует SQL-конструкцию,
    // которая ломается на H2 (reserved keyword `value`). На реальной Postgres-БД в
    // интеграционных тестах День 19 это покрывается.

    @Test
    fun `rollback when notification insert fails — status not changed`() {
        val author = seedUser("a@x.ru")
        val admin = seedUser("adm@x.ru", UserRole.ADMIN)
        val cid = seedComplaint(author)

        val brokenService = ComplaintService(
            repo = complaintRepo,
            storage = LocalStorageService("./uploads", "http://test"),
            voteRepo = voteRepo,
            notifications = object : NotificationService {
                override fun notify(
                    recipientUserIds: List<Long>,
                    kind: NotificationKind,
                    title: String, body: String,
                    iconStyle: String?,
                    complaintId: Long?, announcementId: Long?
                ) {
                    throw IllegalStateException("simulated DB failure")
                }
            },
            audit = NoopAuditLogger
        )

        val ex = runCatching {
            brokenService.changeStatus(
                complaintId = cid,
                actor = Viewer.Authenticated(admin, UserRole.ADMIN),
                req = ChangeStatusRequest(toStatus = ComplaintStatus.IN_PROGRESS, comment = "test"),
                ip = "1.1.1.1", userAgent = "t"
            )
        }.exceptionOrNull()
        assertTrue(ex is IllegalStateException, "expected propagated exception, got $ex")

        val status = transaction { complaintRepo.findById(cid)!!.status }
        assertEquals(ComplaintStatus.NEW, status)

        val sc = transaction {
            StatusChanges.selectAll().where { StatusChanges.complaintId eq cid }.count()
        }
        assertEquals(0, sc)
    }
}
