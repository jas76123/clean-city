package com.example.cleancity.complaints

import com.example.cleancity.database.tables.ComplaintPhotos
import com.example.cleancity.database.tables.Complaints
import com.example.cleancity.database.tables.Users
import com.example.cleancity.shared.models.ComplaintStatus
import com.example.cleancity.shared.models.ProblemCategory
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class ComplaintRow(
    val id: Long,
    val authorId: Long,
    val authorName: String?,
    val category: ProblemCategory,
    val title: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val district: String?,
    val status: ComplaintStatus,
    val duplicateOfId: Long?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val resolvedAt: OffsetDateTime?
)

data class PhotoRow(
    val id: Long,
    val complaintId: Long,
    val storageKey: String,
    val photoUrl: String,
    val thumbUrl: String,
    val sortOrder: Int
)

data class MapMarkerRow(
    val id: Long,
    val category: ProblemCategory,
    val status: ComplaintStatus,
    val latitude: Double,
    val longitude: Double
)

data class NewPhoto(
    val storageKey: String,
    val photoUrl: String,
    val thumbUrl: String,
    val sortOrder: Int
)

enum class ComplaintSort { DATE, VOTES, PRIORITY }

data class ComplaintFilter(
    val visibleStatuses: Set<ComplaintStatus>,
    val category: ProblemCategory? = null,
    val district: String? = null,
    val sort: ComplaintSort = ComplaintSort.DATE,
    val page: Int = 0,
    val size: Int = 20,
    val authorId: Long? = null
)

class ComplaintRepository {

    fun create(
        authorId: Long,
        category: ProblemCategory,
        title: String,
        description: String,
        latitude: Double,
        longitude: Double,
        address: String,
        district: String?
    ): Long = transaction {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        Complaints.insert {
            it[Complaints.authorId] = authorId
            it[Complaints.category] = category.name
            it[Complaints.title] = title
            it[Complaints.description] = description
            it[Complaints.latitude] = latitude
            it[Complaints.longitude] = longitude
            it[Complaints.address] = address
            it[Complaints.district] = district
            it[Complaints.status] = ComplaintStatus.NEW.name
            it[Complaints.createdAt] = now
            it[Complaints.updatedAt] = now
        }[Complaints.id]
    }

    fun savePhotos(complaintId: Long, photos: List<NewPhoto>): List<PhotoRow> = transaction {
        if (photos.isEmpty()) return@transaction emptyList()
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        ComplaintPhotos.batchInsert(photos) { p ->
            this[ComplaintPhotos.complaintId] = complaintId
            this[ComplaintPhotos.storageKey] = p.storageKey
            this[ComplaintPhotos.photoUrl] = p.photoUrl
            this[ComplaintPhotos.thumbUrl] = p.thumbUrl
            this[ComplaintPhotos.sortOrder] = p.sortOrder
            this[ComplaintPhotos.createdAt] = now
        }.map { it.toPhotoRow() }
    }

    private val complaintsWithAuthor =
        Complaints.join(Users, JoinType.LEFT, onColumn = Complaints.authorId, otherColumn = Users.id)

    fun findById(id: Long): ComplaintRow? = transaction {
        complaintsWithAuthor
            .selectAll()
            .where { Complaints.id eq id }
            .firstOrNull()
            ?.toComplaintRow()
    }

    fun listPhotos(complaintId: Long): List<PhotoRow> = transaction {
        ComplaintPhotos
            .selectAll()
            .where { ComplaintPhotos.complaintId eq complaintId }
            .orderBy(ComplaintPhotos.sortOrder to SortOrder.ASC)
            .map { it.toPhotoRow() }
    }

    fun listPhotosForMany(complaintIds: List<Long>): Map<Long, List<PhotoRow>> = transaction {
        if (complaintIds.isEmpty()) return@transaction emptyMap()
        ComplaintPhotos
            .selectAll()
            .where { ComplaintPhotos.complaintId inList complaintIds }
            .orderBy(ComplaintPhotos.sortOrder to SortOrder.ASC)
            .map { it.toPhotoRow() }
            .groupBy { it.complaintId }
    }

    fun list(filter: ComplaintFilter): Pair<List<ComplaintRow>, Long> = transaction {
        val condition = buildCondition(filter)

        val total = complaintsWithAuthor
            .selectAll()
            .where { condition }
            .count()

        val sortColumns = when (filter.sort) {
            ComplaintSort.DATE -> arrayOf(Complaints.createdAt to SortOrder.DESC)
            // Голоса/приоритет появятся в Day 5 — пока сортируем по дате как fallback.
            ComplaintSort.VOTES, ComplaintSort.PRIORITY -> arrayOf(Complaints.createdAt to SortOrder.DESC)
        }

        val items = complaintsWithAuthor
            .selectAll()
            .where { condition }
            .orderBy(*sortColumns)
            .limit(filter.size).offset((filter.page.toLong() * filter.size))
            .map { it.toComplaintRow() }

        items to total
    }

    fun listMarkers(
        swLat: Double, swLon: Double,
        neLat: Double, neLon: Double,
        category: ProblemCategory?,
        visibleStatuses: Set<ComplaintStatus>
    ): List<MapMarkerRow> = transaction {
        Complaints.selectAll().where {
            var op: Op<Boolean> = Complaints.status inList visibleStatuses.map { it.name }
            op = op and (Complaints.latitude greaterEq swLat) and
                (Complaints.latitude lessEq neLat) and
                (Complaints.longitude greaterEq swLon) and
                (Complaints.longitude lessEq neLon)
            if (category != null) op = op and (Complaints.category eq category.name)
            op
        }.map {
            MapMarkerRow(
                id = it[Complaints.id],
                category = ProblemCategory.valueOf(it[Complaints.category]),
                status = ComplaintStatus.valueOf(it[Complaints.status]),
                latitude = it[Complaints.latitude],
                longitude = it[Complaints.longitude]
            )
        }
    }

    private fun buildCondition(filter: ComplaintFilter): Op<Boolean> = with(SqlExpressionBuilder) {
        var op: Op<Boolean> = Complaints.status inList filter.visibleStatuses.map { it.name }
        if (filter.category != null) op = op and (Complaints.category eq filter.category.name)
        if (filter.district != null) op = op and (Complaints.district eq filter.district)
        if (filter.authorId != null) op = op and (Complaints.authorId eq filter.authorId)
        op
    }

    private fun ResultRow.toComplaintRow(): ComplaintRow {
        val authorName = runCatching { this[Users.fullName] }.getOrNull()
        return ComplaintRow(
            id = this[Complaints.id],
            authorId = this[Complaints.authorId],
            authorName = authorName,
            category = ProblemCategory.valueOf(this[Complaints.category]),
            title = this[Complaints.title],
            description = this[Complaints.description],
            latitude = this[Complaints.latitude],
            longitude = this[Complaints.longitude],
            address = this[Complaints.address],
            district = this[Complaints.district],
            status = ComplaintStatus.valueOf(this[Complaints.status]),
            duplicateOfId = this[Complaints.duplicateOfId],
            createdAt = this[Complaints.createdAt],
            updatedAt = this[Complaints.updatedAt],
            resolvedAt = this[Complaints.resolvedAt]
        )
    }

    private fun ResultRow.toPhotoRow() = PhotoRow(
        id = this[ComplaintPhotos.id],
        complaintId = this[ComplaintPhotos.complaintId],
        storageKey = this[ComplaintPhotos.storageKey],
        photoUrl = this[ComplaintPhotos.photoUrl],
        thumbUrl = this[ComplaintPhotos.thumbUrl],
        sortOrder = this[ComplaintPhotos.sortOrder]
    )
}
