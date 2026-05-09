package com.laowang.idea.immersive.extractor

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.laowang.idea.immersive.core.ExtractionScope
import com.laowang.idea.immersive.core.SourceType
import com.laowang.idea.immersive.core.TextSegment

class MarkdownBlockExtractor : TextExtractor {
    override val id: String = "markdown-block"

    override fun isApplicable(editor: Editor): Boolean {
        val virtualFile = FileDocumentManager.getInstance().getFile(editor.document) ?: return false
        val extension = virtualFile.extension?.lowercase()
        return extension == "md" || extension == "markdown" ||
            virtualFile.fileType.name.lowercase().contains("markdown")
    }

    override fun extract(editor: Editor, scope: ExtractionScope): List<TextSegment> {
        val scopeRange = EditorScopeResolver.resolve(editor, scope)
        val document = editor.document
        val filePath = FileDocumentManager.getInstance().getFile(document)?.path
        val segments = mutableListOf<TextSegment>()
        var paragraphStart = -1
        val paragraphLines = mutableListOf<String>()
        var paragraphEnd = -1
        var inFrontMatter = false
        var frontMatterClosed = false
        var inFence = false

        fun flushParagraph() {
            if (paragraphLines.isEmpty()) {
                return
            }
            addSegment(
                segments = segments,
                content = paragraphLines.joinToString("\n"),
                range = TextRange(paragraphStart, paragraphEnd),
                sourceType = SourceType.MARKDOWN_BLOCK,
                filePath = filePath,
            )
            paragraphLines.clear()
            paragraphStart = -1
            paragraphEnd = -1
        }

        for (lineIndex in 0 until document.lineCount) {
            val line = line(document, lineIndex)
            val trimmed = line.text.trim()
            if (lineIndex == 0 && trimmed == "---") {
                flushParagraph()
                inFrontMatter = true
                continue
            }
            if (inFrontMatter) {
                if (trimmed == "---") {
                    inFrontMatter = false
                    frontMatterClosed = true
                }
                continue
            }
            if (!frontMatterClosed && lineIndex == 0) {
                frontMatterClosed = true
            }
            if (isFence(trimmed)) {
                flushParagraph()
                inFence = !inFence
                continue
            }
            if (inFence) {
                continue
            }
            if (!EditorScopeResolver.intersects(scopeRange, line.range)) {
                flushParagraph()
                continue
            }
            if (trimmed.isBlank()) {
                flushParagraph()
                continue
            }
            if (isStandaloneLineBlock(trimmed)) {
                flushParagraph()
                addSegment(
                    segments = segments,
                    content = trimmed,
                    range = line.range,
                    sourceType = SourceType.MARKDOWN_BLOCK,
                    filePath = filePath,
                )
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

    private fun addSegment(
        segments: MutableList<TextSegment>,
        content: String,
        range: TextRange,
        sourceType: SourceType,
        filePath: String?,
    ) {
        if (content.isBlank()) {
            return
        }
        segments += TextSegment(
            id = TextSegment.computeId(content, filePath, range.startOffset, "openai", "zh-CN"),
            source = sourceType,
            content = content,
            ranges = listOf(range),
            filePath = filePath,
            engineId = "openai",
            targetLang = "zh-CN",
        )
    }

    private fun line(document: Document, lineIndex: Int): Line {
        val start = document.getLineStartOffset(lineIndex)
        val end = document.getLineEndOffset(lineIndex)
        return Line(TextRange(start, end), document.getText(TextRange(start, end)))
    }

    private fun isFence(trimmed: String): Boolean =
        trimmed.startsWith("```") || trimmed.startsWith("~~~")

    private fun isStandaloneLineBlock(trimmed: String): Boolean =
        trimmed.startsWith("#") || trimmed.matches(Regex("""([-*+]|\d+\.)\s+.+"""))

    private data class Line(val range: TextRange, val text: String)
}
