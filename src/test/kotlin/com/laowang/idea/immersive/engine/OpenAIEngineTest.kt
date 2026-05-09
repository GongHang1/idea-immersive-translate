package com.laowang.idea.immersive.engine

import com.intellij.openapi.util.TextRange
import com.laowang.idea.immersive.core.SourceType
import com.laowang.idea.immersive.core.TextSegment
import com.laowang.idea.immersive.core.TranslationError
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenAIEngineTest {
    private val server = MockWebServer()

    @AfterEach
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    @Test
    fun `translate preserves segment order on success`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "choices": [
                        {
                          "message": {
                            "content": "第一段译文${OpenAIEngine.RESPONSE_DELIMITER}第二段译文"
                          }
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
        )
        server.start()

        val engine = OpenAIEngine(
            apiKeyProvider = { "test-key" },
            baseUrlProvider = { server.url("/v1").toString().removeSuffix("/") },
            modelProvider = { "gpt-4o-mini" },
        )

        val result = engine.translate(listOf(segment("seg-1", "first"), segment("seg-2", "second")))

        val success = result as TranslationEngineResult.Success
        assertEquals(listOf("seg-1", "seg-2"), success.translations.map { it.segmentId })
        assertEquals(listOf("第一段译文", "第二段译文"), success.translations.map { it.translatedText })
        assertEquals(listOf("openai", "openai"), success.translations.map { it.engineId })

        val request = server.takeRequest()
        val requestBody = request.body.readUtf8()
        assertEquals("POST", request.method)
        assertEquals("/v1/chat/completions", request.path)
        assertEquals("Bearer test-key", request.getHeader("Authorization"))
        assertTrue(requestBody.contains("gpt-4o-mini"))
        assertTrue(requestBody.contains(OpenAIEngine.REQUEST_DELIMITER_TOKEN))
    }

    @Test
    fun `translate returns api error when upstream rejects credentials`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":{"message":"invalid api key"}}"""),
        )
        server.start()

        val engine = OpenAIEngine(
            apiKeyProvider = { "bad-key" },
            baseUrlProvider = { server.url("/v1").toString().removeSuffix("/") },
            modelProvider = { "gpt-4o-mini" },
        )

        val result = engine.translate(listOf(segment("seg-1", "hello")))

        val failure = result as TranslationEngineResult.Failure
        assertEquals(TranslationError.ApiError(401, "invalid api key"), failure.error)
    }

    @Test
    fun `translate returns no api key when key is missing`() {
        val engine = OpenAIEngine(
            apiKeyProvider = { "   " },
            baseUrlProvider = { "https://api.openai.com/v1" },
            modelProvider = { "gpt-4o-mini" },
        )

        val result = engine.translate(listOf(segment("seg-1", "hello")))

        val failure = result as TranslationEngineResult.Failure
        assertEquals(TranslationError.NoApiKey, failure.error)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `translate returns retry after on rate limit`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .addHeader("Retry-After", "7")
                .setBody("""{"error":{"message":"rate limited"}}"""),
        )
        server.start()

        val engine = OpenAIEngine(
            apiKeyProvider = { "test-key" },
            baseUrlProvider = { server.url("/v1").toString().removeSuffix("/") },
            modelProvider = { "gpt-4o-mini" },
        )

        val result = engine.translate(listOf(segment("seg-1", "hello")))

        val failure = result as TranslationEngineResult.Failure
        assertEquals(TranslationError.RateLimited(7000), failure.error)
    }

    private fun segment(id: String, content: String) =
        TextSegment(
            id = id,
            source = SourceType.PSI_COMMENT,
            content = content,
            ranges = listOf(TextRange(0, content.length)),
            filePath = "/tmp/Test.kt",
            engineId = "openai",
            targetLang = "zh-CN",
        )
}
