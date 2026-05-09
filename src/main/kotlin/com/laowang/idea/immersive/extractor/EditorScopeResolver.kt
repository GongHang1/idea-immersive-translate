package com.laowang.idea.immersive.extractor

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.laowang.idea.immersive.core.ExtractionScope

object EditorScopeResolver {
    fun resolve(editor: Editor, scope: ExtractionScope): TextRange {
        val document = editor.document
        return when (scope) {
            ExtractionScope.WHOLE_FILE -> TextRange(0, document.textLength)
            ExtractionScope.CURRENT_SELECTION -> {
                val selectionModel = editor.selectionModel
                if (selectionModel.hasSelection()) {
                    TextRange(selectionModel.selectionStart, selectionModel.selectionEnd)
                } else {
                    TextRange(0, 0)
                }
            }
            ExtractionScope.CURRENT_LINE -> currentLineRange(editor)
            ExtractionScope.VISIBLE_AREA -> editor.calculateVisibleRange()
        }
    }

    fun intersects(first: TextRange, second: TextRange): Boolean =
        first.startOffset < second.endOffset && second.startOffset < first.endOffset

    private fun currentLineRange(editor: Editor): TextRange {
        val document = editor.document
        val caretOffset = editor.caretModel.offset.coerceIn(0, document.textLength)
        if (document.textLength == 0) {
            return TextRange(0, 0)
        }
        val line = document.getLineNumber(caretOffset.coerceAtMost(document.textLength - 1))
        return TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line))
    }
}
