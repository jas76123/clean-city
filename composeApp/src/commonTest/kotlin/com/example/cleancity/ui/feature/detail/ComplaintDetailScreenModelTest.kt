package com.example.cleancity.ui.feature.detail

import com.example.cleancity.data.network.FakeAuthApi
import com.example.cleancity.data.network.FakeUserApi
import com.example.cleancity.data.repository.AuthRepository
import com.example.cleancity.data.storage.FakeTokenStorage
import com.example.cleancity.shared.models.AuthResponse
import com.example.cleancity.shared.models.ComplaintResponse
import com.example.cleancity.shared.models.ComplaintStatus
import com.example.cleancity.shared.models.LoginResponse
import com.example.cleancity.shared.models.ProblemCategory
import com.example.cleancity.shared.models.UserResponse
import com.example.cleancity.shared.models.UserRole
import com.example.cleancity.shared.models.VoteResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComplaintDetailScreenModelTest {

    private lateinit var api: FakeDetailComplaintsApi

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        api = FakeDetailComplaintsApi()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- load() -------------------------------------------------------------

    @Test fun `load sets Loaded with isGuest=true when not authenticated`() = runTest {
        api.nextGetByIdResult = Result.success(complaint(id = 42, votes = 7, voted = false))
        val model = ComplaintDetailScreenModel(42L, api, guestRepo())

        model.load()

        val state = model.state.value as DetailState.Loaded
        assertEquals(42L, state.complaint.id)
        assertEquals(true, state.isGuest)
        assertEquals(false, state.isVoting)
        assertNull(state.transientError)
        assertEquals(listOf(42L), api.getByIdCalls)
    }

    @Test fun `load sets Loaded with isGuest=false when authenticated`() = runTest {
        api.nextGetByIdResult = Result.success(complaint(id = 7))
        val model = ComplaintDetailScreenModel(7L, api, authenticatedRepo())

        model.load()

        val state = model.state.value as DetailState.Loaded
        assertEquals(false, state.isGuest)
    }

    @Test fun `load on API failure sets DetailState_Error with message`() = runTest {
        api.nextGetByIdResult = Result.failure(RuntimeException("offline"))
        val model = ComplaintDetailScreenModel(1L, api, guestRepo())

        model.load()

        val state = model.state.value as DetailState.Error
        assertEquals("offline", state.message)
    }

    @Test fun `load on failure with null message uses fallback string`() = runTest {
        api.nextGetByIdResult = Result.failure(RuntimeException())
        val model = ComplaintDetailScreenModel(1L, api, guestRepo())

        model.load()

        val state = model.state.value as DetailState.Error
        assertEquals("Не удалось загрузить жалобу", state.message)
    }

    @Test fun `load does not re-fetch when already Loaded`() = runTest {
        api.nextGetByIdResult = Result.success(complaint(id = 1))
        val model = ComplaintDetailScreenModel(1L, api, guestRepo())
        model.load()
        assertEquals(1, api.getByIdCalls.size)

        model.load()

        assertEquals(1, api.getByIdCalls.size, "second load() must be a no-op when state is Loaded")
    }

    // --- refresh() ----------------------------------------------------------

    @Test fun `refresh updates complaint inside existing Loaded state`() = runTest {
        api.nextGetByIdResult = Result.success(complaint(id = 1, votes = 5))
        val model = ComplaintDetailScreenModel(1L, api, guestRepo())
        model.load()

        api.nextGetByIdResult = Result.success(complaint(id = 1, votes = 9))
        model.refresh()

        val state = model.state.value as DetailState.Loaded
        assertEquals(9, state.complaint.votesCount)
    }

    @Test fun `refresh from Initial state populates Loaded`() = runTest {
        api.nextGetByIdResult = Result.success(complaint(id = 1))
        val model = ComplaintDetailScreenModel(1L, api, guestRepo())

        model.refresh()

        assertTrue(model.state.value is DetailState.Loaded)
    }

    @Test fun `refresh swallows errors silently`() = runTest {
        api.nextGetByIdResult = Result.success(complaint(id = 1, votes = 3))
        val model = ComplaintDetailScreenModel(1L, api, guestRepo())
        model.load()

        api.nextGetByIdResult = Result.failure(RuntimeException("flaky"))
        model.refresh()

        val state = model.state.value as DetailState.Loaded
        assertEquals(3, state.complaint.votesCount, "refresh error must not change existing data")
    }

    // --- toggleVote() -------------------------------------------------------

    @Test fun `toggleVote optimistically increments count and userVoted`() = runTest {
        api.nextGetByIdResult = Result.success(complaint(id = 1, votes = 10, voted = false))
        api.voteGate = CompletableDeferred()
        api.nextVoteResult = Result.success(VoteResponse(votesCount = 11, userVoted = true))
        val model = ComplaintDetailScreenModel(1L, api, authenticatedRepo())
        model.load()

        model.toggleVote()

        val mid = model.state.value as DetailState.Loaded
        assertEquals(true, mid.isVoting)
        assertEquals(true, mid.complaint.userVoted, "optimistic userVoted=true")
        assertEquals(11, mid.complaint.votesCount, "optimistic +1")

        api.voteGate!!.complete(Unit)
        val final = model.state.value as DetailState.Loaded
        assertEquals(false, final.isVoting)
        assertEquals(true, final.complaint.userVoted)
        assertEquals(11, final.complaint.votesCount)
        assertEquals(listOf(1L), api.voteCalls)
        assertTrue(api.unvoteCalls.isEmpty())
    }

    @Test fun `toggleVote when already voted calls unvote and decrements`() = runTest {
        api.nextGetByIdResult = Result.success(complaint(id = 1, votes = 5, voted = true))
        api.nextUnvoteResult = Result.success(VoteResponse(votesCount = 4, userVoted = false))
        val model = ComplaintDetailScreenModel(1L, api, authenticatedRepo())
        model.load()

        model.toggleVote()

        val state = model.state.value as DetailState.Loaded
        assertEquals(false, state.complaint.userVoted)
        assertEquals(4, state.complaint.votesCount)
        assertEquals(listOf(1L), api.unvoteCalls)
        assertTrue(api.voteCalls.isEmpty())
    }

    @Test fun `toggleVote optimistic decrement never goes below zero`() = runTest {
        api.nextGetByIdResult = Result.success(complaint(id = 1, votes = 0, voted = true))
        api.voteGate = CompletableDeferred()
        api.nextUnvoteResult = Result.success(VoteResponse(votesCount = 0, userVoted = false))
        val model = ComplaintDetailScreenModel(1L, api, authenticatedRepo())
        model.load()

        model.toggleVote()

        val mid = model.state.value as DetailState.Loaded
        assertEquals(0, mid.complaint.votesCount, "coerceAtLeast(0) on optimistic update")
        api.voteGate!!.complete(Unit)
    }

    @Test fun `toggleVote on failure rolls back complaint and sets transientError`() = runTest {
        val original = complaint(id = 1, votes = 10, voted = false)
        api.nextGetByIdResult = Result.success(original)
        api.nextVoteResult = Result.failure(RuntimeException("server 500"))
        val model = ComplaintDetailScreenModel(1L, api, authenticatedRepo())
        model.load()

        model.toggleVote()

        val state = model.state.value as DetailState.Loaded
        assertEquals(false, state.isVoting)
        assertEquals(10, state.complaint.votesCount, "rolled back to original")
        assertEquals(false, state.complaint.userVoted, "rolled back to original")
        assertEquals("server 500", state.transientError)
    }

    @Test fun `toggleVote does nothing when guest`() = runTest {
        api.nextGetByIdResult = Result.success(complaint(id = 1, votes = 5, voted = false))
        val model = ComplaintDetailScreenModel(1L, api, guestRepo())
        model.load()

        model.toggleVote()

        val state = model.state.value as DetailState.Loaded
        assertEquals(5, state.complaint.votesCount, "guest cannot change votes")
        assertEquals(false, state.complaint.userVoted)
        assertTrue(api.voteCalls.isEmpty())
        assertTrue(api.unvoteCalls.isEmpty())
    }

    @Test fun `toggleVote is ignored when already voting`() = runTest {
        api.nextGetByIdResult = Result.success(complaint(id = 1, votes = 10, voted = false))
        api.voteGate = CompletableDeferred()
        api.nextVoteResult = Result.success(VoteResponse(votesCount = 11, userVoted = true))
        val model = ComplaintDetailScreenModel(1L, api, authenticatedRepo())
        model.load()
        model.toggleVote() // первый — стартует, висит на gate

        model.toggleVote() // второй — должен быть проигнорирован

        assertEquals(1, api.voteCalls.size, "second toggleVote during inflight is a no-op")
        api.voteGate!!.complete(Unit)
    }

    @Test fun `toggleVote does nothing when state is not Loaded`() = runTest {
        api.nextGetByIdResult = Result.failure(RuntimeException("offline"))
        val model = ComplaintDetailScreenModel(1L, api, authenticatedRepo())
        model.load()
        assertTrue(model.state.value is DetailState.Error)

        model.toggleVote()

        assertTrue(api.voteCalls.isEmpty())
        assertTrue(api.unvoteCalls.isEmpty())
    }

    // --- clearTransientError() ----------------------------------------------

    @Test fun `clearTransientError resets only the error and keeps other fields`() = runTest {
        api.nextGetByIdResult = Result.success(complaint(id = 1, votes = 10, voted = false))
        api.nextVoteResult = Result.failure(RuntimeException("boom"))
        val model = ComplaintDetailScreenModel(1L, api, authenticatedRepo())
        model.load()
        model.toggleVote()
        val before = model.state.value as DetailState.Loaded
        assertNotNull(before.transientError)

        model.clearTransientError()

        val after = model.state.value as DetailState.Loaded
        assertNull(after.transientError)
        assertEquals(before.complaint, after.complaint)
    }

    @Test fun `clearTransientError is a no-op when state is not Loaded`() = runTest {
        api.nextGetByIdResult = Result.failure(RuntimeException("offline"))
        val model = ComplaintDetailScreenModel(1L, api, guestRepo())
        model.load()

        model.clearTransientError()

        assertTrue(model.state.value is DetailState.Error)
    }

    // --- helpers ------------------------------------------------------------

    private fun complaint(
        id: Long = 1,
        votes: Int = 0,
        voted: Boolean = false,
    ): ComplaintResponse = ComplaintResponse(
        id = id,
        authorId = 99,
        authorName = "Тест",
        category = ProblemCategory.GARBAGE,
        title = "Тестовая жалоба",
        description = "desc",
        latitude = 43.585,
        longitude = 39.723,
        address = "ул. Тестовая, 1",
        status = ComplaintStatus.NEW,
        votesCount = votes,
        userVoted = voted,
        createdAt = "2026-05-18T10:00:00Z",
        updatedAt = "2026-05-18T10:00:00Z",
    )

    private fun guestRepo(): AuthRepository =
        AuthRepository(FakeAuthApi().asAuthApi(), FakeUserApi().asUserApi(), FakeTokenStorage())

    private suspend fun authenticatedRepo(): AuthRepository {
        val user = UserResponse(
            id = 1,
            email = "u@x.com",
            role = UserRole.RESIDENT,
            fullName = "User",
            emailVerified = true,
            createdAt = "2026-01-01T00:00:00Z",
        )
        val authApi = FakeAuthApi(
            loginResult = Result.success(
                LoginResponse(auth = AuthResponse("at", "rt", 3600, 86400, user)),
            ),
        )
        val repo = AuthRepository(authApi.asAuthApi(), FakeUserApi().asUserApi(), FakeTokenStorage())
        repo.login("u@x.com", "Password1").getOrThrow()
        return repo
    }
}
