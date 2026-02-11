# Media3 Adapter Tests

## Quick Start

```bash
# Run all tests
cd android
./gradlew :sdk:adapter-media3:test

# Run with coverage
./gradlew :sdk:adapter-media3:test jacocoTestReport

# View results
open sdk/adapter-media3/build/reports/tests/test/index.html
```

## Test Files

- **Media3PlaybackEventBridgeTest.kt**: Comprehensive test suite (53 tests)
  - Happy path: callback mapping, seek detection, error categorization, lifecycle
  - Edge cases: rapid transitions, buffering, lifecycle, callback ordering, seeks
  - Failure scenarios: error handling, null safety, exceptions
  - Code quality: comprehensive error categorization, performance

## Test Coverage Map

| Category | Tests | Status |
|----------|-------|--------|
| Basic Callback Mapping | 11 | ✅ |
| Seek Detection | 2 | ✅ |
| Error Categorization | 5 | ✅ |
| Lifecycle | 4 | ✅ |
| Rapid State Transitions | 4 | ✅ |
| Buffering Edge Cases | 3 | ✅ |
| Lifecycle Edge Cases | 3 | ✅ |
| Callback Ordering | 3 | ✅ |
| Seek Edge Cases | 3 | ✅ |
| Error Handling | 3 | ✅ |
| Null Safety | 2 | ✅ |
| Callback Exceptions | 2 | ✅ |
| Comprehensive Error Mapping | 3 | ✅ |
| Performance | 1 | ✅ |
| Additional Coverage | 1 | ✅ |
| **TOTAL** | **53** | ✅ |

## Key Test Scenarios

### Happy Path
```kotlin
// Play/Pause changes
onPlayWhenReadyChanged(true) → PlayWhenReadyChanged(playWhenReady=true)

// State transitions
onPlaybackStateChanged(STATE_BUFFERING) → BufferingStarted + PlaybackStateChanged
onPlaybackStateChanged(STATE_READY) → BufferingEnded + PlaybackStateChanged

// First frame
onRenderedFirstFrame() → FirstFrameRendered

// Seeks
onPositionDiscontinuity(SEEK) → SeekStarted + SeekEnded
```

### Edge Cases
```kotlin
// Rapid transitions
play() → pause() → play() ✅ All events emitted

// Buffering deduplication
STATE_BUFFERING → STATE_BUFFERING ✅ No duplicate BufferingStarted

// Lifecycle
detach() → event() ✅ No events forwarded
```

### Error Mapping
```kotlin
IOException → ErrorCategory.NETWORK
DRM errors → ErrorCategory.DRM
Parsing errors → ErrorCategory.SOURCE
Decoder errors → ErrorCategory.DECODER
Unknown → ErrorCategory.UNKNOWN
```

## Dependencies

```kotlin
testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
testImplementation("io.mockk:mockk:1.13.9")
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
```

## Architecture

```
Media3PlaybackEventBridge
├── attach(player) - Register listener
├── detach() - Unregister listener
└── Player.Listener callbacks
    ├── onPlayWhenReadyChanged → PlayWhenReadyChanged event
    ├── onIsPlayingChanged → IsPlayingChanged event
    ├── onPlaybackStateChanged → PlaybackStateChanged + buffering logic
    ├── onRenderedFirstFrame → FirstFrameRendered event
    ├── onMediaItemTransition → MediaItemTransition event
    ├── onPositionDiscontinuity → Seek events (if SEEK reason)
    └── onPlayerError → PlayerError event with ErrorCategory
```

## Testing Patterns

### Setup
```kotlin
@BeforeEach
fun setup() {
    mockPlayer = mockk(relaxed = true)
    bridge = Media3PlaybackEventBridge { event -> events.add(event) }
    bridge.attach(mockPlayer)
}
```

### Assertions
```kotlin
// Event emitted
assertTrue(events[0] is PlaybackEvent.FirstFrameRendered)

// Event content
val event = events[0] as PlaybackEvent.PlayerError
assertEquals(ErrorCategory.NETWORK, event.errorCategory)

// Event count
assertEquals(2, events.size) // SeekStarted + SeekEnded
```

### Mocking Media3
```kotlin
val exception = PlaybackException("Error", null, ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED)
listener.onPlayerError(exception)
```

## Performance

Tests validate no blocking in callbacks:
```kotlin
// 1000 callbacks must complete in <1 second
repeat(1000) { listener.onIsPlayingChanged(it % 2 == 0) }
assertTrue(duration < 1000)
```

## See Also

- [TEST_SUMMARY.md](./TEST_SUMMARY.md) - Comprehensive test documentation
- [Issue #21](https://github.com/oguzhaneksi/Media3Watch/issues/21) - Original test requirements
- [Issue #20](https://github.com/oguzhaneksi/Media3Watch/issues/20) - Related implementation feature
