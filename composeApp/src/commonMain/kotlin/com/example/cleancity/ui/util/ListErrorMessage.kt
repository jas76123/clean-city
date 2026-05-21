package com.example.cleancity.ui.util

import com.example.cleancity.data.network.ApiException
import kotlinx.io.IOException

private const val NETWORK = "Нет соединения. Проверьте интернет и попробуйте позже."
private const val SERVER = "Сервер временно недоступен. Попробуйте позже."
private const val GENERIC = "Что-то пошло не так. Попробуйте ещё раз."

/**
 * Человекочитаемый текст ошибки для ErrorState на экранах-списках.
 * Никогда не показывает сырой текст исключения пользователю.
 */
fun listErrorMessage(t: Throwable): String = when {
    t is IOException -> NETWORK
    t is ApiException && t.httpStatus in 500..599 -> SERVER
    else -> GENERIC
}
