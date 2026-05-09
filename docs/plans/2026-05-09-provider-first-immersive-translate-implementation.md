# Provider-first Immersive Translate Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a provider-first immersive translation plugin that can reliably clear inline translations, translate code comments, Markdown, and plain text files, and route translations through OpenAI-compatible, Gemini, Google Cloud Translation, and Microsoft Translator providers.

**Architecture:** Keep `TranslationCoordinator` as the workflow owner. Add source resolution before extraction, move inlay lifecycle state into a shared overlay store, and keep all providers behind `TranslationEngine` implementations backed by typed settings and isolated credentials.

**Tech Stack:** Kotlin, IntelliJ Platform SDK, Gradle IntelliJ Platform plugin, OkHttp, kotlinx.serialization, JUnit platform tests with IntelliJ test fixtures and MockWebServer.

---

### Task 1: Stable Overlay Clearing

**Files:**
- Create: `src/main/kotlin/com/laowang/idea/immersive/renderer/TranslationOverlayStore.kt`
- Modify: `src/main/kotlin/com/laowang/idea/immersive/renderer/inlay/InlayRenderer.kt`
- Modify: `src/main/kotlin/com/laowang/idea/immersive/core/TranslationCoordinator.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`
- Test: `src/test/kotlin/com/laowang/idea/immersive/renderer/inlay/InlayRendererTest.kt`
- Test: `src/test/kotlin/com/laowang/idea/immersive/core/TranslationCoordinatorTest.kt`

**Step 1: Write failing renderer test**

Add a test proving a second `InlayRenderer` instance can clear an inlay created by the first instance.

```kotlin
fun testClearAllWorksAcrossRendererInstances() {
    myFixture.configureByText("sample.java", "// first")
    val editor = myFixture.editor
    val firstRenderer = InlayRenderer()
    val secondRenderer = InlayRenderer()
    val segment = segment(editor.document.text.indexOf("// first"), "// first".length)

    firstRenderer.render(editor, segment, Translation(segment.id, "一", "openai", 1L))
    secondRenderer.clearAll(editor)

    val inlays = editor.inlayModel.getBlockElementsInRange(0, editor.document.textLength + 1)
    assertTrue(inlays.isEmpty())
}
```

**Step 2: Run test to verify it fails**

Run:

```bash
./gradlew test --tests com.laowang.idea.immersive.renderer.inlay.InlayRendererTest
```

Expected: the new test fails because each renderer has its own inlay map.

**Step 3: Implement `TranslationOverlayStore`**

Create a project service that stores `Editor -> segmentId -> Inlay<*>` with:

```kotlin
fun put(editor: Editor, segmentId: String, inlay: Inlay<*>)
fun clear(editor: Editor, segmentId: String)
fun clearAll(editor: Editor)
fun hasAny(editor: Editor): Boolean
```

**Step 4: Refactor `InlayRenderer`**

Make `InlayRenderer` use `TranslationOverlayStore.getInstance(project)` when `editor.project` exists. Keep a private fallback store for tests without project.

**Step 5: Expose visibility in coordinator**

Add:

```kotlin
fun hasVisibleTranslations(editor: Editor): Boolean
```

Delegate to the active renderer if it supports visibility or directly to `TranslationOverlayStore`.

**Step 6: Run tests**

Run:

```bash
./gradlew test --tests com.laowang.idea.immersive.renderer.inlay.InlayRendererTest --tests com.laowang.idea.immersive.core.TranslationCoordinatorTest
```

Expected: PASS.

**Step 7: Commit**

```bash
git add src/main/kotlin/com/laowang/idea/immersive/renderer/TranslationOverlayStore.kt \
  src/main/kotlin/com/laowang/idea/immersive/renderer/inlay/InlayRenderer.kt \
  src/main/kotlin/com/laowang/idea/immersive/core/TranslationCoordinator.kt \
  src/main/resources/META-INF/plugin.xml \
  src/test/kotlin/com/laowang/idea/immersive/renderer/inlay/InlayRendererTest.kt \
  src/test/kotlin/com/laowang/idea/immersive/core/TranslationCoordinatorTest.kt
git commit -m "fix: 修复翻译内联清除状态"
```

### Task 2: Source Type Resolution

**Files:**
- Create: `src/main/kotlin/com/laowang/idea/immersive/core/SourceTypeResolver.kt`
- Modify: `src/main/kotlin/com/laowang/idea/immersive/core/Model.kt`
- Modify: `src/main/kotlin/com/laowang/idea/immersive/trigger/ImmersiveTranslateAction.kt`
- Modify: `src/main/kotlin/com/laowang/idea/immersive/core/TranslationCoordinator.kt`
- Test: `src/test/kotlin/com/laowang/idea/immersive/core/SourceTypeResolverTest.kt`
- Test: `src/test/kotlin/com/laowang/idea/immersive/trigger/ImmersiveTranslateActionTest.kt`

**Step 1: Write failing resolver tests**

Cover:

- `README.md` resolves to `MARKDOWN_BLOCK`
- `notes.txt` resolves to `PLAIN_TEXT_BLOCK`
- `A.java` resolves to `PSI_COMMENT`

**Step 2: Run resolver tests**

Run:

```bash
./gradlew test --tests com.laowang.idea.immersive.core.SourceTypeResolverTest
```

Expected: FAIL because resolver and `PLAIN_TEXT_BLOCK` do not exist.

**Step 3: Add `PLAIN_TEXT_BLOCK`**

Modify `SourceType`:

```kotlin
enum class SourceType {
    PSI_COMMENT,
    MARKDOWN_BLOCK,
    PLAIN_TEXT_BLOCK,
    CONSOLE_LINE,
    QUICK_DOC,
}
```

Update default extractor ID mapping:

```kotlin
SourceType.PLAIN_TEXT_BLOCK -> "plain-text-block"
```

**Step 4: Implement `SourceTypeResolver`**

Resolve by virtual file extension first, then file type name, then PSI availability.

**Step 5: Update action**

Change `ImmersiveTranslateAction` to call:

```kotlin
val sourceType = SourceTypeResolver.resolve(editor)
project.getService(TranslationCoordinator::class.java)
    .translate(editor, sourceType, ExtractionScope.WHOLE_FILE)
```

**Step 6: Run tests**

Run:

```bash
./gradlew test --tests com.laowang.idea.immersive.core.SourceTypeResolverTest --tests com.laowang.idea.immersive.trigger.ImmersiveTranslateActionTest
```

Expected: PASS.

**Step 7: Commit**

```bash
git add src/main/kotlin/com/laowang/idea/immersive/core/SourceTypeResolver.kt \
  src/main/kotlin/com/laowang/idea/immersive/core/Model.kt \
  src/main/kotlin/com/laowang/idea/immersive/trigger/ImmersiveTranslateAction.kt \
  src/main/kotlin/com/laowang/idea/immersive/core/TranslationCoordinator.kt \
  src/test/kotlin/com/laowang/idea/immersive/core/SourceTypeResolverTest.kt \
  src/test/kotlin/com/laowang/idea/immersive/trigger/ImmersiveTranslateActionTest.kt
git commit -m "feat: 自动识别翻译文件类型"
```

### Task 3: Markdown and Plain Text Extractors

**Files:**
- Create: `src/main/kotlin/com/laowang/idea/immersive/extractor/MarkdownBlockExtractor.kt`
- Create: `src/main/kotlin/com/laowang/idea/immersive/extractor/PlainTextBlockExtractor.kt`
- Create: `src/main/kotlin/com/laowang/idea/immersive/extractor/EditorScopeResolver.kt`
- Modify: `src/main/kotlin/com/laowang/idea/immersive/extractor/PsiCommentExtractor.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`
- Test: `src/test/kotlin/com/laowang/idea/immersive/extractor/MarkdownBlockExtractorTest.kt`
- Test: `src/test/kotlin/com/laowang/idea/immersive/extractor/PlainTextBlockExtractorTest.kt`
- Test: `src/test/kotlin/com/laowang/idea/immersive/extractor/PsiCommentExtractorTest.kt`

**Step 1: Extract common scope resolver**

Move current line, selection, visible area, and whole file range logic out of `PsiCommentExtractor` into `EditorScopeResolver`.

**Step 2: Run existing extractor tests**

Run:

```bash
./gradlew test --tests com.laowang.idea.immersive.extractor.PsiCommentExtractorTest
```

Expected: PASS after refactor.

**Step 3: Write failing Markdown tests**

Test that Markdown extraction:

- Extracts `# Title`
- Extracts paragraph text
- Extracts list item prose
- Skips fenced code blocks
- Skips YAML front matter

**Step 4: Implement `MarkdownBlockExtractor`**

Use a line scanner:

- Track front matter only at file start.
- Track fenced code blocks using lines starting with ``` or `~~~`.
- Group paragraph lines until a blank line.
- Keep each segment anchored to the source text range.

**Step 5: Write failing plain text tests**

Test that plain text extraction:

- Splits paragraphs on blank lines.
- Skips blank and symbol-only blocks.
- Respects selection scope.

**Step 6: Implement `PlainTextBlockExtractor`**

Use `EditorScopeResolver` and paragraph scanning over the selected range.

**Step 7: Register extractors**

Add to `plugin.xml`:

```xml
<textExtractor implementation="com.laowang.idea.immersive.extractor.MarkdownBlockExtractor"/>
<textExtractor implementation="com.laowang.idea.immersive.extractor.PlainTextBlockExtractor"/>
```

**Step 8: Run extractor tests**

Run:

```bash
./gradlew test --tests com.laowang.idea.immersive.extractor.MarkdownBlockExtractorTest \
  --tests com.laowang.idea.immersive.extractor.PlainTextBlockExtractorTest \
  --tests com.laowang.idea.immersive.extractor.PsiCommentExtractorTest
```

Expected: PASS.

**Step 9: Commit**

```bash
git add src/main/kotlin/com/laowang/idea/immersive/extractor \
  src/main/resources/META-INF/plugin.xml \
  src/test/kotlin/com/laowang/idea/immersive/extractor
git commit -m "feat: 支持Markdown和文本提取"
```

### Task 4: Provider Settings and Credentials

**Files:**
- Create: `src/main/kotlin/com/laowang/idea/immersive/settings/ProviderConfig.kt`
- Modify: `src/main/kotlin/com/laowang/idea/immersive/settings/ImmersiveTranslateSettings.kt`
- Modify: `src/main/kotlin/com/laowang/idea/immersive/settings/CredentialsStore.kt`
- Modify: `src/main/kotlin/com/laowang/idea/immersive/settings/SettingsService.kt`
- Modify: `src/main/kotlin/com/laowang/idea/immersive/settings/SettingsPanel.kt`
- Test: `src/test/kotlin/com/laowang/idea/immersive/settings/ImmersiveTranslateSettingsTest.kt`
- Test: `src/test/kotlin/com/laowang/idea/immersive/settings/CredentialsStoreTest.kt`

**Step 1: Write failing settings tests**

Cover:

- Provider config defaults include OpenAI, Gemini, Microsoft, and Google.
- Updating Gemini model does not alter OpenAI model.
- Credentials for provider IDs are isolated.

**Step 2: Run settings tests**

Run:

```bash
./gradlew test --tests com.laowang.idea.immersive.settings.ImmersiveTranslateSettingsTest \
  --tests com.laowang.idea.immersive.settings.CredentialsStoreTest
```

Expected: FAIL because provider-scoped settings do not exist.

**Step 3: Add provider config model**

Implement serializable state classes for:

- `ProviderKind`
- `ProviderConfigState`
- provider ID constants

Keep non-secret values in persistent state.

**Step 4: Refactor credentials**

Add:

```kotlin
fun getApiKey(providerId: String): String?
fun setApiKey(providerId: String, apiKey: String?)
```

Keep old `getApiKey()` and `setApiKey()` temporarily delegating to OpenAI to reduce refactor size.

**Step 5: Refactor settings service**

Expose:

```kotlin
fun activeProviderConfig(): ProviderConfigState
fun providerConfig(providerId: String): ProviderConfigState
```

**Step 6: Update settings panel**

First version can use a provider combo and provider-specific rows. Hide unrelated rows when provider changes.

**Step 7: Run settings tests**

Run:

```bash
./gradlew test --tests com.laowang.idea.immersive.settings.ImmersiveTranslateSettingsTest \
  --tests com.laowang.idea.immersive.settings.CredentialsStoreTest
```

Expected: PASS.

**Step 8: Commit**

```bash
git add src/main/kotlin/com/laowang/idea/immersive/settings \
  src/test/kotlin/com/laowang/idea/immersive/settings
git commit -m "feat: 增加多翻译渠道配置"
```

### Task 5: Gemini Engine

**Files:**
- Create: `src/main/kotlin/com/laowang/idea/immersive/engine/GeminiEngine.kt`
- Modify: `src/main/kotlin/com/laowang/idea/immersive/engine/TranslationEngine.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`
- Test: `src/test/kotlin/com/laowang/idea/immersive/engine/GeminiEngineTest.kt`

**Step 1: Write failing Gemini tests**

Use `MockWebServer` to verify:

- Request URL includes `/v1beta/models/{model}:generateContent`
- API key is sent according to the chosen REST approach.
- Request includes system instruction or prompt constraints.
- Response text maps back to segment translations in order.
- Count mismatch returns `TranslationEngineResult.Failure`.

**Step 2: Run test to verify failure**

Run:

```bash
./gradlew test --tests com.laowang.idea.immersive.engine.GeminiEngineTest
```

Expected: FAIL because engine does not exist.

**Step 3: Implement Gemini request/response models**

Use kotlinx.serialization data classes for `GenerateContentRequest` and response candidates.

**Step 4: Implement engine**

Use the same delimiter discipline as `OpenAIEngine` first. Set temperature to `0.0` through `generationConfig`.

**Step 5: Register engine**

Add to `plugin.xml`:

```xml
<translationEngine implementation="com.laowang.idea.immersive.engine.GeminiEngine"/>
```

**Step 6: Run engine tests**

Run:

```bash
./gradlew test --tests com.laowang.idea.immersive.engine.GeminiEngineTest
```

Expected: PASS.

**Step 7: Commit**

```bash
git add src/main/kotlin/com/laowang/idea/immersive/engine/GeminiEngine.kt \
  src/main/kotlin/com/laowang/idea/immersive/engine/TranslationEngine.kt \
  src/main/resources/META-INF/plugin.xml \
  src/test/kotlin/com/laowang/idea/immersive/engine/GeminiEngineTest.kt
git commit -m "feat: 接入Gemini翻译渠道"
```

### Task 6: Microsoft Translator Engine

**Files:**
- Create: `src/main/kotlin/com/laowang/idea/immersive/engine/MicrosoftTranslatorEngine.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`
- Test: `src/test/kotlin/com/laowang/idea/immersive/engine/MicrosoftTranslatorEngineTest.kt`

**Step 1: Write failing Microsoft tests**

Use `MockWebServer` to verify:

- URL includes `/translate?api-version=3.0&to=zh-Hans` or configured target.
- Header `Ocp-Apim-Subscription-Key` is present.
- Header `Ocp-Apim-Subscription-Region` is present only when configured.
- Body is an array of `{ "Text": "..." }`.
- Response parses `translations[0].text`.

**Step 2: Run test to verify failure**

Run:

```bash
./gradlew test --tests com.laowang.idea.immersive.engine.MicrosoftTranslatorEngineTest
```

Expected: FAIL because engine does not exist.

**Step 3: Implement engine**

Map provider language defaults carefully:

- Internal `zh-CN` should map to Microsoft `zh-Hans`.
- Preserve user-configured target language when it is already a Microsoft-supported code.

**Step 4: Register engine**

Add to `plugin.xml`:

```xml
<translationEngine implementation="com.laowang.idea.immersive.engine.MicrosoftTranslatorEngine"/>
```

**Step 5: Run engine tests**

Run:

```bash
./gradlew test --tests com.laowang.idea.immersive.engine.MicrosoftTranslatorEngineTest
```

Expected: PASS.

**Step 6: Commit**

```bash
git add src/main/kotlin/com/laowang/idea/immersive/engine/MicrosoftTranslatorEngine.kt \
  src/main/resources/META-INF/plugin.xml \
  src/test/kotlin/com/laowang/idea/immersive/engine/MicrosoftTranslatorEngineTest.kt
git commit -m "feat: 接入微软翻译渠道"
```

### Task 7: Google Cloud Translation Engine

**Files:**
- Create: `src/main/kotlin/com/laowang/idea/immersive/engine/GoogleCloudTranslateEngine.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`
- Test: `src/test/kotlin/com/laowang/idea/immersive/engine/GoogleCloudTranslateEngineTest.kt`

**Step 1: Write failing Google tests**

Use `MockWebServer` to verify:

- URL includes `/v3/projects/{project}/locations/{location}:translateText` when location is configured.
- Body includes `contents`, `targetLanguageCode`, and `mimeType = text/plain`.
- Authorization header is present.
- Response parses `translations[].translatedText`.

**Step 2: Run test to verify failure**

Run:

```bash
./gradlew test --tests com.laowang.idea.immersive.engine.GoogleCloudTranslateEngineTest
```

Expected: FAIL because engine does not exist.

**Step 3: Implement engine**

For first version, support bearer token or stored credential string as configured by settings. Do not embed service account parsing unless required by the user; document this limitation.

**Step 4: Register engine**

Add to `plugin.xml`:

```xml
<translationEngine implementation="com.laowang.idea.immersive.engine.GoogleCloudTranslateEngine"/>
```

**Step 5: Run engine tests**

Run:

```bash
./gradlew test --tests com.laowang.idea.immersive.engine.GoogleCloudTranslateEngineTest
```

Expected: PASS.

**Step 6: Commit**

```bash
git add src/main/kotlin/com/laowang/idea/immersive/engine/GoogleCloudTranslateEngine.kt \
  src/main/resources/META-INF/plugin.xml \
  src/test/kotlin/com/laowang/idea/immersive/engine/GoogleCloudTranslateEngineTest.kt
git commit -m "feat: 接入谷歌云翻译渠道"
```

### Task 8: Coordinator Integration and Error Safety

**Files:**
- Modify: `src/main/kotlin/com/laowang/idea/immersive/core/TranslationCoordinator.kt`
- Modify: `src/main/kotlin/com/laowang/idea/immersive/core/ErrorHandler.kt`
- Test: `src/test/kotlin/com/laowang/idea/immersive/core/TranslationCoordinatorTest.kt`

**Step 1: Write failing coordinator tests**

Cover:

- Empty extraction leaves no render calls.
- Translation count mismatch renders nothing.
- Failed provider call renders nothing and notifies error.
- Cached translations still render for matching segment IDs.

**Step 2: Run coordinator tests**

Run:

```bash
./gradlew test --tests com.laowang.idea.immersive.core.TranslationCoordinatorTest
```

Expected: FAIL for new error safety behavior if current coordinator renders partial mismatches.

**Step 3: Harden coordinator**

Before rendering fresh translations:

- Verify `translations.size == segments.size`.
- Verify each translation maps to the intended segment ID when possible.
- On mismatch, call `notifyError(TranslationError.Unknown(...))` and render nothing.

**Step 4: Run coordinator tests**

Run:

```bash
./gradlew test --tests com.laowang.idea.immersive.core.TranslationCoordinatorTest
```

Expected: PASS.

**Step 5: Commit**

```bash
git add src/main/kotlin/com/laowang/idea/immersive/core/TranslationCoordinator.kt \
  src/main/kotlin/com/laowang/idea/immersive/core/ErrorHandler.kt \
  src/test/kotlin/com/laowang/idea/immersive/core/TranslationCoordinatorTest.kt
git commit -m "fix: 防止翻译结果错位渲染"
```

### Task 9: Documentation and Manual Test Plan

**Files:**
- Modify: `README.md`
- Modify: `docs/manual-test-plan.md`
- Modify: `CHANGELOG.md`
- Modify: `src/main/resources/META-INF/plugin.xml`
- Modify: `src/main/resources/messages/ImmersiveTranslateBundle.properties`
- Modify: `src/main/resources/messages/ImmersiveTranslateBundle_zh_CN.properties`

**Step 1: Update README**

Document:

- Supported file types.
- Supported providers.
- Provider credential requirements.
- Clear/toggle behavior.

**Step 2: Update manual test plan**

Add manual scenarios:

- Code comments.
- Markdown with fenced code.
- Plain text paragraphs.
- Clear after translation.
- Provider switching.
- Missing key error for each provider.

**Step 3: Update plugin metadata and messages**

Make plugin description no longer claim v0.1.0 supports only OpenAI + comments.

**Step 4: Run documentation smoke checks**

Run:

```bash
./gradlew test
./gradlew buildPlugin
```

Expected: PASS.

**Step 5: Commit**

```bash
git add README.md docs/manual-test-plan.md CHANGELOG.md \
  src/main/resources/META-INF/plugin.xml \
  src/main/resources/messages/ImmersiveTranslateBundle.properties \
  src/main/resources/messages/ImmersiveTranslateBundle_zh_CN.properties
git commit -m "docs: 更新多渠道翻译使用说明"
```

### Task 10: Final Verification

**Files:**
- No expected source edits unless verification reveals defects.

**Step 1: Run full test suite**

Run:

```bash
./gradlew test
```

Expected: PASS.

**Step 2: Build plugin**

Run:

```bash
./gradlew buildPlugin
```

Expected: PASS and plugin zip under `build/distributions/`.

**Step 3: Optional sandbox smoke**

Run:

```bash
./gradlew runIde
```

Expected:

- Open sandbox IDE.
- Configure at least one provider.
- Translate a Java/Kotlin comment.
- Clear inline output.
- Translate Markdown prose and verify code fences are skipped.
- Translate plain text paragraphs.

**Step 4: Inspect final diff**

Run:

```bash
git status --short
git log --oneline -10
```

Expected:

- Only intentional files are modified.
- Commit history contains small scoped commits from this plan.

