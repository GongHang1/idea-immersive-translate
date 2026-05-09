package com.laowang.idea.immersive.engine

import com.intellij.openapi.util.TextRange
import com.laowang.idea.immersive.core.SourceType
import com.laowang.idea.immersive.core.TextSegment
import com.laowang.idea.immersive.core.TranslationCancellationToken
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class GoogleTranslateEngineTest {
    private val server = MockWebServer()

    @AfterEach
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    @Test
    fun `translate uses free google translate endpoint without credentials`() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val translated = when (request.requestUrl?.queryParameter("q")) {
                    "first" -> "第一段译文"
                    "second" -> "第二段译文"
                    else -> error("Unexpected request: ${request.path}")
                }
                return MockResponse()
                    .setResponseCode(200)
                    .setBody("""[[["$translated","",null,null,10]],null,"en"]""")
            }
        }
        server.start()
        val engine = GoogleTranslateEngine(
            baseUrlProvider = { server.url("/").toString().removeSuffix("/") },
            targetLangProvider = { "zh-CN" },
        )

        val result = engine.translate(listOf(segment("seg-1", "first"), segment("seg-2", "second")))

        val success = result as TranslationEngineResult.Success
        assertEquals(listOf("seg-1", "seg-2"), success.translations.map { it.segmentId })
        assertEquals(listOf("第一段译文", "第二段译文"), success.translations.map { it.translatedText })

        val requests = listOf(server.takeRequest(), server.takeRequest())
        val firstRequest = requests.first { it.requestUrl?.queryParameter("q") == "first" }
        val secondRequest = requests.first { it.requestUrl?.queryParameter("q") == "second" }
        assertEquals("GET", firstRequest.method)
        assertEquals("GET", secondRequest.method)
        assertEquals("/translate_a/single", firstRequest.requestUrl?.encodedPath)
        assertEquals("gtx", firstRequest.requestUrl?.queryParameter("client"))
        assertEquals("auto", firstRequest.requestUrl?.queryParameter("sl"))
        assertEquals("zh-CN", firstRequest.requestUrl?.queryParameter("tl"))
        assertEquals("t", firstRequest.requestUrl?.queryParameter("dt"))
        assertEquals("first", firstRequest.requestUrl?.queryParameter("q"))
        assertEquals("second", secondRequest.requestUrl?.queryParameter("q"))
        assertEquals(null, firstRequest.getHeader("Authorization"))
    }

    @Test
    fun `translate sends google segment requests concurrently`() {
        val firstTwoRequestsArrived = CountDownLatch(2)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                firstTwoRequestsArrived.countDown()
                val concurrent = firstTwoRequestsArrived.await(500, TimeUnit.MILLISECONDS)
                if (!concurrent) {
                    return MockResponse()
                        .setResponseCode(503)
                        .setBody("""{"error":{"message":"requests were serialized"}}""")
                }
                val source = request.requestUrl?.queryParameter("q").orEmpty()
                return MockResponse()
                    .setResponseCode(200)
                    .setBody("""[[["translated $source","$source",null,null,10]],null,"en"]""")
            }
        }
        server.start()
        val engine = GoogleTranslateEngine(
            baseUrlProvider = { server.url("/").toString().removeSuffix("/") },
            targetLangProvider = { "zh-CN" },
        )

        val result = engine.translate(listOf(segment("seg-1", "first"), segment("seg-2", "second")))

        val success = result as TranslationEngineResult.Success
        assertEquals(listOf("translated first", "translated second"), success.translations.map { it.translatedText })
    }

    @Test
    fun `translate returns cancelled when cancellation token cancels active google request`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""[[["第一段译文","first",null,null,10]],null,"en"]""")
                .setBodyDelay(5, TimeUnit.SECONDS),
        )
        server.start()
        val engine = GoogleTranslateEngine(
            baseUrlProvider = { server.url("/").toString().removeSuffix("/") },
            targetLangProvider = { "zh-CN" },
        )
        val cancellationToken = TranslationCancellationToken()
        val executor = Executors.newSingleThreadExecutor()

        try {
            val result = executor.submit<TranslationEngineResult> {
                engine.translate(listOf(segment("seg-1", "first")), cancellationToken)
            }

            assertTrue(server.takeRequest(1, TimeUnit.SECONDS) != null)
            cancellationToken.cancel()

            assertEquals(TranslationEngineResult.Cancelled, result.get(2, TimeUnit.SECONDS))
        } finally {
            executor.shutdownNow()
        }
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
