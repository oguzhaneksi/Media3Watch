package com.media3watch.sdk

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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

    fun reportNow() {
        // If stopped, do not report
        if (reportingJob == null) return

        val now = nowMsProvider()
        val timeSinceLastReport = now - lastReportTimeMs

        if (lastReportTimeMs <= 0L || timeSinceLastReport >= minIntervalMs) {
            triggerReport(now)
            // Restart periodic timer to avoid reporting immediately after a manual report
            startPeriodicReporting()
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
