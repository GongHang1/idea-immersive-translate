package com.laowang.idea.immersive.renderer

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class TranslationOverlayStore {
    private val inlaysByEditor: MutableMap<Editor, MutableMap<String, Inlay<*>>> = ConcurrentHashMap()

    fun put(editor: Editor, segmentId: String, inlay: Inlay<*>) {
        inlaysByEditor.getOrPut(editor) { ConcurrentHashMap() }[segmentId] = inlay
    }

    fun clear(editor: Editor, segmentId: String) {
        val bySegment = inlaysByEditor[editor] ?: return
        bySegment.remove(segmentId)?.dispose()
        if (bySegment.isEmpty()) {
            inlaysByEditor.remove(editor)
        }
    }

    fun clearAll(editor: Editor) {
        inlaysByEditor.remove(editor)?.values?.forEach { it.dispose() }
    }

    fun hasAny(editor: Editor): Boolean = inlaysByEditor[editor]?.isNotEmpty() == true

    companion object {
        fun getInstance(project: Project): TranslationOverlayStore = project.service()
    }
}
