package com.example.cleancity.shared.responses.admin

import kotlinx.serialization.Serializable

@Serializable
data class AuditEntryDto(
    val id: Long,
    val timestamp: String,       // ISO-8601 с offset (audit_log.created_at)
    val actorEmail: String?,     // null для системных действий
    val action: String,          // AuditAction.name
    val targetType: String?,     // например "user"
    val targetId: String?,
    val ip: String?,
    val details: String?         // audit_log.metadata (свободный текст)
)

@Serializable
data class AuditLogResponse(val items: List<AuditEntryDto>)
