package com.media3watch.sdk

import android.os.SystemClock
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jetbrains.annotations.TestOnly
import java.util.UUID

@androidx.annotation.OptIn(UnstableApi::class)
class Media3WatchAnalytics(
    private val config: Media3WatchConfig = Media3WatchConfig(),
) {

    private val analyticsScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var player: ExoPlayer? = null
    private var sessionId: String = ""

    private var sessionStartTs: Long = 0L
    private var sessionStartWallClockMs: Long = 0L
    private var playCommandTs: Long? = null
    private var startupTimeMs: Long? = null
    private var reporter: SessionReporter? = null

    private var firstFrameRendered: Boolean = false
    private val httpSender: HttpSender? = config.backendUrl?.let {
        HttpSender(endpointUrl = it, apiKey = config.apiKey)
    }

    private val uploader: TelemetryUploader? = httpSender?.let {
        TelemetryUploader(
            sender = httpSender,
            coroutineScope = analyticsScope
        )
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
            
            reporter?.reportNow()
        }
        
        override fun onIsPlayingChanged(
            eventTime: AnalyticsListener.EventTime,
            isPlaying: Boolean
        ) {
            reporter?.reportNow()
        }
        
        override fun onPlaybackStateChanged(
            eventTime: AnalyticsListener.EventTime,
            state: Int
        ) {
            reporter?.reportNow()
        }
        
        override fun onPlayWhenReadyChanged(
            eventTime: AnalyticsListener.EventTime,
            playWhenReady: Boolean,
            reason: Int
        ) {
            reporter?.reportNow()
        }
        
        override fun onVideoInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: androidx.media3.common.Format,
            decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?
        ) {
            reporter?.reportNow()
        }
        
        override fun onPlayerError(
            eventTime: AnalyticsListener.EventTime,
            error: androidx.media3.common.PlaybackException
        ) {
            reporter?.reportNow()
        }
        
        override fun onDroppedVideoFrames(
            eventTime: AnalyticsListener.EventTime,
            droppedFrames: Int,
            elapsedMs: Long
        ) {
            reporter?.reportNow()
        }
        
        override fun onPositionDiscontinuity(
            eventTime: AnalyticsListener.EventTime,
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                reporter?.reportNow()
            }
        }
    }

    fun attach(player: ExoPlayer) {
        if (this.player != null) {
            // Cleanup previous session on repeated attach.
            detach()
        }

        resetSession()
        sessionId = UUID.randomUUID().toString()
        sessionStartTs = SystemClock.elapsedRealtime()
        sessionStartWallClockMs = System.currentTimeMillis()

        this.player = player

        player.addAnalyticsListener(analyticsListener)
        player.addAnalyticsListener(playbackStatsListener)

        Log.d(LogUtils.TAG, "session_start sessionId=$sessionId")
        
        // Start real-time reporting if enabled
        if (config.enableRealTimeReporting) {
            reporter = SessionReporter(
                intervalMs = config.reportingIntervalMs,
                isActiveCheck = { 
                    this.player?.let { p ->
                        p.isPlaying && p.playbackState == Player.STATE_READY
                    } ?: false
                },
                onReport = { buildAndUploadSummary() },
                nowMsProvider = { SystemClock.elapsedRealtime() },
                coroutineScope = analyticsScope
            )
            reporter?.start()
        }
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
        reporter?.stop()
        reporter = null
        
        val activePlayer = player ?: return
        val currentSessionId = sessionId

        activePlayer.removeAnalyticsListener(analyticsListener)
        activePlayer.removeAnalyticsListener(playbackStatsListener)
        player = null

        // Always build summary for logging
        val now = SystemClock.elapsedRealtime()
        val summary = LogUtils.buildSessionSummary(
            sessionId = currentSessionId,
            sessionStartWallClockMs = sessionStartWallClockMs,
            sessionStartTs = sessionStartTs,
            now = now,
            startupTimeMs = startupTimeMs,
            sessionEndStats = playbackStatsListener.playbackStats
        )
        Log.d(LogUtils.TAG, summary.toPrettyLog())
        
        // Only upload if sessionDurationMs > 0
        val sessionDurationMs = (now - sessionStartTs).coerceAtLeast(0L)
        if (uploader != null && sessionDurationMs > 0) {
            val payload = summary.toJson()
            uploader.upload(
                sessionId = currentSessionId,
                payload = payload
            )
        }
        
        resetSession()
    }

    @TestOnly
    internal fun release() {
        reporter?.stop()
        detach()
        uploader?.shutdown()
    }

    private fun resetSession() {
        sessionId = ""
        sessionStartTs = 0L
        sessionStartWallClockMs = 0L
        playCommandTs = null
        startupTimeMs = null
        firstFrameRendered = false
    }
    
    private fun buildAndUploadSummary() {
        player ?: return
        val now = SystemClock.elapsedRealtime()
        val sessionDurationMs = (now - sessionStartTs).coerceAtLeast(0L)

        // Do not send report if sessionDurationMs <= 0
        if (sessionDurationMs <= 0) return
        
        val stats = playbackStatsListener.playbackStats
        val summary = LogUtils.buildSessionSummary(
            sessionId = sessionId,
            sessionStartWallClockMs = sessionStartWallClockMs,
            sessionStartTs = sessionStartTs,
            now = now,
            startupTimeMs = startupTimeMs,
            sessionEndStats = stats
        )
        Log.d(
            LogUtils.TAG,
            "Uploading session summary (sessionId=$sessionId, durationMs=$sessionDurationMs)"
        )
        
        uploader?.upload(sessionId = sessionId, payload = summary.toJson())
    }
}
