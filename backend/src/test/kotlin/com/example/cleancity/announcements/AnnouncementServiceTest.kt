package com.example.cleancity.announcements

import com.example.cleancity.ForbiddenException
import com.example.cleancity.NotFoundException
import com.example.cleancity.complaints.Viewer
import com.example.cleancity.database.tables.Announcements
import com.example.cleancity.database.tables.Notifications
import com.example.cleancity.database.tables.Users
import com.example.cleancity.notifications.DbNotificationService
import com.example.cleancity.notifications.NotificationRepository
import com.example.cleancity.notifications.NotificationService
import com.example.cleancity.shared.models.IconStyle
import com.example.cleancity.shared.models.NotificationKind
import com.example.cleancity.shared.models.UserRole
import com.example.cleancity.shared.requests.CreateAnnouncementRequest
import com.example.cleancity.shared.requests.UpdateAnnouncementRequest
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
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnnouncementServiceTest {
    private lateinit var repo: AnnouncementRepository
    private lateinit var notifRepo: NotificationRepository
    private lateinit var notifications: NotificationService
    private lateinit var service: AnnouncementService

    @BeforeTest
    fun setup() {
        Database.connect(
            "jdbc:h2:mem:announce-svc-${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.drop(Notifications, Announcements, Users)
            SchemaUtils.create(Users, Announcements, Notifications)
        }
        repo = AnnouncementRepository()
        notifRepo = NotificationRepository()
        notifications = DbNotificationService(notifRepo)
        service = AnnouncementService(repo, notifications)
    }

    private fun seedUser(email: String, district: String? = null, role: UserRole = UserRole.RESIDENT): Long = transaction {
        Users.insert {
            it[Users.email] = email
            it[Users.passwordHash] = "x"
            it[Users.role] = role.name
            it[Users.district] = district
            it[Users.emailVerified] = true
            it[Users.isActive] = true
            it[Users.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
            it[Users.passwordChangedAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }[Users.id]
    }

    @Test
    fun `non-admin cannot create`() {
        val r = seedUser("r@x.ru")
        assertFailsWith<ForbiddenException> {
            service.create(
                Viewer.Authenticated(r, UserRole.RESIDENT),
                CreateAnnouncementRequest(title = "t", body = "b")
            )
        }
    }

    @Test
    fun `create with ALL pushes to all active verified residents and stores notifications`() {
        val admin = seedUser("admin@x.ru", role = UserRole.ADMIN)
        val r1 = seedUser("r1@x.ru", district = "Центральный")
        val r2 = seedUser("r2@x.ru", district = "Адлерский")
        val r3 = seedUser("r3@x.ru", district = null)

        val resp = service.create(
            Viewer.Authenticated(admin, UserRole.ADMIN),
            CreateAnnouncementRequest(
                title = "Субботник",
                body = "Приходите 21 мая",
                iconStyle = IconStyle.SUCCESS,
                districts = emptyList()
            )
        )

        assertEquals(listOf("ALL"), resp.districts)
        assertEquals(IconStyle.SUCCESS, resp.iconStyle)

        val notifs = transaction {
            Notifications.selectAll().where { Notifications.announcementId eq resp.id }.toList()
        }
        assertEquals(3, notifs.size)
        assertEquals(setOf(r1, r2, r3), notifs.map { it[Notifications.userId] }.toSet())
        notifs.forEach {
            assertEquals(NotificationKind.ANNOUNCEMENT.name, it[Notifications.kind])
            assertEquals("Субботник", it[Notifications.title])
            assertEquals("SUCCESS", it[Notifications.iconStyle])
            assertNull(it[Notifications.complaintId])
        }
        assertTrue(notifs.none { it[Notifications.userId] == admin })
    }

    @Test
    fun `create with CSV pushes only to matching residents`() {
        val admin = seedUser("admin@x.ru", role = UserRole.ADMIN)
        val r1 = seedUser("r1@x.ru", district = "Центральный")
        val r2 = seedUser("r2@x.ru", district = "Адлерский")
        val r3 = seedUser("r3@x.ru", district = "Хостинский")

        service.create(
            Viewer.Authenticated(admin, UserRole.ADMIN),
            CreateAnnouncementRequest(
                title = "т", body = "б",
                districts = listOf("Центральный", "Адлерский")
            )
        )

        val ids = transaction {
            Notifications.selectAll().toList().map { it[Notifications.userId] }
        }
        assertEquals(setOf(r1, r2), ids.toSet())
        assertTrue(r3 !in ids)
    }

    @Test
    fun `notify failure rolls back the announcement insert`() {
        val admin = seedUser("admin@x.ru", role = UserRole.ADMIN)
        seedUser("r1@x.ru")
        val broken = AnnouncementService(repo, object : NotificationService {
            override fun notify(
                recipientUserIds: List<Long>,
                kind: NotificationKind,
                title: String, body: String,
                iconStyle: String?,
                complaintId: Long?, announcementId: Long?
            ) {
                throw IllegalStateException("simulated FCM down")
            }
        })

        val ex = runCatching {
            broken.create(
                Viewer.Authenticated(admin, UserRole.ADMIN),
                CreateAnnouncementRequest(title = "t", body = "b")
            )
        }.exceptionOrNull()
        assertTrue(ex is IllegalStateException, "expected propagated, got $ex")

        val rows = transaction { Announcements.selectAll().count() }
        assertEquals(0L, rows, "announcement insert must be rolled back")
    }

    @Test
    fun `create rejects past expiresAt and sends no notifications`() {
        val admin = seedUser("admin@x.ru", role = UserRole.ADMIN)
        seedUser("r1@x.ru", district = "Центральный")

        val past = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1).toString()
        assertFailsWith<IllegalArgumentException> {
            service.create(
                Viewer.Authenticated(admin, UserRole.ADMIN),
                CreateAnnouncementRequest(title = "t", body = "b", expiresAt = past)
            )
        }
        // ни объявления, ни push — транзакция и не должна была начаться
        assertEquals(0L, transaction { Announcements.selectAll().count() })
        assertEquals(0L, transaction { Notifications.selectAll().count() })
    }

    @Test
    fun `update changes fields without re-pushing`() {
        val admin = seedUser("admin@x.ru", role = UserRole.ADMIN)
        seedUser("r1@x.ru")

        val created = service.create(
            Viewer.Authenticated(admin, UserRole.ADMIN),
            CreateAnnouncementRequest(title = "old", body = "old", districts = emptyList())
        )
        val notifBefore = transaction { Notifications.selectAll().count() }

        service.update(
            Viewer.Authenticated(admin, UserRole.ADMIN),
            created.id,
            UpdateAnnouncementRequest(title = "new")
        )
        val notifAfter = transaction { Notifications.selectAll().count() }
        assertEquals(notifBefore, notifAfter, "update must not produce new notifications")
        assertEquals("new", repo.findById(created.id)!!.title)
    }

    @Test
    fun `get returns full announcement and 404 for unknown id`() {
        val admin = seedUser("admin@x.ru", role = UserRole.ADMIN)
        seedUser("r1@x.ru", district = "Центральный")
        val created = service.create(
            Viewer.Authenticated(admin, UserRole.ADMIN),
            CreateAnnouncementRequest(
                title = "Субботник",
                body = "Полный текст с подробностями…",
                districts = listOf("Центральный")
            )
        )

        val fetched = service.get(created.id)
        assertEquals(created.id, fetched.id)
        assertEquals("Субботник", fetched.title)
        assertEquals("Полный текст с подробностями…", fetched.body)
        assertEquals(listOf("Центральный"), fetched.districts)

        assertFailsWith<NotFoundException> { service.get(999_999L) }
    }

    @Test
    fun `expire is soft and notifications are kept`() {
        val admin = seedUser("admin@x.ru", role = UserRole.ADMIN)
        seedUser("r1@x.ru")
        val created = service.create(
            Viewer.Authenticated(admin, UserRole.ADMIN),
            CreateAnnouncementRequest(title = "t", body = "b")
        )
        val notifCountBefore = transaction { Notifications.selectAll().count() }

        service.expire(Viewer.Authenticated(admin, UserRole.ADMIN), created.id)
        // объявление сохранилось, но списком не возвращается
        assertEquals(0L, repo.listActive(null, 50).second)
        assertEquals(notifCountBefore, transaction { Notifications.selectAll().count() })
    }
}
