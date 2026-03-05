package com.media3watch.sdk

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Internal helper that resolves the current network connection type.
 *
 * All network-related logic is centralized here so [Media3WatchAnalytics]
 * remains focused on playback-session responsibilities (SRP).
 *
 * Requires `android.permission.ACCESS_NETWORK_STATE`, which is declared in the SDK's own
 * `AndroidManifest.xml` and merged automatically into the host application's manifest.
 */
internal class NetworkConnectivityManager(context: Context) {

    // Keep an application-scoped reference to avoid Activity leaks.
    private val appContext: Context = context.applicationContext

    /**
     * Returns one of: `"Wi-Fi"`, `"Cellular"`, `"Ethernet"`, or `"Unknown"`.
     *
     * Uses [ConnectivityManager.getNetworkCapabilities] (requires `ACCESS_NETWORK_STATE`).
     *
     * Must be called on the **Main thread** (ConnectivityManager is not thread-safe).
     */
    fun resolveConnectionType(): String {
        return try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return "Unknown"

            resolveConnectionTypeModern(cm)
        } catch (_: Exception) {
            "Unknown"
        }
    }

    /**
     * Uses [NetworkCapabilities] to identify the transport layer.
     * No special permissions needed.
     */
    private fun resolveConnectionTypeModern(cm: ConnectivityManager): String {
        val network = cm.activeNetwork ?: return "Unknown"
        val caps = cm.getNetworkCapabilities(network) ?: return "Unknown"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Unknown"
        }
    }
}
