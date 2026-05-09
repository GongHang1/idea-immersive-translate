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

class MicrosoftTranslatorEngineTest {
    private val server = MockWebServer()

    @AfterEach
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    @Test
    fun `translate uses edge token and sends free microsoft request`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("edge-token"),
        )
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
            baseUrlProvider = { server.url("/").toString().removeSuffix("/") },
            authUrlProvider = { server.url("/auth").toString() },
            targetLangProvider = { "zh-CN" },
        )

        val result = engine.translate(listOf(segment("seg-1", "first"), segment("seg-2", "second")))

        val success = result as TranslationEngineResult.Success
        assertEquals(listOf("seg-1", "seg-2"), success.translations.map { it.segmentId })
        assertEquals(listOf("第一段译文", "第二段译文"), success.translations.map { it.translatedText })

        val authRequest = server.takeRequest()
        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertEquals("GET", authRequest.method)
        assertEquals("/auth", authRequest.path)
        assertEquals("POST", request.method)
        assertEquals("/translate?api-version=3.0&to=zh-Hans", request.path)
        assertEquals("Bearer edge-token", request.getHeader("Authorization"))
        assertEquals(null, request.getHeader("Ocp-Apim-Subscription-Key"))
        assertEquals(null, request.getHeader("Ocp-Apim-Subscription-Region"))
        assertTrue(body.contains(""""Text":"first""""))
        assertTrue(body.contains(""""Text":"second""""))
    }

    @Test
    fun `translate reuses edge token for subsequent requests`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("edge-token"),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""[{"translations": [{"text": "Bonjour"}]}]"""),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""[{"translations": [{"text": "Salut"}]}]"""),
        )
        server.start()
        val engine = MicrosoftTranslatorEngine(
            baseUrlProvider = { server.url("/").toString().removeSuffix("/") },
            authUrlProvider = { server.url("/auth").toString() },
            targetLangProvider = { "fr" },
        )

        engine.translate(listOf(segment("seg-1", "hello")))
        engine.translate(listOf(segment("seg-2", "hi")))

        val requests = listOf(server.takeRequest(), server.takeRequest(), server.takeRequest())
        assertEquals(listOf("/auth", "/translate?api-version=3.0&to=fr", "/translate?api-version=3.0&to=fr"), requests.map { it.path })
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
