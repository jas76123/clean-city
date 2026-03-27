package com.example.cleancity.markers

import com.example.cleancity.database.tables.Complaints
import com.example.cleancity.database.tables.Subbotniks
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class ComplaintRow(
    val id: Long,
    val type: String,
    val description: String,
    val photoPath: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val deviceId: String,
    val status: String,
    val createdAt: OffsetDateTime
)

data class SubbotnikRow(
    val id: Long,
    val title: String,
    val description: String,
    val photoPath: String?,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val eventDate: String,
    val eventTime: String,
    val deviceId: String,
    val createdAt: OffsetDateTime
)

class MarkerRepository {

    fun createComplaint(
        type: String,
        description: String,
        photoPath: String,
        latitude: Double,
        longitude: Double,
        address: String,
        deviceId: String
    ): ComplaintRow = transaction {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val id = Complaints.insert {
            it[Complaints.type] = type
            it[Complaints.description] = description
            it[Complaints.photoPath] = photoPath
            it[Complaints.latitude] = latitude
            it[Complaints.longitude] = longitude
            it[Complaints.address] = address
            it[Complaints.deviceId] = deviceId
            it[Complaints.status] = "NEW"
            it[Complaints.createdAt] = now
        }[Complaints.id]

        ComplaintRow(id, type, description, photoPath, latitude, longitude, address, deviceId, "NEW", now)
    }

    fun createSubbotnik(
        title: String,
        description: String,
        photoPath: String?,
        latitude: Double,
        longitude: Double,
        address: String,
        eventDate: String,
        eventTime: String,
        deviceId: String
    ): SubbotnikRow = transaction {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val id = Subbotniks.insert {
            it[Subbotniks.title] = title
            it[Subbotniks.description] = description
            it[Subbotniks.photoPath] = photoPath
            it[Subbotniks.latitude] = latitude
            it[Subbotniks.longitude] = longitude
            it[Subbotniks.address] = address
            it[Subbotniks.eventDate] = LocalDate.parse(eventDate)
            it[Subbotniks.eventTime] = LocalTime.parse(eventTime)
            it[Subbotniks.deviceId] = deviceId
            it[Subbotniks.createdAt] = now
        }[Subbotniks.id]

        SubbotnikRow(id, title, description, photoPath, latitude, longitude, address, eventDate, eventTime, deviceId, now)
    }

    fun getAllComplaints(): List<ComplaintRow> = transaction {
        Complaints.selectAll().map { it.toComplaintRow() }
    }

    fun getAllSubbotniks(): List<SubbotnikRow> = transaction {
        Subbotniks.selectAll().map { it.toSubbotnikRow() }
    }

    fun getComplaintById(id: Long): ComplaintRow? = transaction {
        Complaints.selectAll().where { Complaints.id eq id }.firstOrNull()?.toComplaintRow()
    }

    fun getSubbotnikById(id: Long): SubbotnikRow? = transaction {
        Subbotniks.selectAll().where { Subbotniks.id eq id }.firstOrNull()?.toSubbotnikRow()
    }

    private fun ResultRow.toComplaintRow() = ComplaintRow(
        id = this[Complaints.id],
        type = this[Complaints.type],
        description = this[Complaints.description],
        photoPath = this[Complaints.photoPath],
        latitude = this[Complaints.latitude],
        longitude = this[Complaints.longitude],
        address = this[Complaints.address],
        deviceId = this[Complaints.deviceId],
        status = this[Complaints.status],
        createdAt = this[Complaints.createdAt]
    )

    private fun ResultRow.toSubbotnikRow() = SubbotnikRow(
        id = this[Subbotniks.id],
        title = this[Subbotniks.title],
        description = this[Subbotniks.description],
        photoPath = this[Subbotniks.photoPath],
        latitude = this[Subbotniks.latitude],
        longitude = this[Subbotniks.longitude],
        address = this[Subbotniks.address],
        eventDate = this[Subbotniks.eventDate].toString(),
        eventTime = this[Subbotniks.eventTime].toString(),
        deviceId = this[Subbotniks.deviceId],
        createdAt = this[Subbotniks.createdAt]
    )
}
