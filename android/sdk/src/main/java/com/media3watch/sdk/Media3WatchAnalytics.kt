package com.media3watch.sdk

import android.os.SystemClock
import android.util.Log
import androidx.annotation.MainThread
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import java.util.UUID

@androidx.annotation.OptIn(UnstableApi::class)
class Media3WatchAnalytics(
    private val config: Media3WatchConfig = Media3WatchConfig()
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
            coroutineScope = analyticsScope,
            enableLogging = config.enableLogging
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

    @MainThread
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

        if (config.enableLogging) Log.d(LogUtils.TAG, "session_start sessionId=$sessionId")
        
        // Start real-time reporting if enabled and uploader is configured
        if (config.enableRealTimeReporting && uploader != null) {
            reporter = SessionReporter(
                intervalMs = config.reportingIntervalMs,
                isActiveCheck = ::isSessionActive,
                onReport = { buildAndUploadSummary() },
                nowMsProvider = { SystemClock.elapsedRealtime() },
                coroutineScope = analyticsScope
            )
            reporter?.start()
        }
    }

    @MainThread
    fun playRequested() {
        // Multiple calls overwrite pending startup measurement.
        playCommandTs = SystemClock.elapsedRealtime()
        startupTimeMs = null

        if (firstFrameRendered) {
            // First frame already happened, startup for this session is not measurable anymore.
            playCommandTs = null
        }
    }

    @MainThread
    fun detach() {
        player ?: return
        reporter?.stop()
        reporter = null

        buildAndUploadSummary(logSessionSummary = true)

        player?.removeAnalyticsListener(analyticsListener)
        player?.removeAnalyticsListener(playbackStatsListener)
        player = null
        
        resetSession()
    }

    @TestOnly
    internal fun release() {
        reporter?.stop()
        detach()
    }

    @MainThread
    private fun resetSession() {
        sessionId = ""
        sessionStartTs = 0L
        sessionStartWallClockMs = 0L
        playCommandTs = null
        startupTimeMs = null
        firstFrameRendered = false
    }

    // Called on Main; heavy work (summary building + JSON serialisation) is offloaded to Default.
    private fun buildAndUploadSummary(logSessionSummary: Boolean = false) {
        if (player == null) return
        val now = SystemClock.elapsedRealtime()
        val sessionDurationMs = (now - sessionStartTs).coerceAtLeast(0L)

        // Snapshot all Main-thread state before crossing dispatcher boundaries.
        val stats = playbackStatsListener.playbackStats
        val capturedSessionId = sessionId
        val capturedSessionStartWallClockMs = sessionStartWallClockMs
        val capturedSessionStartTs = sessionStartTs
        val capturedStartupTimeMs = startupTimeMs

        analyticsScope.launch(Dispatchers.Default) {
            val summary = LogUtils.buildSessionSummary(
                sessionId = capturedSessionId,
                sessionStartWallClockMs = capturedSessionStartWallClockMs,
                sessionStartTs = capturedSessionStartTs,
                now = now,
                startupTimeMs = capturedStartupTimeMs,
                sessionEndStats = stats
            )

            if (logSessionSummary && config.enableLogging)
                Log.d(LogUtils.TAG, summary.toPrettyLog())

            // Do not send report if sessionDurationMs <= 0
            if (sessionDurationMs <= 0) return@launch

            if (config.enableLogging) Log.d(
                LogUtils.TAG,
                "Uploading session summary (sessionId=$capturedSessionId, durationMs=$sessionDurationMs)"
            )

            // toJson() is CPU work — stays on Default; HttpSender.send() switches to IO.
            val payload = summary.toJson()
            uploader?.upload(sessionId = capturedSessionId, payload = payload)
        }
    }

    @MainThread
    private fun isSessionActive(): Boolean {
        val currentPlayer = player ?: return false
        return currentPlayer.isPlaying && currentPlayer.playbackState == Player.STATE_READY
    }
}
