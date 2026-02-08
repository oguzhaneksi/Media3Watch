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
| Variable | Description | Default |
|----------|-------------|---------|
| `M3W_API_KEY` | Secret key for SDK authentication | `dev-key` |
| `DATABASE_URL` | Postgres JDBC connection string | `jdbc:postgresql://postgres:5432/media3watch` |
| `DATABASE_USER` | Postgres username | `m3w` |
| `DATABASE_PASSWORD` | Postgres password | `m3w` |

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
- [ ] **Short Term**: Automated Grafana dashboard distribution via provisioning.
- [ ] **Short Term**: Prometheus metrics for backend self-monitoring.
- [ ] **Mid Term**: Multi-tenant API key support.
- [ ] **Long Term**: Simple alerting integration for spike in error rates.

## 📜 License & Contribution
- **License**: Apache 2.0
- **Contribution**: PRs are welcome! As a solo-developed project, please open an issue first to discuss major changes.
