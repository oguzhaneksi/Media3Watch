package com.media3watch.sdk.schema

import kotlinx.serialization.Serializable

@Serializable
data class AppInfo(
    val name: String,
    val version: String
)
