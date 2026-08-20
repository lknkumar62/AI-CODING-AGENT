# VASU CODE AGENT

A mobile-first AI software development agent for Android — connect an OpenAI-compatible
coding model, browse a GitHub repository, read/edit files, run commands, and manage Git
changes, all from a phone. Built to work without a laptop.

## Status: Phase 1 (of 5)

This milestone compiles and runs. It ships:

- Jetpack Compose UI shell (Home / Agent chat / Settings) with dark developer theme
- AI provider abstraction (`AIProviderConfig`) — OpenRouter, any custom OpenAI-compatible
  endpoint, or a local Ollama server, all through one Retrofit client
- A working chat round-trip to `POST {baseUrl}/chat/completions`
- Encrypted settings storage (Android Keystore via `EncryptedSharedPreferences`) — the API
  key is never written in plain text, logged, or embedded in prompts
- HTTPS-only network security config, with a narrow cleartext exception scoped to
  `127.0.0.1` / `localhost` / `10.0.2.2` so local Ollama still works
- Offline detection: AI requests show "Offline — AI provider unavailable" instead of a
  fake response when there's no connection or no provider configured

**Not yet built** (see `Roadmap` below): file explorer, code editor, diff viewer, the
tool-calling agent loop, terminal, and GitHub integration. Nothing in this phase claims to
do those things — the Home screen labels them explicitly as upcoming.

## Getting started

1. Open this folder in Android Studio (Koala or newer). It will fetch the Gradle 8.7
   wrapper automatically on first sync.
2. Run on a device/emulator with API 26+.
3. Go to **Settings**, pick a provider preset (e.g. the free OpenRouter coder model) or
   fill in your own Base URL / API key / model, and save.
4. Go to **Agent** and start chatting.

### Suggested free/fast coding model

The Settings screen includes a one-tap preset for OpenRouter's free coder tier
(`qwen/qwen3-coder:free` at `https://openrouter.ai/api/v1`) — sign up for a free
OpenRouter API key and paste it in. A local-Ollama preset is also included for fully
offline/local inference (no API key required).

## Roadmap

- **Phase 2** — local file explorer, code editor (syntax highlighting, tabs, undo/redo), diff viewer
- **Phase 3** — agent tool system (`list_files`, `read_file`, `edit_file`, `git_*`, `run_command`, …), the agent loop, confirmation dialogs for dangerous ops, build/test integration
- **Phase 4** — GitHub OAuth, repository browser, commit/push, pull requests
- **Phase 5** — project memory, advanced debugging, multi-provider polish, performance

## Security

- API keys and (later) GitHub tokens are stored only in Keystore-backed encrypted prefs
- Authorization headers are redacted before anything reaches Logcat
- Cleartext HTTP is disabled except for local-machine Ollama hosts
- Destructive operations (delete, force-push, credential changes) always require explicit
  user confirmation — this lands with the Phase 3 tool system
- See `SECURITY.md` for reporting a vulnerability

## License

MIT — see `LICENSE`.
