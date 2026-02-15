package com.media3watch.sample

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.media3watch.sdk.Media3WatchAnalytics
import com.media3watch.sdk.Media3WatchConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val analytics = Media3WatchAnalytics(
        config = Media3WatchConfig(
            backendUrl = "http://10.0.2.2:8080/",
            apiKey = "dev-key"
        )
    )

    var player: ExoPlayer? by mutableStateOf(null)
        private set

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState

    private var updateJob: Job? = null

    fun initializePlayer() {
        if (player != null) return

        player = ExoPlayer.Builder(getApplication()).build().apply {
            analytics.attach(this)
            val url = "https://download.blender.org/peach/bigbuckbunny_movies/BigBuckBunny_320x180.mp4"
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }

        analytics.playRequested()

        updateJob = viewModelScope.launch {
            while (isActive) {
                player?.let { p ->
                    _uiState.update { current ->
                        current.copy(
                            positionMs = if (current.isScrubbing) current.positionMs else p.currentPosition,
                            durationMs = if (p.duration == C.TIME_UNSET) 0L else p.duration,
                            bufferedPositionMs = p.bufferedPosition,
                            isPlaying = p.isPlaying,
                        )
                    }
                }
                delay(500)
            }
        }
    }

    fun releasePlayer() {
        updateJob?.cancel()
        updateJob = null
        analytics.detach()
        player?.release()
        player = null
    }

    fun seekBack10s() {
        player?.let {
            val target = (it.currentPosition - 10_000L).coerceAtLeast(0L)
            it.seekTo(target)
        }
    }

    fun togglePlayPause() {
        player?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun seekForward10s() {
        player?.let {
            val durationMs = _uiState.value.durationMs
            val max = if (durationMs > 0L) durationMs else Long.MAX_VALUE
            val target = (it.currentPosition + 10_000L).coerceAtMost(max)
            it.seekTo(target)
        }
    }

    fun onScrubPositionChanged(newPositionMs: Long) {
        _uiState.update {
            it.copy(
                isScrubbing = true,
                scrubPositionMs = newPositionMs,
            )
        }
    }

    fun onScrubFinished() {
        val state = _uiState.value
        if (state.durationMs > 0L) {
            player?.seekTo(state.scrubPositionMs)
        }
        _uiState.update { it.copy(isScrubbing = false) }
    }

    override fun onCleared() {
        releasePlayer()
        super.onCleared()
    }
}
