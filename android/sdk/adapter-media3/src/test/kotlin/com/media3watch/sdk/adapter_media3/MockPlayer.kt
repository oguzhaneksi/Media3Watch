package com.media3watch.sdk.adapter_media3

import androidx.media3.common.AudioAttributes
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.Size

class MockPlayer : Player {
    private val listeners = mutableListOf<Player.Listener>()
    private var _playbackState: Int = Player.STATE_IDLE

    override val playbackState: Int
        get() = _playbackState

    override fun addListener(listener: Player.Listener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        listeners.remove(listener)
    }

    fun hasListener(listener: Player.Listener): Boolean = listeners.contains(listener)

    override fun getApplicationLooper() = throw UnsupportedOperationException()

    override fun play() = throw UnsupportedOperationException()

    override fun pause() = throw UnsupportedOperationException()

    override fun prepare() = throw UnsupportedOperationException()

    override fun stop() = throw UnsupportedOperationException()

    override fun release() = throw UnsupportedOperationException()

    override fun seekTo(positionMs: Long) = throw UnsupportedOperationException()

    override fun seekTo(
        mediaItemIndex: Int,
        positionMs: Long,
    ) = throw UnsupportedOperationException()

    override fun getPlaybackSuppressionReason() = throw UnsupportedOperationException()

    override fun getPlayerError(): PlaybackException? = null

    override fun setPlayWhenReady(playWhenReady: Boolean) = throw UnsupportedOperationException()

    override fun getPlayWhenReady() = false

    override fun setRepeatMode(repeatMode: Int) = throw UnsupportedOperationException()

    override fun getRepeatMode() = Player.REPEAT_MODE_OFF

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) = throw UnsupportedOperationException()

    override fun getShuffleModeEnabled() = false

    override fun isLoading() = false

    override fun seekToDefaultPosition() = throw UnsupportedOperationException()

    override fun seekToDefaultPosition(mediaItemIndex: Int) = throw UnsupportedOperationException()

    override fun getSeekBackIncrement() = 0L

    override fun seekBack() = throw UnsupportedOperationException()

    override fun getSeekForwardIncrement() = 0L

    override fun seekForward() = throw UnsupportedOperationException()

    override fun hasPrevious() = false

    override fun hasPreviousWindow() = false

    override fun hasPreviousMediaItem() = false

    override fun seekToPrevious() = throw UnsupportedOperationException()

    override fun seekToPreviousWindow() = throw UnsupportedOperationException()

    override fun seekToPreviousMediaItem() = throw UnsupportedOperationException()

    override fun getMaxSeekToPreviousPosition() = 0L

    override fun hasNext() = false

    override fun hasNextWindow() = false

    override fun hasNextMediaItem() = false

    override fun seekToNext() = throw UnsupportedOperationException()

    override fun seekToNextWindow() = throw UnsupportedOperationException()

    override fun seekToNextMediaItem() = throw UnsupportedOperationException()

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) =
        throw UnsupportedOperationException()

    override fun setPlaybackSpeed(speed: Float) = throw UnsupportedOperationException()

    override fun getPlaybackParameters() = PlaybackParameters.DEFAULT

    override fun getCurrentTimeline() = Timeline.EMPTY

    override fun getCurrentPeriodIndex() = 0

    override fun getCurrentWindowIndex() = 0

    override fun getCurrentMediaItemIndex() = 0

    override fun getNextWindowIndex() = 0

    override fun getNextMediaItemIndex() = 0

    override fun getPreviousWindowIndex() = 0

    override fun getPreviousMediaItemIndex() = 0

    override fun getCurrentMediaItem(): MediaItem? = null

    override fun getMediaItemCount() = 0

    override fun getMediaItemAt(index: Int): MediaItem = throw UnsupportedOperationException()

    override fun getDuration() = 0L

    override fun getCurrentPosition() = 0L

    override fun getBufferedPosition() = 0L

    override fun getBufferedPercentage() = 0

    override fun getTotalBufferedDuration() = 0L

    override fun getCurrentLiveOffset() = 0L

    override fun getContentDuration() = 0L

    override fun getContentPosition() = 0L

    override fun getContentBufferedPosition() = 0L

    override fun isPlayingAd() = false

    override fun getCurrentAdGroupIndex() = 0

    override fun getCurrentAdIndexInAdGroup() = 0

    override fun isPlaying() = false

    override fun getCurrentTracks() = Tracks.EMPTY

    override fun getTrackSelectionParameters() = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT

    override fun setTrackSelectionParameters(parameters: TrackSelectionParameters) =
        throw UnsupportedOperationException()

    override fun getMediaMetadata() = MediaMetadata.EMPTY

    override fun getPlaylistMetadata() = MediaMetadata.EMPTY

    override fun setPlaylistMetadata(mediaMetadata: MediaMetadata) = throw UnsupportedOperationException()

    override fun setMediaItem(mediaItem: MediaItem) = throw UnsupportedOperationException()

    override fun setMediaItem(
        mediaItem: MediaItem,
        startPositionMs: Long,
    ) = throw UnsupportedOperationException()

    override fun setMediaItem(
        mediaItem: MediaItem,
        resetPosition: Boolean,
    ) = throw UnsupportedOperationException()

    override fun setMediaItems(mediaItems: List<MediaItem>) = throw UnsupportedOperationException()

    override fun setMediaItems(
        mediaItems: List<MediaItem>,
        resetPosition: Boolean,
    ) = throw UnsupportedOperationException()

    override fun setMediaItems(
        mediaItems: List<MediaItem>,
        startWindowIndex: Int,
        startPositionMs: Long,
    ) = throw UnsupportedOperationException()

    override fun addMediaItem(mediaItem: MediaItem) = throw UnsupportedOperationException()

    override fun addMediaItem(
        index: Int,
        mediaItem: MediaItem,
    ) = throw UnsupportedOperationException()

    override fun addMediaItems(mediaItems: List<MediaItem>) = throw UnsupportedOperationException()

    override fun addMediaItems(
        index: Int,
        mediaItems: List<MediaItem>,
    ) = throw UnsupportedOperationException()

    override fun moveMediaItem(
        currentIndex: Int,
        newIndex: Int,
    ) = throw UnsupportedOperationException()

    override fun moveMediaItems(
        fromIndex: Int,
        toIndex: Int,
        newIndex: Int,
    ) = throw UnsupportedOperationException()

    override fun removeMediaItem(index: Int) = throw UnsupportedOperationException()

    override fun removeMediaItems(
        fromIndex: Int,
        toIndex: Int,
    ) = throw UnsupportedOperationException()

    override fun clearMediaItems() = throw UnsupportedOperationException()

    override fun replaceMediaItem(
        index: Int,
        mediaItem: MediaItem,
    ) = throw UnsupportedOperationException()

    override fun replaceMediaItems(
        fromIndex: Int,
        toIndex: Int,
        mediaItems: List<MediaItem>,
    ) = throw UnsupportedOperationException()

    override fun getVideoSize() = VideoSize.UNKNOWN

    override fun getSurfaceSize() = Size.UNKNOWN

    override fun clearVideoSurface() = throw UnsupportedOperationException()

    override fun clearVideoSurface(surface: android.view.Surface?) = throw UnsupportedOperationException()

    override fun setVideoSurface(surface: android.view.Surface?) = throw UnsupportedOperationException()

    override fun setVideoSurfaceHolder(surfaceHolder: android.view.SurfaceHolder?) =
        throw UnsupportedOperationException()

    override fun clearVideoSurfaceHolder(surfaceHolder: android.view.SurfaceHolder?) =
        throw UnsupportedOperationException()

    override fun setVideoSurfaceView(surfaceView: android.view.SurfaceView?) =
        throw UnsupportedOperationException()

    override fun clearVideoSurfaceView(surfaceView: android.view.SurfaceView?) =
        throw UnsupportedOperationException()

    override fun setVideoTextureView(textureView: android.view.TextureView?) =
        throw UnsupportedOperationException()

    override fun clearVideoTextureView(textureView: android.view.TextureView?) =
        throw UnsupportedOperationException()

    override fun getAudioAttributes() = AudioAttributes.DEFAULT

    override fun setVolume(volume: Float) = throw UnsupportedOperationException()

    override fun getVolume() = 1f

    override fun setDeviceVolume(volume: Int) = throw UnsupportedOperationException()

    override fun setDeviceVolume(
        volume: Int,
        flags: Int,
    ) = throw UnsupportedOperationException()

    override fun getDeviceVolume() = 0

    override fun isDeviceMuted() = false

    override fun setDeviceMuted(muted: Boolean) = throw UnsupportedOperationException()

    override fun setDeviceMuted(
        muted: Boolean,
        flags: Int,
    ) = throw UnsupportedOperationException()

    override fun increaseDeviceVolume() = throw UnsupportedOperationException()

    override fun increaseDeviceVolume(flags: Int) = throw UnsupportedOperationException()

    override fun decreaseDeviceVolume() = throw UnsupportedOperationException()

    override fun decreaseDeviceVolume(flags: Int) = throw UnsupportedOperationException()

    override fun getDeviceInfo() = DeviceInfo.UNKNOWN

    override fun getCurrentCues() = CueGroup.EMPTY_TIME_ZERO

    override fun getAvailableCommands() = Player.Commands.EMPTY
}
