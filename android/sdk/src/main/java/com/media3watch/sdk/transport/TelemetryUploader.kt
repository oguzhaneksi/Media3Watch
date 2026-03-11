package com.media3watch.sdk.transport

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.media3watch.sdk.util.LogUtils
import com.media3watch.sdk.model.SendResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
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
 * - **Upload retryable failure** (5xx, network error, timeout): upserts the payload to
 *   [fileQueue] (session-keyed — one file per session). The natural retry is the next [upload]
 *   call driven by [com.media3watch.sdk.collector.SessionReporter]'s periodic cycle.
 * - **Upload non-retryable failure** (4xx client errors, invalid API key, bad URL): the payload
 *   is **not** queued, because retrying with the same data will never succeed. Any previously
 *   queued entry for the same session (from an earlier retryable failure) is also removed.
 * - **On [flushPending]**: drains queued entries from previous sessions (called on `attach()`).
 *   If a queued entry receives a non-retryable response, it is removed from the queue.
 *
 * ## When [fileQueue] is null
 * Behaves exactly as before — fire-and-forget.
 *
 * Does NOT own a coroutine scope. All public methods are suspend functions and must be called
 * from an appropriate coroutine context provided by the caller.
 */
@OptIn(UnstableApi::class)
internal class TelemetryUploader(
    private val sender: HttpSender,
    private val uploadTimeoutMs: Long = 15_000,
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
            when (val result = trySend(sessionId, payload)) {
                is SendResult.Success -> {
                    fileQueue?.remove(sessionId)
                    flushPending(exclude = sessionId)
                }

                is SendResult.RetryableFailure -> {
                    fileQueue?.let { queue ->
                        val enqueueResult = queue.enqueue(sessionId, payload)
                        if (enqueueResult.isFailure && enableLogging) {
                            Log.w(
                                LogUtils.TAG,
                                "offline_queue persist failed sessionId=$sessionId",
                                enqueueResult.exceptionOrNull()
                            )
                        }
                        queue.trimToMaxSize(maxQueuedPayloads)
                    }
                }

                is SendResult.NonRetryableFailure -> {
                    // Do NOT queue — retrying a client error (4xx, bad config) will never succeed.
                    // Also remove any previously-queued entry for this session (from an earlier
                    // retryable failure) so that known-doomed payloads are not retained on disk.
                    fileQueue?.remove(sessionId)
                    if (enableLogging) {
                        Log.w(
                            LogUtils.TAG,
                            "session_report_dropped sessionId=$sessionId (non-retryable, not queued)",
                            result.cause
                        )
                    }
                }
            }
        }
    }

    /**
     * Drains all entries in [fileQueue] from previous sessions.
     * Intended to be called from [com.media3watch.sdk.Media3WatchAnalytics.attach] to flush payloads that survived a
     * process restart.
     */
    suspend fun flushPending() {
        flushPending(exclude = null)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    @OptIn(UnstableApi::class)
    private suspend fun trySend(sessionId: String, payload: String): SendResult {
        return try {
            val result = sender.send(payload, callTimeoutMs = uploadTimeoutMs)
            when (result) {
                is SendResult.Success -> {
                    if (enableLogging) Log.d(LogUtils.TAG, "session_report_success sessionId=$sessionId")
                }
                is SendResult.RetryableFailure -> {
                    if (enableLogging) {
                        val cause = result.cause
                        if (cause is SocketTimeoutException || cause is InterruptedIOException) {
                            Log.w(LogUtils.TAG, "session_report_failed sessionId=$sessionId (timeout)", cause)
                        } else {
                            Log.w(LogUtils.TAG, "session_report_failed sessionId=$sessionId", cause)
                        }
                    }
                }
                is SendResult.NonRetryableFailure -> {
                    if (enableLogging) {
                        Log.w(LogUtils.TAG, "session_report_failed sessionId=$sessionId (non-retryable)", result.cause)
                    }
                }
            }
            result
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            if (enableLogging) Log.w(LogUtils.TAG, "session_report_failed sessionId=$sessionId (exception)", t)
            SendResult.RetryableFailure(t)
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
                    when (result) {
                        is SendResult.Success -> {
                            queue.remove(entry.sessionId)
                            if (enableLogging) Log.d(
                                LogUtils.TAG,
                                "offline_queue flushed sessionId=${entry.sessionId}"
                            )
                        }

                        is SendResult.NonRetryableFailure -> {
                            // Remove non-retryable entries from queue — they'll never succeed.
                            queue.remove(entry.sessionId)
                            if (enableLogging) Log.w(
                                LogUtils.TAG,
                                "offline_queue dropped sessionId=${entry.sessionId} (non-retryable)"
                            )
                        }

                        is SendResult.RetryableFailure -> {
                            // Leave in queue for the next cycle.
                        }
                    }
                }
            }
        }
    }
}