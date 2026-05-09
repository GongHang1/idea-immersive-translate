package com.laowang.idea.immersive.extractor

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.laowang.idea.immersive.core.ExtractionScope
import com.laowang.idea.immersive.core.SourceType
import com.laowang.idea.immersive.core.TextSegment
import org.jetbrains.kotlin.kdoc.psi.api.KDoc

class PsiCommentExtractor : TextExtractor {
    override val id: String = "psi-comment"

    override fun isApplicable(editor: Editor): Boolean {
        val project = editor.project ?: return false
        return PsiDocumentManager.getInstance(project).getPsiFile(editor.document) != null
    }

    override fun extract(editor: Editor, scope: ExtractionScope): List<TextSegment> {
        val project = editor.project ?: return emptyList()
        return ReadAction.compute<List<TextSegment>, RuntimeException> {
            val psiDocumentManager = PsiDocumentManager.getInstance(project)
            psiDocumentManager.commitDocument(editor.document)
            val psiFile = psiDocumentManager.getPsiFile(editor.document) ?: return@compute emptyList()
            val scopeRange = EditorScopeResolver.resolve(editor, scope)
            val comments = linkedSetOf<PsiElement>()
            comments += PsiTreeUtil.findChildrenOfType(psiFile, PsiComment::class.java)
            comments += PsiTreeUtil.findChildrenOfType(psiFile, KDoc::class.java)

            comments
                .asSequence()
                .mapNotNull { element ->
                    val range = element.textRange ?: return@mapNotNull null
                    if (!EditorScopeResolver.intersects(scopeRange, range)) {
                        return@mapNotNull null
                    }
                    val normalized = normalizeCommentText(element.text)
                    if (normalized.isBlank()) {
                        return@mapNotNull null
                    }
                    TextSegment(
                        id = TextSegment.computeId(
                            normalized,
                            psiFile.virtualFile?.path,
                            range.startOffset,
                            "openai",
                            "zh-CN",
                        ),
                        source = SourceType.PSI_COMMENT,
                        content = normalized,
                        ranges = listOf(range),
                        filePath = psiFile.virtualFile?.path,
                        engineId = "openai",
                        targetLang = "zh-CN",
                    )
                }
                .sortedBy { it.ranges.first().startOffset }
                .toList()
        }
    }

    private fun normalizeCommentText(rawText: String): String {
        val trimmed = rawText.trim()
        return when {
            trimmed.startsWith("///") -> trimmed.removePrefix("///").trim()
            trimmed.startsWith("//") -> trimmed.removePrefix("//").trim()
            trimmed.startsWith("/**") -> stripBlockComment(trimmed.removePrefix("/**").removeSuffix("*/"))
            trimmed.startsWith("/*") -> stripBlockComment(trimmed.removePrefix("/*").removeSuffix("*/"))
            else -> trimmed
        }
    }

    private fun stripBlockComment(body: String): String =
        body
            .lines()
            .map { it.trim().removePrefix("*").trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
}
