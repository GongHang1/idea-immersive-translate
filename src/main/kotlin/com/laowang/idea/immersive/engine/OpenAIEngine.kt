package com.laowang.idea.immersive.engine

import com.laowang.idea.immersive.core.TextSegment
import com.laowang.idea.immersive.core.Translation
import com.laowang.idea.immersive.core.TranslationError
import com.laowang.idea.immersive.settings.CredentialsStore
import com.laowang.idea.immersive.settings.ImmersiveTranslateSettings
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OpenAIEngine(
    private val apiKeyProvider: () -> String? = { CredentialsStore.getInstance().getApiKey() },
    private val baseUrlProvider: () -> String = { ImmersiveTranslateSettings.getInstance().openAiBaseUrl },
    private val modelProvider: () -> String = { ImmersiveTranslateSettings.getInstance().openAiModel },
    private val client: OkHttpClient = defaultClient(),
    private val json: Json = defaultJson(),
    private val clock: () -> Long = System::currentTimeMillis,
) : TranslationEngine {
    override val id: String = ENGINE_ID
    override val displayName: String = "OpenAI"

    override fun translate(segments: List<TextSegment>): TranslationEngineResult {
        if (segments.isEmpty()) {
            return TranslationEngineResult.Success(emptyList())
        }

        return try {
            val apiKey = apiKeyProvider().normalizeApiKey()
                ?: return TranslationEngineResult.Failure(TranslationError.NoApiKey)
            val request = buildRequest(apiKey, segments)
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw OpenAIEngineException(response.toTranslationError(body, json))
                }

                val content = decodeMessageContent(body)
                val translatedTexts = splitTranslations(content, segments.size)
                TranslationEngineResult.Success(
                    segments.zip(translatedTexts).map { (segment, translatedText) ->
                        Translation(
                            segmentId = segment.id,
                            translatedText = translatedText,
                            engineId = id,
                            timestamp = clock(),
                        )
                    },
                )
            }
        } catch (exception: OpenAIEngineException) {
            TranslationEngineResult.Failure(exception.error)
        } catch (exception: SocketTimeoutException) {
            TranslationEngineResult.Failure(TranslationError.NetworkTimeout)
        } catch (exception: InterruptedIOException) {
            TranslationEngineResult.Failure(TranslationError.NetworkTimeout)
        } catch (exception: Exception) {
            TranslationEngineResult.Failure(TranslationError.Unknown(exception))
        }
    }

    private fun buildRequest(apiKey: String, segments: List<TextSegment>): Request {
        val payload = ChatCompletionsRequest(
            model = modelProvider().trim().ifEmpty { DEFAULT_MODEL },
            messages = listOf(
                ChatMessage(
                    role = "system",
                    content = buildSystemPrompt(segments.first().targetLang),
                ),
                ChatMessage(
                    role = "user",
                    content = buildUserPrompt(segments),
                ),
            ),
            temperature = 0.0,
        )
        val body = json.encodeToString(ChatCompletionsRequest.serializer(), payload)
            .toRequestBody(JSON_MEDIA_TYPE)

        return Request.Builder()
            .url("${baseUrlProvider().normalizeBaseUrl()}/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", JSON_MEDIA_TYPE.toString())
            .post(body)
            .build()
    }

    private fun buildSystemPrompt(targetLang: String): String =
        """
        You translate developer-facing source text into $targetLang.
        Return exactly one translated item for each input segment.
        Keep the original order.
        Return only translated text separated by $RESPONSE_DELIMITER.
        Do not add numbering, markdown, explanations, or extra delimiters.
        """.trimIndent()

    private fun buildUserPrompt(segments: List<TextSegment>): String =
        buildString {
            appendLine("Translate the following segments:")
            segments.forEachIndexed { index, segment ->
                if (index > 0) {
                    appendLine()
                }
                appendLine(REQUEST_DELIMITER_TOKEN)
                append(segment.content)
            }
        }

    private fun decodeMessageContent(responseBody: String): String {
        val payload = runCatching {
            json.decodeFromString(ChatCompletionsResponse.serializer(), responseBody)
        }.getOrElse { error ->
            throw OpenAIEngineException(
                TranslationError.Unknown(
                    IllegalStateException("Failed to parse OpenAI response", error),
                ),
                error,
            )
        }

        val content = payload.choices.firstOrNull()?.message?.content?.trim()
            ?: payload.choices.firstOrNull()?.text?.trim()
            ?: payload.error?.message?.trim()

        if (content.isNullOrEmpty()) {
            throw OpenAIEngineException(
                TranslationError.Unknown(IllegalStateException("OpenAI response did not contain text")),
            )
        }

        return content
    }

    private fun splitTranslations(content: String, expectedCount: Int): List<String> {
        val parts = content
            .split(RESPONSE_DELIMITER)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (parts.size != expectedCount) {
            throw OpenAIEngineException(
                TranslationError.Unknown(
                    IllegalStateException(
                        "Expected $expectedCount translated segments but received ${parts.size}",
                    ),
                ),
            )
        }

        return parts
    }

    private fun okhttp3.Response.toTranslationError(
        responseBody: String,
        json: Json,
    ): TranslationError {
        val parsedMessage = runCatching {
            json.decodeFromString(OpenAIErrorEnvelope.serializer(), responseBody).error?.message
        }.getOrNull()
        val message = parsedMessage?.takeIf { it.isNotBlank() } ?: message.takeIf { it.isNotBlank() } ?: "Request failed"
        return when (code) {
            429 -> TranslationError.RateLimited(retryAfterMillis())
            else -> TranslationError.ApiError(code, message)
        }
    }

    private fun okhttp3.Response.retryAfterMillis(): Long {
        val seconds = header("Retry-After")?.trim()?.toLongOrNull()
        return (seconds ?: 0L) * 1000L
    }

    private fun String?.normalizeApiKey(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun String.normalizeBaseUrl(): String =
        trim().ifEmpty { DEFAULT_BASE_URL }.removeSuffix("/")

    companion object {
        const val ENGINE_ID = "openai"
        const val DEFAULT_MODEL = "gpt-4o-mini"
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        const val REQUEST_DELIMITER_TOKEN = "<<<IMMERSIVE_TRANSLATE_SEGMENT>>>"
        const val RESPONSE_DELIMITER = "<<<IMMERSIVE_TRANSLATE_DELIMITER>>>"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun defaultClient(): OkHttpClient = HttpClientFactory.create()

        private fun defaultJson(): Json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}

class OpenAIEngineException(
    val error: TranslationError,
    cause: Throwable? = null,
) : RuntimeException(error.toString(), cause)

@Serializable
private data class ChatCompletionsRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double,
)

@Serializable
private data class ChatMessage(
    val role: String,
    val content: String,
)

@Serializable
private data class ChatCompletionsResponse(
    val choices: List<ChatChoice> = emptyList(),
    val error: OpenAIError? = null,
)

@Serializable
private data class ChatChoice(
    val message: ChatResponseMessage? = null,
    val text: String? = null,
)

@Serializable
private data class ChatResponseMessage(
    val content: String? = null,
)

@Serializable
private data class OpenAIErrorEnvelope(
    val error: OpenAIError? = null,
)

@Serializable
private data class OpenAIError(
    @SerialName("message")
    val message: String? = null,
)
