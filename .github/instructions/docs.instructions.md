---
applyTo: "docs/**"
---

# Documentation Instructions — Media3Watch

## Core Principles
- **Clarity over completeness**: Short, actionable, and aligned with MVP scope.
- **Examples first**: Prefer code snippets, diagrams, and curl commands over long explanations.
- **Keep it current**: Any schema, endpoint, or API change MUST trigger a docs update.
- **No drift**: Documentation must always reflect the actual implementation, not aspirational features.

---

## Documentation Structure

### Required Documentation Files
1. **Quickstart Guide** (`docs/quickstart.md` or in main README)
   - Local environment setup (docker-compose up)
   - SDK integration (3-line code snippet)
   - First successful session ingestion (curl example)
   - Time to first "hello world": <5 minutes

2. **API Reference** (`docs/api.md` or inline in backend README)
   - Endpoint: `POST /v1/sessions`
   - Headers: `X-API-Key`, `Content-Type`
   - Request body: SessionSummary JSON with annotated field descriptions
   - Response codes: 200, 400, 401, 429, 500
   - Example payloads (valid + invalid)

3. **Schema Documentation** (`docs/schema.md`)
   - `schemaVersion` field semantics
   - All SessionSummary fields with types, required/optional, descriptions
   - Field addition rules (backward compatibility)
   - Version bump strategy (see `schema-versioning-guide.md`)

4. **Integration Guides**
   - Android SDK integration (`android/README.md`)
   - Backend deployment (`backend/README.md`)
   - Grafana dashboard setup

5. **Troubleshooting** (optional but recommended)
   - Common errors (401, 400, connection refused)
   - Debug checklist for SDK not sending data
   - How to verify data reached Postgres

---

## Update Triggers (When to Edit Docs)

### 1. Schema Changes
**When**: Adding/removing fields in `SessionSummary`, changing `schemaVersion`

**Action**:
- [ ] Update `docs/schema.md` with new field descriptions
- [ ] Add example payload in API reference
- [ ] Update `schemaVersion` changelog
- [ ] Note backward compatibility impact

**Example**:
```markdown
## v1.1.0 (2026-02-15)
Added fields:
- `droppedFrameRatio` (float, optional): Dropped frames / total frames

Backward compatibility: ✅ Backend accepts both 1.0.0 and 1.1.0
```

### 2. API Endpoint Changes
**When**: New endpoint, parameter, or header requirement

**Action**:
- [ ] Update API reference with new endpoint/param
- [ ] Provide curl example
- [ ] Update integration guides if SDK behavior changes
- [ ] Note breaking changes clearly with ⚠️ symbol

### 3. Backend Configuration Changes
**When**: New environment variable, database migration, docker-compose change

**Action**:
- [ ] Update `backend/README.md` environment variables table
- [ ] Update docker-compose instructions if ports/services change
- [ ] Add migration notes if schema changed

### 4. SDK API Changes
**When**: New public method, config option, or behavioral change

**Action**:
- [ ] Update `android/README.md` usage example
- [ ] Update initialization snippet
- [ ] Note deprecations with migration path

### 5. Metrics Definition Changes
**When**: Changing how `player_startup_ms`, `rebufferRatio`, etc. are calculated

**Action**:
- [ ] Update main README metrics definition section
- [ ] Add ⚠️ **Breaking Change** notice
- [ ] Update Grafana dashboard queries if needed
- [ ] Require PO approval (this is a critical change)

---

## Documentation Quality Checklist

Before merging docs changes, verify:

- [ ] **Accuracy**: Code examples run without errors
- [ ] **Completeness**: All required fields/parameters documented
- [ ] **Consistency**: Terminology matches codebase (e.g., `sessionId` not `session_id`)
- [ ] **Actionability**: Reader can complete task without guessing
- [ ] **Up-to-date**: No references to removed features or old APIs
- [ ] **No secrets**: No hardcoded API keys, passwords, or sensitive URLs

---

## Style Guidelines

### Code Blocks
Always specify language:
```kotlin
// ✅ Good
Media3Watch.init(context) { ... }
```

```
// ❌ Bad (no language hint)
Media3Watch.init(context) { ... }
```

### API Examples
Always include full context (headers + body + response):
```bash
curl -X POST http://localhost:8080/v1/sessions \
  -H "Content-Type: application/json" \
  -H "X-API-Key: dev-key" \
  -d '{"sessionId": "abc-123", ...}'

# Response
{"status": "ok", "sessionId": "abc-123"}
```

### Field Descriptions
Use consistent format:
- **Field name** (`type`, required/optional): Description with constraints.

Example:
- **sessionId** (`string`, required): Unique session identifier (UUID v4 recommended, max 128 chars).

### Versioning Annotations
When documenting version-specific features:
```markdown
> **Added in**: SDK 1.1.0, Backend 1.0.0  
> **Requires**: `schemaVersion >= 1.1.0`
```

---

## Maintenance Rules

### Deprecation Notice Format
When deprecating a feature:
```markdown
> ⚠️ **Deprecated**: This method is deprecated as of v1.2.0 and will be removed in v2.0.0.  
> **Migration**: Use `Media3Watch.newMethod()` instead.  
> **Reason**: Old method doesn't support new QoE metrics.
```

### Link Hygiene
- Use relative paths for internal docs: `[Schema](docs/schema.md)`
- Keep links up-to-date when renaming/moving files
- Avoid external links that may break (prefer stable references)

### Diagram Requirements
If adding architecture/flow diagrams:
- Use ASCII art or Mermaid (renderable in markdown)
- Keep diagrams simple (max 10 boxes)
- Add alt text for accessibility

---

## Non-Documentation Tasks (Out of Scope)

These do NOT belong in docs:
- ❌ Implementation details (internal class structure)
- ❌ Future roadmap speculation (unless clearly marked)
- ❌ Marketing copy or sales pitches
- ❌ Unimplemented features presented as current

If you're documenting something that doesn't exist yet, mark it clearly:
```markdown
> 🚧 **Planned Feature**: This feature is not yet implemented. See [Issue #123] for progress.
```

---

## Review Checklist for Docs PRs

Docs changes should be reviewed by:
- **Contract Guardian**: If schema/API changes
- **Quality Gatekeeper**: If examples or integration instructions
- **Product Owner**: If metrics definitions change

Reviewer should verify:
- [ ] Examples are copy-pasteable and work
- [ ] No scope creep (documenting unrequested features)
- [ ] Backward compatibility notes are accurate
- [ ] Links are valid and relative
