package com.laowang.idea.immersive.core

import com.intellij.openapi.util.TextRange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModelTest {
    @Test
    fun `TextSegment id is stable for same content path offset engine lang`() {
        val first = TextSegment(
            id = TextSegment.computeId("hello", "/a/b.kt", 100, "openai", "zh-CN"),
            source = SourceType.PSI_COMMENT,
            content = "hello",
            ranges = listOf(TextRange(100, 105)),
            filePath = "/a/b.kt",
            engineId = "openai",
            targetLang = "zh-CN",
        )
        val second = TextSegment(
            id = TextSegment.computeId("hello", "/a/b.kt", 100, "openai", "zh-CN"),
            source = SourceType.PSI_COMMENT,
            content = "hello",
            ranges = listOf(TextRange(100, 105)),
            filePath = "/a/b.kt",
            engineId = "openai",
            targetLang = "zh-CN",
        )

        assertEquals(first.id, second.id)
    }

    @Test
    fun `TextSegment id differs when engine changes`() {
        val openAiId = TextSegment.computeId("hello", "/a.kt", 0, "openai", "zh-CN")
        val deepLId = TextSegment.computeId("hello", "/a.kt", 0, "deepl", "zh-CN")

        assertNotEquals(openAiId, deepLId)
    }

    @Test
    fun `TranslationError sealed hierarchy is exhaustive`() {
        val errors: List<TranslationError> = listOf(
            TranslationError.NoApiKey,
            TranslationError.NetworkTimeout,
            TranslationError.RateLimited(retryAfterMs = 1000),
            TranslationError.ApiError(401, "unauthorized"),
            TranslationError.Unknown(RuntimeException("x")),
        )

        errors.forEach { error ->
            val message = when (error) {
                TranslationError.NoApiKey -> "no key"
                TranslationError.NetworkTimeout -> "timeout"
                is TranslationError.RateLimited -> "limited ${error.retryAfterMs}"
                is TranslationError.ApiError -> "api ${error.code}"
                is TranslationError.Unknown -> "unknown ${error.cause.message}"
            }
            assertTrue(message.isNotEmpty())
        }
    }
}
