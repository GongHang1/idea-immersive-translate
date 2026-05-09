package com.laowang.idea.immersive.renderer

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.laowang.idea.immersive.core.TextSegment
import com.laowang.idea.immersive.core.Translation

interface TranslationRenderer {
    val id: String

    fun isApplicable(editor: Editor): Boolean

    fun render(editor: Editor, segment: TextSegment, translation: Translation)

    fun clear(editor: Editor, segmentId: String)

    fun clearAll(editor: Editor)
}

object RendererRegistry {
    val EP_NAME: ExtensionPointName<TranslationRenderer> =
        ExtensionPointName.create("com.laowang.idea.immersive.translationRenderer")

    fun getRenderers(): List<TranslationRenderer> {
        val area = ApplicationManager.getApplication()?.extensionArea ?: return emptyList()
        if (!area.hasExtensionPoint(EP_NAME)) {
            return emptyList()
        }
        return EP_NAME.extensionList
    }
}
