package com.example.cleancity.data.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.authProviders
import io.ktor.client.plugins.auth.providers.BearerAuthProvider

fun interface TokenInvalidator {
    fun invalidate()
}

// Ktor Auth кеширует результат loadTokens после первого вызова. Без явной очистки
// после logout/login другим аккаунтом запросы продолжают идти со старым токеном —
// сервер отвечает данными прежнего пользователя. clearToken() заставит провайдер
// заново вызвать loadTokens() (а тот прочитает свежий tokenStorage).
fun httpClientTokenInvalidator(client: HttpClient): TokenInvalidator = TokenInvalidator {
    client.authProviders.filterIsInstance<BearerAuthProvider>().forEach { it.clearToken() }
}
