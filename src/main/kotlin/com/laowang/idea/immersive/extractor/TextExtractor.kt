package com.laowang.idea.immersive.extractor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.laowang.idea.immersive.core.ExtractionScope
import com.laowang.idea.immersive.core.TextSegment

interface TextExtractor {
    val id: String

    fun isApplicable(editor: Editor): Boolean

    fun extract(editor: Editor, scope: ExtractionScope): List<TextSegment>
}

object ExtractorRegistry {
    val EP_NAME: ExtensionPointName<TextExtractor> =
        ExtensionPointName.create("com.laowang.idea.immersive.textExtractor")

    fun getExtractors(): List<TextExtractor> {
        val area = ApplicationManager.getApplication()?.extensionArea ?: return emptyList()
        if (!area.hasExtensionPoint(EP_NAME)) {
            return emptyList()
        }
        return EP_NAME.extensionList
    }
}
