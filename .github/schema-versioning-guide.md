# Schema Versioning Guide — Media3Watch

This document defines how `schemaVersion` is managed across the Android SDK and backend to ensure **backward compatibility** and **safe evolution** of the SessionSummary contract.

---

## 1. Overview

The `schemaVersion` field in `SessionSummary` enables:
- **Backward compatibility**: Backend accepts older SDK versions
- **Forward compatibility**: SDK can add new fields without breaking backend
- **Safe migration**: Backend can gradually adopt new fields with appropriate DB migrations

**Format**: Semantic versioning `MAJOR.MINOR.PATCH` (e.g., `1.0.0`, `1.1.0`, `2.0.0`)

---

## 2. Versioning Rules

### 2.1 PATCH Bump (`1.0.0` → `1.0.1`)
**When**: Bug fixes, clarifications, or non-functional changes.

**Examples**:
- Fix typo in field description
- Correct timestamp precision documentation
- Internal refactoring with no external impact

**Impact**:
- No SDK or backend changes required
- No migration needed
- Update docs only

### 2.2 MINOR Bump (`1.0.0` → `1.1.0`)
**When**: Adding **optional** fields (backward compatible).

**Examples**:
- Add new metric: `droppedFrameRatio` (optional)
- Add new device field: `screenResolution` (optional)
- Add new error detail: `errorStackTrace` (optional)

**Rules**:
- New fields MUST be **optional** (nullable in Kotlin, optional in JSON)
- Backend MUST use `ignoreUnknownKeys = true` when parsing
- Old SDK versions (sending `1.0.0`) continue to work
- New SDK versions (sending `1.1.0`) include the new field

**Impact**:
- SDK: Add field to `SessionSummary` data class (nullable)
- Backend: Add column to DB (nullable) + migration
- Backend: Update parser to accept both versions
- Docs: Document new field with "Added in 1.1.0" annotation

**Migration Example** (Flyway):
```sql
-- V1_1_0__add_dropped_frame_ratio.sql
ALTER TABLE sessions ADD COLUMN dropped_frame_ratio FLOAT;
CREATE INDEX idx_sessions_dropped_frame_ratio ON sessions (dropped_frame_ratio) WHERE dropped_frame_ratio IS NOT NULL;
```

### 2.3 MAJOR Bump (`1.x.x` → `2.0.0`)
**When**: **Breaking changes** (incompatible with previous versions).

**Examples**:
- Remove a field: `lastErrorCode` no longer sent
- Rename a field: `sessionId` → `sessionUuid`
- Change field type: `timestamp` from `Long` to `String`
- Change metric definition: `player_startup_ms` calculation changes

**Rules**:
- Backend MUST support BOTH old and new versions during transition period
- SDK MUST update `schemaVersion` to `2.0.0`
- Requires coordination: PO approval + phased rollout

**Impact**:
- SDK: Update `SessionSummary`, bump `schemaVersion` to `2.0.0`
- Backend: Add version-aware parsing logic
- Backend: Maintain dual-write or translation layer
- Docs: Mark old fields as deprecated with removal timeline
- Ops: Monitor adoption, retire old version after threshold (e.g., <5% traffic)

**Example**: Renaming `sessionId` to `sessionUuid`
```kotlin
// Backend: Accept both versions
fun parseSession(json: String): SessionSummary {
    val raw = Json.decodeFromString<JsonObject>(json)
    val version = raw["schemaVersion"]?.jsonPrimitive?.content ?: "1.0.0"
    
    return when {
        version.startsWith("2.") -> {
            // Parse v2 format
            Json.decodeFromString<SessionSummaryV2>(json)
        }
        else -> {
            // Parse v1 format and map to internal model
            val v1 = Json.decodeFromString<SessionSummaryV1>(json)
            v1.toV2() // Translation function
        }
    }
}
```

---

## 3. Version Compatibility Matrix

| SDK Version | Backend Versions | Compatibility |
|-------------|------------------|---------------|
| 1.0.0       | 1.0.x, 1.1.x     | ✅ Full       |
| 1.1.0       | 1.0.x            | ✅ Full (new fields ignored) |
| 1.1.0       | 1.1.x            | ✅ Full (new fields stored) |
| 2.0.0       | 1.x.x            | ❌ Incompatible (breaking change) |
| 2.0.0       | 2.0.x            | ✅ Full       |

**Rule**: Backend MUST accept `schemaVersion` within same MAJOR version. Backend MAY accept older MAJOR versions during transition.

---

## 4. Implementation Workflow

### Step 1: Proposal (Product Owner)
- Define what changes to the schema
- Classify as PATCH / MINOR / MAJOR
- Document in feature issue using `.github/ISSUE_TEMPLATE/01-feature.yml`
- Label: `schema-impact`

### Step 2: Contract Review (Contract Guardian)
- Verify backward compatibility claims
- Propose migration strategy (if needed)
- Update this guide if new patterns emerge

### Step 3: Implementation (Android + Backend)

**Android**:
1. Update `SessionSummary` in `sdk:schema`
2. Bump `schemaVersion` constant
3. Add tests for new field (optional field = handle null)
4. Update docs (`android/README.md`, `docs/schema.md`)

**Backend**:
1. Add DB migration (Flyway)
2. Update column mapping (if query-critical)
3. Ensure parser uses `ignoreUnknownKeys = true`
4. Add tests for both old and new versions
5. Update docs (`backend/README.md`, `docs/schema.md`)

### Step 4: Testing (QA)
- Test old SDK → new backend (must work)
- Test new SDK → old backend (must work if MINOR bump)
- Test new SDK → new backend (must work)
- Test schema validation errors (malformed JSON)

### Step 5: Deployment (DevOps)
- Deploy backend first (accept new version)
- Roll out SDK update gradually
- Monitor error rates and compatibility issues
- Update Grafana dashboards if new metrics added

### Step 6: Documentation (Docs)
- Update `docs/schema.md` with version changelog
- Add "Added in X.Y.Z" annotations
- Update API examples with new fields

---

## 5. Schema Version Changelog

### v1.0.0 (2026-01-15) — Initial Release
**Fields**:
- `schemaVersion`, `sessionId`, `timestamp`
- `contentId`, `streamType`
- `startupTimeMs`, `playTimeMs`, `rebufferTimeMs`, `rebufferCount`, `rebufferRatio`
- `errorCount`, `lastErrorCode`, `lastErrorCategory`
- `qualitySwitchCount`, `avgBitrateKbps`, `droppedFrames`
- `device`: `model`, `os`, `osVersion`
- `app`: `name`, `version`
- `custom`: user-defined key-value pairs

**Compatibility**: Baseline version.

---

### v1.1.0 (Planned) — Example Minor Update
**Added**:
- `droppedFrameRatio` (float, optional): `droppedFrames / totalFrames`
- `device.screenResolution` (string, optional): `1920x1080`

**Backward Compatibility**: ✅ SDK 1.0.0 continues to work; backend stores v1.1.0 fields when present.

**Migration**: `ALTER TABLE sessions ADD COLUMN dropped_frame_ratio FLOAT;`

---

### v2.0.0 (Future / Not Planned)
**Breaking Changes**: TBD (requires PO approval and phased rollout).

---

## 6. Testing Schema Compatibility

### Unit Tests (Android SDK)
```kotlin
@Test
fun `SessionSummary serializes with schemaVersion`() {
    val session = SessionSummary(
        schemaVersion = "1.0.0",
        sessionId = "test-123",
        // ... other fields
    )
    val json = Json.encodeToString(session)
    assertTrue(json.contains("\"schemaVersion\":\"1.0.0\""))
}

@Test
fun `SessionSummary handles optional fields as null`() {
    val session = SessionSummary(
        schemaVersion = "1.1.0",
        sessionId = "test-123",
        droppedFrameRatio = null // New optional field
    )
    assertNotNull(session) // Should not crash
}
```

### Integration Tests (Backend)
```kotlin
@Test
fun `Backend accepts v1_0_0 payload`() {
    val payload = """{"schemaVersion":"1.0.0","sessionId":"abc-123",...}"""
    val response = client.post("/v1/sessions") {
        header("X-API-Key", "test-key")
        setBody(payload)
    }
    assertEquals(HttpStatusCode.OK, response.status)
}

@Test
fun `Backend accepts v1_1_0 payload with new field`() {
    val payload = """{"schemaVersion":"1.1.0","sessionId":"abc-123","droppedFrameRatio":0.02,...}"""
    val response = client.post("/v1/sessions") {
        header("X-API-Key", "test-key")
        setBody(payload)
    }
    assertEquals(HttpStatusCode.OK, response.status)
}

@Test
fun `Backend ignores unknown fields from future version`() {
    val payload = """{"schemaVersion":"1.2.0","sessionId":"abc-123","futureField":"value",...}"""
    val response = client.post("/v1/sessions") {
        header("X-API-Key", "test-key")
        setBody(payload)
    }
    assertEquals(HttpStatusCode.OK, response.status) // Must not fail
}
```

---

## 7. Common Pitfalls

### ❌ Don't: Make optional field required
```kotlin
// BAD: Breaks backward compatibility
data class SessionSummary(
    val schemaVersion: String,
    val sessionId: String,
    val droppedFrameRatio: Float // Required in v1.1.0
)
```

### ✅ Do: Add as optional
```kotlin
// GOOD: Backward compatible
data class SessionSummary(
    val schemaVersion: String,
    val sessionId: String,
    val droppedFrameRatio: Float? = null // Optional in v1.1.0
)
```

### ❌ Don't: Remove field without MAJOR bump
```kotlin
// BAD: Breaking change without version bump
data class SessionSummary(
    val schemaVersion: String = "1.1.0", // Still 1.x
    val sessionId: String
    // lastErrorCode removed — THIS IS BREAKING!
)
```

### ✅ Do: Deprecate first, remove in MAJOR
```kotlin
// v1.1.0: Deprecate
data class SessionSummary(
    val schemaVersion: String = "1.1.0",
    @Deprecated("Use errorDetails instead") val lastErrorCode: Int?
)

// v2.0.0: Remove
data class SessionSummary(
    val schemaVersion: String = "2.0.0",
    val errorDetails: ErrorDetails? // New structure
)
```

---

## 8. Decision Tree

```
Is this a schema change?
├─ No → No version bump needed
└─ Yes
   ├─ Adding optional field?
   │  └─ Yes → MINOR bump (1.0.0 → 1.1.0)
   ├─ Removing field, changing type, renaming field?
   │  └─ Yes → MAJOR bump (1.x.x → 2.0.0) + PO approval
   └─ Documentation/comment change only?
      └─ Yes → PATCH bump (1.0.0 → 1.0.1)
```

---

## 9. Governance

- **Owner**: Contract Guardian agent
- **Approval**: Product Owner (for MAJOR bumps)
- **Review**: All schema changes must be reviewed by Contract Guardian before merge
- **Documentation**: Update this guide + `docs/schema.md` for every version bump

---

## 10. References

- Android SDK schema definition: `android/sdk/schema/src/main/kotlin/SessionSummary.kt`
- Backend parser: `backend/src/main/kotlin/com/media3watch/models/SessionSummary.kt`
- API documentation: `docs/schema.md`
- Copilot instructions: `.github/instructions/copilot.instructions.md` Section 3
