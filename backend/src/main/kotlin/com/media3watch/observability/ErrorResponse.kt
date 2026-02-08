package com.media3watch.observability

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val error: ErrorDetail
)

@Serializable
data class ErrorDetail(
    val code: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

object ErrorCodes {
    const val INVALID_API_KEY = "INVALID_API_KEY"
    const val RATE_LIMIT_EXCEEDED = "RATE_LIMIT_EXCEEDED"
    const val INVALID_SCHEMA = "INVALID_SCHEMA"
    const val DATABASE_ERROR = "DATABASE_ERROR"
    const val INTERNAL_ERROR = "INTERNAL_ERROR"
}

