package com.media3watch.sdk

import android.os.SystemClock
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(UnstableApi::class)
class Media3WatchAnalytics(
    private val config: Media3WatchConfig = Media3WatchConfig(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {

    private var player: ExoPlayer? = null
    private var sessionId: Long = 0L

    private var sessionStartTs: Long = 0L
    private var sessionStartWallClockMs: Long = 0L
    private var playCommandTs: Long? = null
    private var startupTimeMs: Long? = null

    private var firstFrameRendered: Boolean = false

    private val httpSender: HttpSender? = config.backendUrl?.let {
        HttpSender(it, config.apiKey)
    }

    private val playbackStatsListener = PlaybackStatsListener(false) { _, _ ->

    }

    private val analyticsListener = object : AnalyticsListener {
        override fun onRenderedFirstFrame(
            eventTime: AnalyticsListener.EventTime,
            output: Any,
            renderTimeMs: Long
        ) {
            if (firstFrameRendered) {
                return
            }

            firstFrameRendered = true

            val commandTs = playCommandTs
            if (commandTs != null) {
                // Startup = first frame render ts - playRequested ts.
                startupTimeMs = (renderTimeMs - commandTs).coerceAtLeast(0L)
                playCommandTs = null
            }
        }
    }

    fun attach(player: ExoPlayer) {
        if (this.player != null) {
            // Cleanup previous session on repeated attach.
            detach()
        }

        resetSession()
        sessionId += 1
        sessionStartTs = SystemClock.elapsedRealtime()
        sessionStartWallClockMs = System.currentTimeMillis()

        this.player = player

        player.addAnalyticsListener(analyticsListener)
        player.addAnalyticsListener(playbackStatsListener)

        Log.d(LogUtils.TAG, "session_start sessionId=$sessionId")
    }

    fun playRequested() {
        // Multiple calls overwrite pending startup measurement.
        playCommandTs = SystemClock.elapsedRealtime()
        startupTimeMs = null

        if (firstFrameRendered) {
            // First frame already happened, startup for this session is not measurable anymore.
            playCommandTs = null
        }
    }

    fun detach() {
        val activePlayer = player ?: return
        val now = SystemClock.elapsedRealtime()

        val sessionEndStats = playbackStatsListener.playbackStats

        activePlayer.removeAnalyticsListener(analyticsListener)
        activePlayer.removeAnalyticsListener(playbackStatsListener)
        player = null

        val summary = LogUtils.buildSessionSummary(
            sessionId = sessionId,
            sessionStartWallClockMs = sessionStartWallClockMs,
            sessionStartTs = sessionStartTs,
            now = now,
            startupTimeMs = startupTimeMs,
            sessionEndStats = sessionEndStats
        )

        Log.d(LogUtils.TAG, summary.toPrettyLog())

        if (httpSender != null) {
            scope.launch {
                httpSender.send(summary.toJson())
            }
        }

        resetSession()
    }

    fun release() {
        detach()
        scope.cancel()
    }

    private fun resetSession() {
        sessionStartTs = 0L
        sessionStartWallClockMs = 0L
        playCommandTs = null
        startupTimeMs = null
        firstFrameRendered = false
    }

}
