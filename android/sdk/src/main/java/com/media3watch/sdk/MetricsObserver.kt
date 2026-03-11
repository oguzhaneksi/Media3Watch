package com.media3watch.sdk

import com.media3watch.sdk.model.SessionSnapshot

/**
 * Observer interface for receiving real-time playback and session metrics.
 *
 * Implement this interface and register it with the SDK to receive callbacks when:
 *
 * - A new [com.media3watch.sdk.model.SessionSnapshot] is available for the currently active session via [onSnapshotUpdated].
 * - A new session begins tracking via [onSessionStarted].
 * - An existing session finishes and its final metrics are available via [onSessionEnded].
 *
 * All callbacks are invoked on the main (UI) thread by the SDK implementation.
 * Implementations should therefore avoid blocking operations in these methods.
 *
 * Example:
 * ```kotlin
 * class LoggingMetricsObserver : MetricsObserver {
 *
 *     override fun onSnapshotUpdated(snapshot: SessionSnapshot) {
 *         // Inspect or log real-time playback metrics.
 *         // e.g., snapshot.playheadPosition, snapshot.bitrate, etc.
 *     }
 *
 *     override fun onSessionStarted(sessionId: String) {
 *         // Prepare any per-session resources (e.g., start logging).
 *         println("Session started: $sessionId")
 *     }
 *
 *     override fun onSessionEnded(sessionId: String, finalSnapshot: SessionSnapshot) {
 *         // Handle final metrics for the session or clean up resources.
 *         println("Session ended: $sessionId with final metrics: $finalSnapshot")
 *     }
 * }
 *
 * // Registration (example – actual API may differ):
 * // val sdk = Media3WatchSdk(...)
 * // sdk.addMetricsObserver(LoggingMetricsObserver())
 * ```
 */
interface MetricsObserver {
    /**
     * Called whenever a new snapshot of the current session's metrics becomes available.
     *
     * The SDK invokes this method on the main (UI) thread, typically whenever playback
     * state changes or at configured intervals while a session is active.
     *
     * @param snapshot the latest [com.media3watch.sdk.model.SessionSnapshot] describing the current session state
     * and metrics at the time of the callback.
     */
    fun onSnapshotUpdated(snapshot: SessionSnapshot)

    /**
     * Called when the SDK starts tracking a new playback session.
     *
     * This is the first callback for a given session identifier and is invoked on the
     * main (UI) thread before any [onSnapshotUpdated] calls for that session.
     *
     * @param sessionId a unique identifier assigned by the SDK to the new session.
     */
    fun onSessionStarted(sessionId: String)

    /**
     * Called when the SDK has finished tracking a playback session and final metrics
     * are available.
     *
     * This is the last callback for the given [sessionId] and is invoked on the
     * main (UI) thread. After this method is called, no further callbacks will be
     * delivered for the same session.
     *
     * @param sessionId the identifier of the session that has ended.
     * @param finalSnapshot the final [SessionSnapshot] containing the last known
     * metrics and state for the ended session.
     */
    fun onSessionEnded(sessionId: String, finalSnapshot: SessionSnapshot)
}
