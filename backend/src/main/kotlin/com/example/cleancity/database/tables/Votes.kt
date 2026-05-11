package com.example.cleancity.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

/**
 * Голос за жалобу. Источник правды: SPEC.md §3.4, §5.3.
 *
 * Один пользователь — один голос на жалобу (UNIQUE constraint).
 * В MVP value всегда +1, поле оставлено под будущую совместимость («не проблема»).
 * Автор жалобы автоматически получает свой голос при создании.
 */
object Votes : Table("votes") {
    val id = long("id").autoIncrement()
    val complaintId = long("complaint_id").references(Complaints.id)
    val userId = long("user_id").references(Users.id)
    val value = short("value").default(1)
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(complaintId, userId)
    }
}
