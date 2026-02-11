package com.media3watch.sdk.schema

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppInfoTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `serialize and deserialize AppInfo`() {
        val app =
            AppInfo(
                name = "Media3Watch Demo",
                version = "2.1.5",
            )

        val jsonString = json.encodeToString(app)
        val deserialized = json.decodeFromString<AppInfo>(jsonString)

        assertEquals(app, deserialized)
    }

    @Test
    fun `AppInfo handles empty strings`() {
        val app =
            AppInfo(
                name = "",
                version = "",
            )

        val jsonString = json.encodeToString(app)
        val deserialized = json.decodeFromString<AppInfo>(jsonString)

        assertEquals(app, deserialized)
    }

    @Test
    fun `AppInfo handles semantic versioning`() {
        val app =
            AppInfo(
                name = "MyStreamApp",
                version = "1.2.3-beta.4+build.567",
            )

        val jsonString = json.encodeToString(app)
        val deserialized = json.decodeFromString<AppInfo>(jsonString)

        assertEquals(app, deserialized)
        assertEquals("1.2.3-beta.4+build.567", deserialized.version)
    }

    @Test
    fun `AppInfo deserializes from JSON with unknown fields`() {
        val jsonString =
            """
            {
                "name": "TestApp",
                "version": "3.0.0",
                "buildNumber": 42,
                "unknownField": "ignored"
            }
            """.trimIndent()

        val app = json.decodeFromString<AppInfo>(jsonString)

        assertEquals("TestApp", app.name)
        assertEquals("3.0.0", app.version)
    }
}
