# Manual Test Plan

## Scope

Smoke test for the provider-first snapshot of IDEA Immersive Translate.

## Preconditions

- Plugin ZIP can be built with `./gradlew buildPlugin`
- Sandbox IDE can be started with `./gradlew runIde`
- At least one provider credential is available
- Java/Kotlin, Markdown, and plain text sample files are available

## Test Steps

1. Build the plugin ZIP:

   ```bash
   ./gradlew buildPlugin
   ```

2. Start the sandbox IDE:

   ```bash
   ./gradlew runIde
   ```

3. In the sandbox IDE, open plugin settings and configure one provider.
4. Open a Java or Kotlin file that contains line comments, block comments, and Javadoc/KDoc.
5. Trigger translation with `Alt+I`.
6. Confirm translated inlays appear below comment lines.
7. Run the clear action and confirm all existing translation inlays disappear.
8. Open a Markdown file with YAML front matter, prose, list items, and a fenced code block.
9. Trigger translation and confirm prose/list content is translated while front matter and fenced code are skipped.
10. Open a plain text file with multiple paragraphs separated by blank lines.
11. Trigger translation and confirm each prose paragraph is translated.
12. Select one plain text paragraph and trigger translation for the selection path when available.
13. Switch providers in settings and repeat a short comment translation.

## Expected Results

- `Alt+I` and the right-click action both trigger source-aware translation
- Code comment, Markdown, and plain text extraction paths produce inlays
- Markdown fenced code and YAML front matter do not produce translation segments
- Provider switching changes the active translation backend without reusing another provider's credential
- Clear translations removes all current editor inlays without modifying source text

## Error Smoke Cases

### Missing Provider Credential

- For each provider, trigger translation without configuring its credential
- Expect a clear error or notification and no inlay output

### Provider Switching

- Configure OpenAI-compatible and Gemini credentials
- Translate one short comment with OpenAI-compatible
- Switch to Gemini and translate the same comment
- Expect a new request through Gemini and no credential leakage between providers

### Google Cloud Limitation

- Configure Google Cloud Translation with a bearer token, project ID, and location
- Expect translation to work
- Paste a service account JSON key as the credential
- Expect it to be treated as a raw bearer credential; service account JWT exchange is outside this snapshot

### Rate Limit

- Trigger translation until the upstream service returns a rate-limit response
- Expect a visible error or retry-related feedback and no corrupted inlay state

### Network Timeout

- Disconnect the network or force a timeout path
- Expect a timeout error and no editor corruption

## Pass/Fail Checklist

- [ ] Plugin builds successfully
- [ ] Sandbox IDE launches successfully
- [ ] Provider credentials can be configured independently
- [ ] `Alt+I` trigger works
- [ ] Right-click trigger works
- [ ] Bilingual inlay display is rendered below comment lines
- [ ] Markdown prose and list items translate
- [ ] Markdown fenced code and YAML front matter are skipped
- [ ] Plain text paragraphs translate
- [ ] Clear translations removes current inlays
- [ ] Missing credential case reports an error for each provider
- [ ] Rate limit case reports an error
- [ ] Network timeout case reports an error
