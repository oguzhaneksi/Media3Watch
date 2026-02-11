package com.media3watch.sdk.adapter_media3

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.media3watch.sdk.schema.ErrorCategory
import com.media3watch.sdk.schema.PlaybackEvent

class Media3PlaybackEventBridge(
    private val onEvent: (PlaybackEvent) -> Unit,
) {
    private var player: Player? = null
    private var lastBufferingStartTs: Long? = null
    private var lastSeekStartTs: Long? = null
    private var lastPlaybackState: Int = Player.STATE_IDLE

    private val listener =
        object : Player.Listener {
            override fun onPlayWhenReadyChanged(
                playWhenReady: Boolean,
                reason: Int,
            ) {
                emitEvent(
                    PlaybackEvent.PlayWhenReadyChanged(
                        timestamp = System.currentTimeMillis(),
                        playWhenReady = playWhenReady,
                    ),
                )
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                emitEvent(
                    PlaybackEvent.IsPlayingChanged(
                        timestamp = System.currentTimeMillis(),
                        isPlaying = isPlaying,
                    ),
                )
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val timestamp = System.currentTimeMillis()

                if (playbackState == Player.STATE_BUFFERING && lastPlaybackState != Player.STATE_BUFFERING) {
                    lastBufferingStartTs = timestamp
                    emitEvent(PlaybackEvent.BufferingStarted(timestamp))
                }

                if (lastPlaybackState == Player.STATE_BUFFERING && playbackState == Player.STATE_READY) {
                    lastBufferingStartTs?.let { startTs ->
                        emitEvent(
                            PlaybackEvent.BufferingEnded(
                                timestamp = timestamp,
                                durationMs = timestamp - startTs,
                            ),
                        )
                    }
                    lastBufferingStartTs = null
                }

                emitEvent(
                    PlaybackEvent.PlaybackStateChanged(
                        timestamp = timestamp,
                        playbackState = playbackState,
                    ),
                )

                if (playbackState == Player.STATE_ENDED) {
                    emitEvent(PlaybackEvent.PlaybackEnded(timestamp))
                }

                lastPlaybackState = playbackState
            }

            override fun onRenderedFirstFrame() {
                emitEvent(
                    PlaybackEvent.FirstFrameRendered(
                        timestamp = System.currentTimeMillis(),
                    ),
                )
            }

            override fun onMediaItemTransition(
                mediaItem: androidx.media3.common.MediaItem?,
                reason: Int,
            ) {
                emitEvent(
                    PlaybackEvent.MediaItemTransition(
                        timestamp = System.currentTimeMillis(),
                        newContentId = mediaItem?.mediaId,
                    ),
                )
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK ||
                    reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
                ) {
                    val timestamp = System.currentTimeMillis()
                    lastSeekStartTs = timestamp
                    emitEvent(PlaybackEvent.SeekStarted(timestamp))
                    emitEvent(
                        PlaybackEvent.SeekEnded(
                            timestamp = timestamp,
                            durationMs = 0,
                        ),
                    )
                    lastSeekStartTs = null
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val errorCategory = mapErrorCategory(error)
                emitEvent(
                    PlaybackEvent.PlayerError(
                        timestamp = System.currentTimeMillis(),
                        errorCode = error.errorCode,
                        errorCategory = errorCategory,
                    ),
                )
            }
        }

    fun attach(player: Player) {
        require(player != null) { "Player cannot be null" }
        detach()
        this.player = player
        player.addListener(listener)
    }

    fun detach() {
        player?.removeListener(listener)
        player = null
        lastBufferingStartTs = null
        lastSeekStartTs = null
        lastPlaybackState = Player.STATE_IDLE
    }

    private fun emitEvent(event: PlaybackEvent) {
        if (player != null) {
            try {
                onEvent(event)
            } catch (e: Exception) {
                // Log but don't crash - bridge should remain operational
            }
        }
    }

    private fun mapErrorCategory(error: PlaybackException): ErrorCategory {
        return when {
            error.cause is java.io.IOException -> ErrorCategory.NETWORK
            error.errorCode == PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR ||
                error.errorCode == PlaybackException.ERROR_CODE_DRM_UNSPECIFIED ||
                error.errorCode == PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION ||
                error.errorCode == PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR ||
                error.errorCode == PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED ||
                error.errorCode == PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED -> ErrorCategory.DRM
            error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
                error.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ||
                error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                error.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> ErrorCategory.SOURCE
            error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ||
                error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> ErrorCategory.DECODER
            else -> ErrorCategory.UNKNOWN
        }
    }
}
