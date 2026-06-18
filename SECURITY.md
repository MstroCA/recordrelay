# Security Policy

## Supported versions

| Version | Supported |
|---------|-----------|
| 0.1.x   | ✓         |

## Reporting a vulnerability

**Do not open a public GitHub issue for security vulnerabilities.**

Please report security issues by emailing the maintainers directly. You can find
the contact in the project's GitHub profile. We aim to respond within 72 hours.

When reporting, please include:
- A description of the vulnerability and its potential impact
- Steps to reproduce or a proof-of-concept (if safe to share)
- Any suggested mitigations

We will acknowledge receipt, investigate, and coordinate a fix before public disclosure.

## Security model

RecordRelay handles database credentials. The security model is:

- **Credentials are stored encrypted** — passwords use AES-256 format
  (`ENC(AES256:<base64-ciphertext>)`) and are never stored or logged in plaintext.
- **Connection pools are sized minimally** — introspection pools are capped at 1
  connection to limit exposure.
- **No telemetry** — RecordRelay does not send usage data or credentials to any
  external service.
- **OWASP Dependency-Check** runs in CI on every push and fails on CVSS ≥ 7.
- **CodeQL** scans for security and quality issues on every push.

## Dependency updates

Dependabot is configured to submit weekly PRs for Gradle dependencies and GitHub
Actions. Security updates are reviewed and merged promptly.
