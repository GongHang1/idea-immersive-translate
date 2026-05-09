package com.laowang.idea.immersive.engine

import com.laowang.idea.immersive.core.TextSegment
import com.laowang.idea.immersive.core.Translation
import com.laowang.idea.immersive.core.TranslationCancellationToken
import com.laowang.idea.immersive.core.TranslationCancelledException
import com.laowang.idea.immersive.core.TranslationError
import com.laowang.idea.immersive.settings.CredentialsStore
import com.laowang.idea.immersive.settings.ProviderIds
import com.laowang.idea.immersive.settings.SettingsService
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class GeminiEngine(
    private val apiKeyProvider: () -> String? = { CredentialsStore.getInstance().getApiKey(ProviderIds.GEMINI) },
    private val baseUrlProvider: () -> String = { SettingsService.getInstance().providerConfig(ProviderIds.GEMINI).baseUrl },
    private val modelProvider: () -> String = { SettingsService.getInstance().providerConfig(ProviderIds.GEMINI).model },
    private val client: OkHttpClient = defaultClient(),
    private val json: Json = defaultJson(),
    private val clock: () -> Long = System::currentTimeMillis,
) : TranslationEngine {
    override val id: String = ENGINE_ID
    override val displayName: String = "Gemini"

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
            val apiKey = apiKeyProvider().normalizeApiKey()
                ?: return TranslationEngineResult.Failure(TranslationError.NoApiKey)
            val request = buildRequest(apiKey, segments)
            client.newCall(request).executeCancellable(cancellationToken) { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return TranslationEngineResult.Failure(response.toTranslationError(body))
                }
                val content = decodeText(body)
                val translatedTexts = splitTranslations(content, segments.size)
                TranslationEngineResult.Success(
                    segments.zip(translatedTexts).map { (segment, translatedText) ->
                        Translation(segment.id, translatedText, id, clock())
                    },
                )
            }
        } catch (exception: TranslationCancelledException) {
            TranslationEngineResult.Cancelled
        } catch (exception: SocketTimeoutException) {
            TranslationEngineResult.Failure(TranslationError.NetworkTimeout)
        } catch (exception: InterruptedIOException) {
            TranslationEngineResult.Failure(TranslationError.NetworkTimeout)
        } catch (exception: GeminiEngineException) {
            TranslationEngineResult.Failure(exception.error)
        } catch (exception: Exception) {
            TranslationEngineResult.Failure(TranslationError.Unknown(exception))
        }
    }

    private fun buildRequest(apiKey: String, segments: List<TextSegment>): Request {
        val model = modelProvider().trim().ifEmpty { DEFAULT_MODEL }
        val url = "${baseUrlProvider().normalizeBaseUrl()}/v1beta/models/$model:generateContent"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("key", apiKey)
            .build()
        val payload = GenerateContentRequest(
            systemInstruction = GeminiContent(
                parts = listOf(GeminiPart(buildSystemPrompt(segments.first().targetLang))),
            ),
            contents = listOf(
                GeminiContent(
                    role = "user",
                    parts = listOf(GeminiPart(buildUserPrompt(segments))),
                ),
            ),
            generationConfig = GeminiGenerationConfig(temperature = 0.0),
        )
        val body = json.encodeToString(GenerateContentRequest.serializer(), payload)
            .toRequestBody(JSON_MEDIA_TYPE)
        return Request.Builder()
            .url(url)
            .addHeader("Content-Type", JSON_MEDIA_TYPE.toString())
            .post(body)
            .build()
    }

    private fun buildSystemPrompt(targetLang: String): String =
        """
        You translate developer-facing source text into $targetLang.
        Return exactly one translated item for each input segment.
        Keep the original order.
        Return only translated text separated by ${OpenAIEngine.RESPONSE_DELIMITER}.
        Do not add numbering, markdown, explanations, or extra delimiters.
        """.trimIndent()

    private fun buildUserPrompt(segments: List<TextSegment>): String =
        buildString {
            appendLine("Translate the following segments:")
            segments.forEachIndexed { index, segment ->
                if (index > 0) {
                    appendLine()
                }
                appendLine(OpenAIEngine.REQUEST_DELIMITER_TOKEN)
                append(segment.content)
            }
        }

    private fun decodeText(responseBody: String): String {
        val payload = runCatching {
            json.decodeFromString(GenerateContentResponse.serializer(), responseBody)
        }.getOrElse { error ->
            throw GeminiEngineException(
                TranslationError.Unknown(IllegalStateException("Failed to parse Gemini response", error)),
            )
        }
        val text = payload.candidates.firstOrNull()?.content?.parts
            ?.mapNotNull { it.text }
            ?.joinToString("")
            ?.trim()
        if (text.isNullOrEmpty()) {
            throw GeminiEngineException(
                TranslationError.Unknown(IllegalStateException("Gemini response did not contain text")),
            )
        }
        return text
    }

    private fun splitTranslations(content: String, expectedCount: Int): List<String> {
        val parts = content
            .split(OpenAIEngine.RESPONSE_DELIMITER)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (parts.size != expectedCount) {
            throw GeminiEngineException(
                TranslationError.Unknown(
                    IllegalStateException("Expected $expectedCount translated segments but received ${parts.size}"),
                ),
            )
        }
        return parts
    }

    private fun okhttp3.Response.toTranslationError(responseBody: String): TranslationError {
        val parsedMessage = runCatching {
            json.decodeFromString(GeminiErrorEnvelope.serializer(), responseBody).error?.message
        }.getOrNull()
        val message = parsedMessage?.takeIf { it.isNotBlank() } ?: message.takeIf { it.isNotBlank() } ?: "Request failed"
        return when (code) {
            429 -> TranslationError.RateLimited(0)
            else -> TranslationError.ApiError(code, message)
        }
    }

    private fun String?.normalizeApiKey(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun String.normalizeBaseUrl(): String =
        trim().ifEmpty { DEFAULT_BASE_URL }.removeSuffix("/")

    companion object {
        const val ENGINE_ID = ProviderIds.GEMINI
        const val DEFAULT_MODEL = "gemini-1.5-flash"
        const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun defaultClient(): OkHttpClient = HttpClientFactory.create()

        private fun defaultJson(): Json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}

class GeminiEngineException(val error: TranslationError) : RuntimeException(error.toString())

@Serializable
private data class GenerateContentRequest(
    val systemInstruction: GeminiContent,
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig,
)

@Serializable
private data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>,
)

@Serializable
private data class GeminiPart(val text: String)

@Serializable
private data class GeminiGenerationConfig(val temperature: Double)

@Serializable
private data class GenerateContentResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
)

@Serializable
private data class GeminiCandidate(val content: GeminiResponseContent? = null)

@Serializable
private data class GeminiResponseContent(val parts: List<GeminiResponsePart> = emptyList())

@Serializable
private data class GeminiResponsePart(val text: String? = null)

@Serializable
private data class GeminiErrorEnvelope(val error: GeminiError? = null)

@Serializable
private data class GeminiError(val message: String? = null)
