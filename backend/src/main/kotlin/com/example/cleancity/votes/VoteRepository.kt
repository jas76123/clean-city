package com.example.cleancity.votes

import com.example.cleancity.database.tables.Votes
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Голоса как сабресурс жалобы. Все методы открывают свою транзакцию;
 * `insertAuthorVote` рассчитан на вызов изнутри уже открытой транзакции
 * создания жалобы.
 */
class VoteRepository {

    /**
     * Идемпотентный вход голоса. Возвращает true, если запись была реально вставлена
     * (новый голос), false — если уже была (повторный POST).
     */
    fun upsert(complaintId: Long, userId: Long): Boolean = transaction {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val result = Votes.insertIgnore {
            it[Votes.complaintId] = complaintId
            it[Votes.userId] = userId
            it[Votes.value] = 1
            it[Votes.createdAt] = now
        }
        result.insertedCount > 0
    }

    /**
     * Идемпотентное удаление. Возвращает true, если запись была реально удалена,
     * false — если голоса не было (повторный DELETE).
     */
    fun delete(complaintId: Long, userId: Long): Boolean = transaction {
        Votes.deleteWhere {
            (Votes.complaintId eq complaintId) and (Votes.userId eq userId)
        } > 0
    }

    fun userVoted(complaintId: Long, userId: Long): Boolean = transaction {
        Votes
            .selectAll()
            .where { (Votes.complaintId eq complaintId) and (Votes.userId eq userId) }
            .limit(1)
            .any()
    }

    fun countYes(complaintId: Long): Int = transaction {
        Votes
            .selectAll()
            .where { (Votes.complaintId eq complaintId) and (Votes.value eq 1.toShort()) }
            .count()
            .toInt()
    }

    /**
     * Для пуш-уведомлений при REJECTED/DUPLICATE: список user_id, проголосовавших «+1».
     * Автор обычно входит в этот список через автоголос — фильтрацию делает вызывающий код.
     */
    fun listSupporterIds(complaintId: Long): List<Long> = transaction {
        Votes
            .selectAll()
            .where { (Votes.complaintId eq complaintId) and (Votes.value eq 1.toShort()) }
            .map { it[Votes.userId] }
    }

    /**
     * Слияние голосов при DUPLICATE: переносим всех голосовавших с дубликата на оригинал.
     * Конфликты (пользователь уже голосовал за оригинал) — пропускаются.
     */
    fun mergeVotesInto(originalId: Long, duplicateId: Long): Unit = transaction {
        exec(
            """
            INSERT INTO votes (complaint_id, user_id, value, created_at)
            SELECT $originalId, user_id, value, NOW()
            FROM votes WHERE complaint_id = $duplicateId
            ON CONFLICT (complaint_id, user_id) DO NOTHING
            """.trimIndent()
        )
    }

    /**
     * Создать автоголос автора при создании жалобы. Должна вызываться
     * в той же транзакции, что и INSERT complaints.
     */
    fun insertAuthorVote(complaintId: Long, authorId: Long) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        Votes.insertIgnore {
            it[Votes.complaintId] = complaintId
            it[Votes.userId] = authorId
            it[Votes.value] = 1
            it[Votes.createdAt] = now
        }
    }

    /**
     * Для списков: подсчёт «+1» голосов для пачки жалоб одним запросом.
     */
    fun countYesForMany(complaintIds: List<Long>): Map<Long, Int> = transaction {
        if (complaintIds.isEmpty()) return@transaction emptyMap()
        Votes
            .selectAll()
            .where { (Votes.complaintId inList complaintIds) and (Votes.value eq 1.toShort()) }
            .groupBy { it[Votes.complaintId] }
            .mapValues { it.value.size }
    }

    /**
     * Для списков с авторизованным юзером: множество complaint_id, за которые он голосовал.
     */
    fun votedComplaintIds(complaintIds: List<Long>, userId: Long): Set<Long> = transaction {
        if (complaintIds.isEmpty()) return@transaction emptySet()
        Votes
            .selectAll()
            .where {
                (Votes.complaintId inList complaintIds) and
                    (Votes.userId eq userId) and
                    (Votes.value eq 1.toShort())
            }
            .map { it[Votes.complaintId] }
            .toSet()
    }
}
