package com.laowang.idea.immersive.extractor

import com.laowang.idea.immersive.core.ExtractionScope
import com.laowang.idea.immersive.core.SourceType
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MarkdownBlockExtractorTest : BasePlatformTestCase() {
    fun testExtractsMarkdownProseAndSkipsFrontMatterAndFencedCode() {
        val text = """
            ---
            title: Hidden
            ---
            # Title

            Intro paragraph
            continues here.

            - First item
            * Second item

            ```kotlin
            # Not prose
            - Not list
            ```

            After fence paragraph.
        """.trimIndent()
        myFixture.configureByText("README.md", text)

        val segments = MarkdownBlockExtractor().extract(myFixture.editor, ExtractionScope.WHOLE_FILE)

        assertEquals(
            listOf(
                "# Title",
                "Intro paragraph\ncontinues here.",
                "- First item",
                "* Second item",
                "After fence paragraph.",
            ),
            segments.map { it.content },
        )
        assertTrue(segments.all { it.source == SourceType.MARKDOWN_BLOCK })
        assertEquals(text.indexOf("# Title"), segments.first().ranges.single().startOffset)
        assertTrue(segments.none { it.content.contains("Hidden") || it.content.contains("Not prose") })
    }
}
