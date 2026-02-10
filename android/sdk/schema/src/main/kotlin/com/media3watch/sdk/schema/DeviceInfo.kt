package com.media3watch.sdk.schema

import kotlinx.serialization.Serializable

@Serializable
data class DeviceInfo(
    val model: String,
    val os: String,
    val osVersion: String
)
