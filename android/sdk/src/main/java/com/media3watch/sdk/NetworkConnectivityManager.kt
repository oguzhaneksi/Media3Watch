package com.media3watch.sdk

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

/**
 * Internal helper that resolves the current network connection type in a
 * backward-compatible, API-level-aware way.
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
     * - On API 23+ uses [ConnectivityManager.getNetworkCapabilities] (requires
     *   `ACCESS_NETWORK_STATE`).
     * - On older devices falls back to the deprecated [ConnectivityManager.activeNetworkInfo]
     *   to give a best-effort answer without crashing.
     *
     * Must be called on the **Main thread** (ConnectivityManager is not thread-safe).
     */
    fun resolveConnectionType(): String {
        return try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return "Unknown"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                resolveConnectionTypeModern(cm)
            } else {
                resolveConnectionTypeLegacy(cm)
            }
        } catch (_: Exception) {
            "Unknown"
        }
    }

    /**
     * Modern path (API 23+): uses [NetworkCapabilities] to identify the transport layer.
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

    /**
     * Legacy path (API < 23): uses the deprecated [ConnectivityManager.activeNetworkInfo].
     * Returns a coarse-grained type string based on [android.net.ConnectivityManager] type
     * constants.
     */
    @Suppress("DEPRECATION")
    private fun resolveConnectionTypeLegacy(cm: ConnectivityManager): String {
        val info = cm.activeNetworkInfo ?: return "Unknown"
        if (!info.isConnected) return "Unknown"
        return when (info.type) {
            ConnectivityManager.TYPE_WIFI -> "Wi-Fi"
            ConnectivityManager.TYPE_MOBILE -> "Cellular"
            ConnectivityManager.TYPE_ETHERNET -> "Ethernet"
            else -> "Unknown"
        }
    }
}
