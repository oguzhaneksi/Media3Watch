package com.media3watch.db

import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.hours

/**
 * Schedules a periodic data-retention cleanup job.
 *
 * Extracted from [com.media3watch.main] to give the module a single responsibility
 * (orchestration) and this class a single responsibility (retention scheduling).
 */
class RetentionScheduler(
    private val repository: SessionRepository,
    private val retentionDays: Int
) {
    private val logger = LoggerFactory.getLogger(RetentionScheduler::class.java)

    /**
     * Starts the retention job in [scope] and returns the resulting [Job].
     * The job runs once on startup and then every 24 hours thereafter.
     * Cancelling the returned job (or the parent scope) stops the scheduler.
     */
    fun start(scope: CoroutineScope): Job = scope.launch {
        while (isActive) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val deleted = repository.deleteExpiredSessions(retentionDays)
                    logger.info("Retention cleanup: deleted $deleted expired sessions (retentionDays=$retentionDays)")
                }.onFailure { e ->
                    logger.error("Retention cleanup failed", e)
                }
            }
            delay(24.hours)
        }
    }
}
