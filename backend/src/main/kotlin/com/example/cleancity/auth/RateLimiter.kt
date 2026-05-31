package com.example.cleancity.auth

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Простой in-memory rate limiter (фиксированное окно).
 * Подходит для одной ноды. Под прод-сервер с >1 инстансом нужен Redis-вариант.
 *
 * Ключ — ${bucket}:${ip}. В дальнейшем можно расширить ключом по userId.
 */
class RateLimiter(private val now: () -> Long = System::currentTimeMillis) {

    private data class State(val resetAt: Long, val count: Int)

    private val buckets = ConcurrentHashMap<String, State>()

    /**
     * Регистрирует попытку и возвращает true, если она в пределах [limit] за [windowSeconds].
     */
    fun tryAcquire(bucket: String, key: String, limit: Int, windowSeconds: Long): Boolean {
        val composite = "$bucket:$key"
        val nowMs = now()
        val updated = buckets.compute(composite) { _, existing ->
            if (existing == null || nowMs >= existing.resetAt) {
                State(nowMs + windowSeconds * 1000, 1)
            } else {
                State(existing.resetAt, existing.count + 1)
            }
        }!!
        return updated.count <= limit
    }

    fun reset() = buckets.clear()
}

/**
 * Конфигурация для разных бакетов. Числа взяты с запасом для текущего объёма —
 * чтобы не блокировать UI-нагрузочное тестирование/демо. На проде можно ужесточить.
 */
object RateLimits {
    const val LOGIN_LIMIT = 10
    const val LOGIN_WINDOW_SECONDS = 60L
    const val FORGOT_LIMIT = 5
    const val FORGOT_WINDOW_SECONDS = 60L * 60
    const val TWOFA_LIMIT = 10
    const val TWOFA_WINDOW_SECONDS = 60L
}

/**
 * Хелпер для маршрутов: если лимит превышен — отвечает 429 и возвращает true (caller должен прервать).
 */
suspend fun RoutingContext.rateLimitOr429(
    limiter: RateLimiter,
    bucket: String,
    limit: Int,
    windowSeconds: Long
): Boolean {
    val ip = call.clientIp() ?: "unknown"
    if (!limiter.tryAcquire(bucket, ip, limit, windowSeconds)) {
        call.respond(
            HttpStatusCode.TooManyRequests,
            com.example.cleancity.shared.models.ApiError(
                com.example.cleancity.ErrorCodes.RATE_LIMITED,
                "Too many requests, slow down"
            )
        )
        return true
    }
    return false
}

fun ApplicationCall.clientIp(): String? =
    request.header("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
        ?: request.origin.remoteAddress.takeIf { it.isNotBlank() }

fun ApplicationCall.userAgentSafe(): String? =
    request.header(HttpHeaders.UserAgent)?.take(500)
