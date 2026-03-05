package com.media3watch.sdk

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities

/**
 * Unit tests for [NetworkConnectivityManager].
 *
 * Uses Robolectric to exercise the Android framework connectivity APIs without a device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.M])
class NetworkConnectivityManagerTest {

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var connectivityManager: NetworkConnectivityManager

    @Before
    fun setUp() {
        connectivityManager = NetworkConnectivityManager(context)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private fun shadowConnectivityManager() =
        Shadows.shadowOf(context.getSystemService(ConnectivityManager::class.java))

    private fun setActiveTransport(transport: Int) {
        val network = ShadowNetwork.newInstance(100)
        val caps = ShadowNetworkCapabilities.newInstance()
        Shadows.shadowOf(caps).addTransportType(transport)
        val shadow = shadowConnectivityManager()
        shadow.setNetworkCapabilities(network, caps)
        shadow.setActiveNetwork(network)
    }

    // ── Modern path (API 23+) ────────────────────────────────────────────────────

    @Test
    fun resolveConnectionType_wifi_returnsWiFi() {
        setActiveTransport(NetworkCapabilities.TRANSPORT_WIFI)
        assertEquals("Wi-Fi", connectivityManager.resolveConnectionType())
    }

    @Test
    fun resolveConnectionType_cellular_returnsCellular() {
        setActiveTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        assertEquals("Cellular", connectivityManager.resolveConnectionType())
    }

    @Test
    fun resolveConnectionType_ethernet_returnsEthernet() {
        setActiveTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        assertEquals("Ethernet", connectivityManager.resolveConnectionType())
    }

    @Test
    fun resolveConnectionType_noActiveNetwork_returnsUnknown() {
        // Robolectric initializes a Cellular network by default — explicitly clear it.
        @Suppress("DEPRECATION")
        shadowConnectivityManager().setActiveNetworkInfo(null)
        assertEquals("Unknown", connectivityManager.resolveConnectionType())
    }

    @Test
    fun resolveConnectionType_returnedValueIsOneOfKnownStrings() {
        val allowed = setOf("Wi-Fi", "Cellular", "Ethernet", "Unknown")
        val result = connectivityManager.resolveConnectionType()
        assertTrue("Expected one of $allowed, got: $result", result in allowed)
    }
}
