package com.example.cleancity.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.time
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

object Subbotniks : Table("subbotniks") {
    val id = long("id").autoIncrement()
    val title = varchar("title", 300)
    val description = text("description")
    val photoPath = varchar("photo_path", 500).nullable()
    val latitude = double("latitude")
    val longitude = double("longitude")
    val address = varchar("address", 500)
    val eventDate = date("event_date")
    val eventTime = time("event_time")
    val deviceId = varchar("device_id", 100)
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}
