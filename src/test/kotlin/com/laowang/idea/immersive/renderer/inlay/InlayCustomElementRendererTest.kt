package com.laowang.idea.immersive.renderer.inlay

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class InlayCustomElementRendererTest : BasePlatformTestCase() {
    fun testCalcMethodsReturnPositiveValuesForMultiLineTranslation() {
        myFixture.configureByText("sample.java", "class Demo { int value = 1; }")
        val editor = myFixture.editor
        val renderer = TranslationInlayRenderer("第一行\n第二行")
        val inlay = editor.inlayModel.addBlockElement(
            editor.document.textLength,
            true,
            false,
            0,
            renderer,
        )

        assertNotNull(inlay)
        assertTrue(renderer.calcWidthInPixels(inlay!!) > 0)
        assertTrue(renderer.calcHeightInPixels(inlay) > 0)
        assertTrue(inlay.heightInPixels > 0)
        assertTrue(inlay.widthInPixels > 0)
        assertEquals(renderer, inlay.renderer)
    }
}
