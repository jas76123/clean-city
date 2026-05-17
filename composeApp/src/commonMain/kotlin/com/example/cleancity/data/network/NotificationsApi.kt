package com.example.cleancity.data.network

import com.example.cleancity.shared.models.UnreadCountResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

interface NotificationsApiContract {
    suspend fun unreadCount(): UnreadCountResponse
}

class NotificationsApi(private val client: HttpClient) : NotificationsApiContract {
    override suspend fun unreadCount(): UnreadCountResponse =
        client.get("/notifications/unread-count").body()
}
