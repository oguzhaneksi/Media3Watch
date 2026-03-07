# Media3Watch

**Debug video sessions fast. Get a session summary in Logcat.**

> [!TIP]
> **Check out the demo video:**

<video src="https://github.com/user-attachments/assets/8ac95523-b171-480e-a8f5-76e834495e90" controls="controls" style="max-width: 100%;">
  Your browser does not support the video tag.
</video>

> Status: **Android SDK** ✅  
> Backend + Grafana: **Alpha** ✅

## Requirements

* **Minimum SDK:** API 23+
* **Compile SDK:** 36+
* **Java:** 11+
* **Media3:** 1.5.0+ (ExoPlayer based)
* **Docker:** (Optional, for Backend & Grafana)

## Why This Exists

You ship a video app with Media3. Works great in development. Then production hits—users complain about buffering, your PM asks for startup time metrics, and you have nothing.

**Your options:**

| Solution | Problem |
|----------|---------|
| Mux / Bitmovin / Conviva | Expensive, vendor lock-in, data on their servers |
| Build from scratch | 3 months of work, constant maintenance |
| Media3Watch | Debug sessions fast, share summaries, track QoE trends |

---

## What You Get Today

- **Android SDK**
- **Session summary in Logcat** (plain text format, generated on session end)
- **Debug Overlay** (floating widget — live QoE stats, color-coded health indicator, no Logcat needed)
- **Optional Real-time Backend Upload** (HTTP POST with JSON payload, updates every 15s)
- **Grafana Dashboards** (Visualize trends & sessions)

---

## Metrics Collected

The SDK automatically tracks and summarizes:
- **Startup Time:** Delay from play request to first frame.
- **Rebuffer Metrics:** Total time spent rebuffering and total rebuffer count.
- **Playback Stats:** Total play time, rebuffer ratio, and dropped frames.
- **Interaction Stats:** Seek count and total seek time.
- **Quality Stats:** Mean video bitrate.
- **Session Timeline:** High-resolution time-series data of playback state, bitrate, network type, and buffer health.
- **Errors:** Total fatal error count.
- **Context/Metadata:** Device model, OS version (API level), SDK version, and Network Connection Type (Wi-Fi, Cellular, etc.).

---

## 🗺️ Roadmap (What's Next?)

- [x] **Baseline QoE Metrics:** Fully supported for progressive media.
- [x] **Self-hostable Backend:** Ktor + Postgres & auto-provisioned Grafana dashboards.
- [x] **Frictionless Publishing:** Maven Central availability for easy `implementation(...)` integration.
- [x] **Solidifying ABR Telemetry:** Enhance metrics accuracy for HLS/DASH dynamic bitrate switching edge cases.
- [x] **Offline-Resilience:** Store-and-forward caching to flush telemetry when connectivity restores.
- [x] **Standalone Debug Overlay:** Drop-in floating widget for real-time local QA — no Logcat needed.
- [x] **Session Timeline:** Periodic reporting of playback state, network type, and buffering changes for Grafana visualization.

---

## How It Works (Today)

1. The **Android SDK** attaches to your player.
2. It aggregates playback metrics during the session.
3. When the session ends, it prints a **formatted summary** to **Logcat**.
4. (Optional) If configured, it sends **periodic updates** (real-time) and a **final summary** to your backend.

**When does a session end?**
- Explicitly calling `analytics.detach()`
- Calling `analytics.attach(player)` with a new player instance (automatically detaches the previous one)

---

## Implementation

To integrate the Media3Watch SDK into your Android project:

### Option A: From Maven Central (Recommended for external consumers)

1. **Ensure Maven Central is in your repositories** (in `settings.gradle.kts` or `build.gradle.kts`):
   ```kotlin
   repositories {
       mavenCentral()
   }
   ```

2. **Add the dependencies** to your `app/build.gradle.kts`:
   ```kotlin
   implementation("io.github.oguzhaneksi:media3watch-sdk:<version_name>")
   debugImplementation("io.github.oguzhaneksi:media3watch-overlay:<version_name>") // optional
   ```

### Option B: Local project reference (For contributors)

1. **Add the dependencies** to your `app/build.gradle.kts`:
   ```kotlin
   implementation(project(":sdk"))
   debugImplementation(project(":overlay")) // optional
   ```

---

### Usage

**Initialize and attach** the analytics in your Player implementation (e.g., ViewModel):
```kotlin
// 1. Create the analytics instance (with optional backend upload)
private val analytics = Media3WatchAnalytics(
    context = context,
    config = Media3WatchConfig(
        backendUrl = "http://localhost:8080/v1/sessions", // optional, use this for local testing
        apiKey = "dev-key", // optional, matches backend default
        enableRealTimeReporting = true, // default: true
        reportingIntervalMs = 15_000L, // default: 15s
        enableLogging = true, // set to false in production to suppress Logcat output
        enableOfflineResilience = true, // default: true — queues failed uploads to disk and retries on next session
        maxQueuedPayloads = 100 // default: 100 — max payloads kept on disk (FIFO eviction)
    )
)
// Or use default config for Logcat-only mode:
// private val analytics = Media3WatchAnalytics(context = context)

fun initializePlayer() {
    player = ExoPlayer.Builder(context).build().apply {
        // 2. Attach the analytics listener
        analytics.attach(this)
        
        setMediaItem(MediaItem.fromUri(url))
        prepare()
    }
    
    // 3. Log playback request to start measuring startup time
    analytics.playRequested()
}

fun releasePlayer() {
    // 4. Detach ALWAYS before releasing the player to capture final stats
    analytics.detach()
    player?.release()
}
```

---

## Viewing the Summary in Logcat

Filter by tag `Media3WatchAnalytics`:

```bash
# Recommended filter
adb logcat -s Media3WatchAnalytics
```

You should see a formatted summary similar to this:

```text
session_end
  sessionId: a1b2c3d4-e5f6-7890-abcd-ef1234567890
  timestamp: 1740470400000
  sessionStartDateIso: 2026-02-14T10:30:00.000Z
  sessionDurationMs: 45000
  startupTimeMs: 450
  rebufferTimeMs: 1200
  rebufferCount: 2
  playTimeMs: 42000
  rebufferRatio: 0.028
  totalDroppedFrames: 12
  totalSeekCount: 1
  totalSeekTimeMs: 300
  meanVideoFormatBitrate: 2500000
  errorCount: 0
  deviceModel: Pixel 7 Pro
  osVersion: 34
  sdkVersion: 1.0.1
  connectionType: Wi-Fi
```

---

## Debug Overlay

A floating, collapsible widget for real-time local QA — no Logcat required. Add it to **debug builds only**.

```kotlin
private val overlay = Media3WatchOverlay(
    config = OverlayConfig(
        initialPosition = OverlayPosition.TOP_END,   // TOP_START | TOP_END | BOTTOM_START | BOTTOM_END
        initialState = OverlayState.COLLAPSED         // COLLAPSED | EXPANDED
    )
)

fun initializePlayer() {
    analytics.attach(player)
    overlay.attach(analytics, this) // Activity or ViewGroup
}

fun releasePlayer() {
    overlay.detach()
    analytics.detach()
    player?.release()
}
```

The collapsed **pill** shows `▶ STATE | Start Xms | Reb N | Err N` with a color-coded health indicator:
- 🟢 Healthy
- 🟡 Rebuffer ratio > 2%
- 🔴 Rebuffer ratio > 5% or any error

Tap `≡` to expand a full stats card. Drag to reposition — snaps to the nearest corner.

---

## Backend & Grafana Setup

Want to visualize your sessions?

**1. Start the stack:**
```bash
cd backend
cp .env.example .env
docker-compose up -d --build
```

**2. Access the Dashboard:**
* Open **[http://localhost:3000](http://localhost:3000)**
* Login: `admin` / `admin`
* Go to **Dashboards** → **Media3Watch Overview**

**3. Configure the SDK:**
Update your `Media3WatchAnalytics` config to point to your local machine:

```kotlin
private val analytics = Media3WatchAnalytics(
    context = context,
    config = Media3WatchConfig(
        backendUrl = "http://10.0.2.2:8080/v1/sessions", // Android Emulator -> Host
        // backendUrl = "http://localhost:8080/v1/sessions", // Physical Device on same Wi-Fi
        apiKey = "dev-key",
        enableRealTimeReporting = true, // Optional, defaults to true
        reportingIntervalMs = 15_000L, // Optional, defaults to 15s
        enableLogging = true // Optional, set to false in production to suppress Logcat output
    )
)
```

**4. Verify Data Flow:**
1. Play a video in your app.
2. Wait for the session to end (detach or background app).
3. Check the logs: `adb logcat -s Media3WatchAnalytics` (look for `session_report_success`).
4. Refresh the Grafana dashboard to see the new data.

**Cleanup:**
```bash
cd backend
docker-compose down -v  # Stops containers + deletes data
```

See `backend/README.md` for full API details and troubleshooting.

---

## Contributing

PRs welcome — especially around:

* session lifecycle edge cases (content switch, player replace, next episode)
* metric definitions (startup time, rebuffer ratio, errors)
* test app scenarios

