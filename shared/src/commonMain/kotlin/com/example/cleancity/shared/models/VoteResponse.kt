package com.example.cleancity.shared.models

import kotlinx.serialization.Serializable

/**
 * Ответ на POST/DELETE /complaints/{id}/votes — текущее состояние голоса
 * для оптимистичного UI.
 */
@Serializable
data class VoteResponse(
    val votesCount: Int,
    val userVoted: Boolean
)
