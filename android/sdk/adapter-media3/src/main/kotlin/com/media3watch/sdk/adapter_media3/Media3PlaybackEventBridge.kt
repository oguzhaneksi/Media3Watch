package com.media3watch.sdk.adapter_media3

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.STATE_BUFFERING
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Player.STATE_IDLE
import androidx.media3.common.Player.STATE_READY
import androidx.media3.common.VideoSize
import com.media3watch.sdk.schema.ErrorCategory
import com.media3watch.sdk.schema.PlaybackEvent

class Media3PlaybackEventBridge(
    private val onEvent: (PlaybackEvent) -> Unit,
) : Player.Listener {
    private var player: Player? = null
    private var previousPlaybackState: Int = STATE_IDLE
    private var isBuffering = false
    private var bufferingStartTimestamp: Long? = null
    private var seekStartTimestamp: Long? = null

    fun attach(player: Player) {
        this.player = player
        this.previousPlaybackState = player.playbackState
        player.addListener(this)
    }

    fun detach() {
        player?.removeListener(this)
        player = null
        previousPlaybackState = STATE_IDLE
        isBuffering = false
        bufferingStartTimestamp = null
        seekStartTimestamp = null
    }

    override fun onPlayWhenReadyChanged(
        playWhenReady: Boolean,
        reason: Int,
    ) {
        onEvent(
            PlaybackEvent.PlayWhenReadyChanged(
                timestamp = System.currentTimeMillis(),
                playWhenReady = playWhenReady,
            ),
        )
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        onEvent(
            PlaybackEvent.IsPlayingChanged(
                timestamp = System.currentTimeMillis(),
                isPlaying = isPlaying,
            ),
        )
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        val timestamp = System.currentTimeMillis()

        onEvent(
            PlaybackEvent.PlaybackStateChanged(
                timestamp = timestamp,
                playbackState = playbackState,
            ),
        )

        when (playbackState) {
            STATE_BUFFERING -> {
                if (!isBuffering) {
                    isBuffering = true
                    bufferingStartTimestamp = timestamp
                    onEvent(
                        PlaybackEvent.BufferingStarted(
                            timestamp = timestamp,
                        ),
                    )
                }
            }
            STATE_READY -> {
                if (isBuffering && previousPlaybackState == STATE_BUFFERING) {
                    val bufferStart = bufferingStartTimestamp ?: timestamp
                    val durationMs = timestamp - bufferStart
                    isBuffering = false
                    bufferingStartTimestamp = null
                    onEvent(
                        PlaybackEvent.BufferingEnded(
                            timestamp = timestamp,
                            durationMs = durationMs,
                        ),
                    )
                }
            }
            STATE_ENDED -> {
                onEvent(
                    PlaybackEvent.PlaybackEnded(
                        timestamp = timestamp,
                    ),
                )
            }
        }

        previousPlaybackState = playbackState
    }

    override fun onRenderedFirstFrame() {
        onEvent(
            PlaybackEvent.FirstFrameRendered(
                timestamp = System.currentTimeMillis(),
            ),
        )
    }

    override fun onPlayerError(error: PlaybackException) {
        val timestamp = System.currentTimeMillis()
        val errorCategory = mapErrorCategory(error)

        onEvent(
            PlaybackEvent.PlayerError(
                timestamp = timestamp,
                errorCode = error.errorCode,
                errorCategory = errorCategory,
            ),
        )
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        val contentId = mediaItem?.mediaId
        onEvent(
            PlaybackEvent.MediaItemTransition(
                timestamp = System.currentTimeMillis(),
                newContentId = contentId,
            ),
        )
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
            val timestamp = System.currentTimeMillis()

            val seekStart = seekStartTimestamp ?: timestamp
            seekStartTimestamp = null

            onEvent(
                PlaybackEvent.SeekStarted(
                    timestamp = seekStart,
                ),
            )

            val durationMs = timestamp - seekStart
            onEvent(
                PlaybackEvent.SeekEnded(
                    timestamp = timestamp,
                    durationMs = durationMs.coerceAtLeast(0),
                ),
            )
        }
    }

    private fun mapErrorCategory(error: PlaybackException): ErrorCategory {
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
            PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
            -> ErrorCategory.NETWORK

            PlaybackException.ERROR_CODE_DRM_UNSPECIFIED,
            PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED,
            PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR,
            PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED,
            PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION,
            PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR,
            PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED,
            PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED,
            -> ErrorCategory.DRM

            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            -> ErrorCategory.SOURCE

            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            -> ErrorCategory.DECODER

            else -> ErrorCategory.UNKNOWN
        }
    }
}
