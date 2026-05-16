package com.example.cleancity.data.network

import com.example.cleancity.data.storage.TokenStorage
import com.example.cleancity.shared.models.AuthResponse
import com.example.cleancity.shared.requests.auth.RefreshTokenRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.encodedPath
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

interface AuthFailureHandler {
    fun onAuthFailure()
}

private val refreshMutex = Mutex()

fun createHttpClient(
    engine: HttpClientEngine,
    baseUrl: String,
    isDebug: Boolean,
    tokenStorage: TokenStorage,
    onAuthFailure: AuthFailureHandler,
): HttpClient = HttpClient(engine) {
    expectSuccess = true

    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        })
    }

    install(Logging) {
        level = if (isDebug) LogLevel.HEADERS else LogLevel.NONE
        logger = object : Logger { override fun log(message: String) { println(message) } }
        sanitizeHeader { name -> name == HttpHeaders.Authorization }
        filter { req -> !req.url.encodedPath.startsWith("/auth/") }
    }

    defaultRequest {
        url(baseUrl)
        contentType(ContentType.Application.Json)
        headers.append(HttpHeaders.Accept, ContentType.Application.Json.toString())
    }

    install(HttpTimeout) {
        requestTimeoutMillis = 15_000
        connectTimeoutMillis = 5_000
    }

    install(Auth) {
        bearer {
            loadTokens {
                tokenStorage.read()?.let { BearerTokens(it.access, it.refresh) }
            }
            refreshTokens {
                refreshMutex.withLock {
                    val current = tokenStorage.read()
                    if (current == null) {
                        onAuthFailure.onAuthFailure()
                        return@refreshTokens null
                    }
                    try {
                        val refreshed: AuthResponse = client.post("/auth/refresh") {
                            markAsRefreshTokenRequest()
                            setBody(RefreshTokenRequest(current.refresh))
                        }.body()
                        tokenStorage.write(refreshed.accessToken, refreshed.refreshToken)
                        BearerTokens(refreshed.accessToken, refreshed.refreshToken)
                    } catch (t: Throwable) {
                        tokenStorage.clear()
                        onAuthFailure.onAuthFailure()
                        null
                    }
                }
            }
        }
    }

    HttpResponseValidator {
        validateResponse { response: HttpResponse ->
            if (!response.status.isSuccess()) {
                val err = runCatching { response.body<ApiError>() }
                    .getOrDefault(ApiError("UNKNOWN", "HTTP ${response.status.value}"))
                throw ApiException(err, response.status.value)
            }
        }
    }
}
