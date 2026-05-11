package com.example.cleancity.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

/**
 * История смены статусов жалобы. Источник правды: SPEC.md §3.4, §5.2.
 *
 * comment обязателен на уровне БД (SPEC §5.2): админ должен пояснить любую смену.
 * fromStatus может быть NULL только для записи о первом NEW при создании жалобы,
 * но в MVP такие записи не пишем — история начинается с первого действия админа.
 */
object StatusChanges : Table("status_changes") {
    val id = long("id").autoIncrement()
    val complaintId = long("complaint_id").references(Complaints.id)
    val fromStatus = varchar("from_status", 20).nullable()
    val toStatus = varchar("to_status", 20)
    val comment = text("comment")
    val changedById = long("changed_by_id").references(Users.id)
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}
