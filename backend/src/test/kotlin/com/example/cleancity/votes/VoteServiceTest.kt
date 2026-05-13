package com.example.cleancity.votes

import com.example.cleancity.ConflictException
import com.example.cleancity.NotFoundException
import com.example.cleancity.complaints.ComplaintRepository
import com.example.cleancity.database.tables.Complaints
import com.example.cleancity.database.tables.Users
import com.example.cleancity.database.tables.Votes
import com.example.cleancity.shared.models.ComplaintStatus
import com.example.cleancity.shared.models.ProblemCategory
import com.example.cleancity.shared.models.UserRole
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VoteServiceTest {

    private lateinit var complaintRepo: ComplaintRepository
    private lateinit var voteRepo: VoteRepository
    private lateinit var service: VoteService

    @BeforeTest
    fun setup() {
        Database.connect(
            "jdbc:h2:mem:votes-${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.drop(Votes, Complaints, Users)
            SchemaUtils.create(Users, Complaints, Votes)
        }
        complaintRepo = ComplaintRepository()
        voteRepo = VoteRepository()
        service = VoteService(voteRepo, complaintRepo)
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

    private fun seedComplaint(authorId: Long, status: ComplaintStatus = ComplaintStatus.NEW): Long = transaction {
        val cid = Complaints.insert {
            it[Complaints.authorId] = authorId
            it[Complaints.category] = ProblemCategory.GARBAGE.name
            it[Complaints.title] = "Мусор · ул. Тестовая"
            it[Complaints.description] = "test"
            it[Complaints.latitude] = 43.6
            it[Complaints.longitude] = 39.7
            it[Complaints.address] = "ул. Тестовая, 1"
            it[Complaints.status] = status.name
            it[Complaints.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
            it[Complaints.updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }[Complaints.id]
        // Автоголос автора — стандартное поведение из ComplaintService.create.
        Votes.insert {
            it[Votes.complaintId] = cid
            it[Votes.userId] = authorId
            it[Votes.value] = 1
            it[Votes.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }
        cid
    }

    @Test
    fun `addVote returns 404 for missing complaint`() {
        val voter = seedUser("v@x.ru")
        assertFailsWith<NotFoundException> {
            service.addVote(complaintId = 9_999, userId = voter)
        }
    }

    @Test
    fun `addVote on NEW complaint increments vote count and marks userVoted`() {
        val author = seedUser("a@x.ru")
        val voter = seedUser("v@x.ru")
        val cid = seedComplaint(author)

        val resp = service.addVote(cid, voter)
        assertEquals(2, resp.votesCount, "автор + voter = 2")
        assertTrue(resp.userVoted)
    }

    @Test
    fun `addVote on IN_PROGRESS still allowed`() {
        val author = seedUser("a@x.ru")
        val voter = seedUser("v@x.ru")
        val cid = seedComplaint(author, ComplaintStatus.IN_PROGRESS)

        val resp = service.addVote(cid, voter)
        assertEquals(2, resp.votesCount)
    }

    @Test
    fun `addVote on REJECTED complaint returns 409 Conflict`() {
        val author = seedUser("a@x.ru")
        val voter = seedUser("v@x.ru")
        val cid = seedComplaint(author, ComplaintStatus.REJECTED)

        val ex = assertFailsWith<ConflictException> {
            service.addVote(cid, voter)
        }
        assertTrue(ex.message!!.contains("отклонена"))
        // Голос не должен быть записан.
        val count = voteRepo.countYes(cid)
        assertEquals(1, count, "только автогос автора, новый голос не записан")
    }

    @Test
    fun `addVote on DUPLICATE complaint returns 409 Conflict`() {
        val author = seedUser("a@x.ru")
        val voter = seedUser("v@x.ru")
        val cid = seedComplaint(author, ComplaintStatus.DUPLICATE)

        assertFailsWith<ConflictException> {
            service.addVote(cid, voter)
        }
        assertEquals(1, voteRepo.countYes(cid))
    }

    @Test
    fun `removeVote on REJECTED returns 409 Conflict and preserves vote record`() {
        val author = seedUser("a@x.ru")
        val voter = seedUser("v@x.ru")
        val cid = seedComplaint(author)
        // Сначала проставим голос пока NEW.
        service.addVote(cid, voter)
        assertEquals(2, voteRepo.countYes(cid))

        // Переводим в REJECTED.
        transaction {
            Complaints.update({ Complaints.id eq cid }) { it[Complaints.status] = ComplaintStatus.REJECTED.name }
        }

        assertFailsWith<ConflictException> {
            service.removeVote(cid, voter)
        }
        // Голос остался — это «фиксация общественного сигнала на момент закрытия».
        assertEquals(2, voteRepo.countYes(cid))
    }

    @Test
    fun `removeVote on DUPLICATE returns 409 Conflict`() {
        val author = seedUser("a@x.ru")
        val voter = seedUser("v@x.ru")
        val cid = seedComplaint(author)
        service.addVote(cid, voter)
        transaction {
            Complaints.update({ Complaints.id eq cid }) { it[Complaints.status] = ComplaintStatus.DUPLICATE.name }
        }
        assertFailsWith<ConflictException> {
            service.removeVote(cid, voter)
        }
    }

    @Test
    fun `author cannot remove own auto-vote`() {
        val author = seedUser("a@x.ru")
        val cid = seedComplaint(author)
        val ex = assertFailsWith<ConflictException> {
            service.removeVote(cid, author)
        }
        assertTrue(ex.message!!.contains("Автор"))
        assertEquals(1, voteRepo.countYes(cid))
    }

    @Test
    fun `removeVote on NEW is idempotent — second removal stays at the same count`() {
        val author = seedUser("a@x.ru")
        val voter = seedUser("v@x.ru")
        val cid = seedComplaint(author)
        service.addVote(cid, voter)

        val first = service.removeVote(cid, voter)
        assertEquals(1, first.votesCount)
        assertFalse(first.userVoted)

        val second = service.removeVote(cid, voter)
        assertEquals(1, second.votesCount, "повторный removeVote — no-op")
    }
}
