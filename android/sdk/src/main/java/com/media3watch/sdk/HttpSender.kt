package com.media3watch.sdk

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

internal class HttpSender(
    private val endpointUrl: String,
    private val apiKey: String? = null
) {
    suspend fun send(json: String, callTimeoutMs: Long? = null): SendResult = withContext(Dispatchers.IO) {
        try {
            val body = json.toRequestBody(JSON_MEDIA_TYPE)

            val requestBuilder = Request.Builder()
                .url(endpointUrl)
                .post(body)

            if (!apiKey.isNullOrEmpty()) {
                requestBuilder.addHeader("X-API-Key", apiKey)
            }

            val call = sharedClient.newCall(requestBuilder.build())
            if (callTimeoutMs != null && callTimeoutMs > 0L) {
                call.timeout().timeout(callTimeoutMs, TimeUnit.MILLISECONDS)
            }

            call.execute().use { response ->
                when {
                    response.isSuccessful -> SendResult.Success
                    isRetryableHttpCode(response.code) -> SendResult.RetryableFailure(
                        IOException("HTTP ${response.code}: ${response.message}")
                    )
                    else -> SendResult.NonRetryableFailure(
                        IOException("HTTP ${response.code}: ${response.message}")
                    )
                }
            }
        } catch (e: CancellationException) {
            // Rethrow CancellationException to allow proper coroutine cancellation
            throw e
        } catch (e: IOException) {
            // Network and I/O errors — transient
            SendResult.RetryableFailure(e)
        } catch (e: IllegalArgumentException) {
            // Malformed URL (though validated earlier in Media3WatchConfig)
            SendResult.NonRetryableFailure(e)
        } catch (e: IllegalStateException) {
            // OkHttp internal state issues
            SendResult.RetryableFailure(e)
        } catch (e: SecurityException) {
            // Permission denied (missing INTERNET permission) — config problem, not transient
            SendResult.NonRetryableFailure(e)
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /**
         * HTTP status codes that are worth retrying on a future attempt.
         *
         * - **408** Request Timeout — server timed out waiting for the client; retry may succeed.
         * - **429** Too Many Requests — rate-limited; retry after backoff.
         * - **5xx** — server-side errors, expected to be transient.
         *
         * All other non-2xx codes (4xx except 408/429) are treated as [SendResult.NonRetryableFailure]
         * because the same payload will always be rejected.
         */
        private fun isRetryableHttpCode(code: Int): Boolean {
            return code == 408 || code == 429 || code in 500..599
        }

        /**
         * Shared [OkHttpClient] instance. OkHttp's best practice is a single client per
         * application, reusing the connection pool and thread pool across all callers.
         * This is especially important when multiple [Media3WatchAnalytics] instances
         * (e.g. multiple simultaneous players) exist in the same process.
         */
        private val sharedClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
