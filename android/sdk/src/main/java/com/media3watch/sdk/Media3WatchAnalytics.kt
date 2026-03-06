package com.media3watch.sdk

import android.content.Context
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.MainThread
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

@androidx.annotation.OptIn(UnstableApi::class)
class Media3WatchAnalytics(
    context: Context,
    private val config: Media3WatchConfig = Media3WatchConfig()
) {

    private val analyticsScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Application-scoped context to avoid Activity leaks.
    private val appContext = context.applicationContext

    private var player: ExoPlayer? = null
    private var sessionId: String = ""

    private var sessionStartTs: Long = 0L
    private var sessionStartWallClockMs: Long = 0L
    private var playCommandTs: Long? = null
    private var startupTimeMs: Long? = null
    private var reporter: SessionReporter? = null
    private val connectivityManager = NetworkConnectivityManager(appContext)

    private var firstFrameRendered: Boolean = false
    private val httpSender: HttpSender? = config.backendUrl?.let {
        HttpSender(endpointUrl = it, apiKey = config.apiKey)
    }

    private val fileQueue: FileQueue? = if (config.enableOfflineResilience && config.backendUrl != null) {
        FileQueue(dir = File(appContext.cacheDir, "media3watch_queue"))
    } else null

    private val uploader: TelemetryUploader? = httpSender?.let {
        TelemetryUploader(
            sender = httpSender,
            enableLogging = config.enableLogging,
            fileQueue = fileQueue,
            maxQueuedPayloads = config.maxQueuedPayloads,
        )
    }

    private var playbackStatsListener: PlaybackStatsListener? = null
    private val metricsObservers = CopyOnWriteArrayList<MetricsObserver>()

    private val analyticsListener = object : AnalyticsListener {
        override fun onRenderedFirstFrame(
            eventTime: AnalyticsListener.EventTime,
            output: Any,
            renderTimeMs: Long
        ) {
            if (firstFrameRendered) {
                notifyObservers()
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
            notifyObservers()
        }

        override fun onIsPlayingChanged(
            eventTime: AnalyticsListener.EventTime,
            isPlaying: Boolean
        ) {
            reporter?.reportNow()
            notifyObservers()
        }

        override fun onPlaybackStateChanged(
            eventTime: AnalyticsListener.EventTime,
            state: Int
        ) {
            reporter?.reportNow()
            notifyObservers()
        }

        override fun onPlayWhenReadyChanged(
            eventTime: AnalyticsListener.EventTime,
            playWhenReady: Boolean,
            reason: Int
        ) {
            reporter?.reportNow()
            notifyObservers()
        }

        override fun onVideoInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: androidx.media3.common.Format,
            decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?
        ) {
            reporter?.reportNow()
            notifyObservers()
        }

        override fun onPlayerError(
            eventTime: AnalyticsListener.EventTime,
            error: androidx.media3.common.PlaybackException
        ) {
            reporter?.reportNow()
            notifyObservers()
        }

        override fun onDroppedVideoFrames(
            eventTime: AnalyticsListener.EventTime,
            droppedFrames: Int,
            elapsedMs: Long
        ) {
            reporter?.reportNow()
            notifyObservers()
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
            notifyObservers()
        }
    }

    /**
     * Registers a [MetricsObserver] to receive playback metric callbacks.
     *
     * Must be called on the main thread.
     *
     * If a playback session is already active when this method is called, the observer will
     * immediately receive an [MetricsObserver.onSessionStarted] callback followed by an
     * [MetricsObserver.onSnapshotUpdated] callback with the current session snapshot, so it
     * can catch up with the current state.
     *
     * All observer callbacks are guaranteed to be invoked on the main thread.
     *
     * @param observer the observer to register.
     */
    @MainThread
    fun addMetricsObserver(observer: MetricsObserver) {
        if (!metricsObservers.contains(observer)) {
            metricsObservers.add(observer)
        }

        if (player != null && sessionId.isNotBlank()) {
            safeNotifySessionStarted(observer, sessionId)
            buildSnapshot()?.let { safeNotifySnapshotUpdated(observer, it) }
        }
    }

    /**
     * Unregisters a previously registered [MetricsObserver].
     *
     * Must be called on the main thread.
     *
     * @param observer the observer to unregister.
     */
    @MainThread
    fun removeMetricsObserver(observer: MetricsObserver) {
        metricsObservers.remove(observer)
    }

    @MainThread
    fun attach(player: ExoPlayer) {
        require(player.applicationLooper == Looper.getMainLooper()) {
            "Media3WatchAnalytics requires ExoPlayer.applicationLooper to be main looper."
        }

        if (this.player != null) {
            // Cleanup previous session on repeated attach.
            detach()
        }

        resetSession()

        val statsListener = PlaybackStatsListener(false) { _, _ -> }
        playbackStatsListener = statsListener

        sessionId = UUID.randomUUID().toString()
        sessionStartTs = SystemClock.elapsedRealtime()
        sessionStartWallClockMs = System.currentTimeMillis()

        this.player = player

        player.addAnalyticsListener(analyticsListener)
        player.addAnalyticsListener(statsListener)

        if (config.enableLogging) Log.d(LogUtils.TAG, "session_start sessionId=$sessionId")

        // Drain any payloads that survived from previous sessions.
        analyticsScope.launch(Dispatchers.IO) { uploader?.flushPending() }

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

        notifySessionStarted()
        notifyObservers()
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
        val currentPlayer = player
        val finalSnapshot = buildSnapshot()
        val finalSessionId = sessionId

        reporter?.stop()
        reporter = null

        if (currentPlayer != null) {
            buildAndUploadSummary(logSessionSummary = true)

            currentPlayer.removeAnalyticsListener(analyticsListener)
            playbackStatsListener?.let { currentPlayer.removeAnalyticsListener(it) }
        }

        if (finalSessionId.isNotBlank() && finalSnapshot != null) {
            notifySessionEnded(finalSessionId, finalSnapshot)
        }

        playbackStatsListener = null
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

    // Called on Main; heavy work (summary building + JSON serialization) is offloaded to Default.
    private fun buildAndUploadSummary(logSessionSummary: Boolean = false) {
        if (player == null) return
        val statsListener = playbackStatsListener ?: return
        val now = SystemClock.elapsedRealtime()
        val sessionDurationMs = (now - sessionStartTs).coerceAtLeast(0L)

        // Snapshot all mutable Main-thread state before crossing dispatcher boundaries.
        // Build.MODEL, Build.VERSION.SDK_INT and BuildConfig.SDK_VERSION are immutable
        // process-wide constants — read them inline on any thread without snapshotting.
        // resolveConnectionType() is called here on Main so it reflects the connection state
        // at the exact moment each report is sent rather than a stale session-start snapshot.
        val stats = statsListener.playbackStats
        val capturedSessionId = sessionId
        val capturedSessionStartWallClockMs = sessionStartWallClockMs
        val capturedSessionStartTs = sessionStartTs
        val capturedStartupTimeMs = startupTimeMs
        val capturedConnectionTypeNow = connectivityManager.resolveConnectionType()

        analyticsScope.launch(Dispatchers.Default) {
            val summary = LogUtils.buildSessionSummary(
                sessionId = capturedSessionId,
                sessionStartWallClockMs = capturedSessionStartWallClockMs,
                sessionStartTs = capturedSessionStartTs,
                now = now,
                startupTimeMs = capturedStartupTimeMs,
                sessionEndStats = stats,
                deviceModel = Build.MODEL,
                osVersion = Build.VERSION.SDK_INT,
                sdkVersion = BuildConfig.SDK_VERSION,
                connectionType = capturedConnectionTypeNow,
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

    @MainThread
    private fun notifySessionStarted() {
        if (sessionId.isBlank()) return
        metricsObservers.forEach { safeNotifySessionStarted(it, sessionId) }
    }

    @MainThread
    private fun notifySessionEnded(sessionId: String, finalSnapshot: SessionSnapshot) {
        metricsObservers.forEach { observer ->
            try {
                observer.onSessionEnded(sessionId, finalSnapshot)
            } catch (t: Throwable) {
                if (config.enableLogging) {
                    Log.w(LogUtils.TAG, "MetricsObserver.onSessionEnded failed", t)
                }
            }
        }
    }

    @MainThread
    private fun notifyObservers() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "notifyObservers must run on main thread." }
        if (metricsObservers.isEmpty()) return

        val snapshot = buildSnapshot() ?: return
        metricsObservers.forEach { observer ->
            safeNotifySnapshotUpdated(observer, snapshot)
        }
    }

    @MainThread
    private fun buildSnapshot(nowMs: Long = SystemClock.elapsedRealtime()): SessionSnapshot? {
        val currentPlayer = player ?: return null
        if (sessionId.isBlank() || sessionStartTs <= 0L) return null

        val stats = playbackStatsListener?.playbackStats
        val currentPosition = currentPlayer.currentPosition.takeIf { it != C.TIME_UNSET }

        return SessionSnapshot(
            sessionId = sessionId,
            elapsedSessionTimeMs = (nowMs - sessionStartTs).coerceAtLeast(0L),
            playbackState = toSessionPlaybackState(currentPlayer.playbackState, currentPlayer.isPlaying),
            isPlaying = currentPlayer.isPlaying,
            currentPositionMs = currentPosition,
            startupTimeMs = startupTimeMs,
            rebufferTimeMs = stats?.totalRebufferTimeMs ?: 0L,
            rebufferCount = stats?.totalRebufferCount ?: 0,
            playTimeMs = stats?.totalPlayTimeMs ?: 0L,
            rebufferRatio = stats?.rebufferTimeRatio ?: 0f,
            totalDroppedFrames = stats?.totalDroppedFrames ?: 0L,
            totalSeekCount = stats?.totalSeekCount ?: 0,
            totalSeekTimeMs = stats?.totalSeekTimeMs ?: 0L,
            meanVideoFormatBitrate = stats?.meanVideoFormatBitrate?.coerceAtLeast(0),
            currentBitrate = currentPlayer.videoFormat?.bitrate?.takeIf { it > 0 },
            errorCount = stats?.fatalErrorCount ?: 0
        )
    }

    private fun toSessionPlaybackState(playerState: Int, isPlaying: Boolean): SessionPlaybackState {
        return when (playerState) {
            Player.STATE_IDLE -> SessionPlaybackState.IDLE
            Player.STATE_BUFFERING -> SessionPlaybackState.BUFFERING
            Player.STATE_ENDED -> SessionPlaybackState.ENDED
            Player.STATE_READY -> if (isPlaying) SessionPlaybackState.PLAYING else SessionPlaybackState.PAUSED
            else -> SessionPlaybackState.IDLE
        }
    }

    private fun safeNotifySessionStarted(observer: MetricsObserver, sessionId: String) {
        try {
            observer.onSessionStarted(sessionId)
        } catch (t: Throwable) {
            if (config.enableLogging) {
                Log.w(LogUtils.TAG, "MetricsObserver.onSessionStarted failed", t)
            }
        }
    }

    private fun safeNotifySnapshotUpdated(observer: MetricsObserver, snapshot: SessionSnapshot) {
        try {
            observer.onSnapshotUpdated(snapshot)
        } catch (t: Throwable) {
            if (config.enableLogging) {
                Log.w(LogUtils.TAG, "MetricsObserver.onSnapshotUpdated failed", t)
            }
        }
    }

}
