package com.example.cleancity.votes

import com.example.cleancity.ConflictException
import com.example.cleancity.NotFoundException
import com.example.cleancity.complaints.ComplaintRepository
import com.example.cleancity.shared.models.ComplaintStatus
import com.example.cleancity.shared.models.VoteResponse

/**
 * Голосование. SPEC §5.3:
 *  - голосовать может любой аутентифицированный пользователь (резидент или админ);
 *  - один пользователь — один голос на жалобу;
 *  - терминальные статусы (REJECTED/DUPLICATE) → 409 на POST и DELETE;
 *  - автор автоматически получает голос при создании жалобы и не может его отозвать.
 */
class VoteService(
    private val votes: VoteRepository,
    private val complaints: ComplaintRepository
) {
    companion object {
        private val TERMINAL = setOf(ComplaintStatus.REJECTED, ComplaintStatus.DUPLICATE)
    }

    fun addVote(complaintId: Long, userId: Long): VoteResponse {
        val complaint = complaints.findById(complaintId)
            ?: throw NotFoundException("Жалоба не найдена")

        if (complaint.status in TERMINAL) {
            throw ConflictException("Голосование закрыто: жалоба ${complaint.status.ruLabel()}")
        }

        votes.upsert(complaintId, userId)

        return VoteResponse(
            votesCount = votes.countYes(complaintId),
            userVoted = true
        )
    }

    fun removeVote(complaintId: Long, userId: Long): VoteResponse {
        val complaint = complaints.findById(complaintId)
            ?: throw NotFoundException("Жалоба не найдена")

        if (complaint.status in TERMINAL) {
            throw ConflictException("Голосование закрыто: жалоба ${complaint.status.ruLabel()}")
        }

        if (complaint.authorId == userId) {
            throw ConflictException("Автор не может отозвать свой голос")
        }

        votes.delete(complaintId, userId)

        return VoteResponse(
            votesCount = votes.countYes(complaintId),
            userVoted = false
        )
    }
}

private fun ComplaintStatus.ruLabel(): String = when (this) {
    ComplaintStatus.NEW -> "в обработке"
    ComplaintStatus.IN_PROGRESS -> "в работе"
    ComplaintStatus.RESOLVED -> "решена"
    ComplaintStatus.REJECTED -> "отклонена"
    ComplaintStatus.DUPLICATE -> "дубликат"
}
