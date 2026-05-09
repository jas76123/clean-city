package com.example.cleancity.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

/**
 * Жалоба. Источник правды: SPEC.md §3.4.
 * Поле `location` (PostGIS GEOGRAPHY) пересчитывается триггером
 * `complaints_set_location` из `latitude`/`longitude` — в Exposed
 * мы его не маппим, читаем только координаты.
 */
object Complaints : Table("complaints") {
    val id = long("id").autoIncrement()
    val authorId = long("author_id").references(Users.id)
    val category = varchar("category", 30)
    val title = varchar("title", 300)
    val description = text("description")
    val latitude = double("latitude")
    val longitude = double("longitude")
    val address = varchar("address", 500)
    val district = varchar("district", 100).nullable()
    val status = varchar("status", 20).default("NEW")
    val duplicateOfId = long("duplicate_of_id").references(id).nullable()
    val assignedAdminId = long("assigned_admin_id").references(Users.id).nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
    val resolvedAt = timestampWithTimeZone("resolved_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
