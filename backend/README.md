# Media3Watch Backend

A lightweight Kotlin backend built with Ktor for ingesting media session data from the Media3Watch Android SDK. Designed for **local development** — just run `docker-compose up` and start testing.

## 🎯 Purpose

This backend exists to:
- **Store session summaries** sent from the Android SDK
- **Provide a local testing environment** for developers integrating the SDK
- **Enable future analytics** (Grafana dashboards, retention queries, etc.)

**This is NOT a production-ready service.** It's optimized for simplicity and fast local iteration.

## 🛠 Tech Stack

- **Language**: Kotlin 2.3.10 (JVM 21)
- **Framework**: Ktor 3.4.0
- **Database**: PostgreSQL 16
- **Migrations**: Flyway
- **Build Tool**: Gradle (Kotlin DSL)

## 📋 Prerequisites

- **Docker** and **Docker Compose**

That's it. No JDK installation required if you're just running the backend.

## 🏃 Quick Start

From the `backend/` directory:

```bash
docker-compose up -d --build
```

**What this does:**
- Spins up PostgreSQL (port `5432`)
- Runs database migrations (creates `sessions` table)
- Starts the backend API (port `8080`)

**Verify it's running:**
```bash
curl http://localhost:8080/health
```

Expected response:
```json
{"status":"healthy"}
```

## 🔧 Configuration

The backend reads from environment variables. Defaults are set for local development — **you don't need to change anything** to get started.

| Variable | Description | Default |
| :--- | :--- | :--- |
| `M3W_API_KEY` | API key for authentication | `dev-key` |
| `DATABASE_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://postgres:5432/media3watch` |
| `DATABASE_USER` | Database username | `m3w` |
| `DATABASE_PASSWORD` | Database password | `m3w` |
| `PORT` | Server port | `8080` |

**To override:** Create a `.env` file in `backend/` or set them in `docker-compose.yml`.

## 📡 API Endpoints

### 1. Health Check

```
GET /health
```

**Response:**
```json
{"status": "healthy"}
```

---

### 2. Ingest Session

```
POST /v1/sessions
```

**Headers:**
```
X-API-Key: dev-key
Content-Type: application/json
```

**Request Body** (from Android SDK):
```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": 1708000000000,
  "sessionStartDateIso": "2026-02-15T10:00:00.000Z",
  "sessionDurationMs": 45000,
  "startupTimeMs": 450,
  "rebufferTimeMs": 1200,
  "rebufferCount": 2,
  "playTimeMs": 42000,
  "rebufferRatio": 0.028,
  "totalDroppedFrames": 12,
  "totalSeekCount": 1,
  "totalSeekTimeMs": 300,
  "meanVideoFormatBitrate": 2500000,
  "errorCount": 0
}
```

**Success Response** (`200 OK`):
```json
{
  "status": "success",
  "sessionId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Error Response** (`400 Bad Request`):
```json
{
  "error": {
    "code": "INVALID_SCHEMA",
    "message": "Missing or empty required field: sessionId",
    "timestamp": 1698402000000
  }
}
```

**Auth Error** (`401 Unauthorized`):
```json
{
  "error": {
    "code": "INVALID_API_KEY",
    "message": "Invalid or missing API Key",
    "timestamp": 1698402000000
  }
}
```

---

### 3. Metrics (Optional)

```
GET /metrics
```

Prometheus-formatted metrics. Useful if you want to track ingestion stats.

## 📂 Project Structure

```
backend/
├── src/main/kotlin/com/media3watch/
│   ├── Application.kt         # Main entry point
│   ├── api/
│   │   ├── HealthRoutes.kt    # GET /health
│   │   ├── SessionsRoutes.kt  # POST /v1/sessions
│   │   └── MetricsRoutes.kt   # GET /metrics
│   ├── config/                # Environment variable loading
│   ├── db/                    # PostgreSQL + Flyway migrations
│   ├── domain/                # SessionSummary data model
│   ├── observability/         # Error responses
│   └── security/              # API key authentication
├── docker-compose.yml         # Postgres + Backend orchestration
└── build.gradle.kts           # Gradle build config
```

## 🧹 Cleanup

To stop and remove all containers + data:

```bash
docker-compose down -v
```

The `-v` flag deletes the PostgreSQL volume, giving you a fresh database on next startup.

