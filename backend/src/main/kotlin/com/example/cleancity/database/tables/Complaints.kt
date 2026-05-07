package com.example.cleancity.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

object Complaints : Table("complaints") {
    val id = long("id").autoIncrement()
    val category = varchar("category", 30)
    val description = text("description")
    val photoPath = varchar("photo_path", 500)
    val latitude = double("latitude")
    val longitude = double("longitude")
    val address = varchar("address", 500)
    val deviceId = varchar("device_id", 100)
    val status = varchar("status", 20).default("NEW")
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}
