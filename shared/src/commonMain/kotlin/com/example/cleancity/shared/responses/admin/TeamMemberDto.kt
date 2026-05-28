package com.example.cleancity.shared.responses.admin

import kotlinx.serialization.Serializable

@Serializable
data class TeamMemberDto(
    val id: Long,
    val email: String,
    val fullName: String?,
    val role: String,            // "ADMIN" | "OPERATOR"
    val status: TeamStatus,
    val createdAt: String,       // ISO-8601 с offset
    val lastLoginAt: String?,    // null для pending
    val invitedAt: String?       // createdAt для pending, иначе null
)

@Serializable
data class TeamMembersResponse(val items: List<TeamMemberDto>)
