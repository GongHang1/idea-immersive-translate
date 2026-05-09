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

class GoogleCloudTranslateEngineTest {
    private val server = MockWebServer()

    @AfterEach
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    @Test
    fun `translate sends google v3 request and parses response`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "translations": [
                        {"translatedText": "第一段译文"},
                        {"translatedText": "第二段译文"}
                      ]
                    }
                    """.trimIndent(),
                ),
        )
        server.start()
        val engine = GoogleCloudTranslateEngine(
            credentialProvider = { "ya29.token" },
            baseUrlProvider = { server.url("/").toString().removeSuffix("/") },
            projectIdProvider = { "demo-project" },
            locationProvider = { "us-central1" },
            targetLangProvider = { "zh-CN" },
        )

        val result = engine.translate(listOf(segment("seg-1", "first"), segment("seg-2", "second")))

        val success = result as TranslationEngineResult.Success
        assertEquals(listOf("seg-1", "seg-2"), success.translations.map { it.segmentId })
        assertEquals(listOf("第一段译文", "第二段译文"), success.translations.map { it.translatedText })

        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertEquals("POST", request.method)
        assertEquals("/v3/projects/demo-project/locations/us-central1:translateText", request.path)
        assertEquals("Bearer ya29.token", request.getHeader("Authorization"))
        assertTrue(body.contains(""""contents":["first","second"]"""))
        assertTrue(body.contains(""""targetLanguageCode":"zh-CN""""))
        assertTrue(body.contains(""""mimeType":"text/plain""""))
    }

    private fun segment(id: String, content: String) =
        TextSegment(
            id = id,
            source = SourceType.PSI_COMMENT,
            content = content,
            ranges = listOf(TextRange(0, content.length)),
            filePath = "/tmp/Test.kt",
            engineId = "google",
            targetLang = "zh-CN",
        )
}
