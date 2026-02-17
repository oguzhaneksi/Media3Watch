# Acceptance Criteria: Real-Time Session Reporting

## 1. Real-Time Reporting Logic
- [ ] **Periodic Updates**: The SDK must send session summaries to the backend every `reportingIntervalMs` (default: 15 seconds) while the player is active (`isPlaying` && `READY`).
- [ ] **Event-Driven Updates**: A report must be triggered immediately (subject to throttling) on the following events:
    - Play/Pause state change (`onIsPlayingChanged`, `onPlayWhenReadyChanged`)
    - Playback state change (`onPlaybackStateChanged`)
    - Seeking (`onSeekStarted`)
    - Player Errors (`onPlayerError`)
    - Video Format Changes (`onVideoInputFormatChanged`)
    - Dropped Frames (`onDroppedVideoFrames`)
- [ ] **Throttling**: Consecutive reports must be spaced by at least `minIntervalMs` (default: 1000ms) to prevent event storms.
- [ ] **Configuration**: 
    - Users can disable real-time reporting via `Media3WatchConfig(enableRealTimeReporting = false)`.
    - Users can customize the interval via `Media3WatchConfig(reportingIntervalMs = ...)`.\n\n## 2. Session Integrity & Data
- [ ] **Session ID Persistence**: The same `sessionId` must be used for all reports (periodic and final) within a single playback session.
- [ ] **Duration Guard**: Reports with `sessionDurationMs <= 0` must NOT be uploaded to the backend to prevent validation errors.
- [ ] **Final Report**: A final, comprehensive report must be sent when `detach()` is called, ensuring the last bits of playback data are captured.
- [ ] **Local Logging**: Local logs (Logcat) should always be generated on detach for debugging, even if the duration is 0 and upload is skipped.\n\n## 3. Code Structure & Quality
- [ ] **Component Isolation**: `SessionReporter` class handles the timing and throttling logic independently of the main analytics class.
- [ ] **Lifecycle Management**: The reporter starts on `attach()` and stops on `detach()`/`release()` to prevent memory leaks or zombie background tasks.
- [ ] **Concurrency**: Network requests must be performed on a background thread (`Dispatchers.IO`), never blocking the main/UI thread.\n\n## 4. Testing & Verification
- [ ] **Unit Tests**: 
    - `SessionReporterTest`: Verify start/stop, periodic intervals, manual triggers, and throttling.
        - *Note*: Ensure proper TestScope dispatcher usage (`runCurrent` or `advanceUntilIdle`) for coroutine timing tests.
    - `Media3WatchAnalyticsTest`: Verify integration, event hooks, configuration flags, and the zero-duration guard.
- [ ] **Integration**: Tests must verify the `X-API-Key` header is correctly set (not `Bearer`).
- [ ] **Coverage**: Maintain approximately 80% code coverage for the new components.
