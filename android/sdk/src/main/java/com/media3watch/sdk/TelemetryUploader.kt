package com.media3watch.sdk

import android.util.Log
import androidx.annotation.OptIn
import androidx.annotation.VisibleForTesting
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

/**
 * Uploads telemetry payloads via [HttpSender], with optional offline resilience through
 * [fileQueue].
 *
 * ## Store-and-forward behavior (when [fileQueue] is non-null)
 * - **Upload success**: removes any stale queued entry for that session, then attempts to flush
 *   all other pending entries from previous sessions.
 * - **Upload failure**: upserts the payload to [fileQueue] (session-keyed — one file per
 *   session). The natural retry is the next [upload] call driven by [SessionReporter]'s periodic
 *   cycle.
 * - **On [flushPending]**: drains queued entries from previous sessions (called on `attach()`).
 *
 * ## When [fileQueue] is null
 * Behaves exactly as before — fire-and-forget.
 *
 * Does NOT own a coroutine scope. The caller-provided [coroutineScope] (rooted at the shared
 * analytics SupervisorJob) is used so that all SDK coroutines live in a single hierarchy.
 */
@OptIn(UnstableApi::class)
internal class TelemetryUploader(
    private val sender: HttpSender,
    private val uploadTimeoutMs: Long = 15_000,
    private val coroutineScope: CoroutineScope,
    private val enableLogging: Boolean = true,
    private val fileQueue: FileQueue? = null,
    private val maxQueuedPayloads: Int = 100,
) {

    private val flushMutex = Mutex()

    suspend fun upload(sessionId: String, payload: String) {
        // Run the entire upload sequence under NonCancellable so that cancellation cannot leave
        // the queue in an inconsistent state (e.g. a stale entry after a successful send, or
        // enqueue without trimToMaxSize after a failure).
        withContext(NonCancellable) {
            val result = trySend(sessionId, payload)

            if (result.isSuccess) {
                fileQueue?.remove(sessionId)
                flushPending(exclude = sessionId)
            } else {
                fileQueue?.let { queue ->
                    val enqueueResult = queue.enqueue(sessionId, payload)
                    if (enqueueResult.isFailure && enableLogging) {
                        Log.w(LogUtils.TAG, "offline_queue persist failed sessionId=$sessionId", enqueueResult.exceptionOrNull())
                    }
                    queue.trimToMaxSize(maxQueuedPayloads)
                }
            }
        }
    }

    /**
     * Drains all entries in [fileQueue] from previous sessions.
     * Intended to be called from [Media3WatchAnalytics.attach] to flush payloads that survived a
     * process restart.
     */
    suspend fun flushPending() {
        flushPending(exclude = null)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    @OptIn(UnstableApi::class)
    private suspend fun trySend(sessionId: String, payload: String): Result<Unit> {
        return try {
            val result = sender.send(payload, callTimeoutMs = uploadTimeoutMs)
            result
                .onSuccess {
                    if (enableLogging) Log.d(LogUtils.TAG, "session_report_success sessionId=$sessionId")
                }
                .onFailure {
                    if (enableLogging) {
                        if (it is SocketTimeoutException || it is InterruptedIOException) {
                            Log.w(LogUtils.TAG, "session_report_failed sessionId=$sessionId (timeout)", it)
                        } else {
                            Log.w(LogUtils.TAG, "session_report_failed sessionId=$sessionId", it)
                        }
                    }
                }
            result
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            if (enableLogging) Log.w(LogUtils.TAG, "session_report_failed sessionId=$sessionId (exception)", t)
            Result.failure(t)
        }
    }

    private suspend fun flushPending(exclude: String?) {
        val queue = fileQueue ?: return
        // Quick pre-check to avoid acquiring the mutex when there's nothing to flush.
        val pending = queue.peekAll().filter { it.sessionId != exclude }
        if (pending.isEmpty()) return
        flushMutex.withLock {
            // Re-read inside the lock: another coroutine may have flushed while we were waiting.
            val currentPending = queue.peekAll().filter { it.sessionId != exclude }
            if (currentPending.isEmpty()) return
            if (enableLogging) Log.d(LogUtils.TAG, "offline_queue flush ${currentPending.size} pending payload(s)")

            // Flush on IO — each send is independent; failures are left in queue for the next cycle.
            withContext(Dispatchers.IO) {
                for (entry in currentPending) {
                    val result = trySend(entry.sessionId, entry.payload)
                    if (result.isSuccess) {
                        queue.remove(entry.sessionId)
                        if (enableLogging) Log.d(LogUtils.TAG, "offline_queue flushed sessionId=${entry.sessionId}")
                    }
                }
            }
        }
    }

    // Exposed for testing only.
    @VisibleForTesting
    internal fun launchFlushPending() {
        coroutineScope.launch { flushPending() }
    }
}
