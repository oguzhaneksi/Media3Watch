package com.media3watch.domain

import kotlinx.serialization.Serializable

@Serializable
data class SessionSummary(
    val sessionId: String,
    val timestamp: Long,
    val schemaVersion: Int = 1,
    val contentId: String? = null,
    val streamType: String? = null,
    val playerStartupMs: Int? = null,
    val rebufferTimeMs: Int? = null,
    val rebufferCount: Int? = null,
    val errorCount: Int? = null,
    val payload: String? = null, // Raw JSON payload
)
