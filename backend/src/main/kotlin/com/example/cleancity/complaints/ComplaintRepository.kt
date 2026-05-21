package com.example.cleancity.complaints

import com.example.cleancity.database.tables.ComplaintPhotos
import com.example.cleancity.database.tables.Complaints
import com.example.cleancity.database.tables.StatusChanges
import com.example.cleancity.database.tables.Users
import com.example.cleancity.database.tables.Votes
import com.example.cleancity.shared.models.ComplaintStatus
import com.example.cleancity.shared.models.ProblemCategory
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.sql.ResultSet
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

data class StatusChangeRow(
    val fromStatus: ComplaintStatus?,
    val toStatus: ComplaintStatus,
    val comment: String,
    val changedByName: String?,
    val createdAt: OffsetDateTime
)

data class DuplicateRow(
    val id: Long,
    val title: String,
    val category: ProblemCategory,
    val status: ComplaintStatus,
    val address: String,
    val votesCount: Int,
    val distanceMeters: Int,
    val createdAt: OffsetDateTime
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

        val query = complaintsWithAuthor
            .selectAll()
            .where { condition }

        when (filter.sort) {
            ComplaintSort.DATE -> query.orderBy(Complaints.createdAt to SortOrder.DESC)
            ComplaintSort.VOTES -> query.orderBy(VotesCountExpr to SortOrder.DESC, Complaints.createdAt to SortOrder.DESC)
            ComplaintSort.PRIORITY -> query.orderBy(PriorityScoreExpr to SortOrder.DESC, Complaints.createdAt to SortOrder.DESC)
        }

        val items = query
            .limit(filter.size).offset(filter.page.toLong() * filter.size)
            .map { it.toComplaintRow() }

        items to total
    }

    /**
     * Жалобы, за которые проголосовал пользователь («+1»), исключая его собственные.
     * Включает закрытые REJECTED/DUPLICATE — на экране «Поддержанные» юзер должен
     * увидеть пояснение админа от закрытых жалоб.
     */
    fun listVoted(
        userId: Long,
        page: Int,
        size: Int
    ): Pair<List<ComplaintRow>, Long> = transaction {
        val joined = Complaints
            .join(Users, JoinType.LEFT, onColumn = Complaints.authorId, otherColumn = Users.id)
            .join(Votes, JoinType.INNER, onColumn = Complaints.id, otherColumn = Votes.complaintId)

        val condition: Op<Boolean> = with(SqlExpressionBuilder) {
            (Votes.userId eq userId) and
                (Votes.value eq 1.toShort()) and
                (Complaints.authorId neq userId)
        }

        val total = joined.selectAll().where { condition }.count()

        val items = joined
            .selectAll()
            .where { condition }
            .orderBy(Votes.createdAt to SortOrder.DESC)
            .limit(size).offset(page.toLong() * size)
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

    /**
     * Поиск кандидатов в дубликаты через PostGIS ST_DWithin. Учитывает только активные
     * статусы (NEW/IN_PROGRESS/RESOLVED) той же категории в радиусе `radiusMeters`.
     */
    fun findDuplicates(
        latitude: Double,
        longitude: Double,
        category: ProblemCategory,
        radiusMeters: Int,
        limit: Int = 10
    ): List<DuplicateRow> = transaction {
        val sql = """
            SELECT c.id, c.title, c.category, c.status, c.address, c.created_at,
                   COALESCE((SELECT COUNT(*) FROM votes v WHERE v.complaint_id = c.id AND v.value = 1), 0) AS votes_count,
                   ST_Distance(
                       c.location,
                       ST_SetSRID(ST_MakePoint($longitude, $latitude), 4326)::geography
                   ) AS distance_m
            FROM complaints c
            WHERE c.category = '${category.name}'
              AND c.status IN ('NEW','IN_PROGRESS','RESOLVED')
              AND ST_DWithin(
                  c.location,
                  ST_SetSRID(ST_MakePoint($longitude, $latitude), 4326)::geography,
                  $radiusMeters
              )
            ORDER BY distance_m
            LIMIT $limit
        """.trimIndent()

        execAndMap(sql) { rs ->
            DuplicateRow(
                id = rs.getLong("id"),
                title = rs.getString("title"),
                category = ProblemCategory.valueOf(rs.getString("category")),
                status = ComplaintStatus.valueOf(rs.getString("status")),
                address = rs.getString("address"),
                votesCount = rs.getInt("votes_count"),
                distanceMeters = rs.getDouble("distance_m").toInt(),
                createdAt = rs.getObject("created_at", OffsetDateTime::class.java)
            )
        }
    }

    fun listStatusHistory(complaintId: Long): List<StatusChangeRow> = transaction {
        StatusChanges
            .join(Users, JoinType.LEFT, onColumn = StatusChanges.changedById, otherColumn = Users.id)
            .selectAll()
            .where { StatusChanges.complaintId eq complaintId }
            .orderBy(StatusChanges.createdAt to SortOrder.ASC)
            .map {
                StatusChangeRow(
                    fromStatus = it[StatusChanges.fromStatus]?.let(ComplaintStatus::valueOf),
                    toStatus = ComplaintStatus.valueOf(it[StatusChanges.toStatus]),
                    comment = it[StatusChanges.comment],
                    changedByName = runCatching { it[Users.fullName] }.getOrNull(),
                    createdAt = it[StatusChanges.createdAt]
                )
            }
    }

    /**
     * Изменить статус жалобы. Вызывать внутри транзакции, объединяющей
     * UPDATE + INSERT в status_changes + (опц.) merge голосов.
     */
    fun updateStatus(
        complaintId: Long,
        newStatus: ComplaintStatus,
        duplicateOfId: Long?,
        setResolvedNow: Boolean
    ) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        Complaints.update({ Complaints.id eq complaintId }) {
            it[Complaints.status] = newStatus.name
            it[Complaints.duplicateOfId] = duplicateOfId
            it[Complaints.updatedAt] = now
            if (setResolvedNow) {
                it[Complaints.resolvedAt] = now
            }
        }
    }

    fun insertStatusChange(
        complaintId: Long,
        fromStatus: ComplaintStatus?,
        toStatus: ComplaintStatus,
        comment: String,
        changedById: Long
    ) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        StatusChanges.insert {
            it[StatusChanges.complaintId] = complaintId
            it[StatusChanges.fromStatus] = fromStatus?.name
            it[StatusChanges.toStatus] = toStatus.name
            it[StatusChanges.comment] = comment
            it[StatusChanges.changedById] = changedById
            it[StatusChanges.createdAt] = now
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
        // Запрос жалоб делает LEFT JOIN Users, поэтому is_active доступен.
        // У удалённого (анонимизированного) автора скрываем имя.
        val authorActive = runCatching { this[Users.isActive] }.getOrNull()
        val authorName = if (authorActive == false) {
            "Удалённый пользователь"
        } else {
            runCatching { this[Users.fullName] }.getOrNull()
        }
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

    private fun <T> execAndMap(sql: String, map: (ResultSet) -> T): List<T> {
        val conn = TransactionManager.current().connection
        val stmt = conn.prepareStatement(sql, false)
        val rs = stmt.executeQuery()
        val list = mutableListOf<T>()
        try {
            while (rs.next()) list += map(rs)
        } finally {
            rs.close()
        }
        return list
    }
}

/**
 * Подсчёт «+1» голосов жалобы для сортировки. SQL-подзапрос на каждую строку —
 * для пилота (≤1000 жалоб) допустимо, после пилота кэшируем в Redis или
 * денормализованной колонке `votes_count` в complaints.
 */
private object VotesCountExpr : Expression<Long>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder.append(
            "(SELECT COUNT(*) FROM votes WHERE votes.complaint_id = complaints.id AND votes.value = 1)"
        )
    }
}

/**
 * Priority score по SPEC §3.5:
 *   (yes - no) * 1.0
 *   + (status='NEW' ? hours_since_created * 0.5 : 0)
 *   + (category IN (SAFETY, ECOLOGY) ? 100 : 0)
 *
 * «Часы возраста» считаются только пока NEW (буквально по SPEC): фактор срочности
 * перестаёт расти после того, как админ принял жалобу.
 */
private object PriorityScoreExpr : Expression<Double>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder.append(
            "(" +
                "COALESCE((SELECT SUM(value)::float8 FROM votes WHERE votes.complaint_id = complaints.id), 0) * 1.0 " +
                "+ CASE WHEN complaints.status = 'NEW' " +
                "       THEN EXTRACT(EPOCH FROM (NOW() - complaints.created_at)) / 3600.0 * 0.5 " +
                "       ELSE 0 END " +
                "+ CASE WHEN complaints.category IN ('SAFETY','ECOLOGY') THEN 100 ELSE 0 END" +
                ")"
        )
    }
}
