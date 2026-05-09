package com.laowang.idea.immersive.settings

import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import java.awt.event.ItemEvent
import java.util.ResourceBundle
import javax.swing.JComponent

class SettingsPanel(
    private val settings: ImmersiveTranslateSettings = ImmersiveTranslateSettings.getInstance(),
    private val credentialsStore: CredentialsStore = CredentialsStore.getInstance(),
) {
    private val providerOptions = linkedSetOf(
        ImmersiveTranslateSettings.DEFAULT_ACTIVE_ENGINE_ID,
        settings.activeEngineId,
    ).apply {
        addAll(settings.providerConfigs().map { it.id })
    }.toTypedArray()

    private val rendererOptions = linkedSetOf(
        ImmersiveTranslateSettings.DEFAULT_RENDERER_ID,
        settings.rendererId,
    ).toTypedArray()

    private val engineCombo = ComboBox(providerOptions)
    private val apiKeyField = JBPasswordField()
    private val targetLangField = JBTextField()
    private val baseUrlField = JBTextField()
    private val modelField = JBTextField()
    private val regionField = JBTextField()
    private val projectIdField = JBTextField()
    private val locationField = JBTextField()
    private val rendererCombo = ComboBox(rendererOptions)
    private val cacheMaxEntriesField = JBTextField()

    val panel: DialogPanel = panel {
        row(message("settings.provider", "Provider")) {
            cell(engineCombo)
                .align(AlignX.FILL)
                .resizableColumn()
        }
        row(message("settings.api.key", "API Key")) {
            cell(apiKeyField)
                .align(AlignX.FILL)
                .resizableColumn()
        }
        row(message("settings.target.lang", "Target Language")) {
            cell(targetLangField)
                .align(AlignX.FILL)
                .resizableColumn()
        }
        row(message("settings.openai.base.url", "OpenAI Base URL")) {
            cell(baseUrlField)
                .align(AlignX.FILL)
                .resizableColumn()
        }
        row(message("settings.openai.model", "OpenAI Model")) {
            cell(modelField)
                .align(AlignX.FILL)
                .resizableColumn()
        }
        row(message("settings.microsoft.region", "Microsoft Region")) {
            cell(regionField)
                .align(AlignX.FILL)
                .resizableColumn()
        }
        row(message("settings.google.project", "Google Project ID")) {
            cell(projectIdField)
                .align(AlignX.FILL)
                .resizableColumn()
        }
        row(message("settings.google.location", "Google Location")) {
            cell(locationField)
                .align(AlignX.FILL)
                .resizableColumn()
        }
        row("Renderer") {
            cell(rendererCombo)
                .align(AlignX.FILL)
                .resizableColumn()
        }
        row("Cache Max Entries") {
            cell(cacheMaxEntriesField)
                .align(AlignX.FILL)
                .resizableColumn()
        }
    }

    init {
        engineCombo.addItemListener { event ->
            if (event.stateChange == ItemEvent.SELECTED) {
                loadSelectedProvider()
            }
        }
        reset()
    }

    fun preferredFocusedComponent(): JComponent = apiKeyField

    fun isModified(): Boolean {
        val provider = selectedProviderConfig()
        return selectedEngineId() != settings.activeEngineId ||
            currentApiKey() != credentialsStore.getApiKey(provider.id).orEmpty() ||
            targetLangField.text.trim() != provider.targetLang ||
            baseUrlField.text.trim() != provider.baseUrl ||
            modelField.text.trim() != provider.model ||
            regionField.text.trim() != provider.region ||
            projectIdField.text.trim() != provider.projectId ||
            locationField.text.trim() != provider.location ||
            selectedRendererId() != settings.rendererId ||
            cacheMaxEntriesField.text.trim() != settings.cacheMaxEntries.toString()
    }

    @Throws(ConfigurationException::class)
    fun apply() {
        val providerId = selectedEngineId()
        val targetLang = targetLangField.text.trim().ifBlank {
            throw ConfigurationException("Target Language cannot be blank")
        }
        settings.activeEngineId = providerId
        settings.updateProviderConfig(
            settings.providerConfig(providerId).copy(
                targetLang = targetLang,
                baseUrl = baseUrlField.text.trim(),
                model = modelField.text.trim(),
                region = regionField.text.trim(),
                projectId = projectIdField.text.trim(),
                location = locationField.text.trim(),
            ),
        )
        settings.rendererId = selectedRendererId()
        settings.cacheMaxEntries = cacheMaxEntriesField.text.trim().toIntOrNull()?.takeIf { it > 0 }
            ?: throw ConfigurationException("Cache Max Entries must be a positive integer")
        credentialsStore.setApiKey(providerId, currentApiKey().ifBlank { null })
        reset()
    }

    fun reset() {
        engineCombo.selectedItem = settings.activeEngineId
        rendererCombo.selectedItem = settings.rendererId
        cacheMaxEntriesField.text = settings.cacheMaxEntries.toString()
        loadSelectedProvider()
    }

    private fun selectedEngineId(): String =
        (engineCombo.selectedItem as? String)?.trim().takeUnless { it.isNullOrEmpty() }
            ?: ImmersiveTranslateSettings.DEFAULT_ACTIVE_ENGINE_ID

    private fun selectedRendererId(): String =
        (rendererCombo.selectedItem as? String)?.trim().takeUnless { it.isNullOrEmpty() }
            ?: ImmersiveTranslateSettings.DEFAULT_RENDERER_ID

    private fun selectedProviderConfig(): ProviderConfigState = settings.providerConfig(selectedEngineId())

    private fun loadSelectedProvider() {
        val provider = selectedProviderConfig()
        apiKeyField.text = credentialsStore.getApiKey(provider.id).orEmpty()
        targetLangField.text = provider.targetLang
        baseUrlField.text = provider.baseUrl
        modelField.text = provider.model
        regionField.text = provider.region
        projectIdField.text = provider.projectId
        locationField.text = provider.location

        val hasModel = provider.kind == ProviderKind.OPENAI_COMPATIBLE || provider.kind == ProviderKind.GEMINI
        val hasRegion = provider.kind == ProviderKind.MICROSOFT_TRANSLATOR
        val hasGoogleProject = provider.kind == ProviderKind.GOOGLE_CLOUD_TRANSLATE
        modelField.isEnabled = hasModel
        regionField.isEnabled = hasRegion
        projectIdField.isEnabled = hasGoogleProject
        locationField.isEnabled = hasGoogleProject
    }

    private fun currentApiKey(): String = String(apiKeyField.password).trim()

    companion object {
        fun message(key: String, fallback: String): String =
            runCatching { ResourceBundle.getBundle("messages.ImmersiveTranslateBundle").getString(key) }
                .getOrDefault(fallback)
    }
}
