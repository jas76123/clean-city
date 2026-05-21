package com.example.cleancity.data.network

import com.example.cleancity.shared.models.MarkAllReadResponse
import com.example.cleancity.shared.models.NotificationListResponse
import com.example.cleancity.shared.models.UnreadCountResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch

interface NotificationsApiContract {
    suspend fun unreadCount(): UnreadCountResponse
    suspend fun list(limit: Int = 50): NotificationListResponse
    suspend fun markRead(id: Long)
    suspend fun markAllRead(): MarkAllReadResponse
}

class NotificationsApi(private val client: HttpClient) : NotificationsApiContract {
    override suspend fun unreadCount(): UnreadCountResponse =
        client.get("/notifications/unread-count").body()

    override suspend fun list(limit: Int): NotificationListResponse =
        client.get("/notifications") {
            parameter("limit", limit)
        }.body()

    override suspend fun markRead(id: Long) {
        client.patch("/notifications/$id/read")
    }

    override suspend fun markAllRead(): MarkAllReadResponse =
        client.patch("/notifications/read-all").body()
}
