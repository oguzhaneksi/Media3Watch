# Media3Watch Backend

A lightweight, high-performance ingestion engine for the [Media3Watch Android SDK](../android/README.md).

Designed for solo developers and small teams who need a simple, self-hosted solution to monitor video playback performance (QoE) without the complexity of enterprise streaming stacks.

## 🚀 Why this project exists?
Commercial video analytics tools (Conviva, Mux, Bitmovin) are powerful but expensive and often overkill for indie projects or small-scale apps.

Media3Watch Backend provides:
- **Zero-cost telemetry**: Own your data, hosted on your own infra.
- **Privacy by design**: Data stays in your database.
- **Developer simplicity**: One binary, one database, and a pre-configured Grafana dashboard.

## 🏗 Backend Architecture
Lightweight Kotlin/Ktor service optimized for session ingestion.

**Core Stack**:
- **Language**: Kotlin (JVM)
- **Framework**: Ktor (async, non-blocking HTTP server)
- **Database**: PostgreSQL 16+ (hybrid relational + JSONB storage)
- **Migrations**: Flyway (version-controlled schema changes)
- **Connection Pooling**: HikariCP (optimized for concurrent requests)

**Data Flow**: SDK → HTTP POST → Ktor validation → Postgres upsert → Grafana queries

---

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

## 📊 Grafana Setup & Dashboard Provisioning

### Quick Start (Docker Compose)
Grafana is pre-configured in the docker-compose stack with automatic dashboard provisioning.

1. **Start Grafana**:
   ```bash
   docker compose up -d grafana
   ```

2. **Access Grafana**:
   - URL: `http://localhost:3000`
   - Default credentials: `admin` / `admin`
   - You'll be prompted to change password on first login

3. **Verify Dashboard**:
   - Navigate to **Dashboards** → **Browse**
   - Look for **"Media3Watch Overview"**
   - Dashboard should be auto-provisioned from `grafana/dashboards/media3watch-overview.json`

### Dashboard Features

#### QoE Overview Dashboard
Pre-built dashboard includes:
- **Startup Time Distribution**: Histogram of `player_startup_ms`
- **Rebuffer Ratio Trend**: Time-series of rebuffer percentage
- **Error Rate**: Session errors over time
- **Stream Type Breakdown**: VOD vs LIVE metrics
- **Top Content**: Sessions by `contentId`
- **Session List**: Recent sessions with click-to-JSON view

### Manual Dashboard Import (if needed)

If auto-provisioning fails:

1. **Open Grafana** → **Dashboards** → **Import**
2. **Upload JSON**:
   ```bash
   # Copy dashboard JSON
   cat grafana/dashboards/media3watch-overview.json
   ```
3. **Paste JSON** into import field
4. **Select Data Source**: Choose Postgres datasource (usually `Media3Watch DB`)
5. **Click Import**

### Datasource Configuration

Datasource is auto-provisioned via `grafana/provisioning/datasources/datasource.yml`:

```yaml
apiVersion: 1
datasources:
  - name: Media3Watch DB
    type: postgres
    url: postgres:5432
    database: media3watch
    user: m3w
    secureJsonData:
      password: m3w
    jsonData:
      sslmode: disable
      postgresVersion: 1600
```

**Production changes needed**:
- Change `user` and `password` to production values
- Enable SSL: `sslmode: require`
- Use environment variables for secrets

### Custom Dashboard Creation

1. **Create Panel** → **Add Visualization**
2. **Select Datasource**: Media3Watch DB
3. **Write SQL Query**:
   ```sql
   SELECT
     timestamp,
     player_startup_ms
   FROM sessions
   WHERE timestamp > NOW() - INTERVAL '7 days'
   ORDER BY timestamp DESC
   ```
4. **Choose Visualization**: Time series, histogram, table, etc.
5. **Save Dashboard**

### Common Queries

#### Startup Time Percentiles
```sql
SELECT
  percentile_cont(0.5) WITHIN GROUP (ORDER BY player_startup_ms) AS p50,
  percentile_cont(0.75) WITHIN GROUP (ORDER BY player_startup_ms) AS p75,
  percentile_cont(0.95) WITHIN GROUP (ORDER BY player_startup_ms) AS p95,
  percentile_cont(0.99) WITHIN GROUP (ORDER BY player_startup_ms) AS p99
FROM sessions
WHERE player_startup_ms IS NOT NULL
  AND timestamp > NOW() - INTERVAL '24 hours';
```

#### Error Rate by Category
```sql
SELECT
  DATE_TRUNC('day', timestamp) AS day,
  COUNT(*) AS error_count
FROM sessions
WHERE error_count > 0
  AND timestamp > NOW() - INTERVAL '7 days'
GROUP BY day
ORDER BY day DESC;
```

#### Rebuffer Ratio Distribution
```sql
SELECT
  CASE
    WHEN rebuffer_time_ms = 0 THEN '0 ms (no rebuffer)'
    WHEN rebuffer_time_ms <= 1000 THEN '1-1000 ms'
    WHEN rebuffer_time_ms <= 5000 THEN '1001-5000 ms'
    ELSE '>5000 ms'
  END AS rebuffer_bucket,
  COUNT(*) AS session_count
FROM sessions
WHERE rebuffer_time_ms IS NOT NULL
  AND timestamp > NOW() - INTERVAL '7 days'
GROUP BY rebuffer_bucket
ORDER BY rebuffer_bucket;
```

### Dashboard Export

To export custom dashboards for version control:

1. **Open Dashboard** → **Settings** (gear icon)
2. **JSON Model** tab
3. **Copy JSON**
4. **Save to file**:
   ```bash
   # Add to version control
   echo '<json>' > grafana/dashboards/custom-dashboard.json
   ```

### Alerting (Future)

Planned for future releases:
- [ ] Alert on high error rate (>5% of sessions)
- [ ] Alert on degraded startup time (p95 > threshold)
- [ ] Alert on backend health issues
- [ ] Integration with Slack/PagerDuty

### Troubleshooting

**Dashboard not appearing?**
- Check Grafana logs: `docker compose logs grafana`
- Verify provisioning config: `grafana/provisioning/dashboards/dashboard.yml`
- Ensure JSON is valid: `cat grafana/dashboards/*.json | jq .`

**Datasource connection failed?**
- Verify Postgres is running: `docker compose ps postgres`
- Check database credentials in `datasource.yml`
- Test connection: `psql -h localhost -U m3w -d media3watch`

**No data in panels?**
- Verify backend is ingesting: `curl -H "X-API-Key: dev-key" http://localhost:8080/health`
- Check sessions table: `psql -U m3w -d media3watch -c "SELECT COUNT(*) FROM sessions;"`
- Adjust time range in Grafana (top-right corner)

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
- [ ] **Rate limiting**: Per-API-key throttling (100 req/min)
- [ ] **Structured logging**: Request/response logging with duration tracking
- [ ] **Prometheus metrics**: Backend health monitoring (`/metrics` endpoint)
- [ ] **HikariCP connection pooling**: Optimized database connection management
- [ ] **Idempotent upserts**: Safe retry handling with optimistic locking
- [ ] **Index optimization**: Fast queries for common analytics patterns
- [ ] **Data retention strategy**: 90-day default with automated cleanup

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
