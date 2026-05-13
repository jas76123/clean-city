package com.example.cleancity.notifications

import com.example.cleancity.database.tables.ComplaintPhotos
import com.example.cleancity.database.tables.Complaints
import com.example.cleancity.database.tables.Notifications
import com.example.cleancity.database.tables.Users
import com.example.cleancity.shared.models.NotificationKind
import com.example.cleancity.shared.models.UserRole
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

        transaction {
            repo.insertBatch(
                userIds = listOf(a, b, c),
                kind = NotificationKind.COMPLAINT_STATUS,
                title = "T",
                body = "B",
                iconStyle = "INFO",
                complaintId = cid,
                announcementId = null
            )
        }

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
        transaction {
            repo.insertBatch(
                userIds = emptyList(),
                kind = NotificationKind.COMPLAINT_STATUS,
                title = "T", body = "B", iconStyle = null,
                complaintId = 1L, announcementId = null
            )
        }
        val count = transaction { Notifications.selectAll().count() }
        assertEquals(0, count)
    }

    @Test
    fun `listForUser returns own notifications sorted by createdAt desc`() {
        val a = seedUser("a@x.ru")
        val b = seedUser("b@x.ru")
        val cid = seedComplaint(a)

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
            repeat(2) {
                Notifications.insert {
                    it[userId] = a; it[kind] = "COMPLAINT_STATUS"
                    it[title] = "u"; it[body] = "x"
                    it[complaintId] = cid; it[createdAt] = now
                }
            }
            Notifications.insert {
                it[userId] = a; it[kind] = "COMPLAINT_STATUS"
                it[title] = "r"; it[body] = "x"
                it[complaintId] = cid; it[createdAt] = now
                it[readAt] = now
            }
            Notifications.insert {
                it[userId] = a; it[kind] = "COMPLAINT_STATUS"
                it[title] = "old"; it[body] = "x"
                it[complaintId] = cid; it[createdAt] = now.minusDays(91)
            }
        }
        assertEquals(2L, repo.countUnreadForUser(a))
    }

    @Test
    fun `markRead sets readAt only for owner and is idempotent`() {
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

        assertEquals(false, repo.markRead(notificationId = nid, userId = b))

        assertEquals(true, repo.markRead(nid, a))
        val firstReadAt = transaction {
            Notifications.selectAll().where { Notifications.id eq nid }
                .single()[Notifications.readAt]
        }
        assertTrue(firstReadAt != null)

        Thread.sleep(20)
        assertEquals(true, repo.markRead(nid, a))
        val secondReadAt = transaction {
            Notifications.selectAll().where { Notifications.id eq nid }
                .single()[Notifications.readAt]
        }
        assertEquals(firstReadAt, secondReadAt)
    }

    @Test
    fun `deleteOlderThan removes only rows older than cutoff and returns count`() {
        val a = seedUser("a@x.ru")
        val cid = seedComplaint(a)
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        transaction {
            // 2 свежие, 3 старше 90 дней
            repeat(2) {
                Notifications.insert {
                    it[userId] = a; it[kind] = "COMPLAINT_STATUS"
                    it[title] = "fresh"; it[body] = "x"
                    it[complaintId] = cid; it[createdAt] = now
                }
            }
            repeat(3) {
                Notifications.insert {
                    it[userId] = a; it[kind] = "COMPLAINT_STATUS"
                    it[title] = "old"; it[body] = "x"
                    it[complaintId] = cid; it[createdAt] = now.minusDays(91)
                }
            }
        }

        val deleted = repo.deleteOlderThan(90L)
        assertEquals(3, deleted)

        val remaining = transaction { Notifications.selectAll().count() }
        assertEquals(2L, remaining)
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
            Notifications.insert {
                it[userId] = a; it[kind] = "COMPLAINT_STATUS"
                it[title] = "a-r"; it[body] = "x"
                it[complaintId] = cid; it[createdAt] = now
                it[readAt] = now
            }
            Notifications.insert {
                it[userId] = b; it[kind] = "COMPLAINT_STATUS"
                it[title] = "b"; it[body] = "x"
                it[complaintId] = cid; it[createdAt] = now
            }
        }

        assertEquals(3, repo.markAllRead(a))

        val bUnread = transaction {
            Notifications.selectAll().where {
                (Notifications.userId eq b) and Notifications.readAt.isNull()
            }.count()
        }
        assertEquals(1L, bUnread)
    }
}
