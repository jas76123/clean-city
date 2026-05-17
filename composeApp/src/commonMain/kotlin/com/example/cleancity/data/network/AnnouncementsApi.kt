package com.example.cleancity.data.network

import com.example.cleancity.shared.models.AnnouncementsListResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

interface AnnouncementsApiContract {
    suspend fun list(limit: Int = 5, district: String? = null): AnnouncementsListResponse
}

class AnnouncementsApi(private val client: HttpClient) : AnnouncementsApiContract {
    override suspend fun list(limit: Int, district: String?): AnnouncementsListResponse =
        client.get("/announcements") {
            parameter("limit", limit)
            district?.takeIf { it.isNotBlank() }?.let { parameter("district", it) }
        }.body()
}
