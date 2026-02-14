package com.media3watch.sdk

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

internal class TelemetryUploader(
    private val sender: HttpSender,
    private val uploadTimeoutMs: Long = 15_000,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun shutdown() {
        scope.cancel() // call when SDK is disposed, if ever
    }

    @OptIn(UnstableApi::class)
    fun upload(sessionId: Long, payload: String) {
        scope.launch {
            try {
                withTimeout(uploadTimeoutMs) {
                    sender.send(payload).onFailure {
                        Log.w(LogUtils.TAG, "session_upload_failed sessionId=$sessionId", it)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.w(LogUtils.TAG, "session_upload_failed sessionId=$sessionId (timeout)", e)
            } catch (t: Throwable) {
                Log.w(LogUtils.TAG, "session_upload_failed sessionId=$sessionId (exception)", t)
            }
        }
    }
}
