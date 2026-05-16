package com.example.cleancity.ui.feature.auth

import com.example.cleancity.data.network.ApiError
import com.example.cleancity.data.network.ApiException
import com.example.cleancity.data.network.FakeAuthApi
import com.example.cleancity.data.network.FakeUserApi
import com.example.cleancity.data.repository.AuthRepository
import com.example.cleancity.data.storage.FakeTokenStorage
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

class LoginScreenModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildModel(authApi: FakeAuthApi): Pair<LoginScreenModel, AuthRepository> {
        val repo = AuthRepository(authApi.asAuthApi(), FakeUserApi().asUserApi(), FakeTokenStorage())
        return LoginScreenModel(repo) to repo
    }

    @Test fun `submit with invalid credentials sets inline password error`() = runTest {
        val authApi = FakeAuthApi(loginResult = Result.failure(ApiException(ApiError("AUTH_INVALID_CREDENTIALS", "bad"), 401)))
        val (model, _) = buildModel(authApi)
        model.setEmail("u@x.com"); model.setPassword("Password1")
        model.submit()
        // wait propagation
        testScheduler.advanceUntilIdle()
        assertEquals("Неверный email или пароль", model.state.value.passwordError)
        assertNull(model.state.value.emailError)
    }

    @Test fun `submit with email not verified surfaces resend state`() = runTest {
        val authApi = FakeAuthApi(loginResult = Result.failure(ApiException(ApiError("AUTH_EMAIL_NOT_VERIFIED", "verify"), 403)))
        val (model, _) = buildModel(authApi)
        model.setEmail("u@x.com"); model.setPassword("Password1")
        model.submit()
        testScheduler.advanceUntilIdle()
        assertEquals("u@x.com", model.state.value.emailNotVerifiedFor)
    }

    @Test fun `submit with network error sets snackbar`() = runTest {
        val authApi = FakeAuthApi(loginResult = Result.failure(kotlinx.io.IOException("no net")))
        val (model, _) = buildModel(authApi)
        model.setEmail("u@x.com"); model.setPassword("Password1")
        model.submit()
        testScheduler.advanceUntilIdle()
        assertNotNull(model.state.value.snackbar)
    }
}
