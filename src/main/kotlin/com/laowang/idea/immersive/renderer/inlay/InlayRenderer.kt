package com.laowang.idea.immersive.renderer.inlay

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.laowang.idea.immersive.core.TextSegment
import com.laowang.idea.immersive.core.Translation
import com.laowang.idea.immersive.renderer.TranslationOverlayStore
import com.laowang.idea.immersive.renderer.TranslationRenderer

class InlayRenderer : TranslationRenderer {
    override val id: String = "inlay"

    private val fallbackStore = TranslationOverlayStore()

    override fun isApplicable(editor: Editor): Boolean = !editor.isDisposed

    override fun render(editor: Editor, segment: TextSegment, translation: Translation) {
        runOnEdt {
            clear(editor, segment.id)
            val anchorOffset = segment.ranges.firstOrNull()?.endOffset ?: return@runOnEdt
            val inlay = editor.inlayModel.addBlockElement(
                anchorOffset,
                true,
                false,
                0,
                TranslationInlayRenderer(translation.translatedText),
            ) ?: return@runOnEdt
            store(editor).put(editor, segment.id, inlay)
        }
    }

    override fun clear(editor: Editor, segmentId: String) {
        runOnEdt {
            store(editor).clear(editor, segmentId)
        }
    }

    override fun clearAll(editor: Editor) {
        runOnEdt {
            store(editor).clearAll(editor)
        }
    }

    fun hasAny(editor: Editor): Boolean = store(editor).hasAny(editor)

    private fun store(editor: Editor): TranslationOverlayStore {
        val project = editor.project ?: return fallbackStore
        return TranslationOverlayStore.getInstance(project)
    }

    private fun runOnEdt(action: () -> Unit) {
        val application = ApplicationManager.getApplication()
        if (application == null || application.isDispatchThread) {
            action()
        } else {
            application.invokeAndWait(action)
        }
    }
}
