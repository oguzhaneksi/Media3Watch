# Media3PlaybackEventBridge Implementation

## Overview
The `Media3PlaybackEventBridge` is an adapter class that translates Media3's `Player.Listener` callbacks into generic `PlaybackEvent` types defined in the `sdk:schema` module. This keeps Media3 dependencies isolated from the pure Kotlin core modules.

## Location
- **Module**: `sdk:adapter-media3`
- **Package**: `com.media3watch.sdk.adapter_media3`
- **Class**: `Media3PlaybackEventBridge`

## Architecture

### Responsibilities
1. **Listener Management**: Implements `Player.Listener` and registers/unregisters with Media3 Player instances
2. **Event Translation**: Maps Media3 callbacks to `PlaybackEvent` types
3. **State Tracking**: Maintains internal state for buffering and seek operations
4. **Error Categorization**: Maps Media3 error codes to `ErrorCategory` enum

### Design Decisions

#### Constructor Parameter
```kotlin
class Media3PlaybackEventBridge(
    private val onEvent: (PlaybackEvent) -> Unit
)
```
- Takes a callback function to emit events
- Simple, low-allocation design
- Can be easily wrapped with Flow/Channel if needed by consumers

#### State Management
The bridge maintains minimal state to handle:
- **Buffering detection**: Tracks when buffering starts/ends
- **Playback state transitions**: Detects STATE_BUFFERING → STATE_READY
- **Seek timing**: Currently emits SeekStarted/SeekEnded together (MVP approach)

## Callback Mappings

### Direct Mappings
| Media3 Callback | PlaybackEvent | Notes |
|----------------|---------------|-------|
| `onPlayWhenReadyChanged(boolean)` | `PlayWhenReadyChanged(playWhenReady)` | Direct mapping |
| `onIsPlayingChanged(boolean)` | `IsPlayingChanged(isPlaying)` | Direct mapping |
| `onRenderedFirstFrame()` | `FirstFrameRendered` | Direct mapping |
| `onPlaybackStateChanged(int)` | `PlaybackStateChanged(playbackState)` | Forwards raw STATE_* value |

### State-Based Mappings
| Media3 State Transition | PlaybackEvent | Logic |
|------------------------|---------------|-------|
| `onPlaybackStateChanged(STATE_BUFFERING)` | `BufferingStarted` | Only if not already buffering |
| `STATE_BUFFERING → STATE_READY` | `BufferingEnded(durationMs)` | Calculates duration since BufferingStarted |
| `onPlaybackStateChanged(STATE_ENDED)` | `PlaybackEnded` | Direct mapping |

### Complex Mappings

#### Seek Events
```kotlin
onPositionDiscontinuity(oldPos, newPos, DISCONTINUITY_REASON_SEEK)
→ SeekStarted + SeekEnded
```
**Note**: Media3's `onPositionDiscontinuity` fires *after* the seek completes, not when it starts. For MVP, we emit both SeekStarted and SeekEnded together with minimal duration.

#### Error Categorization
```kotlin
onPlayerError(PlaybackException)
→ PlayerError(errorCode, errorCategory)
```

Mapping rules:
- `ERROR_CODE_IO_*` → `ErrorCategory.NETWORK`
- `ERROR_CODE_DRM_*` → `ErrorCategory.DRM`
- `ERROR_CODE_PARSING_*` → `ErrorCategory.SOURCE`
- `ERROR_CODE_DECODER_*` → `ErrorCategory.DECODER`
- All others → `ErrorCategory.UNKNOWN`

## API

### Lifecycle Methods

#### `attach(player: Player)`
Registers the bridge as a listener on the given Player instance.
- Stores player reference
- Captures initial playback state
- Calls `player.addListener(this)`

#### `detach()`
Unregisters the bridge from the player and resets internal state.
- Removes listener
- Clears player reference
- Resets buffering/seek state

**Important**: Events emitted after `detach()` are ignored (not forwarded to callback).

## Testing

### Test Coverage
- 24 unit tests covering all callback mappings
- Tests for attach/detach lifecycle
- Tests for error categorization (all 5 categories)
- Tests for buffering state transitions
- Tests for seek detection
- Tests for realistic callback sequences
- Tests for edge cases (multiple buffering events, detach behavior)

### MockPlayer
A stub implementation of `Player` is provided for testing (`MockPlayer.kt`).
- Implements full `Player` interface
- Only overrides listener registration methods
- All other methods throw `UnsupportedOperationException`

## Known Limitations (By Design)

1. **Seek Timing**: SeekStarted/SeekEnded emitted together because Media3's discontinuity callback fires after seek completes
2. **Quality Switches**: Not handled (requires AnalyticsListener, out of scope for this issue)
3. **Dropped Frames**: Not tracked (requires AnalyticsListener, out of scope)
4. **Error Categorization**: Defaults to UNKNOWN for unrecognized error codes (pragmatic approach)

## Dependencies

### Build Dependencies
- `androidx.media3:media3-exoplayer` (implementation)
- `androidx.media3:media3-common` (implementation)
- `project(:sdk:schema)` (implementation)

### Test Dependencies
- JUnit 5 (Jupiter)
- Media3 common (for error codes in tests)

## Future Enhancements
- Add Flow-based event emission option
- Support AnalyticsListener for quality switches and dropped frames
- More granular seek timing (if Media3 adds beforeSeek callback)
- Performance profiling under high event load

## Example Usage
```kotlin
val bridge = Media3PlaybackEventBridge { event ->
    // Forward to state machine or other consumer
    stateManager.handleEvent(event)
}

// Attach to player
bridge.attach(exoPlayer)

// When done
bridge.detach()
```
