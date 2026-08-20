# Contributing

This project is built in phases (see README → Roadmap). Please:

1. Keep changes scoped to one phase/feature per PR.
2. Never commit API keys, GitHub tokens, `.jks`/`.keystore` files, or `local.properties`.
3. Run a full build (`./gradlew assembleDebug`) before opening a PR.
4. Match the existing package layout: `data/`, `domain/`, `ai/`, `editor/`, `terminal/`,
   `git/`, `ui/`, `security/`.
5. New destructive tool operations must go through the confirmation flow — no exceptions.
