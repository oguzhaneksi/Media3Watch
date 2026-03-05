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
    suspend fun send(json: String, callTimeoutMs: Long? = null): Result<Unit> = withContext(Dispatchers.IO) {
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
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(IOException("HTTP ${response.code}: ${response.message}"))
                }
            }
        } catch (e: CancellationException) {
            // Rethrow CancellationException to allow proper coroutine cancellation
            throw e
        } catch (e: IOException) {
            // Network and I/O errors
            Result.failure(e)
        } catch (e: IllegalArgumentException) {
            // Malformed URL (though validated earlier in Media3WatchConfig)
            Result.failure(e)
        } catch (e: IllegalStateException) {
            // OkHttp internal state issues
            Result.failure(e)
        } catch (e: SecurityException) {
            // Permission denied
            Result.failure(e)
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

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
