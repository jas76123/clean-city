package com.example.cleancity.plugins

import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.request.path
import io.ktor.server.response.header

/**
 * Ставит security-заголовки на каждый ответ. Дополняет защиту reverse-proxy
 * (Caddy/Cloudflare) — defense in depth, SPEC § 8.2.
 *
 * CSP жёсткий, потому что backend — чистый JSON API: ни inline-скриптов, ни iframes
 * мы не отдаём. Если позже появятся HTML-эндпоинты (например, страница подтверждения
 * email), CSP для них надо ослаблять адресно.
 */
// HTML-эндпоинты со своим (ослабленным) CSP. Жёсткий app-wide CSP их бы сломал:
//  - /legal/*               — inline <style>
//  - email-landing страницы — inline <script> + fetch (CSP задаётся в WebAuthRoutes)
// Заголовок здесь НЕ ставим: два CSP-заголовка браузер применяет как пересечение
// (самый строгий), и страничный relax был бы аннулирован строгим default-src 'none'.
private val EMAIL_LANDING_PATHS = setOf("/verify-email", "/reset-password", "/accept-invite")

val SecurityHeaders = createApplicationPlugin(name = "SecurityHeaders") {
    onCall { call ->
        val path = call.request.path()
        if (path.startsWith("/legal/") || path in EMAIL_LANDING_PATHS) return@onCall

        with(call.response) {
            header("Strict-Transport-Security", "max-age=63072000; includeSubDomains; preload")
            header(
                "Content-Security-Policy",
                "default-src 'none'; frame-ancestors 'none'; base-uri 'none'"
            )
            header("X-Frame-Options", "DENY")
            header("X-Content-Type-Options", "nosniff")
            header("Referrer-Policy", "no-referrer")
            header(
                "Permissions-Policy",
                "geolocation=(), microphone=(), camera=(), interest-cohort=()"
            )
        }
    }
}
