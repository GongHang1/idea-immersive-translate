package com.laowang.idea.immersive.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ImmersiveTranslateSettingsTest {
    @Test
    fun `defaults match plugin plan`() {
        val settings = ImmersiveTranslateSettings()

        assertEquals("openai", settings.activeEngineId)
        assertEquals("zh-CN", settings.targetLang)
        assertEquals("https://api.openai.com/v1", settings.openAiBaseUrl)
        assertEquals("gpt-4o-mini", settings.openAiModel)
        assertEquals("inlay", settings.rendererId)
        assertEquals(5000, settings.cacheMaxEntries)
    }

    @Test
    fun `defaults include provider configurations`() {
        val settings = ImmersiveTranslateSettings()

        assertEquals(
            listOf(ProviderIds.OPENAI, ProviderIds.GEMINI, ProviderIds.MICROSOFT, ProviderIds.GOOGLE),
            settings.providerConfigs().map { it.id },
        )
        assertEquals("Microsoft Translator", settings.providerConfig(ProviderIds.MICROSOFT).displayName)
        assertEquals("https://api-edge.cognitive.microsofttranslator.com", settings.providerConfig(ProviderIds.MICROSOFT).baseUrl)
        assertEquals("Google Translate", settings.providerConfig(ProviderIds.GOOGLE).displayName)
        assertEquals("https://translate.googleapis.com", settings.providerConfig(ProviderIds.GOOGLE).baseUrl)
        assertEquals(ProviderKind.GOOGLE_TRANSLATE, settings.providerConfig(ProviderIds.GOOGLE).kind)
    }

    @Test
    fun `updating gemini model does not alter openai model`() {
        val settings = ImmersiveTranslateSettings()

        settings.updateProviderConfig(
            settings.providerConfig(ProviderIds.GEMINI).copy(model = "gemini-2.0-flash"),
        )

        assertEquals("gpt-4o-mini", settings.providerConfig(ProviderIds.OPENAI).model)
        assertEquals("gemini-2.0-flash", settings.providerConfig(ProviderIds.GEMINI).model)
    }

    @Test
    fun `loadState replaces stored values`() {
        val settings = ImmersiveTranslateSettings()
        val newState = ImmersiveTranslateSettings.State().apply {
            activeEngineId = "custom"
            targetLang = "ja"
            openAiBaseUrl = "https://example.test/v1"
            openAiModel = "gpt-custom"
            rendererId = "custom-renderer"
            cacheMaxEntries = 128
        }

        settings.loadState(newState)

        assertEquals("custom", settings.activeEngineId)
        assertEquals("ja", settings.targetLang)
        assertEquals("https://example.test/v1", settings.openAiBaseUrl)
        assertEquals("gpt-custom", settings.openAiModel)
        assertEquals("custom-renderer", settings.rendererId)
        assertEquals(128, settings.cacheMaxEntries)
    }
}
