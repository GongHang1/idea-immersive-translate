package com.laowang.idea.immersive.core

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.laowang.idea.immersive.cache.TranslationCache
import com.laowang.idea.immersive.engine.TranslationEngine
import com.laowang.idea.immersive.engine.TranslationEngineResult
import com.laowang.idea.immersive.extractor.TextExtractor
import com.laowang.idea.immersive.renderer.TranslationRenderer
import com.laowang.idea.immersive.renderer.inlay.InlayRenderer

class TranslationCoordinatorTest : BasePlatformTestCase() {
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
