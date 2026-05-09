package com.example.cleancity.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

object ComplaintPhotos : Table("complaint_photos") {
    val id = long("id").autoIncrement()
    val complaintId = long("complaint_id").references(Complaints.id)
    val storageKey = varchar("storage_key", 500)
    val photoUrl = varchar("photo_url", 500)
    val thumbUrl = varchar("thumb_url", 500)
    val sortOrder = integer("sort_order").default(0)
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}
