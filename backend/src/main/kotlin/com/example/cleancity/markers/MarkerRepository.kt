package com.example.cleancity.markers

import com.example.cleancity.database.tables.Complaints
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class ComplaintRow(
    val id: Long,
    val category: String,
    val description: String,
    val photoPath: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val deviceId: String,
    val status: String,
    val createdAt: OffsetDateTime
)

class MarkerRepository {

    fun createComplaint(
        category: String,
        description: String,
        photoPath: String,
        latitude: Double,
        longitude: Double,
        address: String,
        deviceId: String
    ): ComplaintRow = transaction {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val id = Complaints.insert {
            it[Complaints.category] = category
            it[Complaints.description] = description
            it[Complaints.photoPath] = photoPath
            it[Complaints.latitude] = latitude
            it[Complaints.longitude] = longitude
            it[Complaints.address] = address
            it[Complaints.deviceId] = deviceId
            it[Complaints.status] = "NEW"
            it[Complaints.createdAt] = now
        }[Complaints.id]

        ComplaintRow(id, category, description, photoPath, latitude, longitude, address, deviceId, "NEW", now)
    }

    fun getAllComplaints(): List<ComplaintRow> = transaction {
        Complaints.selectAll().map { it.toComplaintRow() }
    }

    fun getComplaintsInBounds(swLat: Double, swLon: Double, neLat: Double, neLon: Double): List<ComplaintRow> = transaction {
        Complaints.selectAll().where {
            (Complaints.latitude greaterEq swLat) and
            (Complaints.latitude lessEq neLat) and
            (Complaints.longitude greaterEq swLon) and
            (Complaints.longitude lessEq neLon)
        }.map { it.toComplaintRow() }
    }

    fun getComplaintById(id: Long): ComplaintRow? = transaction {
        Complaints.selectAll().where { Complaints.id eq id }.firstOrNull()?.toComplaintRow()
    }

    private fun ResultRow.toComplaintRow() = ComplaintRow(
        id = this[Complaints.id],
        category = this[Complaints.category],
        description = this[Complaints.description],
        photoPath = this[Complaints.photoPath],
        latitude = this[Complaints.latitude],
        longitude = this[Complaints.longitude],
        address = this[Complaints.address],
        deviceId = this[Complaints.deviceId],
        status = this[Complaints.status],
        createdAt = this[Complaints.createdAt]
    )
}
