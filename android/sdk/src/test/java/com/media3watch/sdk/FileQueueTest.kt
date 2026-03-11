package com.media3watch.sdk

import com.media3watch.sdk.transport.FileQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileQueueTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var queue: FileQueue

    @Before
    fun setUp() {
        queue = FileQueue(dir = tempFolder.newFolder("queue"))
    }

    // ── enqueue / peekAll / remove round-trip ─────────────────────────────────

    @Test
    fun enqueue_peekAll_remove_roundTrip() = runTest {
        val result = queue.enqueue("session-1", """{"a":1}""")
        assertTrue("enqueue should succeed", result.isSuccess)

        val entries = queue.peekAll()
        assertEquals(1, entries.size)
        assertEquals("session-1", entries[0].sessionId)
        assertEquals("""{"a":1}""", entries[0].payload)

        queue.remove("session-1")
        assertTrue(queue.peekAll().isEmpty())
    }

    // ── upsert: same sessionId overwrites ─────────────────────────────────────

    @Test
    fun enqueue_sameSession_overwritesPayload_fileCountStaysOne() = runTest {
        queue.enqueue("session-x", """{"v":1}""")
        queue.enqueue("session-x", """{"v":2}""")

        val entries = queue.peekAll()
        assertEquals("Second enqueue should not create a second file", 1, entries.size)
        assertEquals("Latest payload should win", """{"v":2}""", entries[0].payload)
    }

    // ── multiple sessions ─────────────────────────────────────────────────────

    @Test
    fun enqueue_differentSessions_allRetained() = runTest {
        queue.enqueue("alpha", """{"s":"alpha"}""")
        queue.enqueue("beta",  """{"s":"beta"}""")
        queue.enqueue("gamma", """{"s":"gamma"}""")

        assertEquals(3, queue.size())
        val ids = queue.peekAll().map { it.sessionId }.toSet()
        assertTrue(ids.containsAll(listOf("alpha", "beta", "gamma")))
    }

    // ── remove non-existent file is a no-op ───────────────────────────────────

    @Test
    fun remove_nonExistentSession_isNoOp() = runTest {
        // Should not throw
        queue.remove("ghost-session")
        assertEquals(0, queue.size())
    }

    // ── empty queue ───────────────────────────────────────────────────────────

    @Test
    fun peekAll_emptyQueue_returnsEmptyList() = runTest {
        assertTrue(queue.peekAll().isEmpty())
    }

    @Test
    fun size_emptyQueue_returnsZero() = runTest {
        assertEquals(0, queue.size())
    }

    // ── trimToMaxSize ─────────────────────────────────────────────────────────

    @Test
    fun trimToMaxSize_evictsOldestFiles() = runTest {
        queue.enqueue("old-1", """{"i":1}""")
        queue.enqueue("old-2", """{"i":2}""")
        queue.enqueue("new-3", """{"i":3}""")

        val queueDir = tempFolder.root.resolve("queue")
        queueDir.resolve("old-1.json").setLastModified(1_000L)
        queueDir.resolve("old-2.json").setLastModified(2_000L)
        queueDir.resolve("new-3.json").setLastModified(3_000L)

        queue.trimToMaxSize(1)

        assertEquals(1, queue.size())
        val remaining = queue.peekAll()
        assertEquals(1, remaining.size)
        assertEquals("new-3", remaining.first().sessionId)
    }

    @Test
    fun trimToMaxSize_belowLimit_doesNothing() = runTest {
        queue.enqueue("s1", """{"x":1}""")
        queue.enqueue("s2", """{"x":2}""")

        queue.trimToMaxSize(10)

        assertEquals(2, queue.size())
    }

    @Test
    fun trimToMaxSize_zero_clearsAll() = runTest {
        queue.enqueue("s1", """{"x":1}""")
        queue.enqueue("s2", """{"x":2}""")

        queue.trimToMaxSize(0)

        assertEquals(0, queue.size())
    }

    // ── size ──────────────────────────────────────────────────────────────────

    @Test
    fun size_incrementsOnEnqueue_decrementsOnRemove() = runTest {
        assertEquals(0, queue.size())
        queue.enqueue("a", "{}")
        assertEquals(1, queue.size())
        queue.enqueue("b", "{}")
        assertEquals(2, queue.size())
        queue.remove("a")
        assertEquals(1, queue.size())
    }

    @Test
    fun enqueue_concurrentDifferentSessions_allPersistedSafely() = runTest {
        val sessionCount = 100

        withContext(Dispatchers.Default) {
            (1..sessionCount)
                .map { index ->
                    async {
                        queue.enqueue("s-$index", """{"i":$index}""")
                    }
                }
                .awaitAll()
        }

        assertEquals(sessionCount, queue.size())
        val ids = queue.peekAll().map { it.sessionId }.toSet()
        assertEquals(sessionCount, ids.size)
        assertTrue(ids.contains("s-1"))
        assertTrue(ids.contains("s-$sessionCount"))
    }

    // ── enqueue returns Result reflecting success / failure ────────────────────

    @Test
    fun enqueue_returnsSuccess_onSuccessfulWrite() = runTest {
        val result = queue.enqueue("result-session", """{"ok":true}""")
        assertTrue("enqueue should return success", result.isSuccess)
    }

    @Test
    fun enqueue_returnsFailure_whenDirIsUnwritable() = runTest {
        val readOnlyDir = tempFolder.newFolder("readonly")
        readOnlyDir.setWritable(false)
        val unwritableQueue = FileQueue(dir = readOnlyDir)

        val result = try {
            unwritableQueue.enqueue("session-fail", """{"ok":false}""")
        } finally {
            readOnlyDir.setWritable(true)
        }

        assertFalse("enqueue should return failure for unwritable dir", result.isSuccess)
        assertNotNull("failure should carry the cause", result.exceptionOrNull())
    }
}
