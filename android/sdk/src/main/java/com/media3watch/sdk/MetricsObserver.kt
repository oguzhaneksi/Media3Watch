package com.media3watch.sdk

interface MetricsObserver {
    fun onSnapshotUpdated(snapshot: SessionSnapshot)
    fun onSessionStarted(sessionId: String)
    fun onSessionEnded(sessionId: String, finalSnapshot: SessionSnapshot)
}
