# Media3Watch

**Debug video sessions fast. Get a session summary in Logcat.**

> Status: **Android SDK (Alpha)** ✅  
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

## What You Get Today (Alpha)

- **Android SDK**
- **Session summary in Logcat** (plain text format, generated on session end)
- **Optional backend upload** (HTTP POST with JSON payload)
- **Grafana Dashboards** (Visualize trends & sessions)

---

## Metrics Collected

The SDK automatically tracks and summarizes:
- **Startup Time:** Delay from play request to first frame.
- **Rebuffer Metrics:** Total time spent rebuffering and total rebuffer count.
- **Playback Stats:** Total play time, rebuffer ratio, and dropped frames.
- **Interaction Stats:** Seek count and total seek time.
- **Quality Stats:** Mean video bitrate.
- **Errors:** Total fatal error count.

---

## Roadmap (Planned)

- [x] Self-hostable backend (store session summaries)
- [x] Grafana dashboards (QoE trends + session drill-down)
- JSON export format (file / clipboard)
- Optional debug overlay (offline-friendly)

---

## How It Works (Today)

1. The **Android SDK** attaches to your player.
2. It aggregates playback metrics during the session.
3. When the session ends, it prints a **formatted summary** to **Logcat**.
4. (Optional) It **uploads the session** to your backend for analysis.

**When does a session end?**
- Explicitly calling `analytics.detach()`
- Calling `analytics.attach(player)` with a new player instance (automatically detaches the previous one)

---

## Implementation

To integrate the Media3Watch SDK into your Android project:

1. **Add the dependency** to your `app/build.gradle.kts`:
   ```kotlin
   implementation(project(":sdk"))
   ```

2. **Initialize and attach** the analytics in your Player implementation (e.g., ViewModel):
   ```kotlin
   // 1. Create the analytics instance (with optional backend upload)
   private val analytics = Media3WatchAnalytics(
       config = Media3WatchConfig(
           backendUrl = "http://localhost:8080/v1/sessions", // optional, use this for local testing
           apiKey = "dev-key" // optional, matches backend default
       )
   )
   // Or use default config for Logcat-only mode:
   // private val analytics = Media3WatchAnalytics()

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

## Viewing the Summary in Logcat

Filter by tag `Media3WatchAnalytics`:

```bash
# Recommended filter
adb logcat -s Media3WatchAnalytics
```

You should see a formatted summary similar to this:

```text
session_end
  sessionId: 1
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
```

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
    config = Media3WatchConfig(
        backendUrl = "http://10.0.2.2:8080/v1/sessions", // Android Emulator -> Host
        // backendUrl = "http://localhost:8080/v1/sessions", // Physical Device on same Wi-Fi
        apiKey = "dev-key"
    )
)
```

**4. Verify Data Flow:**
1. Play a video in your app.
2. Wait for the session to end (detach or background app).
3. Check the logs: `adb logcat -s Media3WatchAnalytics` (look for "Upload success").
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

