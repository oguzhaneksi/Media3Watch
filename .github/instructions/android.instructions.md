---
applyTo: "android/**"
---

# Android scope instructions (Media3Watch)

## Hard boundaries
- `sdk:schema` and `sdk:core` MUST remain pure Kotlin (no Android, no Media3 imports).
- Media3/ExoPlayer code MUST stay inside the Media3 adapter module only.
- Android runtime concerns (Context, storage, WorkManager, overlay UI) MUST be isolated in Android-specific modules.

## QoE semantics (MVP)
- `player_startup_ms = first_frame_rendered_ts - play_command_ts`
- `markPlayRequested()` is called exactly when the app calls `player.play()` or sets `playWhenReady=true`.

## Build & test
- Prefer unit tests for pure Kotlin modules (`schema`, `core`) for state machine + metric math.
- Avoid allocations on hot paths (player callbacks).
- Never log secrets or PII.

## PR hygiene
Include:
- What changed + why
- How to test
- Schema impact? (yes/no)
- Risk notes
