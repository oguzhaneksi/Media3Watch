package com.media3watch.sdk.schema

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DeviceInfoTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `serialize and deserialize DeviceInfo`() {
        val device =
            DeviceInfo(
                model = "Pixel 8 Pro",
                os = "Android",
                osVersion = "14.0.1",
            )

        val jsonString = json.encodeToString(device)
        val deserialized = json.decodeFromString<DeviceInfo>(jsonString)

        assertEquals(device, deserialized)
    }

    @Test
    fun `DeviceInfo handles empty strings`() {
        val device =
            DeviceInfo(
                model = "",
                os = "",
                osVersion = "",
            )

        val jsonString = json.encodeToString(device)
        val deserialized = json.decodeFromString<DeviceInfo>(jsonString)

        assertEquals(device, deserialized)
        assertEquals("", deserialized.model)
    }

    @Test
    fun `DeviceInfo handles special characters`() {
        val device =
            DeviceInfo(
                model = "Samsung Galaxy S23 Ultra 5G (SM-S918B/DS)",
                os = "Android™",
                osVersion = "14.0.0-beta.2",
            )

        val jsonString = json.encodeToString(device)
        val deserialized = json.decodeFromString<DeviceInfo>(jsonString)

        assertEquals(device, deserialized)
        assertEquals("Samsung Galaxy S23 Ultra 5G (SM-S918B/DS)", deserialized.model)
    }

    @Test
    fun `DeviceInfo deserializes from JSON with unknown fields`() {
        val jsonString =
            """
            {
                "model": "iPhone 15 Pro",
                "os": "iOS",
                "osVersion": "17.2",
                "unknownField": "should be ignored"
            }
            """.trimIndent()

        val device = json.decodeFromString<DeviceInfo>(jsonString)

        assertEquals("iPhone 15 Pro", device.model)
        assertEquals("iOS", device.os)
        assertEquals("17.2", device.osVersion)
    }
}
