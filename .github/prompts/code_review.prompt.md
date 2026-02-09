---
agent: agent
---
Review this PR for Media3Watch with focus on:
- Architecture boundaries (pure Kotlin schema/core, adapter isolation)
- QoE semantics (startup = play command → first frame)
- API contract + idempotency (sessionId upsert, ignoreUnknownKeys)
- Performance & threading (no blocking in hot path)
- Security/privacy (no API key logs, avoid PII)
Return:
- Must-fix issues
- Suggestions
- Test gaps
