package com.example.cleancity.shared.models

import kotlinx.serialization.Serializable

/**
 * Роли пользователей. Источник правды: SPEC.md § 3.3.
 *
 * В MVP OPERATOR/INSPECTOR имеют те же права что ADMIN
 * (поля сохранены для будущей фазы).
 *
 * Гости (без JWT) — read-only доступ к карте/ленте/объявлениям.
 */
@Serializable
enum class UserRole {
    RESIDENT,
    OPERATOR,
    INSPECTOR,
    ADMIN
}
