package com.example.cleancity.announcements

import com.example.cleancity.database.tables.Announcements
import com.example.cleancity.database.tables.Users
import com.example.cleancity.shared.models.IconStyle
import com.example.cleancity.shared.models.ProblemCategory
import com.example.cleancity.shared.models.UserRole
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class AnnouncementRow(
    val id: Long,
    val title: String,
    val body: String,
    val iconStyle: IconStyle,
    val category: ProblemCategory?,
    val districts: List<String>,
    val authorId: Long,
    val publishedAt: OffsetDateTime,
    val expiresAt: OffsetDateTime?
)

const val DISTRICTS_ALL = "ALL"

class AnnouncementRepository {

    fun insert(
        title: String,
        body: String,
        iconStyle: IconStyle,
        category: ProblemCategory?,
        districts: List<String>,
        authorId: Long,
        expiresAt: OffsetDateTime?
    ): Long {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        return Announcements.insert {
            it[Announcements.title] = title
            it[Announcements.body] = body
            it[Announcements.iconStyle] = iconStyle.name
            it[Announcements.category] = category?.name
            it[Announcements.districts] = districts.toStored()
            it[Announcements.authorId] = authorId
            it[Announcements.publishedAt] = now
            it[Announcements.expiresAt] = expiresAt
        }[Announcements.id]
    }

    fun findById(id: Long): AnnouncementRow? = transaction {
        Announcements.selectAll().where { Announcements.id eq id }
            .singleOrNull()?.toRow()
    }

    fun listActive(district: String?, limit: Int): Pair<List<AnnouncementRow>, Long> = transaction {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val districtFilter = if (district.isNullOrBlank()) null else district

        val rows = Announcements.selectAll().where {
            (Announcements.publishedAt lessEq now) and
                (Announcements.expiresAt.isNull() or (Announcements.expiresAt greater now))
        }
            .orderBy(Announcements.publishedAt to SortOrder.DESC)
            .map { it.toRow() }
            .let { all ->
                if (districtFilter == null) all
                else all.filter { row ->
                    row.districts.contains(DISTRICTS_ALL) ||
                        row.districts.any { it.equals(districtFilter, ignoreCase = true) }
                }
            }
        rows.take(limit) to rows.size.toLong()
    }

    fun update(
        id: Long,
        title: String?,
        body: String?,
        iconStyle: IconStyle?,
        category: ProblemCategory?,
        districts: List<String>?,
        expiresAt: OffsetDateTime?,
        clearExpiresAt: Boolean
    ): Boolean = transaction {
        val updated = Announcements.update({ Announcements.id eq id }) {
            if (title != null) it[Announcements.title] = title
            if (body != null) it[Announcements.body] = body
            if (iconStyle != null) it[Announcements.iconStyle] = iconStyle.name
            if (category != null) it[Announcements.category] = category.name
            if (districts != null) it[Announcements.districts] = districts.toStored()
            when {
                clearExpiresAt -> it[Announcements.expiresAt] = null
                expiresAt != null -> it[Announcements.expiresAt] = expiresAt
            }
        }
        updated > 0
    }

    /**
     * Soft delete: expires_at = NOW(). Уведомления у получателей остаются.
     */
    fun expire(id: Long): Boolean = transaction {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val updated = Announcements.update({
            (Announcements.id eq id) and
                ((Announcements.expiresAt.isNull()) or (Announcements.expiresAt greater now))
        }) {
            it[Announcements.expiresAt] = now
        }
        updated > 0
    }

    /**
     * Получатели push'а для рассылки: активные верифицированные резиденты в
     * указанных районах. 'ALL' (или пустой список) → все.
     * Вызывать ВНУТРИ открытой транзакции (использует тот же контекст, что и notify).
     */
    fun recipientIdsForDistricts(districts: List<String>): List<Long> {
        val allDistricts = districts.isEmpty() || districts.contains(DISTRICTS_ALL)
        val rows = Users.selectAll().where {
            (Users.role eq UserRole.RESIDENT.name) and
                (Users.emailVerified eq true) and
                (Users.isActive eq true)
        }
        val filtered = if (allDistricts) {
            rows
        } else {
            val want = districts.map { it.trim() }.filter { it.isNotBlank() }.toSet()
            rows.filter { row ->
                val ud = row[Users.district]
                ud != null && want.any { it.equals(ud, ignoreCase = true) }
            }
        }
        return filtered.map { it[Users.id] }
    }

    private fun List<String>.toStored(): String =
        if (isEmpty() || contains(DISTRICTS_ALL)) DISTRICTS_ALL
        else joinToString(",") { it.trim() }.ifBlank { DISTRICTS_ALL }

    private fun ResultRow.toRow() = AnnouncementRow(
        id = this[Announcements.id],
        title = this[Announcements.title],
        body = this[Announcements.body],
        iconStyle = IconStyle.valueOf(this[Announcements.iconStyle]),
        category = this[Announcements.category]?.let { runCatching { ProblemCategory.valueOf(it) }.getOrNull() },
        districts = parseDistricts(this[Announcements.districts]),
        authorId = this[Announcements.authorId],
        publishedAt = this[Announcements.publishedAt],
        expiresAt = this[Announcements.expiresAt]
    )

    companion object {
        fun parseDistricts(stored: String): List<String> =
            if (stored == DISTRICTS_ALL) listOf(DISTRICTS_ALL)
            else stored.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
}
