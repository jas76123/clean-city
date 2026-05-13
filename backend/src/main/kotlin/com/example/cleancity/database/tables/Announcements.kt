package com.example.cleancity.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

object Announcements : Table("announcements") {
    val id = long("id").autoIncrement()
    val title = varchar("title", 300)
    val body = text("body")
    val iconStyle = varchar("icon_style", 20)
    val category = varchar("category", 30).nullable()
    val districts = text("districts")
    val authorId = long("author_id").references(Users.id)
    val publishedAt = timestampWithTimeZone("published_at")
    val expiresAt = timestampWithTimeZone("expires_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
