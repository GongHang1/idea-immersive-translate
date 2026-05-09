package com.laowang.idea.immersive.engine

import com.intellij.openapi.util.TextRange
import com.laowang.idea.immersive.core.SourceType
import com.laowang.idea.immersive.core.TextSegment
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeminiEngineTest {
    private val server = MockWebServer()

    @AfterEach
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    @Test
    fun `translate posts generateContent request and preserves order`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "candidates": [
                        {
                          "content": {
                            "parts": [
                              {"text": "第一段译文${OpenAIEngine.RESPONSE_DELIMITER}第二段译文"}
                            ]
                          }
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
        )
        server.start()
        val engine = GeminiEngine(
            apiKeyProvider = { "gemini-key" },
            baseUrlProvider = { server.url("/").toString().removeSuffix("/") },
            modelProvider = { "gemini-1.5-flash" },
        )

        val result = engine.translate(listOf(segment("seg-1", "first"), segment("seg-2", "second")))

        val success = result as TranslationEngineResult.Success
        assertEquals(listOf("seg-1", "seg-2"), success.translations.map { it.segmentId })
        assertEquals(listOf("第一段译文", "第二段译文"), success.translations.map { it.translatedText })
        assertEquals(listOf("gemini", "gemini"), success.translations.map { it.engineId })

        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertEquals("POST", request.method)
        assertEquals("/v1beta/models/gemini-1.5-flash:generateContent?key=gemini-key", request.path)
        assertTrue(body.contains("systemInstruction"))
        assertTrue(body.contains("Return exactly one translated item for each input segment"))
        assertTrue(body.contains(OpenAIEngine.REQUEST_DELIMITER_TOKEN))
        assertTrue(body.contains(""""temperature":0.0"""))
    }

    @Test
    fun `translate returns failure when response count mismatches`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "candidates": [
                        {"content": {"parts": [{"text": "only one"}]}}
                      ]
                    }
                    """.trimIndent(),
                ),
        )
        server.start()
        val engine = GeminiEngine(
            apiKeyProvider = { "gemini-key" },
            baseUrlProvider = { server.url("/").toString().removeSuffix("/") },
            modelProvider = { "gemini-1.5-flash" },
        )

        val result = engine.translate(listOf(segment("seg-1", "first"), segment("seg-2", "second")))

        assertTrue(result is TranslationEngineResult.Failure)
    }

    private fun segment(id: String, content: String) =
        TextSegment(
            id = id,
            source = SourceType.PSI_COMMENT,
            content = content,
            ranges = listOf(TextRange(0, content.length)),
            filePath = "/tmp/Test.kt",
            engineId = "gemini",
            targetLang = "zh-CN",
        )
}
