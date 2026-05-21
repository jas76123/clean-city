package com.example.cleancity.ui.util

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Относительное время из ISO-8601 строки для карточек уведомлений.
 * «только что» / «N мин назад» / «N ч назад» / «вчера» / «N дн назад» / дата.
 * При неразборчивой строке — fallback на первые 10 символов.
 */
fun relativeTime(iso: String, now: Instant = Clock.System.now()): String {
    val instant = runCatching { Instant.parse(iso) }.getOrNull()
        ?: return iso.take(10)
    val seconds = (now - instant).inWholeSeconds
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    return when {
        seconds < 60 -> "только что"
        minutes < 60 -> "$minutes мин назад"
        hours < 24 -> "$hours ч назад"
        days == 1L -> "вчера"
        days < 7 -> "$days дн назад"
        else -> iso.take(10)
    }
}
