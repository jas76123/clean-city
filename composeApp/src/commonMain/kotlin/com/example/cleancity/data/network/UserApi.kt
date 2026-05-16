package com.example.cleancity.data.network

import com.example.cleancity.shared.models.UserResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

interface UserApiContract {
    suspend fun me(): UserResponse
}

class UserApi(private val client: HttpClient) : UserApiContract {
    override suspend fun me(): UserResponse =
        client.get("/users/me").body()
}
