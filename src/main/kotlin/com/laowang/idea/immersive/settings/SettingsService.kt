package com.laowang.idea.immersive.settings

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.laowang.idea.immersive.engine.EngineRegistry
import com.laowang.idea.immersive.engine.TranslationEngine

@Service(Service.Level.APP)
class SettingsService(
    private val settings: ImmersiveTranslateSettings = ImmersiveTranslateSettings.getInstance(),
) {
    fun activeEngine(): TranslationEngine =
        EngineRegistry.findById(settings.activeEngineId)
            ?: EngineRegistry.findById(ImmersiveTranslateSettings.DEFAULT_ACTIVE_ENGINE_ID)
            ?: EngineRegistry.all().first()

    fun targetLang(): String = settings.targetLang

    fun activeProviderConfig(): ProviderConfigState = settings.activeProviderConfig()

    fun providerConfig(providerId: String): ProviderConfigState = settings.providerConfig(providerId)

    fun rendererId(): String = settings.rendererId

    fun cacheMaxEntries(): Int = settings.cacheMaxEntries

    companion object {
        fun getInstance(): SettingsService = service()
    }
}
