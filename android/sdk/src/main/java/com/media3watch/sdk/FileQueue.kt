package com.media3watch.sdk

import androidx.core.util.AtomicFile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException

/**
 * Lightweight, file-backed queue that persists telemetry payloads across process restarts.
 *
 * **Session-keyed upsert semantics**: each [sessionId] maps to exactly one file
 * (`<sessionId>.json`). Calling [enqueue] for an already-queued session simply overwrites the
 * file with the latest payload, so the file count is bounded by the number of active sessions
 * (typically 1).
 *
 * All operations are coroutine-safe via [Mutex].
 * The directory itself is the index — no in-memory state, crash-safe.
 *
 * @param dir Queue directory. Created lazily on first write.
 */
internal class FileQueue(private val dir: File) {

    private val mutex = Mutex()

    data class Entry(val sessionId: String, val payload: String)

    /**
     * Persist [payload] for [sessionId]. If a file already exists for this session, it is
     * atomically overwritten with the newer payload.
     */
    suspend fun enqueue(sessionId: String, payload: String) = mutex.withLock {
        runCatching {
            dir.mkdirs()
            val atomicFile = AtomicFile(fileFor(sessionId))
            atomicFile.startWrite().use { out ->
                try {
                    out.write(payload.toByteArray(Charsets.UTF_8))
                    atomicFile.finishWrite(out)
                } catch (e: IOException) {
                    atomicFile.failWrite(out)
                    throw e
                }
            }
        }
        // Swallow silently — failing to persist must not crash the caller.
        Unit
    }

    /**
     * Returns all queued entries, oldest first (by file last-modified time).
     * Returns an empty list if the directory does not exist or is empty.
     */
    suspend fun peekAll(): List<Entry> = mutex.withLock {
        val files = dir.listFiles { f -> f.extension == "json" } ?: return@withLock emptyList()
        files
            .sortedBy { it.lastModified() }
            .mapNotNull { file ->
                runCatching {
                    val payload = file.readText(Charsets.UTF_8)
                    Entry(sessionId = file.nameWithoutExtension, payload = payload)
                }.getOrNull()
                // Corrupt / unreadable files are silently skipped.
            }
    }

    /**
     * Deletes the queued file for [sessionId]. No-op if no file exists.
     */
    suspend fun remove(sessionId: String) = mutex.withLock {
        runCatching { fileFor(sessionId).delete() }
        Unit
    }

    /** Returns the number of files currently in the queue directory. */
    suspend fun size(): Int = mutex.withLock {
        dir.listFiles { f -> f.extension == "json" }?.size ?: 0
    }

    /**
     * If the queue exceeds [maxCount] entries, the oldest files (by last-modified) are deleted
     * until the count is within the limit.
     */
    suspend fun trimToMaxSize(maxCount: Int) = mutex.withLock {
        require(maxCount >= 0)
        val files = dir.listFiles { f -> f.extension == "json" } ?: return@withLock
        if (files.size <= maxCount) return@withLock
        files
            .sortedBy { it.lastModified() }
            .dropLast(maxCount)
            .forEach { runCatching { it.delete() } }
    }

    private fun fileFor(sessionId: String): File {
        // UUIDs are safe as filenames; sanitize defensively.
        val safeName = sessionId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        return File(dir, "$safeName.json")
    }
}
