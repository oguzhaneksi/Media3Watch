package com.media3watch.sdk

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

/**
 * Uploads telemetry payloads via [HttpSender].
 *
 * Does NOT own a coroutine scope. The caller-provided [coroutineScope] (rooted at the shared
 * analytics SupervisorJob) is used so that all SDK coroutines live in a single hierarchy.
 *
 * Dispatcher responsibilities:
 *  - Launched on [Dispatchers.Default] (CPU / parsing work lives here if needed before the call).
 *  - Network I/O is delegated to [Dispatchers.IO] inside [HttpSender.send].
 */
internal class TelemetryUploader(
    private val sender: HttpSender,
    private val uploadTimeoutMs: Long = 15_000,
    private val coroutineScope: CoroutineScope,
    private val enableLogging: Boolean = true,
) {
    @OptIn(UnstableApi::class)
    suspend fun upload(sessionId: String, payload: String) {
        try {
            withContext(NonCancellable) {
                sender.send(payload, callTimeoutMs = uploadTimeoutMs)
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
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            if (enableLogging) Log.w(LogUtils.TAG, "session_report_failed sessionId=$sessionId (exception)", t)
        }
    }
}
