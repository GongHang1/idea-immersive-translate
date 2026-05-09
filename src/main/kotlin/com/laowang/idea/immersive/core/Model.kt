package com.laowang.idea.immersive.core

import com.intellij.openapi.util.TextRange
import java.security.MessageDigest

enum class SourceType {
    PSI_COMMENT,
    MARKDOWN_BLOCK,
    PLAIN_TEXT_BLOCK,
    CONSOLE_LINE,
    QUICK_DOC,
}

enum class ExtractionScope {
    CURRENT_LINE,
    CURRENT_SELECTION,
    VISIBLE_AREA,
    WHOLE_FILE,
}

data class TextSegment(
    val id: String,
    val source: SourceType,
    val content: String,
    val ranges: List<TextRange>,
    val filePath: String?,
    val engineId: String,
    val targetLang: String = "zh-CN",
) {
    companion object {
        fun computeId(
            content: String,
            filePath: String?,
            offset: Int,
            engineId: String,
            targetLang: String,
        ): String {
            val raw = "$content|${filePath.orEmpty()}|$offset|$engineId|$targetLang"
            val digest = MessageDigest.getInstance("SHA-1").digest(raw.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}

data class Translation(
    val segmentId: String,
    val translatedText: String,
    val engineId: String,
    val timestamp: Long,
)

sealed class TranslationError {
    data object NoApiKey : TranslationError()
    data object NetworkTimeout : TranslationError()
    data class RateLimited(val retryAfterMs: Long) : TranslationError()
    data class ApiError(val code: Int, val message: String) : TranslationError()
    data class Unknown(val cause: Throwable) : TranslationError()
}
