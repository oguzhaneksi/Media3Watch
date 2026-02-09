# Contributing to Media3Watch

Thank you for your interest in contributing to Media3Watch! This guide will help you get started.

---

## Table of Contents
1. [Code of Conduct](#code-of-conduct)
2. [Getting Started](#getting-started)
3. [Development Workflow](#development-workflow)
4. [Pull Request Process](#pull-request-process)
5. [Coding Standards](#coding-standards)
6. [Testing Requirements](#testing-requirements)
7. [Documentation](#documentation)
8. [Issue Guidelines](#issue-guidelines)

---

## Code of Conduct

This project adheres to a simple principle: **Be respectful and professional**.

- Treat all contributors with respect
- Provide constructive feedback
- Focus on the problem, not the person
- Welcome newcomers and help them learn

Violations may result in temporary or permanent bans from the project.

---

## Getting Started

### Prerequisites
- **Android**: Android Studio (Ladybug+), JDK 17+, Android SDK API 36
- **Backend**: JDK 17+, Docker, Postgres 16+
- **Tools**: Git, Gradle 8.x

### Setup
1. **Fork** the repository on GitHub
2. **Clone** the repository:
   ```bash
   git clone https://github.com/oguzhaneksi/Media3Watch.git
   cd Media3Watch
   ```

3. **Start backend** (for integration testing):
   ```bash
   docker compose up -d
   ```

4. **Build Android SDK**:
   ```bash
   cd android
   ./gradlew build
   ```

5. **Build backend**:
   ```bash
   cd backend
   ./gradlew build
   ```

6. **Run tests**:
   ```bash
   # Android
   cd android && ./gradlew test
   
   # Backend
   cd backend && ./gradlew test
   ```

---

## Development Workflow

### 1. Pick or Create an Issue
- Browse [Issues](https://github.com/oguzhaneksi/Media3Watch/issues)
- Look for `good-first-issue` or `help-wanted` labels
- Comment on the issue to claim it
- Wait for maintainer approval before starting work

### 2. Create a Branch
```bash
git checkout -b feature/short-description
# or
git checkout -b fix/bug-description
```

**Branch naming**:
- `feature/` — New features
- `fix/` — Bug fixes
- `docs/` — Documentation changes
- `refactor/` — Code refactoring
- `test/` — Test additions/fixes

### 3. Make Changes
- Follow [Coding Standards](#coding-standards)
- Write tests for new code
- Update documentation if needed
- Keep commits small and focused

### 4. Commit Your Changes
Use [Conventional Commits](https://www.conventionalcommits.org/):

```bash
git commit -m "feat(sdk): add rebuffer ratio metric"
git commit -m "fix(backend): handle null contentId in upsert"
git commit -m "docs: update schema versioning guide"
```

**Commit types**:
- `feat:` — New feature
- `fix:` — Bug fix
- `docs:` — Documentation only
- `refactor:` — Code change without feature/fix
- `test:` — Adding tests
- `chore:` — Build, CI, dependencies

### 5. Push and Open PR
```bash
git push origin feature/your-branch
```
Then open a Pull Request on GitHub.

---

## Pull Request Process

### Before Opening PR
- [ ] Code builds successfully
- [ ] All tests pass
- [ ] Lint checks pass (`ktlint`, `detekt`)
- [ ] Documentation updated (if applicable)
- [ ] Commits follow conventional format

### PR Template
Use the provided [PR template](.github/pull_request_template.md). Include:
- **What changed**: Brief summary (2-5 bullets)
- **Why**: Motivation or issue reference
- **How tested**: Manual + automated tests
- **Scope**: Android, Backend, Docs, etc.
- **Breaking changes**: Yes/No (describe if yes)

### Review Process
1. **CI checks** must pass (GitHub Actions)
2. **Code review** by maintainer (uses Quality Gatekeeper agent)
3. **Approval** required before merge
4. **Squash merge** into `main` (maintainer will merge)

### What to Expect
- **Feedback**: Reviewers may request changes
- **Timeline**: Most PRs reviewed within 3-5 business days
- **Iteration**: Be prepared to make adjustments

---

## Coding Standards

### Kotlin Style
- **Formatting**: `ktlint` (auto-format: `./gradlew ktlintFormat`)
- **Static analysis**: `detekt` (run: `./gradlew detekt`)
- **Naming**:
  - Classes: `PascalCase`
  - Functions/variables: `camelCase`
  - Constants: `UPPER_SNAKE_CASE`

### Architecture Rules (CRITICAL)
See [`.github/instructions/copilot.instructions.md`](.github/instructions/copilot.instructions.md) for full details.

**Android**:
- `sdk:schema` and `sdk:core` MUST be **pure Kotlin** (no Android/Media3 imports)
- Media3 integration ONLY in `sdk:adapter-media3`
- Android runtime concerns in `sdk:android-runtime`

**Backend**:
- Idempotency via `sessionId` upsert (required)
- Store full payload as JSONB
- Backward compatible (tolerate unknown fields)

**Never**:
- Change QoE metric definitions without PO approval
- Break schema compatibility without version bump
- Log API keys or PII

### Performance
- No blocking on main thread (Android)
- No blocking on event loop (Backend)
- Avoid allocations in hot paths (player callbacks)

---

## Testing Requirements

### Minimum Test Coverage
- **Android**: Unit tests for `sdk:schema` and `sdk:core` (pure Kotlin modules)
- **Backend**: Tests for auth, validation, idempotency, rate limiting

### Test Types
1. **Unit tests**: Fast, isolated, no external dependencies
2. **Integration tests**: Backend with test DB, Android with real player (optional)
3. **Manual tests**: If automated testing is insufficient

### Writing Tests
```kotlin
// Android example
@Test
fun `startup time calculated correctly`() {
    val session = SessionState()
    session.markPlayRequested(1000L)
    session.markFirstFrame(2500L)
    assertEquals(1500, session.calculateStartupMs())
}

// Backend example
@Test
fun `POST sessions returns 401 without API key`() {
    val response = client.post("/v1/sessions") {
        setBody(validPayload)
    }
    assertEquals(HttpStatusCode.Unauthorized, response.status)
}
```

### Run Tests Locally
```bash
# Android
cd android && ./gradlew test

# Backend (requires Postgres)
docker compose up -d postgres
cd backend && ./gradlew test
```

---

## Documentation

### When to Update Docs
- New feature: Update relevant README + API docs
- Schema change: Update `docs/schema.md` + `.github/schema-versioning-guide.md`
- Config change: Update environment variables table
- Breaking change: Add migration guide

### Documentation Style
See [`.github/instructions/docs.instructions.md`](.github/instructions/docs.instructions.md) for full guidelines.

**Key points**:
- Short, actionable, with examples
- Code snippets must be copy-pasteable and working
- Update schema version annotations (`Added in X.Y.Z`)
- No secrets in examples

---

## Issue Guidelines

### Reporting Bugs
Use [Bug Report template](.github/ISSUE_TEMPLATE/02-bug.yml).

**Include**:
- Steps to reproduce
- Expected vs actual behavior
- Environment (SDK version, device, backend version)
- Logs or payload samples (redact secrets!)

### Proposing Features
Use [Feature Proposal template](.github/ISSUE_TEMPLATE/01-feature.yml).

**Required from PO**:
- Goal (one sentence)
- User story
- Acceptance criteria
- Non-goals (what you're NOT building)

⚠️ **Note**: Features require Product Owner approval before implementation. Issues are marked `needs-po` until reviewed, then `spec-ready` when approved.

### Research / Spikes
Use [Spike template](.github/ISSUE_TEMPLATE/03-spike.yml).

Spikes are **timeboxed** research tasks (0.5-3 days) producing a decision or prototype.

---

## Special Considerations

### Schema Changes
- Follow [Schema Versioning Guide](.github/schema-versioning-guide.md)
- Consult Contract Guardian agent before implementation
- Adding optional field: MINOR bump (`1.0.0` → `1.1.0`)
- Removing field or changing type: MAJOR bump (`1.x.x` → `2.0.0`) + PO approval

### QoE Metric Definitions
**DO NOT** change these without explicit PO approval:
- `player_startup_ms` = play command → first rendered frame
- `rebufferRatio` = rebuffer time / (play time + rebuffer time)

### Breaking Changes
- Require `spec-ready` label + PO approval
- Must include migration guide
- Phased rollout plan required

---

## Getting Help

- **Questions**: Open a [Discussion](https://github.com/oguzhaneksi/Media3Watch/discussions) (or ask in Issues if no Discussions enabled)
- **Bugs**: Use Bug Report template
- **Features**: Use Feature Proposal template
- **Security**: See [SECURITY.md](SECURITY.md)

---

## Recognition

Contributors are acknowledged in:
- Release notes
- `CONTRIBUTORS.md` (if we create one)
- GitHub contributor graph

Thank you for contributing! 🎉

---

**Maintainers**: This document is maintained by the Product Owner agent. Updates require approval.
