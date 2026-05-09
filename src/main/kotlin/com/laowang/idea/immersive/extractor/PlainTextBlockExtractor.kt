package com.laowang.idea.immersive.extractor

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.laowang.idea.immersive.core.ExtractionScope
import com.laowang.idea.immersive.core.SourceType
import com.laowang.idea.immersive.core.TextSegment

class PlainTextBlockExtractor : TextExtractor {
    override val id: String = "plain-text-block"

    override fun isApplicable(editor: Editor): Boolean {
        val virtualFile = FileDocumentManager.getInstance().getFile(editor.document) ?: return false
        val extension = virtualFile.extension?.lowercase()
        val fileTypeName = virtualFile.fileType.name.lowercase()
        return extension == "txt" || extension == "text" ||
            "plain_text" in fileTypeName || "plain text" in fileTypeName || fileTypeName == "text"
    }

    override fun extract(editor: Editor, scope: ExtractionScope): List<TextSegment> {
        val scopeRange = EditorScopeResolver.resolve(editor, scope)
        val document = editor.document
        val filePath = FileDocumentManager.getInstance().getFile(document)?.path
        val segments = mutableListOf<TextSegment>()
        val paragraphLines = mutableListOf<String>()
        var paragraphStart = -1
        var paragraphEnd = -1

        fun flushParagraph() {
            if (paragraphLines.isEmpty()) {
                return
            }
            val content = paragraphLines.joinToString("\n")
            if (content.any { it.isLetterOrDigit() }) {
                val range = TextRange(paragraphStart, paragraphEnd)
                segments += TextSegment(
                    id = TextSegment.computeId(content, filePath, range.startOffset, "openai", "zh-CN"),
                    source = SourceType.PLAIN_TEXT_BLOCK,
                    content = content,
                    ranges = listOf(range),
                    filePath = filePath,
                    engineId = "openai",
                    targetLang = "zh-CN",
                )
            }
            paragraphLines.clear()
            paragraphStart = -1
            paragraphEnd = -1
        }

        for (lineIndex in 0 until document.lineCount) {
            val line = line(document, lineIndex)
            if (!EditorScopeResolver.intersects(scopeRange, line.range)) {
                flushParagraph()
                continue
            }
            val trimmed = line.text.trim()
            if (trimmed.isBlank()) {
                flushParagraph()
                continue
            }
            if (paragraphStart < 0) {
                paragraphStart = line.range.startOffset
            }
            paragraphLines += trimmed
            paragraphEnd = line.range.endOffset
        }
        flushParagraph()

        return segments
    }

    private fun line(document: Document, lineIndex: Int): Line {
        val start = document.getLineStartOffset(lineIndex)
        val end = document.getLineEndOffset(lineIndex)
        return Line(TextRange(start, end), document.getText(TextRange(start, end)))
    }

    private data class Line(val range: TextRange, val text: String)
}
