package com.example.cleancity

/**
 * Исключения бизнес-слоя, которые StatusPages в Application.module() конвертирует
 * в соответствующие HTTP-коды. Сервисы бросают эти исключения, роуты их не ловят
 * напрямую — обработка централизована.
 *
 * IllegalArgumentException → 400 (валидация ввода) обрабатывается отдельно.
 */
class NotFoundException(message: String) : RuntimeException(message)
class ConflictException(message: String) : RuntimeException(message)
class ForbiddenException(message: String) : RuntimeException(message)
