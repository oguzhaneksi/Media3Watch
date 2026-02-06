# 📺 Media3Watch

<div align="center">

**Open-source QoE debugging and lightweight analytics for Android Media3 (ExoPlayer).**

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-green.svg?style=flat-square)](LICENSE)
[![Android](https://img.shields.io/badge/Android-Media3%201.2+-3DDC84?style=flat-square&logo=android)](https://developer.android.com/media/media3)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?style=flat-square&logo=docker)](docker-compose.yml)

[Why This Exists](#why-this-exists) • [Features](#features) • [Quick Start](#quick-start) • [Data Model](#data-model) • [Roadmap](#roadmap)

</div>

---

## Why This Exists

You ship a video app with Media3. Works great in development. Then production hits—users complain about buffering, your PM asks for startup time metrics, and you have nothing.

**Your options:**

| Solution | Problem |
|----------|---------|
| Mux / Bitmovin / Conviva | Expensive, vendor lock-in, data on their servers |
| Build from scratch | 3 months of work, constant maintenance |
| Media3Watch | Debug sessions fast, share JSON, track QoE trends |

**Media3Watch is:**
- **Self-hostable** — Your data stays on your infrastructure
- **Local-first** — Debug overlay works offline, upload when ready
- **Lightweight** — Postgres + Grafana, no complex pipelines
- **Open-source** — Apache 2.0, forever free

---

## Features

### Android SDK

The `media3watch-sdk` module wraps Media3's `AnalyticsListener` and produces a per-session summary.

**Collected metrics:**
- `sessionId` — Unique session identifier
- `contentId` — Optional content identifier for grouping
- `streamType` — `VOD` or `LIVE`
- `startupTimeMs` — Time from play request to first frame
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

```kotlin
// Call when user initiates playback (e.g., taps play button)
Media3Watch.markPlayRequested()

// SDK automatically measures time to first frame
```

This ensures accurate startup time even when preloading or buffering before user intent.

---

### Session Inspector Overlay

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

```
┌─────────────────────────────────────────────────────────────────┐
│                        Android App                              │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────────────┐  │
│  │ Media3      │───▶│ Media3Watch │───▶│ Session Inspector   │  │
│  │ ExoPlayer   │    │ SDK         │    │ (overlay + export)  │  │
│  └─────────────┘    └──────┬──────┘    └─────────────────────┘  │
│                            │                                     │
│                    ┌───────▼───────┐                            │
│                    │ Local Queue   │                            │
│                    │ (offline-first)│                           │
│                    └───────┬───────┘                            │
└────────────────────────────┼────────────────────────────────────┘
                             │ HTTPS POST /v1/sessions
                             ▼
              ┌──────────────────────────────┐
              │    Ingest API (Kotlin)      │
              │   - API key validation       │
              │   - Schema validation        │
              │   - Idempotent upsert        │
              └──────────────┬───────────────┘
                             │
                             ▼
              ┌──────────────────────────────┐
              │         Postgres             │
              │   - sessions table           │
              │   - JSON column for payload  │
              └──────────────┬───────────────┘
                             │
                             ▼
              ┌──────────────────────────────┐
              │          Grafana             │
              │   - QoE Overview             │
              │   - Breakdown by type        │
              │   - Session Explorer         │
              └──────────────────────────────┘
```

---

## Quick Start

### Prerequisites

- Docker & Docker Compose
- Android Studio

### 1. Start the Backend

```bash
git clone https://github.com/yourusername/media3watch.git
cd media3watch

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
    implementation("io.media3watch:sdk:1.0.0")
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

Attach to your player:

```kotlin
val player = ExoPlayer.Builder(context).build()

// Attach SDK
Media3Watch.attach(player)

// Set content metadata
Media3Watch.setContentId("video-123")
Media3Watch.setStreamType(StreamType.VOD)

// Mark when user requests playback (for accurate startup time)
playButton.setOnClickListener {
    Media3Watch.markPlayRequested()
    player.play()
}
```

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
    "userId": "user-abc",
    "experimentGroup": "variant-b"
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
| `startupTimeMs` | integer | No | Time from `markPlayRequested()` to first frame |
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

### Schema Versioning

The `schemaVersion` field allows the backend to handle different payload versions. When adding new fields:

1. Add as optional fields (nullable)
2. Bump minor version (e.g., `1.0.0` → `1.1.0`)
3. Backend continues accepting older versions

---

## Project Structure

```
media3watch/
├── sdk/                    # Android SDK (Kotlin)
│   ├── core/               # Session tracking, listeners
│   ├── inspector/          # Debug overlay UI
│   └── transport/          # Local queue, HTTP upload
├── backend/                # Kotlin service
│   ├── cmd/ingest/         # Main entry point
│   ├── internal/api/       # HTTP handlers
│   └── internal/db/        # Postgres repository
├── dashboards/             # Grafana dashboard JSON
├── migrations/             # Postgres schema migrations
├── docker-compose.yml      # Local development stack
└── docs/                   # Additional documentation
```

---

## Configuration

### SDK Configuration

```kotlin
Media3Watch.init(context) {
    // Remote endpoint
    endpoint = "https://your-backend.com/v1/sessions"
    apiKey = "your-api-key"
    
    // Inspector overlay (local debugging)
    enableInspector = true
    
    // Upload behavior
    uploadOnSessionEnd = true      // Auto-upload when session ends
    uploadOnBackground = true      // Upload when app backgrounds
    maxRetries = 3                 // Retry failed uploads
    
    // Privacy
    collectDeviceInfo = true       // Include device model, OS
    anonymizeSessionId = false     // Use random session IDs
}
```

### Backend Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `M3W_API_KEY` | `dev-key` | Required API key for ingestion |
| `DATABASE_URL` | (required) | Postgres connection string |
| `PORT` | `8080` | HTTP server port |
| `LOG_LEVEL` | `info` | Logging verbosity |

---

## Roadmap

### v1.0 (MVP) — Current

- [x] Android SDK with session summary collection
- [x] `markPlayRequested()` for explicit startup measurement
- [x] Session Inspector overlay (timeline, stats, export/share)
- [x] Backend ingest API with Postgres storage
- [x] Grafana dashboards (QoE overview, breakdown, session explorer)

### v1.1 — Short-term

- [ ] Optional raw event timeline storage (short retention)
- [ ] Click-through from session explorer to event timeline
- [ ] Configurable retention policies
- [ ] Basic DRM timing metrics (license fetch duration)

### v2.0+ — Future

> **Note:** The following features are not in MVP and require additional infrastructure.

- [ ] Event streaming pipeline (Redis Streams or Kafka)
- [ ] Event processor service for aggregation
- [ ] OpenSearch for full-text event search
- [ ] Prometheus metrics export and alerting
- [ ] Vendor compatibility modes (Mux, Bitmovin, FastPix semantics)
- [ ] Ads/SSAI analytics integration
- [ ] Advanced DRM analytics

---

## Out of Scope (MVP)

The following are explicitly **not included** in the current release:

- **Full raw event pipeline** — No Redis Streams, Kafka, or event processor
- **OpenSearch** — Postgres handles session storage; no full-text event search
- **Prometheus/alerting** — No time-series metrics export in MVP
- **Vendor compatibility modes** — Single schema, no Mux/Bitmovin mapping
- **Ads/SSAI analytics** — Session-level metrics only
- **Deep DRM analytics** — Basic error category only, no detailed breakdowns

See [Roadmap](#roadmap) for future plans.

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
