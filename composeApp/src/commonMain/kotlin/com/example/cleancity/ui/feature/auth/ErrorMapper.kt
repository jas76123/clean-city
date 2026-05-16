package com.example.cleancity.ui.feature.auth

import com.example.cleancity.data.network.ApiException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.io.IOException

sealed interface UiErrorPlacement {
    data class InlineEmail(val message: String) : UiErrorPlacement
    data class InlinePassword(val message: String) : UiErrorPlacement
    data class Snackbar(val message: String) : UiErrorPlacement
    data class FullScreen(val message: String) : UiErrorPlacement
    data class EmailNotVerified(val email: String) : UiErrorPlacement
}

object ErrorMapper {
    private const val GENERIC = "Что-то пошло не так. Попробуйте ещё раз."
    private const val NETWORK = "Нет соединения с интернетом"
    private const val TIMEOUT = "Сервер не отвечает. Попробуйте позже."

    fun map(t: Throwable, fallbackEmail: String = ""): UiErrorPlacement = when (t) {
        is ApiException -> when (t.error.code) {
            "AUTH_EMAIL_NOT_VERIFIED", "EMAIL_NOT_VERIFIED" -> UiErrorPlacement.EmailNotVerified(fallbackEmail)
            "AUTH_INVALID_CREDENTIALS", "INVALID_CREDENTIALS" -> UiErrorPlacement.InlinePassword("Неверный email или пароль")
            "AUTH_EMAIL_TAKEN", "EMAIL_ALREADY_EXISTS" -> UiErrorPlacement.InlineEmail("Этот email уже зарегистрирован")
            "VALIDATION_INVALID_EMAIL", "EMAIL_INVALID_FORMAT" -> UiErrorPlacement.InlineEmail("Неверный формат email")
            "VALIDATION_WEAK_PASSWORD", "WEAK_PASSWORD" -> UiErrorPlacement.InlinePassword(t.error.message)
            "RATE_LIMITED", "AUTH_RATE_LIMITED" -> UiErrorPlacement.Snackbar("Слишком много попыток. Попробуйте через минуту.")
            "AUTH_ACCOUNT_FROZEN", "ACCOUNT_FROZEN" -> UiErrorPlacement.Snackbar("Аккаунт временно заблокирован. Свяжитесь с поддержкой.")
            "EMAIL_TOKEN_EXPIRED", "INVITE_TOKEN_INVALID" ->
                UiErrorPlacement.FullScreen("Ссылка устарела. Запросите новую.")
            else -> UiErrorPlacement.Snackbar(GENERIC)
        }
        is HttpRequestTimeoutException -> UiErrorPlacement.Snackbar(TIMEOUT)
        is IOException -> UiErrorPlacement.Snackbar(NETWORK)
        else -> UiErrorPlacement.Snackbar(GENERIC)
    }
}
