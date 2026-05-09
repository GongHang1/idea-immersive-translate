package com.laowang.idea.immersive.engine

import com.laowang.idea.immersive.core.TextSegment
import com.laowang.idea.immersive.core.Translation
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

class GoogleCloudTranslateEngine(
    private val credentialProvider: () -> String? = { CredentialsStore.getInstance().getApiKey(ProviderIds.GOOGLE) },
    private val baseUrlProvider: () -> String = { SettingsService.getInstance().providerConfig(ProviderIds.GOOGLE).baseUrl },
    private val projectIdProvider: () -> String = { SettingsService.getInstance().providerConfig(ProviderIds.GOOGLE).projectId },
    private val locationProvider: () -> String = { SettingsService.getInstance().providerConfig(ProviderIds.GOOGLE).location },
    private val targetLangProvider: () -> String = { SettingsService.getInstance().providerConfig(ProviderIds.GOOGLE).targetLang },
    private val client: OkHttpClient = defaultClient(),
    private val json: Json = defaultJson(),
    private val clock: () -> Long = System::currentTimeMillis,
) : TranslationEngine {
    override val id: String = ENGINE_ID
    override val displayName: String = "Google Cloud Translation"

    override fun translate(segments: List<TextSegment>): TranslationEngineResult {
        if (segments.isEmpty()) {
            return TranslationEngineResult.Success(emptyList())
        }

        return try {
            val credential = credentialProvider().normalizeCredential()
                ?: return TranslationEngineResult.Failure(TranslationError.NoApiKey)
            val projectId = projectIdProvider().trim()
            if (projectId.isEmpty()) {
                return TranslationEngineResult.Failure(
                    TranslationError.Unknown(IllegalStateException("Google Cloud project ID is not configured")),
                )
            }
            val request = buildRequest(credential, projectId, segments)
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
        } catch (exception: GoogleCloudTranslateException) {
            TranslationEngineResult.Failure(exception.error)
        } catch (exception: Exception) {
            TranslationEngineResult.Failure(TranslationError.Unknown(exception))
        }
    }

    private fun buildRequest(credential: String, projectId: String, segments: List<TextSegment>): Request {
        val location = locationProvider().trim().ifEmpty { DEFAULT_LOCATION }
        val url = "${baseUrlProvider().normalizeBaseUrl()}/v3/projects/$projectId/locations/$location:translateText"
            .toHttpUrl()
        val payload = GoogleTranslateRequest(
            contents = segments.map { it.content },
            targetLanguageCode = targetLangProvider().trim().ifEmpty { segments.first().targetLang },
            mimeType = "text/plain",
        )
        val body = json.encodeToString(GoogleTranslateRequest.serializer(), payload)
            .toRequestBody(JSON_MEDIA_TYPE)
        return Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $credential")
            .addHeader("Content-Type", JSON_MEDIA_TYPE.toString())
            .post(body)
            .build()
    }

    private fun decodeTranslations(responseBody: String, expectedCount: Int): List<String> {
        val payload = runCatching {
            json.decodeFromString(GoogleTranslateResponse.serializer(), responseBody)
        }.getOrElse { error ->
            throw GoogleCloudTranslateException(
                TranslationError.Unknown(IllegalStateException("Failed to parse Google response", error)),
            )
        }
        val translations = payload.translations.mapNotNull { it.translatedText?.trim() }
        if (translations.size != expectedCount) {
            throw GoogleCloudTranslateException(
                TranslationError.Unknown(
                    IllegalStateException("Expected $expectedCount translated segments but received ${translations.size}"),
                ),
            )
        }
        return translations
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

    private fun String?.normalizeCredential(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun String.normalizeBaseUrl(): String =
        trim().ifEmpty { DEFAULT_BASE_URL }.removeSuffix("/")

    companion object {
        const val ENGINE_ID = ProviderIds.GOOGLE
        const val DEFAULT_BASE_URL = "https://translation.googleapis.com"
        const val DEFAULT_LOCATION = "global"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun defaultClient(): OkHttpClient = HttpClientFactory.create()

        private fun defaultJson(): Json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}

class GoogleCloudTranslateException(val error: TranslationError) : RuntimeException(error.toString())

@Serializable
private data class GoogleTranslateRequest(
    val contents: List<String>,
    val targetLanguageCode: String,
    val mimeType: String,
)

@Serializable
private data class GoogleTranslateResponse(
    val translations: List<GoogleTranslationItem> = emptyList(),
)

@Serializable
private data class GoogleTranslationItem(val translatedText: String? = null)

@Serializable
private data class GoogleErrorEnvelope(val error: GoogleError? = null)

@Serializable
private data class GoogleError(val message: String? = null)
