package com.media3watch.sdk

import java.net.MalformedURLException
import java.net.URL

data class Media3WatchConfig(
    val backendUrl: String? = null,
    val apiKey: String? = null,
    val reportingIntervalMs: Long = 15_000L,
    val enableRealTimeReporting: Boolean = true,
    /**
     * Controls whether the SDK emits output to Logcat.
     *
     * Logcat is world-readable on non-rooted devices and can be captured by any app holding
     * READ_LOGS permission. Disable in production builds to prevent session metadata
     * (session IDs, timing data) from appearing in device logs.
     */
    val enableLogging: Boolean = true,
    /**
     * When true, failed telemetry uploads are persisted to a file-backed queue and retried
     * automatically on the next upload cycle or on the next [Media3WatchAnalytics.attach] call.
     * Uses only [java.io] + [AtomicFile] — no Room or WorkManager dependency.
     */
    val enableOfflineResilience: Boolean = true,
    /**
     * Maximum number of queued payloads kept on disk. When the limit is exceeded, the oldest
     * file is evicted (FIFO). Must be positive.
     */
    val maxQueuedPayloads: Int = 100,
) {

    init {
        if (backendUrl != null) {
            try {
                URL(backendUrl)
            } catch (e: MalformedURLException) {
                throw IllegalArgumentException("Invalid backendUrl: $backendUrl", e)
            }
        }
        
        if (reportingIntervalMs <= 0) {
            throw IllegalArgumentException("reportingIntervalMs must be positive, got: $reportingIntervalMs")
        }

        if (maxQueuedPayloads <= 0) {
            throw IllegalArgumentException("maxQueuedPayloads must be positive, got: $maxQueuedPayloads")
        }
    }
}
