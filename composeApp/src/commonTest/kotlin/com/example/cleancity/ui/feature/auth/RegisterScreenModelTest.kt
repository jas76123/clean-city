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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegisterScreenModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newModel(authApi: FakeAuthApi = FakeAuthApi()) = RegisterScreenModel(
        AuthRepository(authApi.asAuthApi(), FakeUserApi().asUserApi(), FakeTokenStorage())
    )

    @Test fun `canSubmit false when fields empty and consent off`() {
        val model = newModel()
        assertFalse(model.state.value.canSubmit)
    }

    @Test fun `canSubmit false when consent off but fields filled`() {
        val model = newModel()
        model.setFullName("Test"); model.setEmail("u@x.com"); model.setPassword("Password1")
        assertFalse(model.state.value.canSubmit)
    }

    @Test fun `canSubmit true when all filled and consent on`() {
        val model = newModel()
        model.setFullName("Test"); model.setEmail("u@x.com"); model.setPassword("Password1")
        model.setConsent(true)
        assertTrue(model.state.value.canSubmit)
    }

    @Test fun `submit on EMAIL_ALREADY_EXISTS sets inline email error`() = runTest {
        val authApi = FakeAuthApi(registerResult = Result.failure(ApiException(ApiError("AUTH_EMAIL_TAKEN", "taken"), 409)))
        val model = newModel(authApi)
        model.setFullName("Test"); model.setEmail("u@x.com"); model.setPassword("Password1"); model.setConsent(true)
        model.submit()
        testScheduler.advanceUntilIdle()
        assertEquals("Этот email уже зарегистрирован", model.state.value.emailError)
    }
}
