---
applyTo: '**'
---
# GitHub Copilot Instructions — Media3Watch

You are working on **Media3Watch**, an open-source, self-hostable QoE debugging + lightweight analytics system for Android Media3 (ExoPlayer).

## North Star
- Ship a **modular Android SDK** that collects QoE metrics and can optionally upload sessions.
- Ship a **minimal Kotlin/Ktor ingest API** that stores sessions in Postgres (hybrid relational columns + JSONB payload) and supports Grafana dashboards.
- Keep it **local-first**, **self-hostable**, and **low-complexity** (no Kafka/Flink/stream processing).

## Non-goals (DO NOT build)
- Real-time stream processing pipeline, user dashboards (Grafana is enough), identity/auth systems beyond API key, multi-tenant complexities unless explicitly requested.
- Vendor-specific analytics semantics beyond current MVP definitions.

---

# 1) Architecture & Boundaries (must follow)

## Android SDK module boundaries
- `sdk:schema` MUST stay **pure Kotlin** (no Android imports).
  - Contains data models (e.g., SessionSummary, enums), `schemaVersion`, JSON (kotlinx.serialization).
- `sdk:core` MUST stay **pure Kotlin**.
  - Contains event processing/state machine and metric computation.
- Media3 integration MUST be isolated:
  - Media3/ExoPlayer APIs belong in `sdk:adapter-media3` (or equivalent adapter module).
- Android runtime concerns (Context, storage, WorkManager, overlay UI) MUST be isolated:
  - Belong in `sdk:android-runtime`, `sdk:transport-okhttp`, `sdk:inspector-overlay`, `sdk:starter-media3`.

**Hard rule:** never import Android/Media3 into `schema` or `core`.

## Backend boundaries
- Backend is **Kotlin + Ktor**.
- Storage is **Postgres** with:
  - Indexed relational columns for core metrics (startup, rebuffer, errors, etc.)
  - Full request payload stored in **JSONB** for flexibility.
- Idempotency is REQUIRED:
  - `sessionId` is the unique key; repeated POSTs must **upsert** safely.

---

# 2) Definitions that MUST NOT drift (QoE semantics)

## MVP Startup Metric (player_startup_ms)
- Definition: time from **play command** to **first rendered frame**.
- Formula:
  `player_startup_ms = first_frame_rendered_ts - play_command_ts`

## markPlayRequested() semantics
- `markPlayRequested()` MUST be called EXACTLY when the app executes `player.play()` or sets `playWhenReady=true`.
- It is NOT the user tap time, NOT navigation time, NOT entitlement/API/preload time.
- Navigation, API calls, entitlement checks, preloads are EXCLUDED.

If changing anything around startup, you MUST keep this definition stable unless the task explicitly requests a new metric.

---

# 3) API Contract Rules (Android ↔ Backend)

## Ingest endpoint
- `POST /v1/sessions`
- Header: `X-API-Key`
- Body: SessionSummary JSON with `schemaVersion` and `sessionId`.

## Backward/forward compatibility
- SDK may add new fields over time.
- Backend MUST tolerate unknown fields:
  - Use `kotlinx.serialization` with `ignoreUnknownKeys = true` where appropriate.
- Schema evolution rules:
  - Bump `schemaVersion` on breaking schema changes.
  - Update `docs/schema.md` (or equivalent) alongside code changes.
  - If new metrics become query-critical, add relational columns + indexes + migration.

## Idempotency rules
- `sessionId` is unique.
- Upsert MUST be safe under retries and concurrency.
- Store full payload in JSONB even if mapping to columns fails for a new optional field.

---

# 4) Coding Standards (Kotlin)

## Kotlin style
- Prefer clear, boring Kotlin.
- Avoid magic globals and hidden singletons unless already established.
- Use sealed classes/enums for categories (e.g., ErrorCategory).
- Keep allocations low on hot paths (especially backend ingest and SDK event callbacks).
- Prefer `Result`/typed errors or a consistent error response model.

## Coroutines / threading
- Android: never block main thread; use coroutines/WorkManager for upload.
- Backend: avoid blocking calls on event loop; use appropriate dispatchers and pool settings.

## Logging & privacy
- Never log API keys, tokens, or sensitive identifiers.
- Avoid PII in payload. If adding client metadata, keep it generic (device model/OS is okay; user identity is not).

---

# 5) Backend Quality Gates

## Required endpoints
- `GET /health` for health checks (fast, no DB required if possible).
- `/metrics` (Prometheus) if enabled.

## Rate limiting
- Per API key throttling must remain in place (or improve), never remove casually.

## DB migrations
- All schema changes MUST come with a migration (e.g., Flyway).
- Prefer additive migrations; avoid destructive changes without retention strategy.

## Indexing
- If adding queryable columns, add indexes thoughtfully (often partial indexes).
- Keep Grafana query paths fast: time filtering + startup distribution + error rate.

---

# 6) Android SDK Quality Gates

## Public API stability
- Keep public API minimal and stable.
- If changing public API, update docs and consider binary compatibility implications.

## Offline-first & reliability
- Upload should be “at-least-once” with idempotency on backend.
- Local queue/export should not corrupt data:
  - Use atomic writes for JSON files.
- Inspector overlay is optional; SDK must work without it.

---

# 7) Tests (minimum expectations)

## Android
- Unit test pure Kotlin modules (`schema`, `core`) heavily:
  - state machine transitions
  - startup calculation
  - rebuffer aggregation
  - error categorization mapping
- Optional: instrumentation tests only when needed.

## Backend
- Add tests for:
  - API key auth (401 on missing/invalid key)
  - schema validation (400 on invalid)
  - upsert idempotency (duplicate sessionId)
  - rate limit behavior (429)
- Prefer integration tests with ephemeral Postgres when feasible.

---

# 8) PR/Change Hygiene

When producing a PR, ALWAYS include:
- What changed and why (2–5 bullets).
- How to test (commands / steps).
- Any migrations (yes/no).
- Any schemaVersion impact (yes/no).
- Risk notes (what could break).

Keep PRs small and scoped. If a change touches both Android and backend, split into two PRs unless explicitly asked otherwise.

---

# 9) Task Execution Protocol (how you should work)

1. Restate the goal in one sentence.
2. List impacted modules/files BEFORE editing.
3. Implement in the smallest safe increment.
4. Add/adjust tests.
5. Update docs if public behavior changed.
6. Provide a concise PR summary + test plan.

If any requirement is ambiguous, make a best-effort assumption consistent with this document and leave a short TODO note.

# 10) Scope Governance (MANDATORY)

- Work MUST start from an Issue labeled `spec-ready`.
- `spec-ready` means: Goal, Acceptance Criteria, Non-goals, Test plan are defined.
- If a request is ambiguous or risks expanding scope:
  STOP and ask for Product Owner clarification instead of implementing assumptions.
- Never add “nice-to-have” features opportunistically.
- Any scope change requires updating the Issue and re-adding `spec-ready`.

