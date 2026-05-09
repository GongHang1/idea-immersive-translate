package com.laowang.idea.immersive.core

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.laowang.idea.immersive.cache.TranslationCache
import com.laowang.idea.immersive.engine.TranslationEngine
import com.laowang.idea.immersive.engine.TranslationEngineResult
import com.laowang.idea.immersive.extractor.TextExtractor
import com.laowang.idea.immersive.renderer.TranslationRenderer
import com.laowang.idea.immersive.renderer.inlay.InlayRenderer

class TranslationCoordinatorTest : BasePlatformTestCase() {
    fun testTranslateRunsInsideProgressTask() {
        myFixture.configureByText("A.java", "// hello\nclass A {}")
        val segment = segment("seg-1", "hello")
        val renderer = RecordingRenderer()
        val progressRunner = RecordingProgressRunner()
        val coordinator = TranslationCoordinator(
            project = project,
            extractorProvider = { FakeExtractor(listOf(segment)) },
            engineProvider = { PrefixEngine() },
            cache = TranslationCache(maxEntries = 10),
            rendererProvider = { renderer },
            progressRunner = progressRunner,
            progressTitleProvider = { "test translating" },
        )

        coordinator.translate(myFixture.editor, SourceType.PSI_COMMENT, ExtractionScope.WHOLE_FILE)

        assertEquals(listOf("test translating"), progressRunner.titles)
        assertEquals(listOf("seg-1" to "[zh] hello"), renderer.rendered)
    }

    fun testTranslatesMissingSegmentsAndRendersResults() {
        myFixture.configureByText("A.java", "// hello\nclass A {}")
        val segment = segment("seg-1", "hello")
        val renderer = RecordingRenderer()
        val coordinator = TranslationCoordinator(
            project = project,
            extractorProvider = { FakeExtractor(listOf(segment)) },
            engineProvider = { PrefixEngine() },
            cache = TranslationCache(maxEntries = 10),
            rendererProvider = { renderer },
        )

        coordinator.translateBlocking(myFixture.editor, SourceType.PSI_COMMENT, ExtractionScope.WHOLE_FILE)

        assertEquals(listOf("seg-1" to "[zh] hello"), renderer.rendered)
    }

    fun testUsesCacheOnSecondTranslation() {
        myFixture.configureByText("A.java", "// hello\nclass A {}")
        val segment = segment("seg-1", "hello")
        val engine = PrefixEngine()
        val renderer = RecordingRenderer()
        val coordinator = TranslationCoordinator(
            project = project,
            extractorProvider = { FakeExtractor(listOf(segment)) },
            engineProvider = { engine },
            cache = TranslationCache(maxEntries = 10),
            rendererProvider = { renderer },
        )

        coordinator.translateBlocking(myFixture.editor, SourceType.PSI_COMMENT, ExtractionScope.WHOLE_FILE)
        coordinator.translateBlocking(myFixture.editor, SourceType.PSI_COMMENT, ExtractionScope.WHOLE_FILE)

        assertEquals(1, engine.calls)
        assertEquals(listOf("seg-1" to "[zh] hello", "seg-1" to "[zh] hello"), renderer.rendered)
    }

    fun testReportsVisibleTranslationsForInlayRenderer() {
        myFixture.configureByText("A.java", "// hello\nclass A {}")
        val segment = segment("seg-1", "hello")
        val renderer = InlayRenderer()
        val coordinator = TranslationCoordinator(
            project = project,
            extractorProvider = { FakeExtractor(listOf(segment)) },
            engineProvider = { PrefixEngine() },
            cache = TranslationCache(maxEntries = 10),
            rendererProvider = { renderer },
        )

        coordinator.translateBlocking(myFixture.editor, SourceType.PSI_COMMENT, ExtractionScope.WHOLE_FILE)

        assertTrue(coordinator.hasVisibleTranslations(myFixture.editor))

        coordinator.clear(myFixture.editor)

        assertFalse(coordinator.hasVisibleTranslations(myFixture.editor))
    }

    fun testToggleClearsVisibleTranslationsInsteadOfRetranslating() {
        myFixture.configureByText("A.java", "// hello\nclass A {}")
        val segment = segment("seg-1", "hello")
        val renderer = InlayRenderer()
        val engine = PrefixEngine()
        val coordinator = TranslationCoordinator(
            project = project,
            extractorProvider = { FakeExtractor(listOf(segment)) },
            engineProvider = { engine },
            cache = TranslationCache(maxEntries = 10),
            rendererProvider = { renderer },
        )

        coordinator.toggle(myFixture.editor, SourceType.PSI_COMMENT, ExtractionScope.WHOLE_FILE)

        assertEquals(1, engine.calls)
        assertTrue(coordinator.hasVisibleTranslations(myFixture.editor))

        coordinator.toggle(myFixture.editor, SourceType.PSI_COMMENT, ExtractionScope.WHOLE_FILE)

        assertEquals(1, engine.calls)
        assertFalse(coordinator.hasVisibleTranslations(myFixture.editor))
    }

    fun testEmptyExtractionRendersNothing() {
        myFixture.configureByText("A.java", "class A {}")
        val renderer = RecordingRenderer()
        val coordinator = TranslationCoordinator(
            project = project,
            extractorProvider = { FakeExtractor(emptyList()) },
            engineProvider = { PrefixEngine() },
            cache = TranslationCache(maxEntries = 10),
            rendererProvider = { renderer },
        )

        coordinator.translateBlocking(myFixture.editor, SourceType.PSI_COMMENT, ExtractionScope.WHOLE_FILE)

        assertTrue(renderer.rendered.isEmpty())
    }

    fun testTranslationCountMismatchRendersNothingAndNotifiesError() {
        myFixture.configureByText("A.java", "// hello\n// world")
        val first = segment("seg-1", "hello")
        val second = segment("seg-2", "world")
        val renderer = RecordingRenderer()
        val errors = mutableListOf<TranslationError>()
        val coordinator = TranslationCoordinator(
            project = project,
            extractorProvider = { FakeExtractor(listOf(first, second)) },
            engineProvider = {
                FixedEngine(
                    listOf(
                        Translation(first.id, "你好", "openai", 1L),
                    ),
                )
            },
            cache = TranslationCache(maxEntries = 10),
            rendererProvider = { renderer },
            errorNotifier = { errors += it },
        )

        coordinator.translateBlocking(myFixture.editor, SourceType.PSI_COMMENT, ExtractionScope.WHOLE_FILE)

        assertTrue(renderer.rendered.isEmpty())
        assertTrue(errors.single() is TranslationError.Unknown)
    }

    fun testFailedProviderCallRendersNothingAndNotifiesError() {
        myFixture.configureByText("A.java", "// hello")
        val renderer = RecordingRenderer()
        val errors = mutableListOf<TranslationError>()
        val coordinator = TranslationCoordinator(
            project = project,
            extractorProvider = { FakeExtractor(listOf(segment("seg-1", "hello"))) },
            engineProvider = { FailureEngine(TranslationError.NoApiKey) },
            cache = TranslationCache(maxEntries = 10),
            rendererProvider = { renderer },
            errorNotifier = { errors += it },
        )

        coordinator.translateBlocking(myFixture.editor, SourceType.PSI_COMMENT, ExtractionScope.WHOLE_FILE)

        assertTrue(renderer.rendered.isEmpty())
        assertEquals(listOf(TranslationError.NoApiKey), errors)
    }

    fun testCachedTranslationsRenderForMatchingSegmentIds() {
        myFixture.configureByText("A.java", "// hello")
        val segment = segment("seg-1", "hello")
        val cache = TranslationCache(maxEntries = 10)
        cache.put(Translation(segment.id, "缓存译文", "openai", 1L))
        val renderer = RecordingRenderer()
        val engine = PrefixEngine()
        val coordinator = TranslationCoordinator(
            project = project,
            extractorProvider = { FakeExtractor(listOf(segment)) },
            engineProvider = { engine },
            cache = cache,
            rendererProvider = { renderer },
        )

        coordinator.translateBlocking(myFixture.editor, SourceType.PSI_COMMENT, ExtractionScope.WHOLE_FILE)

        assertEquals(0, engine.calls)
        assertEquals(listOf("seg-1" to "缓存译文"), renderer.rendered)
    }

    private fun segment(id: String, content: String): TextSegment =
        TextSegment(
            id = id,
            source = SourceType.PSI_COMMENT,
            content = content,
            ranges = listOf(TextRange(0, content.length)),
            filePath = "A.java",
            engineId = "openai",
            targetLang = "zh-CN",
        )

    private class FakeExtractor(private val segments: List<TextSegment>) : TextExtractor {
        override val id: String = "psi-comment"

        override fun isApplicable(editor: Editor): Boolean = true

        override fun extract(editor: Editor, scope: ExtractionScope): List<TextSegment> = segments
    }

    private class PrefixEngine : TranslationEngine {
        var calls: Int = 0
            private set

        override val id: String = "openai"
        override val displayName: String = "Fake OpenAI"

        override fun translate(segments: List<TextSegment>): TranslationEngineResult {
            calls += 1
            return TranslationEngineResult.Success(
                segments.map { segment ->
                    Translation(
                        segmentId = segment.id,
                        translatedText = "[zh] ${segment.content}",
                        engineId = id,
                        timestamp = calls.toLong(),
                    )
                },
            )
        }
    }

    private class FixedEngine(private val translations: List<Translation>) : TranslationEngine {
        override val id: String = "openai"
        override val displayName: String = "Fixed OpenAI"

        override fun translate(segments: List<TextSegment>): TranslationEngineResult =
            TranslationEngineResult.Success(translations)
    }

    private class FailureEngine(private val error: TranslationError) : TranslationEngine {
        override val id: String = "openai"
        override val displayName: String = "Failing OpenAI"

        override fun translate(segments: List<TextSegment>): TranslationEngineResult =
            TranslationEngineResult.Failure(error)
    }

    private class RecordingProgressRunner : TranslationProgressRunner {
        val titles = mutableListOf<String>()

        override fun run(project: Project, title: String, task: (TranslationCancellationToken) -> Unit) {
            titles += title
            task(TranslationCancellationToken())
        }
    }

    private class RecordingRenderer : TranslationRenderer {
        val rendered = mutableListOf<Pair<String, String>>()

        override val id: String = "inlay"

        override fun isApplicable(editor: Editor): Boolean = true

        override fun render(editor: Editor, segment: TextSegment, translation: Translation) {
            rendered += segment.id to translation.translatedText
        }

        override fun clear(editor: Editor, segmentId: String) = Unit

        override fun clearAll(editor: Editor) = Unit
    }
}
