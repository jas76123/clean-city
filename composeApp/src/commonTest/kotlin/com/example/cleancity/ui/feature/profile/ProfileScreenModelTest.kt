package com.example.cleancity.ui.feature.profile

import com.example.cleancity.data.network.ApiError
import com.example.cleancity.data.network.ApiException
import com.example.cleancity.data.network.FakeAuthApi
import com.example.cleancity.data.network.FakeUserApi
import com.example.cleancity.data.repository.AuthRepository
import com.example.cleancity.data.storage.FakeTokenStorage
import com.example.cleancity.domain.AuthState
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
