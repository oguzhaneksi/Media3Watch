package com.media3watch.sdk

import android.os.SystemClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Drives periodic and event-triggered session reporting.
 *
 * Does NOT own a coroutine scope. The caller-provided [coroutineScope] (rooted at the shared
 * analytics SupervisorJob) is used so that all SDK coroutines live in a single hierarchy.
 *
 * All coroutines are dispatched on [callbackDispatcher] (default: [Dispatchers.Main.immediate]),
 * so [onReport] and [isActiveCheck] are invoked on [callbackDispatcher] (default: Main.immediate).
 */
internal class SessionReporter(
    private val intervalMs: Long = 15_000L,
    private val minIntervalMs: Long = 1_000L,
    private val isActiveCheck: () -> Boolean,
    private val onReport: () -> Unit,
    private val nowMsProvider: () -> Long = { SystemClock.elapsedRealtime() },
    private val callbackDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val coroutineScope: CoroutineScope,
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
        coroutineScope.launch(callbackDispatcher) {
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
        reportingJob = coroutineScope.launch(callbackDispatcher) {
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
