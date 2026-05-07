package com.example.cleancity.shared.models

import kotlinx.serialization.Serializable

/**
 * 5 статусов жалобы. Источник правды: SPEC.md § 3.2.
 *
 * Переходы:
 *   NEW → IN_PROGRESS → RESOLVED
 *   NEW/IN_PROGRESS → REJECTED (с обязательным комментарием админа)
 *   NEW/IN_PROGRESS → DUPLICATE (с обязательным duplicate_of_id)
 *
 * REJECTED и DUPLICATE — терминальные. Жителям не показываются на карте/в ленте,
 * но видны автору и проголосовавшим (с пояснением админа).
 */
@Serializable
enum class ComplaintStatus {
    NEW,
    IN_PROGRESS,
    RESOLVED,
    REJECTED,
    DUPLICATE
}
