package com.media3watch.sdk

import java.net.MalformedURLException
import java.net.URL

data class Media3WatchConfig(
    val backendUrl: String? = null,
    val apiKey: String? = null,
    val reportingIntervalMs: Long = 15_000L,
    val enableRealTimeReporting: Boolean = true
) {

    init {
        if (backendUrl != null) {
            try {
                URL(backendUrl)
            } catch (e: MalformedURLException) {
                throw IllegalArgumentException("Invalid backendUrl: $backendUrl", e)
            }
        }
        
        if (reportingIntervalMs <= 0) {
            throw IllegalArgumentException("reportingIntervalMs must be positive, got: $reportingIntervalMs")
        }
    }
}
