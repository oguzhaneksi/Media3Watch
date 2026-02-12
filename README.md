# 📺 Media3Watch

<div align="center">

**Open-source QoE debugging and lightweight analytics for Android Media3 (ExoPlayer).**

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-green.svg?style=flat-square)](LICENSE)
[![Android](https://img.shields.io/badge/Android-Media3%201.9+-3DDC84?style=flat-square&logo=android)](https://developer.android.com/media/media3)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?style=flat-square&logo=docker)](backend/docker-compose.yml)

[Why This Exists](#why-this-exists) • [Features](#features) • [Quick Start](#quick-start) • [Data Model](#data-model) • [Roadmap](#roadmap)

</div>

---

## Why This Exists

You ship a video app with Media3. Works great in development. Then production hits—users complain about buffering, your PM asks for startup time metrics, and you have nothing. Media3Watch is a **self-hostable**, **local-first**, **lightweight** alternative to expensive vendor solutions. Debug sessions fast, share JSON, track QoE trends—all on your infrastructure.

---

## Features

### Android SDK (Modular)

The `starter-media3` package provides a modular integration for Media3's `AnalyticsListener` and produces a per-session summary.

**Collected metrics:**
- `sessionId` — Unique session identifier
- `contentId` — Optional content identifier for grouping
- `streamType` — `VOD` or `LIVE`
- `startupTimeMs` — Time from play command to first frame (`player_startup_ms`)
- `rebufferTimeMs` — Total time spent rebuffering
- `rebufferCount` — Number of rebuffer events
- `rebufferRatio` — `rebufferTimeMs / (playTimeMs + rebufferTimeMs)`
- `errorCount` — Number of errors during session
- `lastErrorCode` — Most recent error code (if any)
- `lastErrorCategory` — Error category (`NETWORK`, `DRM`, `SOURCE`, `DECODER`, `UNKNOWN`)
- `qualitySwitchCount` — Number of quality/rendition changes
- `avgBitrateKbps` — Approximate average bitrate
- `droppedFrames` — Dropped frame count (when available)

**Explicit startup measurement:**

Current MVP focus is on **player_startup** only.

Formula:
`player_startup_ms = first_frame_rendered_ts - play_command_ts`

```kotlin
// Call EXACTLY when player.play() or playWhenReady = true is executed.
// This represents the play command, NOT the user tap or navigation start.
Media3Watch.markPlayRequested()
```

Notes:
- **Excluded:** Navigation, API calls, entitlement checks, and preload times are **not** part of this metric.
- **Media3 Integration:** `markPlayRequested()` is called manually when the play command is given. The first frame is captured automatically via Media3 callbacks.

---

### Inspector Overlay (Optional)

A local-first debug overlay for development and QA.

**Timeline events:**
- Play requested
- Player ready
- Buffering start/end
- Quality change
- Error occurred

**Live stats:**
- `startupMs` — Startup time
- `rebufferMs` — Total rebuffer duration
- `rebufferRatio` — Rebuffer ratio
- `errorCount` — Error count
- `lastErrorCategory` — Last error type
- `qualitySwitchCount` — Quality switches
- `avgBitrateKbps` — Approximate bitrate

**Actions:**
- Export session as JSON file
- Share session JSON via Android share sheet
- Upload this session to backend (optional)

```
┌──────────────────────────────────────────┐
│ Media3Watch Inspector                    │
├──────────────────────────────────────────┤
│ Session: a1b2c3d4  │ Startup: 1,234 ms   │
│ State: PLAYING     │ Rebuffer: 0.8%      │
├──────────────────────────────────────────┤
│ Timeline                                 │
│ 00:00 play_requested                     │
│ 00:01 ready                              │
│ 00:03 quality_change → 720p              │
│ 00:08 quality_change → 1080p             │
│ 01:15 buffering_start                    │
│ 01:16 buffering_end (1.2s)               │
├──────────────────────────────────────────┤
│ [Export JSON] [Share] [Upload] [Close]   │
└──────────────────────────────────────────┘
```

---

### Backend (Ingest API + Postgres)

A minimal backend that receives session summaries and stores them for querying.

- **Endpoint:** `POST /v1/sessions`
- **Auth:** API key via `X-API-Key` header
- **Storage:** Postgres (simple, reliable, easy to query)
- **Delivery:** At-least-once; sessions are idempotent by `sessionId`

---

### Grafana Dashboards

Pre-built dashboards for visualizing session data:

| Dashboard | Description |
|-----------|-------------|
| **QoE Overview** | Startup time distribution, rebuffer ratio trends, error rate |
| **Breakdown** | Metrics by `streamType` (VOD vs LIVE), top `contentId` values |
| **Session Explorer** | List/filter sessions, click to view full JSON |

---

## Architecture

**High-level flow:**
- Android SDK collects session metrics from Media3
- Optional inspector overlay for local debugging
- Sessions queue locally and upload to backend
- Backend stores in Postgres (relational + JSONB)
- Grafana dashboards visualize QoE trends

For detailed SDK architecture, see [android/README.md](android/README.md).

---

## Quick Start

### Prerequisites

- Docker & Docker Compose
- Android Studio (Ladybug or newer)
- Android App: `minSdk 23`, `targetSdk 36`
- Build Environment: AGP 8.13.2+, Kotlin 2.3.0+

### 1. Start the Backend

```bash
git clone https://github.com/oguzhaneksi/Media3Watch.git
cd Media3Watch

# Start services
docker compose up -d

# Verify
docker compose ps
```

Expected output:
```
NAME            STATUS    PORTS
m3w-ingest      running   0.0.0.0:8080->8080/tcp
m3w-postgres    running   0.0.0.0:5432->5432/tcp
m3w-grafana     running   0.0.0.0:3000->3000/tcp
```

**Environment variables** (set in `.env` or `docker-compose.yml`):

| Variable | Default | Description |
|----------|---------|-------------|
| `M3W_API_KEY` | `dev-key` | API key for session ingestion |
| `DATABASE_URL` | `postgres://m3w:m3w@postgres:5432/media3watch` | Postgres connection |
| `GRAFANA_PASSWORD` | `admin` | Grafana admin password |

Open Grafana at http://localhost:3000 (login: `admin` / `admin`).

### 2. Integrate the Android SDK

Add the dependency:

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.media3watch:starter-media3:1.0.0")
}
```

Initialize in your Application:

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        Media3Watch.init(this) {
            // Always enable local inspector in debug builds
            enableInspector = BuildConfig.DEBUG
            
            // Remote upload (optional in debug, recommended in release)
            endpoint = "https://your-m3w-backend.com/v1/sessions"
            apiKey = BuildConfig.M3W_API_KEY
        }
    }
}
```

Attach to your player and mark play requested:

```kotlin
val player = ExoPlayer.Builder(context).build()

// 1. Attach (starts session tracking)
Media3Watch.attach(player)

// 2. Mark when play() is called (starts startup timer)
Media3Watch.markPlayRequested()
player.play()

// 3. Detach when releasing (ends and uploads session)
Media3Watch.detach()
player.release()
```

**⚠️ Important**: You MUST call `Media3Watch.detach()` before releasing the player or leaving playback permanently. Failure to detach will prevent session metrics from uploading and may cause memory leaks.

### 3. Test with curl

Send a sample session summary:

```bash
curl -X POST http://localhost:8080/v1/sessions \
  -H "Content-Type: application/json" \
  -H "X-API-Key: dev-key" \
  -d '{
    "schemaVersion": "1.0.0",
    "sessionId": "abc-123-def",
    "timestamp": 1706900000000,
    "contentId": "video-456",
    "streamType": "VOD",
    "startupTimeMs": 1850,
    "playTimeMs": 120000,
    "rebufferTimeMs": 1200,
    "rebufferCount": 2,
    "rebufferRatio": 0.0099,
    "errorCount": 0,
    "lastErrorCode": null,
    "lastErrorCategory": null,
    "qualitySwitchCount": 3,
    "avgBitrateKbps": 4200,
    "droppedFrames": 12,
    "device": {
      "model": "Pixel 8",
      "os": "Android",
      "osVersion": "14"
    },
    "app": {
      "name": "MyApp",
      "version": "1.0.0"
    }
  }'
```

Verify the response:

```json
{"status": "ok", "sessionId": "abc-123-def"}
```

Check Grafana—data should appear in the dashboards.

---

## Data Model

### Session Summary JSON

Each session produces one JSON document submitted to `/v1/sessions`.

```json
{
  "schemaVersion": "1.0.0",
  "sessionId": "uuid-v4",
  "timestamp": 1706900000000,
  
  "contentId": "optional-content-id",
  "streamType": "VOD",
  
  "startupTimeMs": 1850,
  "playTimeMs": 120000,
  "rebufferTimeMs": 1200,
  "rebufferCount": 2,
  "rebufferRatio": 0.0099,
  
  "errorCount": 0,
  "lastErrorCode": null,
  "lastErrorCategory": null,
  
  "qualitySwitchCount": 3,
  "avgBitrateKbps": 4200,
  "droppedFrames": 12,
  
  "device": {
    "model": "Pixel 8",
    "os": "Android",
    "osVersion": "14"
  },
  "app": {
    "name": "MyApp",
    "version": "1.0.0"
  },
  
  "custom": {
    "experimentGroup": "variant-b",
    "featureSet": "new_player_ui",
    "cdn": "cloudfront"
  }
}
```

### Field Reference

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `schemaVersion` | string | Yes | Schema version for forward compatibility |
| `sessionId` | string | Yes | Unique session ID (UUID v4 recommended) |
| `timestamp` | integer | Yes | Session end time (Unix ms) |
| `contentId` | string | No | Content identifier for grouping |
| `streamType` | string | No | `VOD` or `LIVE` |
| `startupTimeMs` | integer | No | `first_frame_rendered_ts - play_command_ts` |
| `playTimeMs` | integer | No | Total playback time |
| `rebufferTimeMs` | integer | No | Total rebuffering time |
| `rebufferCount` | integer | No | Number of rebuffer events |
| `rebufferRatio` | float | No | `rebufferTimeMs / (playTimeMs + rebufferTimeMs)` |
| `errorCount` | integer | No | Total errors |
| `lastErrorCode` | integer | No | Most recent error code |
| `lastErrorCategory` | string | No | `NETWORK`, `DRM`, `SOURCE`, `DECODER`, `UNKNOWN` |
| `qualitySwitchCount` | integer | No | Quality/rendition changes |
| `avgBitrateKbps` | integer | No | Approximate average bitrate |
| `droppedFrames` | integer | No | Dropped frame count |
| `device` | object | No | Device info (`model`, `os`, `osVersion`) |
| `app` | object | No | App info (`name`, `version`) |
| `custom` | object | No | User-defined key-value pairs |

---

## Project Structure

```
media3watch/
├── android/                # Android Project Root
│   └── sdk/                # Modular Android SDK
│       ├── schema/         # Pure Kotlin: JSON schema & versions
│       ├── core/           # Pure Kotlin: Session state machine
│       ├── android-runtime/# Android Glue: Context, Storage, Lifecycle
│       ├── adapter-media3/ # Media3 (ExoPlayer) events mapping
│       ├── transport-okhttp/# Optional: Local queue & OkHttp upload
│       ├── inspector-overlay/# Optional: View-based debug overlay
│       └── starter-media3/ # Meta-package: All-in-one dependency
├── backend/                # Kotlin (Ktor) service
│   ├── src/                # Standard Kotlin source sets
│   │   ├── main/kotlin/    # API handlers, DB repository, and models
│   │   └── main/resources/ # Flyway migrations and config
│   └── build.gradle.kts    # Gradle build configuration
├── dashboards/             # Grafana dashboard JSON
├── migrations/             # Postgres schema migrations (Flyway)
├── docker-compose.yml      # Local development stack
└── docs/                   # Additional documentation
```

---

## Configuration

See SDK init examples in [Quick Start](#quick-start) section.

**Backend environment variables:**

| Variable | Default | Description |
|----------|---------|-------------|
| `M3W_API_KEY` | `dev-key` | Required API key for ingestion |
| `DATABASE_URL` | `jdbc:postgresql://postgres:5432/media3watch` | Postgres JDBC connection string |
| `DATABASE_USER` | `m3w` | Postgres username |
| `DATABASE_PASSWORD` | `m3w` | Postgres password |
| `PORT` | `8080` | HTTP server port |
| `LOG_LEVEL` | `info` | Logging verbosity |

---

## Roadmap

### v1.0 (MVP)

- [ ] Android SDK with session summary collection
- [ ] `markPlayRequested()` for explicit startup measurement
- [ ] Session Inspector overlay (timeline, stats, export/share)
- [ ] Backend ingest API with Postgres storage
- [ ] Grafana dashboards (QoE overview, breakdown, session explorer)

---

## Contributing

- 🐛 **Bug reports** — Open an issue
- ✨ **Feature requests** — Open an issue with `[Feature]` prefix
- 🔧 **Pull requests** — Fork, branch, PR

See [CONTRIBUTING.md](CONTRIBUTING.md) for details.

---

## License

Apache 2.0

---

## Acknowledgments

- [SRG SSR / Pillarbox](https://github.com/SRGSSR/pillarbox-android) — Architecture inspiration
- [Media3 Team](https://developer.android.com/media/media3) — The foundation
- [Grafana](https://grafana.com/) — Visualization
