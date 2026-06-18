package com.example.cleancity.ui.feature.profile

import com.example.cleancity.data.network.ApiError
import com.example.cleancity.data.network.ApiException
import com.example.cleancity.data.network.FakeAuthApi
import com.example.cleancity.data.network.FakeUserApi
import com.example.cleancity.data.repository.AuthRepository
import com.example.cleancity.data.storage.FakeTokenStorage
import com.example.cleancity.data.storage.Tokens
import com.example.cleancity.domain.AuthState
import com.example.cleancity.shared.models.UserResponse
import com.example.cleancity.shared.models.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProfileScreenModelTest {

    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private fun buildModel(authApi: FakeAuthApi): Pair<ProfileScreenModel, AuthRepository> {
        val repo = AuthRepository(authApi.asAuthApi(), FakeUserApi().asUserApi(), FakeTokenStorage())
        return ProfileScreenModel(FakeProfileComplaintsApi(), repo) to repo
    }

    private suspend fun authedRepo(): AuthRepository {
        val user = UserResponse(
            id = 1, email = "u@x.com", role = UserRole.RESIDENT,
            fullName = "U", emailVerified = true, createdAt = "2026-05-21T00:00:00Z",
        )
        return AuthRepository(
            FakeAuthApi().asAuthApi(),
            FakeUserApi(meResult = Result.success(user)).asUserApi(),
            FakeTokenStorage().apply { preset(Tokens("acc", "ref")) },
        ).apply { init() }
    }

    // Регресс: при сетевой ошибке профиля приложение крашилось — исключение из
    // async уходило мимо try/catch в родительский scope (Dispatchers.Main) и
    // валило процесс. load() обязан показать Error, а не падать.
    @Test fun `load surfaces Error state when complaints api fails instead of crashing`() = runTest {
        val repo = authedRepo()
        testScheduler.advanceUntilIdle()
        assertIs<AuthState.Authenticated>(repo.state.value)

        val networkError = ApiException(ApiError("NETWORK", "Connection refused"), 0)
        val model = ProfileScreenModel(
            FakeProfileComplaintsApi(
                mineResult = Result.failure(networkError),
                votedResult = Result.failure(networkError),
            ),
            repo,
        )

        model.load()
        testScheduler.advanceUntilIdle()

        assertIs<ProfileState.Error>(model.state.value)
    }

    @Test fun `deleteAccount success switches auth state to Anonymous`() = runTest {
        val authApi = FakeAuthApi(deleteAccountResult = Result.success(Unit))
        val (model, repo) = buildModel(authApi)

        model.deleteAccount()
        testScheduler.advanceUntilIdle()

        assertEquals(1, authApi.deleteAccountCalls)
        assertEquals(AuthState.Anonymous, repo.state.value)
    }

    @Test fun `deleteAccount failure surfaces error state`() = runTest {
        val authApi = FakeAuthApi(
            deleteAccountResult = Result.failure(ApiException(ApiError("SERVER", "boom"), 500)),
        )
        val (model, _) = buildModel(authApi)

        model.deleteAccount()
        testScheduler.advanceUntilIdle()

        assertIs<DeleteAccountState.Error>(model.deleteState.value)
    }

    @Test fun `dismissDeleteError resets state to Idle after error`() = runTest {
        val authApi = FakeAuthApi(
            deleteAccountResult = Result.failure(ApiException(ApiError("SERVER", "boom"), 500)),
        )
        val (model, _) = buildModel(authApi)

        model.deleteAccount()
        testScheduler.advanceUntilIdle()

        model.dismissDeleteError()

        assertEquals(DeleteAccountState.Idle, model.deleteState.value)
    }
}
