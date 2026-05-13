package com.example.cleancity.testutils

import com.example.cleancity.ApiException
import com.example.cleancity.ErrorCodes
import com.example.cleancity.shared.models.ApiError
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond

/**
 * Подключает тот же набор обработчиков ошибок, что и [com.example.cleancity.module],
 * чтобы тесты получали продакшен-формат `{code, message}`.
 *
 * Используется в роут-тестах, которые не запускают полный module().
 */
fun Application.installApiErrorHandling() {
    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.respond(cause.status, ApiError(cause.code, cause.message ?: cause.code))
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ApiError(ErrorCodes.BAD_REQUEST, cause.message ?: "Bad request")
            )
        }
        exception<Throwable> { call, _ ->
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(ErrorCodes.INTERNAL, "Internal server error")
            )
        }
    }
}
