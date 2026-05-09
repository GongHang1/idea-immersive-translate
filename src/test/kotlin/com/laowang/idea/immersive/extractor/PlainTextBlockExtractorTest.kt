package com.laowang.idea.immersive.extractor

import com.laowang.idea.immersive.core.ExtractionScope
import com.laowang.idea.immersive.core.SourceType
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PlainTextBlockExtractorTest : BasePlatformTestCase() {
    fun testSplitsParagraphsAndSkipsBlankAndSymbolOnlyBlocks() {
        myFixture.configureByText(
            "notes.txt",
            """
            First paragraph
            continues here.

            -----

            Second paragraph.
            """.trimIndent(),
        )

        val segments = PlainTextBlockExtractor().extract(myFixture.editor, ExtractionScope.WHOLE_FILE)

        assertEquals(listOf("First paragraph\ncontinues here.", "Second paragraph."), segments.map { it.content })
        assertTrue(segments.all { it.source == SourceType.PLAIN_TEXT_BLOCK })
    }

    fun testRespectsSelectionScope() {
        val text = """
            First paragraph.

            Selected paragraph
            continues here.

            Third paragraph.
        """.trimIndent()
        myFixture.configureByText("notes.txt", text)
        val selectionStart = text.indexOf("Selected paragraph")
        val selectionEnd = text.indexOf("Third paragraph") - 1
        myFixture.editor.selectionModel.setSelection(selectionStart, selectionEnd)

        val segments = PlainTextBlockExtractor().extract(myFixture.editor, ExtractionScope.CURRENT_SELECTION)

        assertEquals(listOf("Selected paragraph\ncontinues here."), segments.map { it.content })
    }
}
