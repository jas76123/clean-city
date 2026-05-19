package com.example.cleancity.ui.feature.detail

import com.example.cleancity.data.network.ComplaintsApiContract
import com.example.cleancity.shared.models.ComplaintListResponse
import com.example.cleancity.shared.models.ComplaintResponse
import com.example.cleancity.shared.models.MapMarkersResponse
import com.example.cleancity.shared.models.ProblemCategory
import com.example.cleancity.shared.models.VoteResponse
import kotlinx.coroutines.CompletableDeferred

class FakeDetailComplaintsApi : ComplaintsApiContract {
    var nextGetByIdResult: Result<ComplaintResponse>? = null
    var nextVoteResult: Result<VoteResponse>? = null
    var nextUnvoteResult: Result<VoteResponse>? = null

    /** Gate для удержания vote/unvote до [release] — позволяет проверить промежуточный isVoting=true. */
    var voteGate: CompletableDeferred<Unit>? = null

    val getByIdCalls = mutableListOf<Long>()
    val voteCalls = mutableListOf<Long>()
    val unvoteCalls = mutableListOf<Long>()

    override suspend fun getById(id: Long): ComplaintResponse {
        getByIdCalls += id
        return requireNotNull(nextGetByIdResult) { "nextGetByIdResult not set" }.getOrThrow()
    }

    override suspend fun vote(id: Long): VoteResponse {
        voteCalls += id
        voteGate?.await()
        return requireNotNull(nextVoteResult) { "nextVoteResult not set" }.getOrThrow()
    }

    override suspend fun unvote(id: Long): VoteResponse {
        unvoteCalls += id
        voteGate?.await()
        return requireNotNull(nextUnvoteResult) { "nextUnvoteResult not set" }.getOrThrow()
    }

    override suspend fun getMapMarkers(
        swLat: Double, swLon: Double, neLat: Double, neLon: Double,
        category: ProblemCategory?,
    ): MapMarkersResponse = error("not used in detail tests")

    override suspend fun list(
        page: Int, size: Int, sort: String,
        category: ProblemCategory?, district: String?,
    ): ComplaintListResponse = error("not used in detail tests")

    override suspend fun mine(page: Int, size: Int): ComplaintListResponse =
        error("not used in detail tests")

    override suspend fun voted(page: Int, size: Int): ComplaintListResponse =
        error("not used in detail tests")

    override suspend fun create(
        request: com.example.cleancity.shared.requests.CreateComplaintRequest,
        photos: List<com.example.cleancity.domain.photo.PhotoBytes>,
    ): ComplaintResponse = error("not used in detail tests")

    override suspend fun findDuplicates(
        latitude: Double, longitude: Double,
        category: ProblemCategory, radiusMeters: Int?,
    ): com.example.cleancity.shared.models.DuplicateCandidatesResponse =
        error("not used in detail tests")
}
