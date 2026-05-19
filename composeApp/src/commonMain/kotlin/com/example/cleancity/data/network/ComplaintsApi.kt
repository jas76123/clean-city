package com.example.cleancity.data.network

import com.example.cleancity.shared.models.ComplaintListResponse
import com.example.cleancity.shared.models.ComplaintResponse
import com.example.cleancity.shared.models.MapMarkersResponse
import com.example.cleancity.shared.models.ProblemCategory
import com.example.cleancity.shared.models.VoteResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post

interface ComplaintsApiContract {
    suspend fun getMapMarkers(
        swLat: Double,
        swLon: Double,
        neLat: Double,
        neLon: Double,
        category: ProblemCategory?,
    ): MapMarkersResponse

    suspend fun list(
        page: Int = 0,
        size: Int = 20,
        sort: String = "date",
        category: ProblemCategory? = null,
        district: String? = null,
    ): ComplaintListResponse

    suspend fun mine(page: Int = 0, size: Int = 20): ComplaintListResponse

    suspend fun voted(page: Int = 0, size: Int = 20): ComplaintListResponse

    suspend fun getById(id: Long): ComplaintResponse

    suspend fun vote(id: Long): VoteResponse

    suspend fun unvote(id: Long): VoteResponse
}

class ComplaintsApi(private val client: HttpClient) : ComplaintsApiContract {

    override suspend fun getMapMarkers(
        swLat: Double,
        swLon: Double,
        neLat: Double,
        neLon: Double,
        category: ProblemCategory?,
    ): MapMarkersResponse = client.get("/complaints/map") {
        parameter("swLat", swLat)
        parameter("swLon", swLon)
        parameter("neLat", neLat)
        parameter("neLon", neLon)
        category?.let { parameter("category", it.name) }
    }.body()

    override suspend fun list(
        page: Int,
        size: Int,
        sort: String,
        category: ProblemCategory?,
        district: String?,
    ): ComplaintListResponse = client.get("/complaints") {
        parameter("page", page)
        parameter("size", size)
        parameter("sort", sort)
        category?.let { parameter("category", it.name) }
        district?.takeIf { it.isNotBlank() }?.let { parameter("district", it) }
    }.body()

    override suspend fun mine(page: Int, size: Int): ComplaintListResponse =
        client.get("/complaints/mine") {
            parameter("page", page)
            parameter("size", size)
        }.body()

    override suspend fun voted(page: Int, size: Int): ComplaintListResponse =
        client.get("/complaints/voted") {
            parameter("page", page)
            parameter("size", size)
        }.body()

    override suspend fun getById(id: Long): ComplaintResponse =
        client.get("/complaints/$id").body()

    override suspend fun vote(id: Long): VoteResponse =
        client.post("/complaints/$id/votes").body()

    override suspend fun unvote(id: Long): VoteResponse =
        client.delete("/complaints/$id/votes").body()
}
