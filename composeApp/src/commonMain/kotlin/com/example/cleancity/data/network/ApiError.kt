package com.example.cleancity.data.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ApiError(
    val code: String,
    val message: String,
    val details: JsonObject? = null,
)

class ApiException(
    val error: ApiError,
    val httpStatus: Int,
) : RuntimeException(error.message)
