package com.example.cleancity.data.network

import com.example.cleancity.shared.models.UserResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

class UserApi(private val client: HttpClient) {
    suspend fun me(): UserResponse =
        client.get("/users/me").body()
}
