package com.laowang.idea.immersive.extractor

import com.laowang.idea.immersive.core.ExtractionScope
import com.laowang.idea.immersive.core.SourceType
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PsiCommentExtractorTest : BasePlatformTestCase() {
    fun testExtractsJavaLineJavadocAndBlockComments() {
        myFixture.configureByText(
            "Demo.java",
            """
            class Demo {
                // single line
                /**
                 * doc line 1
                 * doc line 2
                 */
                /* block line 1
                 * block line 2
                 */
                int value = 1;
            }
            """.trimIndent(),
        )
        val segments = PsiCommentExtractor().extract(myFixture.editor, ExtractionScope.WHOLE_FILE)

        assertEquals(3, segments.size)
        assertEquals(listOf("single line", "doc line 1\ndoc line 2", "block line 1\nblock line 2"), segments.map { it.content })
        assertTrue(segments.all { it.source == SourceType.PSI_COMMENT })
        assertTrue(segments.all { it.engineId == "openai" && it.targetLang == "zh-CN" })
        assertEquals(segments.map { it.id }, segments.map { it.id }.distinct())
    }

    fun testStripsMarkersAndBlankLines() {
        myFixture.configureByText(
            "Demo.java",
            """
            class Demo {
                /// triple slash
                /**
                 *
                 * first line
                 *
                 * second line
                 */
            }
            """.trimIndent(),
        )

        val segments = PsiCommentExtractor().extract(myFixture.editor, ExtractionScope.WHOLE_FILE)

        assertEquals(listOf("triple slash", "first line\nsecond line"), segments.map { it.content })
    }

    fun testExtractsKotlinKDocComments() {
        myFixture.configureByText(
            "Demo.kt",
            """
            /**
             * Kotlin doc
             * second line
             */
            class Demo
            """.trimIndent(),
        )

        val segments = PsiCommentExtractor().extract(myFixture.editor, ExtractionScope.WHOLE_FILE)

        assertEquals(1, segments.size)
        assertEquals("Kotlin doc\nsecond line", segments.single().content)
    }

    fun testReturnsEmptyForEmptyFile() {
        myFixture.configureByText("Empty.java", "")

        val segments = PsiCommentExtractor().extract(myFixture.editor, ExtractionScope.WHOLE_FILE)

        assertTrue(segments.isEmpty())
    }
}
