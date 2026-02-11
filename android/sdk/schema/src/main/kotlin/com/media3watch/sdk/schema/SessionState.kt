package com.media3watch.sdk.schema

import kotlinx.serialization.Serializable

@Serializable
enum class SessionState {
    NO_SESSION,
    ATTACHED,
    PLAYING,
    PAUSED,
    BUFFERING,
    SEEKING,
    BACKGROUND,
    ENDED,
}
