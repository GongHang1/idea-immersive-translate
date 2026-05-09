package com.laowang.idea.immersive.renderer.inlay

import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.laowang.idea.immersive.core.SourceType
import com.laowang.idea.immersive.core.TextSegment
import com.laowang.idea.immersive.core.Translation

class InlayRendererTest : BasePlatformTestCase() {
    fun testRenderAddsBelowLineInlayForSegment() {
        myFixture.configureByText(
            "sample.java",
            """
            class Demo {
                // hello
                int value = 1;
            }
            """.trimIndent(),
        )
        val editor = myFixture.editor
        val renderer = InlayRenderer()
        val segment = segment(editor.document.text.indexOf("// hello"), "// hello".length)
        val translation = Translation(segment.id, "你好", "openai", 1L)

        renderer.render(editor, segment, translation)

        val inlays = editor.inlayModel.getBlockElementsInRange(0, editor.document.textLength + 1)
        assertEquals(1, inlays.size)
        assertEquals(com.intellij.openapi.editor.Inlay.Placement.BELOW_LINE, inlays.single().placement)
        assertEquals(segment.ranges.first().endOffset, inlays.single().offset)
        assertEquals("你好", (inlays.single().renderer as TranslationInlayRenderer).text)
    }

    fun testClearDisposesOnlyTargetSegmentInlay() {
        myFixture.configureByText(
            "sample.java",
            """
            class Demo {
                // first
                // second
            }
            """.trimIndent(),
        )
        val editor = myFixture.editor
        val renderer = InlayRenderer()
        val firstOffset = editor.document.text.indexOf("// first")
        val secondOffset = editor.document.text.indexOf("// second")
        val firstSegment = segment(firstOffset, "// first".length)
        val secondSegment = segment(secondOffset, "// second".length)

        renderer.render(editor, firstSegment, Translation(firstSegment.id, "一", "openai", 1L))
        renderer.render(editor, secondSegment, Translation(secondSegment.id, "二", "openai", 2L))

        renderer.clear(editor, firstSegment.id)

        val inlays = editor.inlayModel.getBlockElementsInRange(0, editor.document.textLength + 1)
        assertEquals(1, inlays.size)
        assertEquals("二", (inlays.single().renderer as TranslationInlayRenderer).text)
    }

    fun testClearAllDisposesAllTrackedInlays() {
        myFixture.configureByText(
            "sample.java",
            """
            class Demo {
                // first
                // second
            }
            """.trimIndent(),
        )
        val editor = myFixture.editor
        val renderer = InlayRenderer()
        val firstOffset = editor.document.text.indexOf("// first")
        val secondOffset = editor.document.text.indexOf("// second")
        val firstSegment = segment(firstOffset, "// first".length)
        val secondSegment = segment(secondOffset, "// second".length)

        renderer.render(editor, firstSegment, Translation(firstSegment.id, "一", "openai", 1L))
        renderer.render(editor, secondSegment, Translation(secondSegment.id, "二", "openai", 2L))

        renderer.clearAll(editor)

        val inlays = editor.inlayModel.getBlockElementsInRange(0, editor.document.textLength + 1)
        assertTrue(inlays.isEmpty())
    }

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

    private fun segment(offset: Int, length: Int): TextSegment {
        val content = myFixture.editor.document.getText(TextRange(offset, offset + length))
        return TextSegment(
            id = TextSegment.computeId(content, myFixture.file.virtualFile.path, offset, "openai", "zh-CN"),
            source = SourceType.PSI_COMMENT,
            content = content,
            ranges = listOf(TextRange(offset, offset + length)),
            filePath = myFixture.file.virtualFile.path,
            engineId = "openai",
            targetLang = "zh-CN",
        )
    }
}
