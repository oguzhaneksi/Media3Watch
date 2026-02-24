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
- **Visualization**: Grafana 11.4.0
- **Migrations**: Flyway
- **Build Tool**: Gradle (Kotlin DSL)

## 📋 Prerequisites

- **Docker** and **Docker Compose**

That's it. No JDK installation required if you're just running the backend.

## 🏃 Quick Start

From the `backend/` directory:

1. **Set up environment variables:**
   ```bash
   cp .env.example .env
   # Edit .env if needed (defaults work for local development)
   ```

2. **Start the services:**
   ```bash
   docker-compose up -d --build
   ```

**What this does:**
- Spins up PostgreSQL (port `5433`)
- Runs database migrations (creates `sessions` table)
- Starts the backend API (port `8080`)
- Starts Grafana dashboard (port `3000`)

**Verify it's running:**
```bash
# Backend Health
curl http://localhost:8080/health
# {"status":"healthy"}

# Grafana Dashboard
# Open http://localhost:3000 (admin / admin)
```

## 🔧 Configuration

**SECURITY NOTICE:** This backend is designed for local development. Database credentials and API keys must be configured via environment variables and should **never be hardcoded** in docker-compose.yml.

### Setting Up Environment Variables

1. **Copy the example environment file:**
   ```bash
   cp .env.example .env
   ```

2. **Edit `.env` with your credentials:**
   ```bash
   # For local development, you can use the default credentials:
   POSTGRES_USER=m3w
   POSTGRES_PASSWORD=m3w
   
   # For shared or production-like environments, use strong credentials:
   POSTGRES_USER=your_secure_username
   POSTGRES_PASSWORD=your_strong_password_here
   ```

3. **Never commit `.env` to version control** — it's already in `.gitignore`.

### Environment Variables Reference

The backend reads from environment variables. All sensitive values must be set in your `.env` file.

| Variable | Description | Required | Example/Default |
| :--- | :--- | :--- | :--- |
| `M3W_API_KEY` | API key for authentication | No | `dev-key` |
| `POSTGRES_DB` | PostgreSQL database name | **Yes** | `media3watch` |
| `POSTGRES_USER` | PostgreSQL username | **Yes** | `m3w` (dev), customize for production |
| `POSTGRES_PASSWORD` | PostgreSQL password | **Yes** | `m3w` (dev), use strong password for production |
| `DATABASE_URL` | PostgreSQL JDBC URL | **Yes** | `jdbc:postgresql://postgres:5432/media3watch` |
| `DATABASE_USER` | Database username (must match POSTGRES_USER) | **Yes** | Same as `POSTGRES_USER` |
| `DATABASE_PASSWORD` | Database password (must match POSTGRES_PASSWORD) | **Yes** | Same as `POSTGRES_PASSWORD` |
| `PORT` | Server port | No | `8080` |
| `RATE_LIMIT_REQUESTS` | Max requests per rate-limit window | No | `100` |
| `RATE_LIMIT_WINDOW_SEC` | Rate-limit window duration in seconds | No | `60` |
| `RETENTION_DAYS` | Days to retain session data before cleanup | No | `90` |
| `HIKARI_MAX_POOL_SIZE` | HikariCP maximum connection pool size | No | `20` |
| `HIKARI_MIN_IDLE` | HikariCP minimum idle connections | No | `5` |
| `LOG_LEVEL` | Logging level (`DEBUG`, `INFO`, `WARN`, `ERROR`) | No | `INFO` |
| `ENABLE_METRICS` | Enable Prometheus `/metrics` endpoint | No | `true` |

**For production or shared environments:**
- Use strong, unique passwords
- Rotate credentials regularly
- Use secret management tools (e.g., AWS Secrets Manager, HashiCorp Vault)
- Never expose credentials in docker-compose.yml or commit them to git

## 📊 Grafana Dashboards

The stack comes with **pre-configured dashboards** automatically provisioned from local files.

* **URL**: [http://localhost:3000](http://localhost:3000)
* **Default Credentials**: `admin` / `admin`
* **Provisioning Path**: `backend/grafana/dashboards/`

### Available Dashboards:
1. **Media3Watch Overview**: High-level metrics like Total Sessions, Startup Time, Rebuffer Ratio, and Error Rates.

Changes to JSON files in `backend/grafana/dashboards/` are reflected on container restart.

## 📡 API Endpoints

### 1. Health Check

```
GET /health
```

**Response:**
```json
{
  "status": "healthy",
  "timestamp": 1708000000000
}
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

**Rate Limit Error** (`429 Too Many Requests`):
```json
{
  "error": {
    "code": "RATE_LIMIT_EXCEEDED",
    "message": "Too many requests",
    "timestamp": 1698402000000
  }
}
```

**Payload Too Large** (`413 Payload Too Large`):
```json
{
  "error": {
    "code": "INVALID_SCHEMA",
    "message": "Payload exceeds maximum size",
    "timestamp": 1698402000000
  }
}
```

**Database Error** (`503 Service Unavailable`):
```json
{
  "error": {
    "code": "DATABASE_ERROR",
    "message": "Temporary storage issue",
    "timestamp": 1698402000000
  }
}
```

#### Validation Rules

The following rules are enforced on the request body:

| Field | Rule |
| :--- | :--- |
| `sessionId` | Required, non-blank, max 128 chars, must be a valid UUID |
| `timestamp` | Required, must be a positive integer |
| `sessionStartDateIso` | Required, non-blank |
| `sessionDurationMs` | Required, must be > 0 |
| `startupTimeMs` | Optional, must be ≥ 0 if provided |
| `rebufferTimeMs` | Optional, must be ≥ 0 if provided |
| `rebufferCount` | Optional, must be ≥ 0 if provided |
| `playTimeMs` | Optional, must be ≥ 0 if provided |
| `rebufferRatio` | Optional, must be between 0 and 1 (inclusive) if provided |
| `totalDroppedFrames` | Optional, must be ≥ 0 if provided |
| `totalSeekCount` | Optional, must be ≥ 0 if provided |
| `totalSeekTimeMs` | Optional, must be ≥ 0 if provided |
| `meanVideoFormatBitrate` | Optional, must be ≥ 0 if provided |
| `errorCount` | Optional, must be ≥ 0 if provided |

**Request body limit:** 64 KB. Requests exceeding this are rejected with `413`.

---

### 3. Metrics (Optional)

```
GET /metrics
```

**Headers:**
```
X-API-Key: dev-key
```

Prometheus-formatted metrics. Useful if you want to track ingestion stats.

> **Note:** This endpoint is only registered when `ENABLE_METRICS=true` (default). If disabled, the route returns `404 Not Found`.

## � Rate Limiting

The `/v1/sessions` endpoint is rate-limited per API key. The default limit is **100 requests per 60 seconds**. Exceeding the limit results in a `429 Too Many Requests` response with error code `RATE_LIMIT_EXCEEDED`.

Configure with `RATE_LIMIT_REQUESTS` and `RATE_LIMIT_WINDOW_SEC` environment variables.

## 🗄 Data Retention

A background job runs on startup and then **every 24 hours** to delete sessions older than `RETENTION_DAYS` (default: 90 days). Adjust the retention window with the `RETENTION_DAYS` environment variable.

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

