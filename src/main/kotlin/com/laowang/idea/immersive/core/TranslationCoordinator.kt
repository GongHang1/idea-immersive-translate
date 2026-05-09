package com.laowang.idea.immersive.core

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.laowang.idea.immersive.cache.TranslationCache
import com.laowang.idea.immersive.engine.TranslationEngine
import com.laowang.idea.immersive.engine.TranslationEngineResult
import com.laowang.idea.immersive.extractor.ExtractorRegistry
import com.laowang.idea.immersive.extractor.PsiCommentExtractor
import com.laowang.idea.immersive.extractor.TextExtractor
import com.laowang.idea.immersive.renderer.RendererRegistry
import com.laowang.idea.immersive.renderer.TranslationOverlayStore
import com.laowang.idea.immersive.renderer.TranslationRenderer
import com.laowang.idea.immersive.renderer.inlay.InlayRenderer
import com.laowang.idea.immersive.settings.SettingsService
import com.laowang.idea.immersive.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@Service(Service.Level.PROJECT)
class TranslationCoordinator(private val project: Project) {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var extractorProvider: (SourceType) -> TextExtractor = { sourceType ->
        ExtractorRegistry.getExtractors()
            .firstOrNull { it.id == sourceType.defaultExtractorId }
            ?: PsiCommentExtractor()
    }
    private var engineProvider: () -> TranslationEngine = {
        SettingsService.getInstance().activeEngine()
    }
    private var rendererProvider: () -> TranslationRenderer = {
        val rendererId = SettingsService.getInstance().rendererId()
        RendererRegistry.getRenderers().firstOrNull { it.id == rendererId } ?: InlayRenderer()
    }
    private var cache: TranslationCache = service()

    constructor(
        project: Project,
        extractorProvider: (SourceType) -> TextExtractor,
        engineProvider: () -> TranslationEngine,
        cache: TranslationCache,
        rendererProvider: () -> TranslationRenderer,
    ) : this(project) {
        this.extractorProvider = extractorProvider
        this.engineProvider = engineProvider
        this.cache = cache
        this.rendererProvider = rendererProvider
    }

    fun translate(editor: Editor, sourceType: SourceType, scope: ExtractionScope) {
        coroutineScope.launch {
            translateInternal(editor, sourceType, scope)
        }
    }

    fun translateBlocking(editor: Editor, sourceType: SourceType, scope: ExtractionScope) {
        runBlocking {
            translateInternal(editor, sourceType, scope)
        }
    }

    fun clear(editor: Editor) {
        rendererProvider().clearAll(editor)
    }

    fun hasVisibleTranslations(editor: Editor): Boolean {
        val renderer = rendererProvider()
        if (renderer is InlayRenderer) {
            return renderer.hasAny(editor)
        }
        return TranslationOverlayStore.getInstance(project).hasAny(editor)
    }

    private suspend fun translateInternal(editor: Editor, sourceType: SourceType, scope: ExtractionScope) {
        val extractor = extractorProvider(sourceType)
        if (!extractor.isApplicable(editor)) {
            Log.info("No applicable extractor found for $sourceType")
            return
        }
        val segments = extractor.extract(editor, scope)
        if (segments.isEmpty()) {
            Log.info("No translatable segments found")
            return
        }

        val renderer = rendererProvider()
        val missingSegments = mutableListOf<TextSegment>()
        segments.forEach { segment ->
            val cached = cache.get(segment.id)
            if (cached == null) {
                missingSegments += segment
            } else {
                renderer.render(editor, segment, cached)
            }
        }
        if (missingSegments.isEmpty()) {
            return
        }

        val result = withContext(Dispatchers.IO) {
            engineProvider().translate(missingSegments)
        }
        when (result) {
            is TranslationEngineResult.Success -> renderFreshTranslations(
                editor = editor,
                renderer = renderer,
                segments = missingSegments,
                translations = result.translations,
            )
            is TranslationEngineResult.Failure -> notifyError(result.error)
        }
    }

    private fun renderFreshTranslations(
        editor: Editor,
        renderer: TranslationRenderer,
        segments: List<TextSegment>,
        translations: List<Translation>,
    ) {
        segments.zip(translations).forEach { (segment, translation) ->
            cache.put(translation)
            renderer.render(editor, segment, translation)
        }
    }

    private fun notifyError(error: TranslationError) {
        ApplicationManager.getApplication().invokeLater {
            ErrorHandler.notify(project, error)
        }
    }

    private val SourceType.defaultExtractorId: String
        get() = when (this) {
            SourceType.PSI_COMMENT -> "psi-comment"
            SourceType.MARKDOWN_BLOCK -> "markdown-block"
            SourceType.PLAIN_TEXT_BLOCK -> "plain-text-block"
            SourceType.CONSOLE_LINE -> "console-line"
            SourceType.QUICK_DOC -> "quick-doc"
        }

}
