package com.laowang.idea.immersive.core

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDocumentManager

object SourceTypeResolver {
    fun resolve(editor: Editor): SourceType {
        val virtualFile = FileDocumentManager.getInstance().getFile(editor.document)
        when (virtualFile?.extension?.lowercase()) {
            "md", "markdown" -> return SourceType.MARKDOWN_BLOCK
            "txt", "text" -> return SourceType.PLAIN_TEXT_BLOCK
        }

        val fileTypeName = virtualFile?.fileType?.name?.lowercase().orEmpty()
        when {
            "markdown" in fileTypeName -> return SourceType.MARKDOWN_BLOCK
            "plain_text" in fileTypeName || "plain text" in fileTypeName || fileTypeName == "text" ->
                return SourceType.PLAIN_TEXT_BLOCK
        }

        val project = editor.project ?: return SourceType.PLAIN_TEXT_BLOCK
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
        return if (psiFile == null) SourceType.PLAIN_TEXT_BLOCK else SourceType.PSI_COMMENT
    }
}
