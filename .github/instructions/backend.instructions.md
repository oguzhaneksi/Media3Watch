---
applyTo: "backend/**"
---

# Backend scope instructions (Media3Watch)

## Backend rules
- Kotlin + Ktor backend.
- Idempotency is REQUIRED: `sessionId` unique; repeated POST must be safe (UPSERT).
- Store full payload in Postgres JSONB even if relational mapping is partial.
- Be backward compatible: tolerate unknown fields.

## Data model guidance
- Keep indexed relational columns for query-critical metrics.
- Keep the raw JSON payload in JSONB for flexibility.

## Tests
Add/keep tests for:
- Missing/invalid API key -> 401
- Invalid payload -> 400
- Duplicate sessionId -> idempotent upsert behavior
- Rate limit -> 429 (if enabled)

## Security
- Never log API keys or sensitive headers.
- Validate inputs; consistent error response model.
- Avoid PII in payload; if adding client metadata, keep it generic (device model/OS is okay; user identity is not).