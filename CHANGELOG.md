# Changelog

## [0.1.0-SNAPSHOT] - WIP

### Added

- Initial project scaffold for the IntelliJ Platform plugin
- Java and Kotlin comment extraction path for v0.1.0
- OpenAI translation engine integration with BYOK setup
- Gemini translation engine integration
- Microsoft Translator integration
- Google Cloud Translation v3 integration with bearer-token credentials
- Markdown heading, prose, and list extraction
- Plain text paragraph extraction
- Provider-scoped settings and credential storage
- Shared overlay store for stable cross-action clearing
- Inlay block renderer for bilingual display below source lines
- In-memory LRU translation cache
- PasswordSafe-backed credential storage and settings entry point

### Changed

- Translation action now resolves source type from the active editor file.
- Fresh translation results are rendered only when provider response count and segment IDs align.
