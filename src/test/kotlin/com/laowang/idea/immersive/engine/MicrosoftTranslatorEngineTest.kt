package com.laowang.idea.immersive.engine

import com.intellij.openapi.util.TextRange
import com.laowang.idea.immersive.core.SourceType
import com.laowang.idea.immersive.core.TextSegment
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MicrosoftTranslatorEngineTest {
    private val server = MockWebServer()

    @AfterEach
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    @Test
    fun `translate sends microsoft request and parses response`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    [
                      {"translations": [{"text": "第一段译文"}]},
                      {"translations": [{"text": "第二段译文"}]}
                    ]
                    """.trimIndent(),
                ),
        )
        server.start()
        val engine = MicrosoftTranslatorEngine(
            apiKeyProvider = { "microsoft-key" },
            baseUrlProvider = { server.url("/").toString().removeSuffix("/") },
            targetLangProvider = { "zh-CN" },
            regionProvider = { "eastus" },
        )

        val result = engine.translate(listOf(segment("seg-1", "first"), segment("seg-2", "second")))

        val success = result as TranslationEngineResult.Success
        assertEquals(listOf("seg-1", "seg-2"), success.translations.map { it.segmentId })
        assertEquals(listOf("第一段译文", "第二段译文"), success.translations.map { it.translatedText })

        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertEquals("POST", request.method)
        assertEquals("/translate?api-version=3.0&to=zh-Hans", request.path)
        assertEquals("microsoft-key", request.getHeader("Ocp-Apim-Subscription-Key"))
        assertEquals("eastus", request.getHeader("Ocp-Apim-Subscription-Region"))
        assertTrue(body.contains(""""Text":"first""""))
        assertTrue(body.contains(""""Text":"second""""))
    }

    @Test
    fun `translate omits region header when region is blank`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""[{"translations": [{"text": "Bonjour"}]}]"""),
        )
        server.start()
        val engine = MicrosoftTranslatorEngine(
            apiKeyProvider = { "microsoft-key" },
            baseUrlProvider = { server.url("/").toString().removeSuffix("/") },
            targetLangProvider = { "fr" },
            regionProvider = { " " },
        )

        engine.translate(listOf(segment("seg-1", "hello")))

        val request = server.takeRequest()
        assertEquals("/translate?api-version=3.0&to=fr", request.path)
        assertNull(request.getHeader("Ocp-Apim-Subscription-Region"))
    }

    private fun segment(id: String, content: String) =
        TextSegment(
            id = id,
            source = SourceType.PSI_COMMENT,
            content = content,
            ranges = listOf(TextRange(0, content.length)),
            filePath = "/tmp/Test.kt",
            engineId = "microsoft",
            targetLang = "zh-CN",
        )
}
