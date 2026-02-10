# Security Policy

## Supported Versions

Media3Watch is currently in **MVP / early development**. Security updates are provided for the following versions:

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |
| < 1.0   | :x:                |

---

## Reporting a Vulnerability

We take security seriously. If you discover a security vulnerability in Media3Watch, please report it responsibly.

### ⚠️ Do NOT:
- Open a public GitHub issue for security vulnerabilities
- Disclose the vulnerability publicly before it has been addressed
- Exploit the vulnerability in production systems

### ✅ Do:
1. **Email** your findings to: **security@media3watch.dev**
2. Include:
   - Description of the vulnerability
   - Steps to reproduce
   - Potential impact
   - Suggested fix (if any)
   - Your contact information (for follow-up)

### Response Timeline
- **Acknowledgment**: Within 48 hours
- **Initial assessment**: Within 5 business days
- **Fix timeline**: Depends on severity:
  - **Critical**: 7 days
  - **High**: 14 days
  - **Medium**: 30 days
  - **Low**: 60 days or next release

---

## Security Best Practices

### For Users Deploying Media3Watch

#### 1. API Key Management
- **Never commit API keys** to version control
- Use environment variables or secret management systems
- Rotate keys regularly (every 90 days recommended)
- Use different keys for dev/staging/production

#### 2. Network Security
- **Always use HTTPS** for the ingest endpoint in production
- Configure TLS 1.2+ on the backend
- Use firewall rules to restrict access to backend ports
- Consider API gateway / reverse proxy (nginx, Caddy)

#### 3. Database Security
- Change default Postgres password (`m3w` is for dev only!)
- Use strong passwords (16+ characters, mixed case, symbols)
- Restrict database access to backend service only
- Enable SSL for Postgres connections in production
- Regular backups with encryption at rest

#### 4. Grafana Security
- Change default admin password immediately
- Use role-based access control (RBAC)
- Enable HTTPS for Grafana
- Disable public signup if not needed
- Review dashboard permissions regularly

#### 5. Container Security
- Use official base images (Alpine, Debian slim)
- Keep base images updated (automated scanning recommended)
- Run containers as non-root user
- Use Docker secrets for sensitive data

#### 6. Rate Limiting
- Backend has built-in rate limiting (100 req/min per API key)
- Adjust limits based on your traffic patterns
- Monitor for abuse (sudden spikes in traffic)

---

## Known Security Considerations

### Android SDK
- **Local storage**: Session data stored in app-private directory (encrypted by Android OS on modern devices)
- **Network**: Uses OkHttp with certificate pinning disabled by default (enable for production)
- **Privacy**: No PII collection by default; review `custom` field usage

### Backend
- **Authentication**: API key only (no OAuth/JWT in MVP)
- **Authorization**: Single-tenant (no per-user access control)
- **Input validation**: JSON schema validation + SQL parameterization
- **Logging**: API keys are filtered from logs

### Dependencies
- Regular dependency updates via Dependabot (if enabled)
- Review `libs.versions.toml` and `build.gradle.kts` for known CVEs
- Use `./gradlew dependencyCheckAnalyze` (if configured)

---

## Security Enhancements (Roadmap)

Planned for future releases:
- [ ] Multi-tenant API key support with per-tenant quotas
- [ ] API key rotation mechanism
- [ ] Mutual TLS (mTLS) support
- [ ] End-to-end encryption for session payloads
- [ ] Audit logging for backend operations
- [ ] Automated vulnerability scanning (Snyk, Trivy)

---

## Security Disclosure Policy

Once a vulnerability is fixed:
1. We will release a patch version immediately
2. A security advisory will be published on GitHub
3. Credits will be given to the reporter (if desired)
4. A CVE ID will be requested if applicable

---

## Contact

For security-related questions or concerns, contact: **[security@media3watch.dev]**

For general bugs (non-security), use the [Bug Report template](.github/ISSUE_TEMPLATE/02-bug.yml).

---

## Acknowledgments

We appreciate responsible disclosure from the security community. Contributors will be acknowledged in release notes and this file (with permission).

---

**Last Updated**: February 9, 2026
