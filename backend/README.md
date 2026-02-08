# Media3Watch Backend

A lightweight, high-performance ingestion engine for the [Media3Watch Android SDK](../android/README.md).

Designed for solo developers and small teams who need a simple, self-hosted solution to monitor video playback performance (QoE) without the complexity of enterprise streaming stacks.

## 🚀 Why this project exists?
Commercial video analytics tools (Conviva, Mux, Bitmovin) are powerful but expensive and often overkill for indie projects or small-scale apps.

Media3Watch Backend provides:
- **Zero-cost telemetry**: Own your data, hosted on your own infra.
- **Privacy by design**: Data stays in your database.
- **Developer simplicity**: One binary, one database, and a pre-configured Grafana dashboard.

## 🏗 High-Level Architecture
The backend is built with **Kotlin** and **Ktor**, optimized for low-latency ingestion and minimal resource footprint.

```text
[Android SDK] --- (JSON/HTTP) ---> [Ktor Backend] ---> [PostgreSQL] <--- [Grafana]
```

## 🔄 Data Flow
1. **SDK**: Aggregates playback events (startup, buffering, errors) into a `SessionSummary`.
2. **Transport**: Sends the summary via `POST /v1/sessions` (with automatic retry and idempotency).
3. **Backend**: Validates the `X-API-Key`, maps the JSON to structured columns, and stores the full payload in a `JSONB` column.
4. **Postgres**: Acts as the single source of truth.
5. **Grafana**: Queries Postgres directly to provide real-time dashboards for metrics like Startup Time (`player_startup_ms`) and Rebuffer Ratio.

## 📡 API Overview

### `POST /v1/sessions`
Ingests a completed playback session summary.

- **Headers**:
    - `X-API-Key: <your_secret_key>`
    - `Content-Type: application/json`
- **Body**: [SessionSummary JSON](../docs/schema.md)
- **Idempotency**: The `sessionId` field is used as a unique key. Repeated requests with the same `sessionId` will update the existing record (upsert).
- **Response**: `200 OK` on success.

### `GET /health`
Returns system health status for Docker/Kubernetes health checks.

## 🔑 Authentication
Authentication is handled via a simple `X-API-Key` header.
- The key is defined in environment variables.
- Requests without a valid key return `401 Unauthorized`.

### Rate Limiting (Production Ready)
To prevent abuse and ensure fair resource usage, the backend implements rate limiting:

```kotlin
// RouteRateLimit configuration in Application.kt
install(RateLimit) {
    register(RateLimitName("api-key-limit")) {
        rateLimiter(limit = 100, refillPeriod = 60.seconds)
        requestKey { applicationCall ->
            applicationCall.request.header("X-API-Key") ?: "anonymous"
        }
    }
}

// Apply to routes
routing {
    rateLimit(RateLimitName("api-key-limit")) {
        post("/v1/sessions") {
            // Handle session ingestion
        }
    }
}
```

**Current limits:**
- **100 requests per minute** per API key
- Exceeding the limit returns `429 Too Many Requests` with `Retry-After` header

**Future roadmap:**
- [ ] Multi-tenant API key support with per-tenant quotas
- [ ] API key rotation mechanism
- [ ] Distributed rate limiting (Redis-backed) for multi-instance deployments

## 📈 Scalability & Data Retention

### Current Performance Capacity
PostgreSQL handles the expected load efficiently for solo developers and small teams:

- ✅ **1-2M sessions**: Excellent performance with proper indexing
- ⚠️ **10M+ sessions**: Monitor query performance and consider partitioning
- ⚠️ **JSONB growth**: Large `payload` columns increase storage linearly

### Implemented Solutions

#### 1. Time-Based Partitioning
For high-volume environments, the database schema supports declarative partitioning:

```sql
-- Main sessions table (partitioned)
CREATE TABLE sessions (
    id BIGSERIAL,
    session_id VARCHAR(128) NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL,
    content_id VARCHAR(256),
    stream_type VARCHAR(16),
    player_startup_ms INTEGER,
    rebuffer_time_ms INTEGER,
    rebuffer_count INTEGER,
    error_count INTEGER,
    payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
) PARTITION BY RANGE (timestamp);

-- Auto-create monthly partitions
CREATE TABLE sessions_2026_01 PARTITION OF sessions
    FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');

CREATE TABLE sessions_2026_02 PARTITION OF sessions
    FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');
```

#### 2. Automated Data Retention
Default retention policy: **90 days**

Using `pg_cron` for production environments:

```sql
-- Enable pg_cron extension
CREATE EXTENSION IF NOT EXISTS pg_cron;

-- Schedule daily cleanup at 2 AM UTC
SELECT cron.schedule(
    'cleanup-old-sessions',
    '0 2 * * *',
    $$DELETE FROM sessions WHERE timestamp < NOW() - INTERVAL '90 days'$$
);
```

For lightweight deployments without `pg_cron`, use a cron job:

```bash
# Add to crontab: Daily cleanup at 2 AM
0 2 * * * psql -U m3w -d media3watch -c "DELETE FROM sessions WHERE timestamp < NOW() - INTERVAL '90 days';"
```

#### 3. Index Optimization
Core indexes for fast querying:

```sql
CREATE INDEX idx_sessions_timestamp ON sessions (timestamp DESC);
CREATE INDEX idx_sessions_content_id ON sessions (content_id) WHERE content_id IS NOT NULL;
CREATE INDEX idx_sessions_stream_type ON sessions (stream_type) WHERE stream_type IS NOT NULL;
CREATE INDEX idx_sessions_player_startup ON sessions (player_startup_ms) WHERE player_startup_ms IS NOT NULL;

-- Partial index for error analysis
CREATE INDEX idx_sessions_errors ON sessions (error_count, timestamp DESC) WHERE error_count > 0;
```

## 🔍 Observability & Error Handling

### Structured Logging
All requests are logged with structured metadata for easy troubleshooting:

```kotlin
install(CallLogging) {
    level = Level.INFO
    format { call ->
        val status = call.response.status()
        val method = call.request.httpMethod.value
        val path = call.request.path()
        val duration = call.processingTimeMillis()
        "$method $path - $status (${duration}ms)"
    }
    
    filter { call ->
        !call.request.path().startsWith("/health")
    }
}
```

**Log output example:**
```
INFO  POST /v1/sessions - 200 (45ms)
WARN  POST /v1/sessions - 401 (2ms) [Invalid API key]
ERROR POST /v1/sessions - 500 (120ms) [Database connection timeout]
```

### Application Metrics (Prometheus)
Monitoring critical KPIs for backend health:

```kotlin
install(MicrometerMetrics) {
    registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    
    meterBinders = listOf(
        JvmMemoryMetrics(),
        JvmGcMetrics(),
        ProcessorMetrics(),
        JvmThreadMetrics()
    )
}

// Expose metrics endpoint
routing {
    get("/metrics") {
        call.respond(registry.scrape())
    }
}
```

**Available metrics:**
- `http_server_requests_total` — Total requests by endpoint and status
- `http_server_request_duration_seconds` — Request latency distribution
- `db_connection_pool_active` — Active database connections
- `db_query_duration_seconds` — Database query latency
- `sessions_ingested_total` — Successful session writes

### Error Response Format
Standardized JSON error responses for client-side handling:

```json
{
  "error": {
    "code": "INVALID_SCHEMA",
    "message": "Missing required field: sessionId",
    "timestamp": 1706900000000
  }
}
```

**Error codes:**
- `INVALID_API_KEY` — Authentication failure (401)
- `RATE_LIMIT_EXCEEDED` — Too many requests (429)
- `INVALID_SCHEMA` — Malformed request body (400)
- `DATABASE_ERROR` — Temporary storage issue (503)
- `INTERNAL_ERROR` — Unexpected server error (500)

## ⚡ Idempotency & Conflict Resolution

### Atomic Upsert with Optimistic Locking
The backend guarantees **at-least-once delivery** with safe concurrent writes:

```kotlin
suspend fun upsertSession(session: SessionSummary): Result<Unit> {
    return try {
        database.execute { connection ->
            connection.prepareStatement("""
                INSERT INTO sessions (
                    session_id, timestamp, content_id, stream_type,
                    player_startup_ms, rebuffer_time_ms, rebuffer_count,
                    error_count, payload, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, NOW())
                ON CONFLICT (session_id) 
                DO UPDATE SET
                    timestamp = EXCLUDED.timestamp,
                    content_id = EXCLUDED.content_id,
                    stream_type = EXCLUDED.stream_type,
                    player_startup_ms = EXCLUDED.player_startup_ms,
                    rebuffer_time_ms = EXCLUDED.rebuffer_time_ms,
                    rebuffer_count = EXCLUDED.rebuffer_count,
                    error_count = EXCLUDED.error_count,
                    payload = EXCLUDED.payload,
                    created_at = EXCLUDED.created_at
                WHERE sessions.created_at <= EXCLUDED.created_at
            """).apply {
                setString(1, session.sessionId)
                setTimestamp(2, Timestamp(session.timestamp))
                // ... additional bindings
            }.executeUpdate()
        }
        Result.success(Unit)
    } catch (e: SQLException) {
        logger.error("Database upsert failed", e)
        Result.failure(e)
    }
}
```

**Guarantees:**
- ✅ Same `sessionId` → Update only if new data is newer (`created_at` check)
- ✅ Concurrent requests → Postgres serializable isolation prevents data corruption
- ✅ Network retry from SDK → Safe to retry, no duplicate metrics

## 🗄️ Database Connection Pooling

### HikariCP Configuration
Production-ready connection pool settings for optimal performance:

```kotlin
// application.conf
ktor {
    deployment {
        port = 8080
    }
}

database {
    driverClassName = "org.postgresql.Driver"
    jdbcUrl = ${DATABASE_URL}
    username = ${DATABASE_USER}
    password = ${DATABASE_PASSWORD}
    
    hikari {
        maximumPoolSize = 20
        minimumIdle = 5
        connectionTimeout = 30000      # 30 seconds
        idleTimeout = 600000           # 10 minutes
        maxLifetime = 1800000          # 30 minutes
        leakDetectionThreshold = 60000 # 1 minute
        
        # Performance tuning
        cachePrepStmts = true
        prepStmtCacheSize = 250
        prepStmtCacheSqlLimit = 2048
    }
}
```

**Rationale:**
- `maximumPoolSize = 20` — Sufficient for ~1,000 req/min with avg 50ms query time
- `minimumIdle = 5` — Keep warm connections for instant response
- `leakDetectionThreshold` — Detect connection leaks during development

**Monitoring pool health:**
```kotlin
val poolMXBean = hikariDataSource.hikariPoolMXBean
logger.info(
    "Pool stats: active=${poolMXBean.activeConnections}, " +
    "idle=${poolMXBean.idleConnections}, total=${poolMXBean.totalConnections}"
)
```

## 💾 Database Design Philosophy
We use **PostgreSQL** for its reliability and excellent JSON support.

- **Hybrid Schema**: Core metrics (startup time, rebuffer count) are stored in indexed relational columns for fast querying.
- **JSONB for Flexibility**: The entire raw payload is stored in a `JSONB` column, allowing you to add custom metadata in the SDK without changing the backend schema.
- **Migrations**: Database schema versioning is managed via **Flyway**.

## 🛠 Local Development (Docker Compose)
The fastest way to get started is using Docker Compose:

1. Clone the repo.
2. Run the stack:
   ```bash
   docker compose up -d
   ```
3. Access services:
    - **Backend**: `http://localhost:8080`
    - **Grafana**: `http://localhost:3000` (Default credentials: `admin`/`admin`)
    - **Postgres**: `localhost:5432`

## ⚙️ Environment Variables
### Core Configuration
| Variable | Description | Default |
|----------|-------------|---------|
| `M3W_API_KEY` | Secret key for SDK authentication | `dev-key` |
| `DATABASE_URL` | Postgres JDBC connection string | `jdbc:postgresql://postgres:5432/media3watch` |
| `DATABASE_USER` | Postgres username | `m3w` |
| `DATABASE_PASSWORD` | Postgres password | `m3w` |
| `PORT` | HTTP server port | `8080` |

### Advanced Configuration
| Variable | Description | Default |
|----------|-------------|---------|
| `RATE_LIMIT_REQUESTS` | Max requests per minute per API key | `100` |
| `RATE_LIMIT_WINDOW_SEC` | Rate limit window in seconds | `60` |
| `RETENTION_DAYS` | Session data retention period | `90` |
| `HIKARI_MAX_POOL_SIZE` | Maximum database connections | `20` |
| `HIKARI_MIN_IDLE` | Minimum idle connections | `5` |
| `LOG_LEVEL` | Logging verbosity (`INFO`, `DEBUG`, `WARN`) | `INFO` |
| `ENABLE_METRICS` | Enable Prometheus metrics endpoint | `true` |

## 🎯 MVP Scope & Non-Goals
**What this is:**
- High-efficiency session ingestion.
- Structured storage for analytics.
- Direct-to-DB visualization.

**What this is NOT (Non-Goals):**
- A user-facing dashboard (use Grafana).
- A real-time stream processing engine (no Kafka/Flink).
- An identity management system.

## 🗺 Roadmap
### ✅ Implemented (Production Ready)
- [x] **Rate limiting**: Per-API-key throttling (100 req/min)
- [x] **Structured logging**: Request/response logging with duration tracking
- [x] **Prometheus metrics**: Backend health monitoring (`/metrics` endpoint)
- [x] **HikariCP connection pooling**: Optimized database connection management
- [x] **Idempotent upserts**: Safe retry handling with optimistic locking
- [x] **Index optimization**: Fast queries for common analytics patterns
- [x] **Data retention strategy**: 90-day default with automated cleanup

### 📋 Short Term
- [ ] **Grafana dashboard provisioning**: Automated dashboard distribution
- [ ] **Partition management automation**: Auto-create monthly partitions
- [ ] **Alerting integration**: Spike detection for error rates

### 🔮 Mid-Long Term
- [ ] **Multi-tenant API keys**: Per-tenant authentication and quotas
- [ ] **API key rotation**: Zero-downtime key management
- [ ] **Distributed rate limiting**: Redis-backed for horizontal scaling
- [ ] **Advanced retention policies**: Configurable per content type or tenant

## 📜 License & Contribution
- **License**: Apache 2.0
- **Contribution**: PRs are welcome! As a solo-developed project, please open an issue first to discuss major changes.
