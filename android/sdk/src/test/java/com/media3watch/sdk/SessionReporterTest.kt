package com.media3watch.sdk

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.coroutines.ContinuationInterceptor

@OptIn(ExperimentalCoroutinesApi::class)
class SessionReporterTest {

    @Test
    fun periodicReport_firesOnlyWhenActive() = runTest {
        var reportCount = 0
        var isActive = true
        
        val reporter = createReporter(
            scope = this,
            isActiveCheck = { isActive },
            onReport = { reportCount++ }
        )

        reporter.start()
        runCurrent() // Allow start() coroutine to launch

        // 1. Should report when active
        advanceTimeBy(15_000)
        runCurrent()
        assertEquals(1, reportCount)

        // 2. Should NOT report when inactive
        isActive = false
        advanceTimeBy(15_000)
        runCurrent()
        assertEquals(1, reportCount) // Count remains same

        // 3. Should resume reporting when active again
        isActive = true
        advanceTimeBy(15_000)
        runCurrent()
        assertEquals(2, reportCount)
        
        reporter.stop()
    }

    @Test
    fun reportNow_triggersImmediateReport() = runTest {
        var reportCount = 0
        val reporter = createReporter(
            scope = this,
            onReport = { reportCount++ }
        )

        reporter.start()
        
        // Advance a bit, but not enough for periodic
        advanceTimeBy(5_000)
        assertEquals(0, reportCount)

        // Manual trigger
        reporter.reportNow()
        runCurrent()
        assertEquals(1, reportCount)
        
        reporter.stop()
    }

    @Test
    fun reportNow_throttlesRapidCalls() = runTest {
        var reportCount = 0
        val reporter = createReporter(
            scope = this,
            minIntervalMs = 1_000,
            onReport = { reportCount++ }
        )

        reporter.start()

        // First call goes through
        reporter.reportNow()
        runCurrent()
        assertEquals(1, reportCount)

        // Immediate subsequent calls should be throttled (within 1s)
        reporter.reportNow()
        reporter.reportNow()
        reporter.reportNow()
        runCurrent()
        assertEquals(1, reportCount)

        // After minInterval, it should accept again
        advanceTimeBy(1_001)
        reporter.reportNow()
        runCurrent()
        assertEquals(2, reportCount)
        
        reporter.stop()
    }

    @Test
    fun stop_cancelsPeriodicReporting() = runTest {
        var reportCount = 0
        val reporter = createReporter(
            scope = this,
            onReport = { reportCount++ }
        )

        reporter.start()
        advanceTimeBy(15_000)
        runCurrent()
        assertEquals(1, reportCount)

        reporter.stop()
        
        // Should not report after stop
        advanceTimeBy(30_000)
        assertEquals(1, reportCount)
    }

    @Test
    fun reportNow_resetsPeriodicTimer() = runTest {
        var reportCount = 0
        val reporter = createReporter(
            scope = this,
            intervalMs = 10_000,
            onReport = { reportCount++ }
        )

        reporter.start()

        // Trigger manual report at 9s (just before periodic 10s)
        advanceTimeBy(9_000)
        reporter.reportNow()
        runCurrent()
        assertEquals(1, reportCount)

        // The periodic timer should have reset. 
        // If it didn't reset, it would fire at 10s (1s later).
        advanceTimeBy(2_000) 
        // Total time 11s. If reset: next periodic is at 9+10=19s. 
        // If NOT reset: periodic would have fired at 10s.
        assertEquals(1, reportCount)

        // Advance to 19s (10s after manual trigger)
        advanceTimeBy(8_000)
        runCurrent()
        assertEquals(2, reportCount)
        
        reporter.stop()
    }

    @Test
    fun reportNow_afterStop_isNoOp() = runTest {
        var reportCount = 0
        val reporter = createReporter(
            scope = this,
            onReport = { reportCount++ }
        )

        reporter.start()
        reporter.stop()

        reporter.reportNow()
        runCurrent()
        assertEquals(0, reportCount)
    }

    private fun createReporter(
        scope: TestScope,
        intervalMs: Long = 15_000,
        minIntervalMs: Long = 1_000,
        isActiveCheck: () -> Boolean = { true },
        onReport: () -> Unit
    ): SessionReporter {
        return SessionReporter(
            intervalMs = intervalMs,
            minIntervalMs = minIntervalMs,
            isActiveCheck = isActiveCheck,
            onReport = onReport,
            callbackDispatcher = scope.coroutineContext[ContinuationInterceptor] as CoroutineDispatcher,
            nowMsProvider = { scope.testScheduler.currentTime + 1L },
            coroutineScope = scope
        )
    }
}
