package com.media3watch.sdk.util

import androidx.media3.common.util.UnstableApi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@UnstableApi
internal object LogUtils {
    const val TAG = "Media3WatchAnalytics"

    /**
     * Thread-local [SimpleDateFormat] avoids allocating a new formatter on every call
     * (reducing GC pressure) while remaining thread-safe — [SimpleDateFormat] is not
     * thread-safe and must not be shared across threads.
     *
     * Uses an anonymous subclass instead of [ThreadLocal.withInitial] to remain compatible
     * with API levels below 26.
     */
    private val ISO_FORMATTER: ThreadLocal<SimpleDateFormat> = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
    }

    fun toIsoDateTime(epochMillis: Long): String {
        if (epochMillis <= 0L) return "null"
        val formatter = ISO_FORMATTER.get() ?: SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return formatter.format(Date(epochMillis))
    }
}

