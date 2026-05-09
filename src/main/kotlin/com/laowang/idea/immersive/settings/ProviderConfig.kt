package com.laowang.idea.immersive.settings

import com.laowang.idea.immersive.engine.OpenAIEngine
import kotlinx.serialization.Serializable

object ProviderIds {
    const val OPENAI = "openai"
    const val GEMINI = "gemini"
    const val MICROSOFT = "microsoft"
    const val GOOGLE = "google"
}

@Serializable
enum class ProviderKind {
    OPENAI_COMPATIBLE,
    GEMINI,
    MICROSOFT_TRANSLATOR,
    GOOGLE_TRANSLATE,
    GOOGLE_CLOUD_TRANSLATE,
}

@Serializable
data class ProviderConfigState(
    var id: String = ProviderIds.OPENAI,
    var kind: ProviderKind = ProviderKind.OPENAI_COMPATIBLE,
    var displayName: String = "OpenAI Compatible",
    var baseUrl: String = "",
    var model: String = "",
    var targetLang: String = ImmersiveTranslateSettings.DEFAULT_TARGET_LANG,
    var region: String = "",
    var projectId: String = "",
    var location: String = "",
)

fun defaultProviderConfigs(): List<ProviderConfigState> = listOf(
    ProviderConfigState(
        id = ProviderIds.OPENAI,
        kind = ProviderKind.OPENAI_COMPATIBLE,
        displayName = "OpenAI Compatible",
        baseUrl = OpenAIEngine.DEFAULT_BASE_URL,
        model = OpenAIEngine.DEFAULT_MODEL,
    ),
    ProviderConfigState(
        id = ProviderIds.GEMINI,
        kind = ProviderKind.GEMINI,
        displayName = "Gemini",
        baseUrl = "https://generativelanguage.googleapis.com",
        model = "gemini-1.5-flash",
    ),
    ProviderConfigState(
        id = ProviderIds.MICROSOFT,
        kind = ProviderKind.MICROSOFT_TRANSLATOR,
        displayName = "Microsoft Translator",
        baseUrl = "https://api-edge.cognitive.microsofttranslator.com",
    ),
    ProviderConfigState(
        id = ProviderIds.GOOGLE,
        kind = ProviderKind.GOOGLE_TRANSLATE,
        displayName = "Google Translate",
        baseUrl = "https://translate.googleapis.com",
    ),
)

fun ProviderConfigState.normalized(defaults: ProviderConfigState): ProviderConfigState = copy(
    id = id.trim().ifEmpty { defaults.id },
    displayName = displayName.trim().ifEmpty { defaults.displayName },
    baseUrl = baseUrl.trim().ifEmpty { defaults.baseUrl },
    model = model.trim().ifEmpty { defaults.model },
    targetLang = targetLang.trim().ifEmpty { defaults.targetLang },
    region = region.trim(),
    projectId = projectId.trim(),
    location = location.trim().ifEmpty { defaults.location },
)

fun ProviderKind.requiresApiKey(): Boolean =
    this == ProviderKind.OPENAI_COMPATIBLE || this == ProviderKind.GEMINI

fun ProviderKind.usesModel(): Boolean =
    this == ProviderKind.OPENAI_COMPATIBLE || this == ProviderKind.GEMINI
