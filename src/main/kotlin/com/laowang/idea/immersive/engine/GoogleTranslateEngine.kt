package com.laowang.idea.immersive.engine

import com.laowang.idea.immersive.core.TextSegment
import com.laowang.idea.immersive.core.Translation
import com.laowang.idea.immersive.core.TranslationCancellationToken
import com.laowang.idea.immersive.core.TranslationCancelledException
import com.laowang.idea.immersive.core.TranslationError
import com.laowang.idea.immersive.settings.ProviderIds
import com.laowang.idea.immersive.settings.SettingsService
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class GoogleTranslateEngine(
    private val baseUrlProvider: () -> String = { SettingsService.getInstance().providerConfig(ProviderIds.GOOGLE).baseUrl },
    private val targetLangProvider: () -> String = { SettingsService.getInstance().providerConfig(ProviderIds.GOOGLE).targetLang },
    private val client: OkHttpClient = defaultClient(),
    private val json: Json = defaultJson(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxConcurrentRequests: Int = DEFAULT_MAX_CONCURRENT_REQUESTS,
) : TranslationEngine {
    override val id: String = ENGINE_ID
    override val displayName: String = "Google Translate"

    override fun translate(segments: List<TextSegment>): TranslationEngineResult =
        translate(segments, TranslationCancellationToken())

    override fun translate(
        segments: List<TextSegment>,
        cancellationToken: TranslationCancellationToken,
    ): TranslationEngineResult {
        if (segments.isEmpty()) {
            return TranslationEngineResult.Success(emptyList())
        }

        return try {
            cancellationToken.throwIfCancelled()
            TranslationEngineResult.Success(translateConcurrently(segments, cancellationToken))
        } catch (exception: ExecutionException) {
            resultFor(exception.cause ?: exception)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            if (cancellationToken.isCancelled) {
                TranslationEngineResult.Cancelled
            } else {
                TranslationEngineResult.Failure(TranslationError.NetworkTimeout)
            }
        } catch (exception: TranslationCancelledException) {
            TranslationEngineResult.Cancelled
        } catch (exception: SocketTimeoutException) {
            TranslationEngineResult.Failure(TranslationError.NetworkTimeout)
        } catch (exception: InterruptedIOException) {
            TranslationEngineResult.Failure(TranslationError.NetworkTimeout)
        } catch (exception: GoogleTranslateException) {
            TranslationEngineResult.Failure(exception.error)
        } catch (exception: Exception) {
            TranslationEngineResult.Failure(TranslationError.Unknown(exception))
        }
    }

    private fun translateConcurrently(
        segments: List<TextSegment>,
        cancellationToken: TranslationCancellationToken,
    ): List<Translation> {
        val poolSize = maxConcurrentRequests.coerceAtLeast(1).coerceAtMost(segments.size)
        val executor = Executors.newFixedThreadPool(poolSize)
        val futures = segments.map { segment ->
            executor.submit<Translation> {
                cancellationToken.throwIfCancelled()
                Translation(segment.id, translateSegment(segment, cancellationToken), id, clock())
            }
        }
        return try {
            futures.map { it.get() }
        } catch (exception: Exception) {
            futures.forEach { it.cancel(true) }
            throw exception
        } finally {
            executor.shutdownNow()
        }
    }

    private fun translateSegment(
        segment: TextSegment,
        cancellationToken: TranslationCancellationToken,
    ): String {
        val request = buildRequest(segment)
        client.newCall(request).executeCancellable(cancellationToken) { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw GoogleTranslateException(response.toTranslationError(body))
            }
            return decodeTranslation(body)
        }
    }

    private fun resultFor(exception: Throwable): TranslationEngineResult =
        when (exception) {
            is TranslationCancelledException -> TranslationEngineResult.Cancelled
            is SocketTimeoutException -> TranslationEngineResult.Failure(TranslationError.NetworkTimeout)
            is InterruptedIOException -> TranslationEngineResult.Failure(TranslationError.NetworkTimeout)
            is GoogleTranslateException -> TranslationEngineResult.Failure(exception.error)
            else -> TranslationEngineResult.Failure(TranslationError.Unknown(exception))
        }

    private fun buildRequest(segment: TextSegment): Request {
        val targetLang = targetLangProvider().trim().ifEmpty { segment.targetLang }
        val url = "${baseUrlProvider().normalizeBaseUrl()}/translate_a/single"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("client", "gtx")
            .addQueryParameter("sl", "auto")
            .addQueryParameter("tl", normalizeTargetLang(targetLang))
            .addQueryParameter("dt", "t")
            .addQueryParameter("q", segment.content)
            .build()
        return Request.Builder()
            .url(url)
            .get()
            .build()
    }

    private fun decodeTranslation(responseBody: String): String {
        val translatedText = runCatching {
            val sentences = json.parseToJsonElement(responseBody)
                .jsonArray
                .getOrNull(0)
                ?.jsonArray
                ?: emptyList()
            sentences.joinToString("") { sentence ->
                sentence.jsonArray.getOrNull(0)?.jsonPrimitive?.contentOrNull.orEmpty()
            }.trim()
        }.getOrElse { error ->
            throw GoogleTranslateException(
                TranslationError.Unknown(IllegalStateException("Failed to parse Google response", error)),
            )
        }
        if (translatedText.isEmpty()) {
            throw GoogleTranslateException(
                TranslationError.Unknown(IllegalStateException("Google response did not contain translated text")),
            )
        }
        return translatedText
    }

    private fun okhttp3.Response.toTranslationError(responseBody: String): TranslationError {
        val parsedMessage = runCatching {
            json.decodeFromString(GoogleErrorEnvelope.serializer(), responseBody).error?.message
        }.getOrNull()
        val message = parsedMessage?.takeIf { it.isNotBlank() } ?: message.takeIf { it.isNotBlank() } ?: "Request failed"
        return when (code) {
            429 -> TranslationError.RateLimited(0)
            else -> TranslationError.ApiError(code, message)
        }
    }

    private fun normalizeTargetLang(targetLang: String): String =
        when (targetLang.trim()) {
            "", "zh-CN", "zh-Hans" -> "zh-CN"
            "zh-TW", "zh-Hant" -> "zh-TW"
            else -> targetLang.trim()
        }

    private fun String.normalizeBaseUrl(): String =
        trim().ifEmpty { DEFAULT_BASE_URL }.removeSuffix("/")

    companion object {
        const val ENGINE_ID = ProviderIds.GOOGLE
        const val DEFAULT_BASE_URL = "https://translate.googleapis.com"
        private const val DEFAULT_MAX_CONCURRENT_REQUESTS = 4

        private fun defaultClient(): OkHttpClient = HttpClientFactory.create()

        private fun defaultJson(): Json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}

class GoogleTranslateException(val error: TranslationError) : RuntimeException(error.toString())

@Serializable
private data class GoogleErrorEnvelope(val error: GoogleError? = null)

@Serializable
private data class GoogleError(val message: String? = null)
