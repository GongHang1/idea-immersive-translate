package com.laowang.idea.immersive.engine

import com.laowang.idea.immersive.core.TextSegment
import com.laowang.idea.immersive.core.Translation
import com.laowang.idea.immersive.core.TranslationError
import com.laowang.idea.immersive.settings.CredentialsStore
import com.laowang.idea.immersive.settings.ProviderIds
import com.laowang.idea.immersive.settings.SettingsService
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class MicrosoftTranslatorEngine(
    private val apiKeyProvider: () -> String? = { CredentialsStore.getInstance().getApiKey(ProviderIds.MICROSOFT) },
    private val baseUrlProvider: () -> String = { SettingsService.getInstance().providerConfig(ProviderIds.MICROSOFT).baseUrl },
    private val targetLangProvider: () -> String = { SettingsService.getInstance().providerConfig(ProviderIds.MICROSOFT).targetLang },
    private val regionProvider: () -> String = { SettingsService.getInstance().providerConfig(ProviderIds.MICROSOFT).region },
    private val client: OkHttpClient = defaultClient(),
    private val json: Json = defaultJson(),
    private val clock: () -> Long = System::currentTimeMillis,
) : TranslationEngine {
    override val id: String = ENGINE_ID
    override val displayName: String = "Microsoft Translator"

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
                    return TranslationEngineResult.Failure(response.toTranslationError(body))
                }
                val translatedTexts = decodeTranslations(body, segments.size)
                TranslationEngineResult.Success(
                    segments.zip(translatedTexts).map { (segment, translatedText) ->
                        Translation(segment.id, translatedText, id, clock())
                    },
                )
            }
        } catch (exception: SocketTimeoutException) {
            TranslationEngineResult.Failure(TranslationError.NetworkTimeout)
        } catch (exception: InterruptedIOException) {
            TranslationEngineResult.Failure(TranslationError.NetworkTimeout)
        } catch (exception: MicrosoftTranslatorException) {
            TranslationEngineResult.Failure(exception.error)
        } catch (exception: Exception) {
            TranslationEngineResult.Failure(TranslationError.Unknown(exception))
        }
    }

    private fun buildRequest(apiKey: String, segments: List<TextSegment>): Request {
        val url = "${baseUrlProvider().normalizeBaseUrl()}/translate"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("api-version", "3.0")
            .addQueryParameter("to", normalizeTargetLang(targetLangProvider()))
            .build()
        val payload = segments.map { MicrosoftTranslateRequest(it.content) }
        val body = json.encodeToString(ListSerializer(MicrosoftTranslateRequest.serializer()), payload)
            .toRequestBody(JSON_MEDIA_TYPE)
        val builder = Request.Builder()
            .url(url)
            .addHeader("Content-Type", JSON_MEDIA_TYPE.toString())
            .addHeader("Ocp-Apim-Subscription-Key", apiKey)
            .post(body)
        regionProvider().trim().takeIf { it.isNotEmpty() }?.let {
            builder.addHeader("Ocp-Apim-Subscription-Region", it)
        }
        return builder.build()
    }

    private fun decodeTranslations(responseBody: String, expectedCount: Int): List<String> {
        val payload = runCatching {
            json.decodeFromString(ListSerializer(MicrosoftTranslateResponse.serializer()), responseBody)
        }.getOrElse { error ->
            throw MicrosoftTranslatorException(
                TranslationError.Unknown(IllegalStateException("Failed to parse Microsoft response", error)),
            )
        }
        val translations = payload.mapNotNull { item -> item.translations.firstOrNull()?.text?.trim() }
        if (translations.size != expectedCount) {
            throw MicrosoftTranslatorException(
                TranslationError.Unknown(
                    IllegalStateException("Expected $expectedCount translated segments but received ${translations.size}"),
                ),
            )
        }
        return translations
    }

    private fun okhttp3.Response.toTranslationError(responseBody: String): TranslationError {
        val parsedMessage = runCatching {
            json.decodeFromString(MicrosoftErrorEnvelope.serializer(), responseBody).error?.message
        }.getOrNull()
        val message = parsedMessage?.takeIf { it.isNotBlank() } ?: message.takeIf { it.isNotBlank() } ?: "Request failed"
        return when (code) {
            429 -> TranslationError.RateLimited(0)
            else -> TranslationError.ApiError(code, message)
        }
    }

    private fun normalizeTargetLang(targetLang: String): String =
        when (targetLang.trim()) {
            "", "zh-CN", "zh-Hans" -> "zh-Hans"
            "zh-TW", "zh-Hant" -> "zh-Hant"
            else -> targetLang.trim()
        }

    private fun String?.normalizeApiKey(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun String.normalizeBaseUrl(): String =
        trim().ifEmpty { DEFAULT_BASE_URL }.removeSuffix("/")

    companion object {
        const val ENGINE_ID = ProviderIds.MICROSOFT
        const val DEFAULT_BASE_URL = "https://api.cognitive.microsofttranslator.com"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun defaultClient(): OkHttpClient = HttpClientFactory.create()

        private fun defaultJson(): Json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}

class MicrosoftTranslatorException(val error: TranslationError) : RuntimeException(error.toString())

@Serializable
private data class MicrosoftTranslateRequest(
    @SerialName("Text")
    val text: String,
)

@Serializable
private data class MicrosoftTranslateResponse(
    val translations: List<MicrosoftTranslationItem> = emptyList(),
)

@Serializable
private data class MicrosoftTranslationItem(val text: String? = null)

@Serializable
private data class MicrosoftErrorEnvelope(val error: MicrosoftError? = null)

@Serializable
private data class MicrosoftError(val message: String? = null)
