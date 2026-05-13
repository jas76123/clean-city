package com.example.cleancity.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

object Notifications : Table("notifications") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(Users.id)
    val kind = varchar("kind", 40)
    val title = varchar("title", 300)
    val body = text("body")
    val iconStyle = varchar("icon_style", 20).nullable()
    val complaintId = long("complaint_id").references(Complaints.id).nullable()
    val announcementId = long("announcement_id").nullable()
    val readAt = timestampWithTimeZone("read_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}
