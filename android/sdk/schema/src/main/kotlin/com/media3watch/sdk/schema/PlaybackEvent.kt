package com.media3watch.sdk.schema

sealed class PlaybackEvent(open val timestamp: Long) {
    data class PlayRequested(override val timestamp: Long) : PlaybackEvent(timestamp)
    
    data class FirstFrameRendered(override val timestamp: Long) : PlaybackEvent(timestamp)
    
    data class BufferingStarted(override val timestamp: Long) : PlaybackEvent(timestamp)
    
    data class BufferingEnded(
        override val timestamp: Long,
        val durationMs: Long
    ) : PlaybackEvent(timestamp)
    
    data class IsPlayingChanged(
        override val timestamp: Long,
        val isPlaying: Boolean
    ) : PlaybackEvent(timestamp)
    
    data class SeekStarted(override val timestamp: Long) : PlaybackEvent(timestamp)
    
    data class SeekEnded(
        override val timestamp: Long,
        val durationMs: Long
    ) : PlaybackEvent(timestamp)
    
    data class PlayerError(
        override val timestamp: Long,
        val errorCode: Int,
        val errorCategory: ErrorCategory
    ) : PlaybackEvent(timestamp)
    
    data class MediaItemTransition(
        override val timestamp: Long,
        val newContentId: String?
    ) : PlaybackEvent(timestamp)
    
    data class AppBackgrounded(override val timestamp: Long) : PlaybackEvent(timestamp)
    
    data class AppForegrounded(override val timestamp: Long) : PlaybackEvent(timestamp)
    
    data class PlayerReleased(override val timestamp: Long) : PlaybackEvent(timestamp)
    
    data class PlaybackEnded(override val timestamp: Long) : PlaybackEvent(timestamp)
    
    data class BackgroundIdleTimeout(override val timestamp: Long) : PlaybackEvent(timestamp)
}
