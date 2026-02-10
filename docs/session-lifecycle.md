# Session Lifecycle (MVP)

This document defines **exact triggers** for **session start**, **session end**, and **state transitions** used by `sdk:core`’s session state machine.

> MVP intent: one active session at a time (no concurrent players). Sessions are **sequential** (you can have many over time).

---

## 1) Terms & Invariants

### Session (MVP definition)
A **session** represents **one playback attempt** for **one Media3 `Player` instance** and (typically) **one primary media item**.  
A session may include pauses, buffering, seeks, and recoverable errors.

### Key timestamps
- **play_command_ts**: recorded only when the app calls `Media3Watch.markPlayRequested()` (this is the canonical “play requested” moment).
- **first_frame_ts**: captured automatically from Media3 callbacks (adapter module).
- **session_end_ts**: captured when session end trigger fires.

### Startup metric (MVP focus)
`player_startup_ms = first_frame_ts - play_command_ts`  
Navigation/API gating is explicitly out of scope. (This matches the README’s intended measurement semantics.)

### Single active session
At any moment:
- either **no active session**, or
- exactly **one active session**, bound to exactly **one attached Player**.

---

## 2) Public API Responsibilities

### `Media3Watch.init(context, config)`
**Global SDK setup**:
- configures endpoint, api key, sinks/queue/inspector flags
- does **not** start a session

### `Media3Watch.attach(player)`
**Binds the SDK to a Player** and prepares session tracking.

MVP rules:
1. If no player is attached → attach succeeds and creates/arms a new session context.
2. If the **same Player instance** is already attached → **idempotent** (no new session).
3. If a **different Player instance** is attached while a session is active → the previous session is **finalized and ended immediately**, then the new player becomes attached.

### `Media3Watch.markPlayRequested()`
Must be called **exactly when the play command is issued** (`player.play()` or `playWhenReady=true`).  
This marks the **start of startup measurement** and may also transition the session into Active/Playing depending on subsequent Media3 signals.

---

## 3) Session Start (exact)

### Session ID generation
**Generated at `attach(player)`**.

Rationale (MVP):
- You want a stable identifier early for debugging/overlay and for mapping subsequent events.
- Sessions that never reach “meaningful activity” are safe to **discard on end** (see §6.4).

### “Timer start”
- **Startup timer starts** at `markPlayRequested()`.
- Playtime/rebuffer timers start based on playback state transitions (see §5).

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
   - Media3 `onMediaItemTransition(...)` *after the session has started* (i.e., once we’ve seen `markPlayRequested()` or any meaningful playback/buffering).
   - Old session ends with reason `CONTENT_SWITCH`, then a new session starts (new `sessionId`) for the new item.
5. **Background idle timeout (NOT just background)**
   - App goes to background and stays there longer than **BG_IDLE_END_TIMEOUT_MS = 120_000 (2 minutes)** **while playback is not active**.
   - The session ends with reason `BACKGROUND_IDLE_TIMEOUT`.

**Playback Active (MVP definition)**
`playbackActive = (isPlaying == true) OR (playWhenReady == true AND playbackState == BUFFERING)`

Rationale:
- Supports PiP / audio-only (notification) playback in background without ending sessions.
- Prevents ending sessions during transient buffering when user intent is “keep playing”.

> Not MVP: persisting partial sessions on process death. If Android kills the process, you may lose the unfinished session. We mitigate this by ending on **background-idle timeout** when possible.

---

## 5) State Model (MVP)

### Core session states
- **NO_SESSION**: nothing attached
- **ATTACHED**: player attached, sessionId exists, waiting for meaningful activity
- **PLAYING**
- **PAUSED**
- **BUFFERING**
- **SEEKING** (transient; can be merged into BUFFERING if you want simpler MVP)
- **BACKGROUND** (app in background; playback may or may not be ongoing depending on app behavior)
- **ENDED** (finalized; awaiting sink enqueue/upload)

### State transition rules (events → state)
The core only understands **PlaybackEvents** (adapter/runtime map platform events into these).

#### “Meaningful activity” (promotes ATTACHED → active)
Any of:
- `PlayRequested` (from `markPlayRequested()`)
- `FirstFrameRendered`
- `BufferingStarted`
- `IsPlaying=true`

---

## 6) Android Lifecycle Mapping (sdk:android-runtime)

### Foreground / Background
Use `ProcessLifecycleOwner` (recommended) or equivalent.

- On app **background** → emit `AppBackgrounded(ts)`
- On app **foreground** → emit `AppForegrounded(ts)`

### Background behavior (MVP)
- `AppBackgrounded` transitions to **BACKGROUND** (does not immediately end).
- If `playbackActive == false`, start a **background-idle end timer** (2 minutes).
- If `playbackActive == true` (PiP / audio / background playback), **do not start the timer**.
- While in background:
  - if playback becomes active → cancel timer
  - if playback becomes inactive → start timer
- If timer fires → end session (`BACKGROUND_IDLE_TIMEOUT`).
- `AppForegrounded` cancels any pending background-idle timer and returns to playback-derived state.

> MVP decision: this is **fixed** (not configurable yet) to keep scope tight.

---

## 7) Configuration Changes (Rotation)

### Requirement
Avoid sessionId reset on Activity recreation.

### MVP rule (simple & testable)
**Session continuity is tied to the Player instance**:
- If rotation recreates Activity/Fragment but the app retains the **same Player instance** (e.g., in a ViewModel/service) and calls `attach(player)` again → attach is idempotent → **same session continues**.
- If the app recreates the Player on rotation → **new Player = new session**.

**SDK guidance (recommended integration)**:
- Store Player in a ViewModel or retained component if you want a single session across rotation.

---

## 8) Errors & Recovery

### Error handling (MVP)
- `PlayerError` increments `errorCount`, sets `lastErrorCode` and `lastErrorCategory`.
- Errors **do not end** the session automatically.
- If playback resumes (isPlaying true / buffering resolves) → session continues.

### When does error end a session?
Only via the standard end triggers in §4 (release, ended, background timeout, player replaced, content switch).

> Non-goal: sophisticated “resume-after-crash” semantics.

---

## 9) Finalization Rules

### What happens on end
On any end trigger:
1. Freeze metrics in `sdk:core`
2. Generate `SessionSummary` JSON with:
   - `timestamp = session_end_ts`
   - derived metrics (startup, rebuffer, playTime, etc.)
3. Publish to the configured `SessionSink` (queue/upload).

### Discard rule (MVP)
If a session ends while still effectively “empty”:
- no `PlayRequested`
- no `FirstFrameRendered`
- no buffering/playing
→ discard (don’t upload).

This prevents “attach-only” noise.

---

## 10) State Diagram (Mermaid)

```mermaid
stateDiagram-v2
  [*] --> NO_SESSION

  NO_SESSION --> ATTACHED: attach(player)\n(sessionId generated)

  ATTACHED --> PLAYING: PlayRequested + isPlaying=true
  ATTACHED --> BUFFERING: BufferingStarted
  ATTACHED --> PLAYING: FirstFrameRendered (implies started)

  PLAYING --> PAUSED: isPlaying=false (paused)
  PLAYING --> BUFFERING: BufferingStarted
  BUFFERING --> PLAYING: BufferingEnded + isPlaying=true
  BUFFERING --> PAUSED: BufferingEnded + isPlaying=false

  PLAYING --> SEEKING: SeekStarted
  SEEKING --> PLAYING: SeekEnded + isPlaying=true
  SEEKING --> PAUSED: SeekEnded + isPlaying=false

  ATTACHED --> BACKGROUND: AppBackgrounded
  PLAYING --> BACKGROUND: AppBackgrounded
  PAUSED --> BACKGROUND: AppBackgrounded
  BUFFERING --> BACKGROUND: AppBackgrounded
  SEEKING --> BACKGROUND: AppBackgrounded

  BACKGROUND --> PLAYING: AppForegrounded + isPlaying=true
  BACKGROUND --> PAUSED: AppForegrounded + isPlaying=false

  BACKGROUND --> ENDED: BackgroundIdleTimeout(2m)\n(playbackActive=false)

  PLAYING --> ENDED: Player.STATE_ENDED
  PAUSED --> ENDED: Player.STATE_ENDED
  BUFFERING --> ENDED: Player.STATE_ENDED

  ATTACHED --> ENDED: player released/detached
  PLAYING --> ENDED: player released/detached
  PAUSED --> ENDED: player released/detached
  BUFFERING --> ENDED: player released/detached
  SEEKING --> ENDED: player released/detached
  BACKGROUND --> ENDED: player released/detached

  ENDED --> NO_SESSION: finalize + publish\n(clear active session)

  ATTACHED --> ENDED: attach(newPlayer)\n(PLAYER_REPLACED)
  PLAYING --> ENDED: attach(newPlayer)\n(PLAYER_REPLACED)

  PLAYING --> ENDED: MediaItemTransition\n(CONTENT_SWITCH)
  PAUSED --> ENDED: MediaItemTransition\n(CONTENT_SWITCH)
  BUFFERING --> ENDED: MediaItemTransition\n(CONTENT_SWITCH)

