package com.media3watch.sdk

import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

internal fun toSessionPlaybackState(playerState: Int, isPlaying: Boolean): SessionPlaybackState {
    return when (playerState) {
        Player.STATE_IDLE -> SessionPlaybackState.IDLE
        Player.STATE_BUFFERING -> SessionPlaybackState.BUFFERING
        Player.STATE_ENDED -> SessionPlaybackState.ENDED
        Player.STATE_READY -> if (isPlaying) SessionPlaybackState.PLAYING else SessionPlaybackState.PAUSED
        else -> SessionPlaybackState.IDLE
    }
}

internal val ExoPlayer.currentBitrate
    @OptIn(UnstableApi::class)
    get() = videoFormat?.bitrate?.takeIf { it > 0 }