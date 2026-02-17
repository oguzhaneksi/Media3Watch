package com.media3watch.sdk

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class SessionReporter(
    private val intervalMs: Long = 15_000L,
    private val minIntervalMs: Long = 1_000L,
    private val isActiveCheck: () -> Boolean,
    private val onReport: () -> Unit,
    private val nowMsProvider: () -> Long = { SystemClock.elapsedRealtime() },
    // Injectable scope for testing, defaults to Dispatchers.Main + SupervisorJob
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) {
    private var reportingJob: Job? = null
    private var lastReportTimeMs: Long = 0L

    fun start() {
        stop() // Ensure no duplicate jobs
        startPeriodicReporting()
    }

    fun stop() {
        reportingJob?.cancel()
        reportingJob = null
    }

    /**
     * Triggers an immediate report if the minimum interval has elapsed.
     * This method is safe to call from any thread - it dispatches to the Main thread internally.
     * Note: This is a fire-and-forget operation. The method returns immediately while the 
     * report executes asynchronously on the Main dispatcher.
     */
    fun reportNow() {
        // Explicitly launch on Main dispatcher to ensure thread-safe access to mutable state
        coroutineScope.launch(Dispatchers.Main) {
            // If stopped, do not report
            if (reportingJob == null) return@launch

            val now = nowMsProvider()
            val timeSinceLastReport = now - lastReportTimeMs

            if (lastReportTimeMs <= 0L || timeSinceLastReport >= minIntervalMs) {
                triggerReport(now)
                // Restart periodic timer to avoid reporting immediately after a manual report
                startPeriodicReporting()
            }
        }
    }

    private fun startPeriodicReporting() {
        reportingJob?.cancel()
        reportingJob = coroutineScope.launch {
            while (isActive) {
                delay(intervalMs)
                if (isActiveCheck()) {
                    triggerReport(nowMsProvider())
                }
            }
        }
    }

    private fun triggerReport(now: Long) {
        lastReportTimeMs = now
        onReport()
    }
}
