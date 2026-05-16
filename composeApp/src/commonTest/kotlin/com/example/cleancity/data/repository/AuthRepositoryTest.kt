package com.example.cleancity.data.repository

import com.example.cleancity.data.network.FakeAuthApi
import com.example.cleancity.data.network.FakeUserApi
import com.example.cleancity.data.storage.FakeTokenStorage
import com.example.cleancity.data.storage.Tokens
import com.example.cleancity.domain.AuthState
import com.example.cleancity.shared.models.AuthResponse
import com.example.cleancity.shared.models.LoginResponse
import com.example.cleancity.shared.models.UserResponse
import com.example.cleancity.shared.models.UserRole
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AuthRepositoryTest {

    private val sampleUser = UserResponse(
        id = 1, email = "u@x.com", role = UserRole.RESIDENT,
        fullName = "User", emailVerified = true, createdAt = "2026-05-13T00:00:00Z"
    )

    @Test fun `init with no tokens yields Anonymous`() = runTest {
        val repo = AuthRepository(FakeAuthApi().asAuthApi(), FakeUserApi().asUserApi(), FakeTokenStorage())
        repo.init()
        assertEquals(AuthState.Anonymous, repo.state.value)
    }

    @Test fun `init with valid tokens fetches me and yields Authenticated`() = runTest {
        val storage = FakeTokenStorage().apply { preset(Tokens("acc", "ref")) }
        val userApi = FakeUserApi(meResult = Result.success(sampleUser))
        val repo = AuthRepository(FakeAuthApi().asAuthApi(), userApi.asUserApi(), storage)
        repo.init()
        assertEquals(AuthState.Authenticated(sampleUser), repo.state.value)
    }

    @Test fun `init with invalid tokens clears storage and yields Anonymous`() = runTest {
        val storage = FakeTokenStorage().apply { preset(Tokens("bad", "bad")) }
        val userApi = FakeUserApi(meResult = Result.failure(RuntimeException("401")))
        val repo = AuthRepository(FakeAuthApi().asAuthApi(), userApi.asUserApi(), storage)
        repo.init()
        assertEquals(AuthState.Anonymous, repo.state.value)
        assertEquals(1, storage.clearCount)
    }

    @Test fun `register success yields NeedsVerification`() = runTest {
        val authApi = FakeAuthApi(registerResult = Result.success(sampleUser.copy(emailVerified = false)))
        val repo = AuthRepository(authApi.asAuthApi(), FakeUserApi().asUserApi(), FakeTokenStorage())
        val r = repo.register("u@x.com", "Password1", "Full Name")
        assertTrue(r.isSuccess)
        assertEquals(AuthState.NeedsVerification("u@x.com"), repo.state.value)
    }

    @Test fun `verifyEmail success writes tokens and yields Authenticated`() = runTest {
        val storage = FakeTokenStorage()
        val authApi = FakeAuthApi(verifyResult = Result.success(
            AuthResponse(accessToken = "acc", refreshToken = "ref", accessExpiresIn = 0L, refreshExpiresIn = 0L, user = sampleUser)
        ))
        val repo = AuthRepository(authApi.asAuthApi(), FakeUserApi().asUserApi(), storage)
        val r = repo.verifyEmail("token-xyz")
        assertTrue(r.isSuccess)
        assertEquals(AuthState.Authenticated(sampleUser), repo.state.value)
        assertEquals(Tokens("acc", "ref"), storage.read())
    }

    @Test fun `login success writes tokens`() = runTest {
        val storage = FakeTokenStorage()
        val authApi = FakeAuthApi(loginResult = Result.success(
            LoginResponse(
                requires2fa = false,
                auth = AuthResponse(accessToken = "a", refreshToken = "r", accessExpiresIn = 0L, refreshExpiresIn = 0L, user = sampleUser),
            )
        ))
        val repo = AuthRepository(authApi.asAuthApi(), FakeUserApi().asUserApi(), storage)
        val r = repo.login("u@x.com", "Password1")
        assertTrue(r.isSuccess)
        assertIs<AuthState.Authenticated>(repo.state.value)
        assertEquals(Tokens("a", "r"), storage.read())
    }

    @Test fun `logout clears storage and yields Anonymous`() = runTest {
        val storage = FakeTokenStorage().apply { preset(Tokens("a", "r")) }
        val repo = AuthRepository(FakeAuthApi().asAuthApi(), FakeUserApi().asUserApi(), storage)
        repo.logout()
        assertEquals(AuthState.Anonymous, repo.state.value)
        assertEquals(1, storage.clearCount)
    }
}
