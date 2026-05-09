# IDEA Immersive Translate

Immersive bilingual translation for IntelliJ IDEA. The current snapshot translates code comments, Markdown prose, and plain text paragraphs into inline inlay text inside the editor.

## Status

`v0.1.0-SNAPSHOT`

## Supported Content

- Java and Kotlin comments, including line comments, block comments, Javadoc, and KDoc
- Markdown headings, paragraphs, and list items
- Plain text paragraphs split by blank lines

Markdown YAML front matter and fenced code blocks are skipped.

## Providers

- OpenAI-compatible chat completions
- Gemini `generateContent`
- Microsoft Translator
- Google Cloud Translation v3

Each provider uses its own stored credential. OpenAI-compatible and Gemini providers use model settings. Microsoft Translator can optionally use a region. Google Cloud Translation requires a project ID, location, and bearer token; service account JSON parsing is not implemented in this snapshot.

## Editor Actions

- Trigger translation with `Alt+I` or the editor right-click menu
- Clear rendered translations with the clear action
- Clear removes current editor inlays only and never edits source text
- Translation results are cached by segment ID, provider, and target language

## Build

```bash
./gradlew buildPlugin
```

Expected artifact:

```text
build/distributions/idea-immersive-translate-0.1.0-SNAPSHOT.zip
```

## Run in Sandbox IDE

```bash
./gradlew runIde
```

## License

MIT. See [LICENSE](/Volumes/gong_hang/Github_projects/idea-immersive-translate/LICENSE).
