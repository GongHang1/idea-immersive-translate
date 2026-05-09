package com.laowang.idea.immersive.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

@Service(Service.Level.APP)
@State(
    name = "com.laowang.idea.immersive.settings.ImmersiveTranslateSettings",
    storages = [Storage(value = "immersive-translate.xml", roamingType = RoamingType.DISABLED)],
)
class ImmersiveTranslateSettings : PersistentStateComponent<ImmersiveTranslateSettings.State> {
    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state.normalized()
    }

    var activeEngineId: String
        get() = state.activeEngineId
        set(value) {
            state.activeEngineId = value.trim().ifEmpty { DEFAULT_ACTIVE_ENGINE_ID }
        }

    var targetLang: String
        get() = providerConfig(activeEngineId).targetLang
        set(value) {
            val target = value.trim().ifEmpty { DEFAULT_TARGET_LANG }
            state.targetLang = target
            updateProviderConfig(providerConfig(activeEngineId).copy(targetLang = target))
        }

    var openAiBaseUrl: String
        get() = providerConfig(ProviderIds.OPENAI).baseUrl
        set(value) {
            val baseUrl = value.trim().ifEmpty { DEFAULT_OPENAI_BASE_URL }
            state.openAiBaseUrl = baseUrl
            updateProviderConfig(providerConfig(ProviderIds.OPENAI).copy(baseUrl = baseUrl))
        }

    var openAiModel: String
        get() = providerConfig(ProviderIds.OPENAI).model
        set(value) {
            val model = value.trim().ifEmpty { DEFAULT_OPENAI_MODEL }
            state.openAiModel = model
            updateProviderConfig(providerConfig(ProviderIds.OPENAI).copy(model = model))
        }

    var rendererId: String
        get() = state.rendererId
        set(value) {
            state.rendererId = value.trim().ifEmpty { DEFAULT_RENDERER_ID }
        }

    var cacheMaxEntries: Int
        get() = state.cacheMaxEntries
        set(value) {
            state.cacheMaxEntries = value.coerceAtLeast(1)
        }

    fun providerConfigs(): List<ProviderConfigState> = state.providerConfigs.map { it.copy() }

    fun providerConfig(providerId: String): ProviderConfigState =
        state.providerConfigs.firstOrNull { it.id == providerId }?.copy()
            ?: defaultProviderConfigs().firstOrNull { it.id == providerId }?.copy()
            ?: ProviderConfigState(
                id = providerId,
                displayName = providerId,
                baseUrl = state.openAiBaseUrl,
                model = state.openAiModel,
                targetLang = state.targetLang,
            )

    fun activeProviderConfig(): ProviderConfigState = providerConfig(activeEngineId)

    fun updateProviderConfig(config: ProviderConfigState) {
        val defaults = defaultProviderConfigs().firstOrNull { it.id == config.id } ?: config
        val normalized = config.normalized(defaults)
        val index = state.providerConfigs.indexOfFirst { it.id == normalized.id }
        if (index >= 0) {
            state.providerConfigs[index] = normalized
        } else {
            state.providerConfigs += normalized
        }
        if (normalized.id == ProviderIds.OPENAI) {
            state.openAiBaseUrl = normalized.baseUrl
            state.openAiModel = normalized.model
        }
        if (normalized.id == activeEngineId) {
            state.targetLang = normalized.targetLang
        }
    }

    private fun State.normalized(): State = State().also {
        it.activeEngineId = activeEngineId.trim().ifEmpty { DEFAULT_ACTIVE_ENGINE_ID }
        it.targetLang = targetLang.trim().ifEmpty { DEFAULT_TARGET_LANG }
        it.openAiBaseUrl = openAiBaseUrl.trim().ifEmpty { DEFAULT_OPENAI_BASE_URL }
        it.openAiModel = openAiModel.trim().ifEmpty { DEFAULT_OPENAI_MODEL }
        it.rendererId = rendererId.trim().ifEmpty { DEFAULT_RENDERER_ID }
        it.cacheMaxEntries = cacheMaxEntries.coerceAtLeast(1)
        it.providerConfigs = mergeProviderConfigs(
            providerConfigs = providerConfigs,
            openAiBaseUrl = it.openAiBaseUrl,
            openAiModel = it.openAiModel,
            targetLang = it.targetLang,
        ).toMutableList()
    }

    class State {
        var activeEngineId: String = DEFAULT_ACTIVE_ENGINE_ID
        var targetLang: String = DEFAULT_TARGET_LANG
        var openAiBaseUrl: String = DEFAULT_OPENAI_BASE_URL
        var openAiModel: String = DEFAULT_OPENAI_MODEL
        var rendererId: String = DEFAULT_RENDERER_ID
        var cacheMaxEntries: Int = DEFAULT_CACHE_MAX_ENTRIES
        var providerConfigs: MutableList<ProviderConfigState> = defaultProviderConfigs().toMutableList()
    }

    companion object {
        const val DEFAULT_ACTIVE_ENGINE_ID = ProviderIds.OPENAI
        const val DEFAULT_TARGET_LANG = "zh-CN"
        const val DEFAULT_OPENAI_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_OPENAI_MODEL = "gpt-4o-mini"
        const val DEFAULT_RENDERER_ID = "inlay"
        const val DEFAULT_CACHE_MAX_ENTRIES = 5000

        fun getInstance(): ImmersiveTranslateSettings = service()

        private fun mergeProviderConfigs(
            providerConfigs: List<ProviderConfigState>,
            openAiBaseUrl: String,
            openAiModel: String,
            targetLang: String,
        ): List<ProviderConfigState> {
            val configuredById = providerConfigs.associateBy { it.id }
            val defaults = defaultProviderConfigs()
            val mergedDefaults = defaults.map { defaultConfig ->
                val configured = configuredById[defaultConfig.id] ?: defaultConfig
                val migrated = when (defaultConfig.id) {
                    ProviderIds.OPENAI -> configured.copy(baseUrl = openAiBaseUrl, model = openAiModel)
                    ProviderIds.MICROSOFT,
                    ProviderIds.GOOGLE,
                    -> configured.copy(
                        kind = defaultConfig.kind,
                        displayName = defaultConfig.displayName,
                        baseUrl = defaultConfig.baseUrl,
                        region = "",
                        projectId = "",
                        location = "",
                    )
                    else -> configured
                }
                migrated.copy(targetLang = migrated.targetLang.ifBlank { targetLang }).normalized(defaultConfig)
            }
            val extraConfigs = providerConfigs
                .filterNot { config -> defaults.any { it.id == config.id } }
                .map { config -> config.copy(targetLang = config.targetLang.ifBlank { targetLang }).normalized(config) }
            return mergedDefaults + extraConfigs
        }
    }
}
