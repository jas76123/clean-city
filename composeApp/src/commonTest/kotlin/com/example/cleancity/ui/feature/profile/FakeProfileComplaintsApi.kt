package com.example.cleancity.ui.feature.profile

import com.example.cleancity.data.network.ComplaintsApiContract
import com.example.cleancity.domain.photo.PhotoBytes
import com.example.cleancity.shared.models.ComplaintListResponse
import com.example.cleancity.shared.models.ComplaintResponse
import com.example.cleancity.shared.models.DuplicateCandidatesResponse
import com.example.cleancity.shared.models.MapMarkersResponse
import com.example.cleancity.shared.models.ProblemCategory
import com.example.cleancity.shared.models.VoteResponse
import com.example.cleancity.shared.requests.CreateComplaintRequest

/** Заглушка: ProfileScreenModelTest проверяет только deleteAccount, load() не вызывается. */
class FakeProfileComplaintsApi : ComplaintsApiContract {
    override suspend fun mine(page: Int, size: Int): ComplaintListResponse = error("not used")
    override suspend fun voted(page: Int, size: Int): ComplaintListResponse = error("not used")
    override suspend fun getMapMarkers(
        swLat: Double, swLon: Double, neLat: Double, neLon: Double,
        category: ProblemCategory?,
    ): MapMarkersResponse = error("not used")
    override suspend fun list(
        page: Int, size: Int, sort: String,
        category: ProblemCategory?, district: String?,
    ): ComplaintListResponse = error("not used")
    override suspend fun getById(id: Long): ComplaintResponse = error("not used")
    override suspend fun vote(id: Long): VoteResponse = error("not used")
    override suspend fun unvote(id: Long): VoteResponse = error("not used")
    override suspend fun findDuplicates(
        latitude: Double, longitude: Double, category: ProblemCategory, radiusMeters: Int?,
    ): DuplicateCandidatesResponse = error("not used")
    override suspend fun create(
        request: CreateComplaintRequest, photos: List<PhotoBytes>,
    ): ComplaintResponse = error("not used")
}
