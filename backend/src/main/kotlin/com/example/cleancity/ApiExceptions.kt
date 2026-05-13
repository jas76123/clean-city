package com.example.cleancity

import io.ktor.http.HttpStatusCode

/**
 * Исключения бизнес-слоя. StatusPages в Application.module() конвертирует их в HTTP-ответ
 * единого формата ApiError(code, message). Сервисы бросают эти исключения, роуты их не
 * ловят напрямую — обработка централизована.
 *
 * `code` — строковая константа для клиента (используется для i18n и логики UI).
 * `message` — человекочитаемое сообщение, безопасное для прямого показа пользователю
 *  (никаких stacktrace, технических деталей или PII).
 */
sealed class ApiException(
    message: String,
    val code: String,
    val status: HttpStatusCode
) : RuntimeException(message)

class BadRequestException(message: String, code: String = ErrorCodes.BAD_REQUEST) :
    ApiException(message, code, HttpStatusCode.BadRequest)

class UnauthorizedException(message: String, code: String = ErrorCodes.UNAUTHORIZED) :
    ApiException(message, code, HttpStatusCode.Unauthorized)

class ForbiddenException(message: String, code: String = ErrorCodes.FORBIDDEN) :
    ApiException(message, code, HttpStatusCode.Forbidden)

class NotFoundException(message: String, code: String = ErrorCodes.NOT_FOUND) :
    ApiException(message, code, HttpStatusCode.NotFound)

class ConflictException(message: String, code: String = ErrorCodes.CONFLICT) :
    ApiException(message, code, HttpStatusCode.Conflict)

class RateLimitedException(message: String = "Too many requests, slow down") :
    ApiException(message, ErrorCodes.RATE_LIMITED, HttpStatusCode.TooManyRequests)

class LockedException(message: String) :
    ApiException(message, ErrorCodes.AUTH_ACCOUNT_LOCKED, HttpStatusCode(423, "Locked"))

/**
 * Каноничные коды ошибок. Любой новый код добавляется сюда и документируется в SPEC § 8.
 */
object ErrorCodes {
    const val BAD_REQUEST = "BAD_REQUEST"
    const val UNAUTHORIZED = "UNAUTHORIZED"
    const val FORBIDDEN = "FORBIDDEN"
    const val NOT_FOUND = "NOT_FOUND"
    const val CONFLICT = "CONFLICT"
    const val RATE_LIMITED = "RATE_LIMITED"
    const val INTERNAL = "INTERNAL"

    const val AUTH_INVALID_CREDENTIALS = "AUTH_INVALID_CREDENTIALS"
    const val AUTH_INVALID_TOKEN = "AUTH_INVALID_TOKEN"
    const val AUTH_EMAIL_UNVERIFIED = "AUTH_EMAIL_UNVERIFIED"
    const val AUTH_2FA_INVALID = "AUTH_2FA_INVALID"
    const val AUTH_2FA_ALREADY_ENABLED = "AUTH_2FA_ALREADY_ENABLED"
    const val AUTH_2FA_NOT_AVAILABLE = "AUTH_2FA_NOT_AVAILABLE"
    const val AUTH_2FA_SETUP_REQUIRED = "AUTH_2FA_SETUP_REQUIRED"
    const val AUTH_ACCOUNT_LOCKED = "AUTH_ACCOUNT_LOCKED"

    const val VALIDATION_INVALID_EMAIL = "VALIDATION_INVALID_EMAIL"
    const val VALIDATION_WEAK_PASSWORD = "VALIDATION_WEAK_PASSWORD"
    const val VALIDATION_TERMS_REQUIRED = "VALIDATION_TERMS_REQUIRED"
    const val VALIDATION_BAD_FIELD = "VALIDATION_BAD_FIELD"

    const val EMAIL_ALREADY_REGISTERED = "EMAIL_ALREADY_REGISTERED"
    const val SESSION_NOT_FOUND = "SESSION_NOT_FOUND"
}
