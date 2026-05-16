package com.example.cleancity.data.network

import com.example.cleancity.shared.models.UserResponse

class FakeUserApi(var meResult: Result<UserResponse>? = null) {
    fun asUserApi(): UserApiContract = object : UserApiContract {
        override suspend fun me(): UserResponse = requireNotNull(meResult).getOrThrow()
    }
}
