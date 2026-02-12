# Session Lifecycle (MVP)

This document defines **exact triggers** for **session start**, **session end**, and **state transitions** used by `sdk:core`’s session state machine.

> MVP intent: one active session at a time (no concurrent players). Sessions are **sequential** (you can have many over time).

---

## 1) Terms & Invariants

### Session
A **session** represents **one playback attempt** for **one Media3 `Player` instance** and (typically) **one primary media item**.  
A session may include pauses, buffering, seeks, and recoverable errors.

### Key timestamps
- **play_command_ts**: recorded when the app calls `Media3Watch.markPlayRequested()`
- **first_frame_ts**: captured automatically from Media3 callbacks
- **session_end_ts**: captured when session end trigger fires

**Timestamp source**: All timestamps use `System.currentTimeMillis()` (Unix epoch milliseconds).

### Startup metric
`player_startup_ms = first_frame_ts - play_command_ts`  
Navigation/API gating is explicitly out of scope.

### Single active session
At any moment:
- either **no active session**, or
- exactly **one active session**, bound to exactly **one attached Player**.

---

## 2) Public API Responsibilities

### `Media3Watch.init(context, config)`
Global SDK setup — configures endpoint, api key, sinks/queue/inspector flags.

### `Media3Watch.attach(player)`
Binds the SDK to a Player and prepares session tracking.

Rules:
1. If no player is attached → attach succeeds and creates a new session.
2. If the **same Player instance** is already attached → **idempotent** (no new session).
3. If a **different Player instance** is attached while a session is active → the previous session ends immediately, then the new player becomes attached.

### `Media3Watch.detach()`
Ends the current session and cleans up listeners.

Rules:
1. Host app **MUST** call `detach()` when:
   - Releasing the player (`player.release()`)
   - Leaving playback permanently
2. Calling `detach()` ends the session with reason `DETACHED`.
3. Failure to call `detach()` may result in:
   - Session metrics not uploading
   - Memory/listener leaks

### `Media3Watch.markPlayRequested()`
Must be called **exactly when the play command is issued** (`player.play()` or `playWhenReady=true`).  
This marks the **start of startup measurement**.

---

## 3) Session Start

### Session ID generation
**Generated at `attach(player)`**.

### Timer start
- **Startup timer starts** at `markPlayRequested()`
- Playtime/rebuffer timers start based on playback state transitions

### Metadata capture
Session metadata (`contentId`, `streamType`, `device`, `app`) is captured at session start.  
Metadata cannot be changed after session start.
---

## 4) Session End (exact)

A session is **finalized** (SessionSummary JSON generated) and becomes eligible for queue/upload when any of the following occurs:

### End triggers (MVP)
1. **Player released / detached**
   - Adapter detects `player.release()` indirectly (listener removal / player becomes unusable) OR runtime calls an explicit detach hook (if implemented).
2. **Playback ended**
   - Media3 `Player.STATE_ENDED` for the current item.
3. **Player replaced**
   - `Media3Watch.attach(newPlayer)` while another *different* player is currently attached (previous session ends with reason `PLAYER_REPLACED`).
4. **Media item transition (content switch)**
   - Media3 `onMediaItemTransition(...)` **only if meaningful activity has occurred** (see §5 for definition: `PlayRequested`, `FirstFrameRendered`, `BufferingStarted`, or `IsPlaying=true`).
   - If no meaningful activity has occurred → ignore the transition (no session to end).
   - If meaningful activity occurred → old session ends with reason `CONTENT_SWITCH`, then a new session starts (new `sessionId`) for the new item.

---

## 5) State Model

### Core session states
- **NO_SESSION**: nothing attached
- **ATTACHED**: player attached, sessionId exists, waiting for meaningful activity
- **PLAYING**
- **PAUSED**
- **BUFFERING** (rebuffering during playback)
- **SEEKING** (buffering caused by seek operations)
- **ENDED** (finalized; awaiting upload)

### "Meaningful activity" (promotes ATTACHED → active)
Any of:
- `PlayRequested` (from `markPlayRequested()`)
- `FirstFrameRendered`
- `BufferingStarted`
- `IsPlaying=true`

---

## 6) Errors & Recovery

- `PlayerError` increments `errorCount`, sets `lastErrorCode` and `lastErrorCategory`.
- Errors **do not end** the session automatically.
- Session continues if playback resumes.

---

## 7) Finalization

On any end trigger:
1. Generate `SessionSummary` JSON with derived metrics
2. Publish to queue/upload
3. Discard if no meaningful activity occurred

---

## 8) State Diagram

```mermaid
stateDiagram-v2
  [*] --> NO_SESSION

  NO_SESSION --> ATTACHED: attach(player)

  ATTACHED --> PLAYING: PlayRequested + isPlaying=true
  ATTACHED --> BUFFERING: BufferingStarted
  ATTACHED --> PLAYING: FirstFrameRendered

  PLAYING --> PAUSED: isPlaying=false
  PLAYING --> BUFFERING: BufferingStarted
  BUFFERING --> PLAYING: BufferingEnded + isPlaying=true
  BUFFERING --> PAUSED: BufferingEnded + isPlaying=false

  PLAYING --> SEEKING: SeekStarted
  SEEKING --> PLAYING: SeekEnded + isPlaying=true
  SEEKING --> PAUSED: SeekEnded + isPlaying=false

  ATTACHED --> ENDED: detach()
  PLAYING --> ENDED: detach()
  PAUSED --> ENDED: detach()
  BUFFERING --> ENDED: detach()
  SEEKING --> ENDED: detach()

  ATTACHED --> ENDED: player.release()
  PLAYING --> ENDED: player.release()
  PAUSED --> ENDED: player.release()
  BUFFERING --> ENDED: player.release()
  SEEKING --> ENDED: player.release()

  PLAYING --> ENDED: Player.STATE_ENDED
  PAUSED --> ENDED: Player.STATE_ENDED
  BUFFERING --> ENDED: Player.STATE_ENDED

  ATTACHED --> ENDED: attach(newPlayer)
  PLAYING --> ENDED: attach(newPlayer)
  PAUSED --> ENDED: attach(newPlayer)
  BUFFERING --> ENDED: attach(newPlayer)
  SEEKING --> ENDED: attach(newPlayer)

  PLAYING --> ENDED: MediaItemTransition
  PAUSED --> ENDED: MediaItemTransition
  BUFFERING --> ENDED: MediaItemTransition
  SEEKING --> ENDED: MediaItemTransition

  ENDED --> NO_SESSION: finalize + publish

