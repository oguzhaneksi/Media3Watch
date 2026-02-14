package com.media3watch.sdk

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
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun send(json: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val body = json.toRequestBody(JSON_MEDIA_TYPE)

            val requestBuilder = Request.Builder()
                .url(endpointUrl)
                .header("Content-Type", "application/json")
                .post(body)

            if (!apiKey.isNullOrEmpty()) {
                requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            }

            val response = client.newCall(requestBuilder.build()).execute()

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(IOException("HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
