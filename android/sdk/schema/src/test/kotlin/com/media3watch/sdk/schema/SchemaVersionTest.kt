package com.media3watch.sdk.schema

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class SchemaVersionTest {
    @Test
    fun `SCHEMA_VERSION is defined`() {
        assertNotNull(SCHEMA_VERSION)
    }

    @Test
    fun `SCHEMA_VERSION follows semantic versioning format`() {
        val versionRegex = Regex("""^\d+\.\d+\.\d+$""")
        assertEquals(
            true,
            versionRegex.matches(SCHEMA_VERSION),
            "Schema version should follow semantic versioning (x.y.z)",
        )
    }

    @Test
    fun `SCHEMA_VERSION is not empty`() {
        assertEquals(true, SCHEMA_VERSION.isNotEmpty())
    }

    @Test
    fun `SCHEMA_VERSION has expected value`() {
        // This test documents the current version and will fail if it changes
        // Update this test when intentionally bumping schema version
        assertEquals("1.0.0", SCHEMA_VERSION)
    }
}
