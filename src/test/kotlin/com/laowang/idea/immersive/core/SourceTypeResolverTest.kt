package com.laowang.idea.immersive.core

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SourceTypeResolverTest : BasePlatformTestCase() {
    fun testReadmeMarkdownResolvesToMarkdownBlock() {
        myFixture.configureByText("README.md", "# Title")

        assertEquals(SourceType.MARKDOWN_BLOCK, SourceTypeResolver.resolve(myFixture.editor))
    }

    fun testTextFileResolvesToPlainTextBlock() {
        myFixture.configureByText("notes.txt", "first paragraph")

        assertEquals(SourceType.PLAIN_TEXT_BLOCK, SourceTypeResolver.resolve(myFixture.editor))
    }

    fun testJavaFileResolvesToPsiComment() {
        myFixture.configureByText("A.java", "// hello\nclass A {}")

        assertEquals(SourceType.PSI_COMMENT, SourceTypeResolver.resolve(myFixture.editor))
    }
}
