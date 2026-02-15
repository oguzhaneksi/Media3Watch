package com.media3watch.sdk

import java.net.MalformedURLException
import java.net.URL

data class Media3WatchConfig(
    val backendUrl: String? = null,
    val apiKey: String? = null
) {

    init {
        if (backendUrl != null) {
            try {
                URL(backendUrl)
            } catch (e: MalformedURLException) {
                throw IllegalArgumentException("Invalid backendUrl: $backendUrl", e)
            }
        }
    }
}
