# Media3Watch Android SDK

Modular, modern, and lightweight Android Media3 (ExoPlayer) analytics SDK.

## 🏗 Architectural Overview

The SDK features a modular structure with clearly separated responsibilities. The following diagram illustrates the dependencies between modules:

```text
                        ┌─────────────┐
                        │ Android App │
                        └──────┬──────┘
                               ▼
                    ┌────────────────────┐
                    │ sdk:starter-media3 │
                    │(Single Dependency) │
                    └─┬───┬─────────┬───┬┘
                      │   │         │   │ (optional)
            ┌─────────┘   │         │   └──────────┐
            │             │         │              │
    ┌───────▼───────┐ ┌───▼─────────▼───┐ ┌────────▼────────┐
    │ sdk:android-  │ │ sdk:adapter-    │ │ sdk:transport-  │
    │    runtime    │ │     media3      │ │     okhttp      │
    └───────┬───────┘ └─────┬─────┬─────┘ └────────┬────────┘
            │               │     │                │
            │               │   ┌─▼───────────┐    │
            │               │   │   Media3    │    │
            │               │   │ (ExoPlayer) │    │
            │               │   └─────────────┘    │
            └───────────────┼─────────┬────────────┘
                            ▼         ▼
                    ┌────────────────────────┐
                    │ sdk:core (Pure Kotlin) │
                    └───────────┬────────────┘
                                ▼
                    ┌────────────────────────┐
                    │ sdk:schema (Pure Kot)  │
                    └────────────────────────┘
```
*(Note: sdk:inspector-overlay also depends on runtime, core, and schema.)*

## 📦 Module Structure and Responsibilities

The project is structured under the `sdk/` directory with the following module hierarchy:

### 1. `sdk:schema` (Pure Kotlin)
*   **Responsibility:** Data models (SessionSummary, StreamType, ErrorCategory, etc.), `schemaVersion`, and forward-compatibility rules.
*   **Technology:** Pure Kotlin + `kotlinx.serialization` (JSON encode/decode).
*   **Dependency:** Only standard Kotlin libraries.

### 2. `sdk:core` (Pure Kotlin)
*   **Responsibility:**
    *   Processing `PlaybackEvent` signals to manage the session state machine.
    *   Metric generation (startup time, rebuffer, quality switches, dropped frames).
    *   Publishing finalized sessions to `SessionSink` implementations.
    *   Testable, pure business logic.
*   **Constraints:** No knowledge of Android, Media3, View, or OkHttp. It only understands its own event model and the `Clock` abstraction.

### 3. `sdk:android-runtime` (Android)
*   **Responsibility:**
    *   Android-specific implementations: `DeviceInfoProvider`, `AppInfoProvider`.
    *   Simple persistent storage (session JSON files).
    *   App lifecycle hooks (background/foreground tracking).
    *   Ergonomic Facade API for app developers.
*   **Dependency:** AndroidX Core. **No Media3 dependency.**

### 4. `sdk:adapter-media3` (Android + Media3)
*   **Responsibility:**
    *   Listens to Media3 `AnalyticsListener` and `Player.Listener` events.
    *   Maps these events into the `PlaybackEvent` model understood by `sdk:core`.
    *   Captures "first frame rendered" signal from Media3 callbacks to finalize `player_startup` metric.
*   **Note:** This is the only module with a Media3 (ExoPlayer) dependency. Risk isolation ensures that major Media3 API changes only affect this module.

### 5. `sdk:transport-okhttp` (Android, Optional)
*   **Responsibility:**
    *   `SessionSink` implementation.
    *   Writes finalized sessions to a local queue and uploads them to the endpoint.
    *   **Idempotency:** Ensures at-least-once delivery using `sessionId`.
    *   Retry/Backoff mechanism.
*   **Technology:** OkHttp (+ optional WorkManager).

### 6. `sdk:inspector-overlay` (Android, Optional)
*   **Responsibility:**
    *   Debug UI for developers/QA.
    *   Live timeline, statistics, and export/share buttons.
    *   Listens to live session state from `core` (does not connect directly to the Player).
*   **Technology:** View-based UI (for minimum dependency and maximum compatibility).

### 7. `sdk:starter-media3` (Bundle / Starter)
*   **Responsibility:** A "battery-included" single dependency for the app side.
*   **Content:** Transitive inclusion of `android-runtime` + `adapter-media3` + (optional) transport and inspector modules.

---

## � Integration

Add the following dependency to your app-level `build.gradle.kts` file:

```kotlin
dependencies {
    // The only dependency you need for Media3 integration
    implementation("io.media3watch:starter-media3:1.0.0")
}
```

---

## 💡 Usage Example

### 1. Initialization
In your `Application` class:

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        Media3Watch.init(this) {
            apiKey = "your-api-key"
            endpoint = "https://your-m3w-backend.com/v1/sessions"
            enableInspector = BuildConfig.DEBUG
        }
    }
}
```

### 2. Attaching to Player
In your `Activity` or `Fragment` where the player is created:

```kotlin
val player = ExoPlayer.Builder(context).build()

// Attach the SDK to the player
Media3Watch.attach(player)

// Optional: Set content metadata
Media3Watch.setContentId("video-789")
Media3Watch.setStreamType(StreamType.VOD)

// KPI: player_startup_ms = first_frame_rendered_ts - play_command_ts
// Navigation, API calls, and preloads are EXCLUDED.
playButton.setOnClickListener {
    // 1. Mark exactly when the play command is issued
    Media3Watch.markPlayRequested()
    
    // 2. Execute play command
    player.play()
    
    // Note: First frame is captured automatically via Media3 callbacks.
}
```

---

## 🛠 Module-Specific Tech Stack

### SDK Requirements
*   **minSdk:** 23 (Required by Media3 1.9.x)
*   **compileSdk / targetSdk:** 36 (Android 16)
*   **Kotlin:** 2.3.0+
*   **AGP:** 9.0.0+

### Key Dependencies
*   **Media3:** 1.9.x — ExoPlayer integration (adapter module only)
*   **kotlinx.serialization** — JSON encoding for SessionSummary (schema module)
*   **Coroutines + Flow** — Event streaming and state machine (core module)
*   **OkHttp** — Network transport (transport-okhttp module, optional)
*   **WorkManager** — Background upload (android-runtime module)

### Quality Tools
*   **ktlint + detekt** — Code style and static analysis
*   **Dokka** — API documentation generation
*   **Binary Compatibility Validator** — Public API stability tracking

