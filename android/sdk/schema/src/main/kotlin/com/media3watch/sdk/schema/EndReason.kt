package com.media3watch.sdk.schema

import kotlinx.serialization.Serializable

@Serializable
enum class EndReason {
    PLAYER_RELEASED,
    PLAYBACK_ENDED,
    PLAYER_REPLACED,
    CONTENT_SWITCH,
    BACKGROUND_IDLE_TIMEOUT,
}
