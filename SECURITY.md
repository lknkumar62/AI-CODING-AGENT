# Security Policy

VASU CODE AGENT handles AI provider API keys and (from Phase 4) GitHub access tokens.

## Reporting a vulnerability

Please open a private security advisory on this repository rather than a public issue.

## Handling of secrets

- API keys and tokens are stored only via Android Keystore-backed
  `EncryptedSharedPreferences`, never in plain text, SharedPreferences, or app logs.
- Authorization headers are redacted before any HTTP logging.
- The app is HTTPS-only, with a narrow, explicit cleartext exception limited to
  local-machine hosts (`127.0.0.1`, `localhost`, `10.0.2.2`) to support a locally running
  Ollama server.
- The AI agent must not receive unrelated private files — only files relevant to the
  current task are attached to a prompt unless the user explicitly requests broader
  repository access (enforced starting Phase 3's tool system).
