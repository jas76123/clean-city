package com.example.cleancity.shared.models

import kotlinx.serialization.Serializable

/**
 * Роли пользователей CleanCity.
 *
 * - RESIDENT — житель: создаёт жалобы, голосует.
 * - OPERATOR — сотрудник: обрабатывает жалобы, публикует объявления, видит команду в read-only.
 * - ADMIN — администратор: всё, что OPERATOR, плюс управление командой.
 */
@Serializable
enum class UserRole {
    RESIDENT,
    OPERATOR,
    ADMIN
}
