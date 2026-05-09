package com.laowang.idea.immersive.engine

import com.intellij.openapi.extensions.ExtensionPointName
import com.laowang.idea.immersive.core.TextSegment
import com.laowang.idea.immersive.core.Translation
import com.laowang.idea.immersive.core.TranslationError

interface TranslationEngine {
    val id: String
    val displayName: String

    fun translate(segments: List<TextSegment>): TranslationEngineResult
}

sealed interface TranslationEngineResult {
    data class Success(val translations: List<Translation>) : TranslationEngineResult

    data class Failure(val error: TranslationError) : TranslationEngineResult
}

object EngineRegistry {
    val EP_NAME: ExtensionPointName<TranslationEngine> =
        ExtensionPointName.create("com.laowang.idea.immersive.translationEngine")

    fun all(): List<TranslationEngine> {
        val registered = runCatching { EP_NAME.extensionList }.getOrDefault(emptyList())
        return if (registered.isNotEmpty()) {
            registered
        } else {
            listOf(OpenAIEngine())
        }
    }

    fun findById(id: String): TranslationEngine? = all().firstOrNull { it.id == id }
}
