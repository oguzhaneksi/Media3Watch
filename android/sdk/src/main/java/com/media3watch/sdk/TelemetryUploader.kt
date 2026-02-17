package com.media3watch.sdk

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

internal class TelemetryUploader(
    private val sender: HttpSender,
    private val uploadTimeoutMs: Long = 15_000,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) {
    fun shutdown() {
        coroutineScope.cancel() // call when SDK is disposed, if ever
    }

    @OptIn(UnstableApi::class)
    fun upload(sessionId: String, payload: String) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                withContext(NonCancellable) {
                    sender.send(payload, callTimeoutMs = uploadTimeoutMs)
                        .onSuccess {
                            Log.d(LogUtils.TAG, "session_report_success sessionId=$sessionId")
                        }
                        .onFailure {
                            if (it is SocketTimeoutException || it is InterruptedIOException) {
                                Log.w(LogUtils.TAG, "session_report_failed sessionId=$sessionId (timeout)", it)
                            } else {
                                Log.w(LogUtils.TAG, "session_report_failed sessionId=$sessionId", it)
                            }
                        }
                }
            } catch (t: Throwable) {
                Log.w(LogUtils.TAG, "session_report_failed sessionId=$sessionId (exception)", t)
            }
        }
    }
}
