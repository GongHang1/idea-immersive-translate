package com.laowang.idea.immersive.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import javax.swing.JComponent

class ImmersiveTranslateConfigurable : SearchableConfigurable, Configurable.NoScroll {
    private var settingsPanel: SettingsPanel? = null

    override fun getId(): String = ID

    override fun getDisplayName(): String = SettingsPanel.message("settings.title", "Immersive Translate")

    override fun createComponent(): JComponent {
        val panel = settingsPanel ?: SettingsPanel().also { settingsPanel = it }
        return panel.panel
    }

    override fun isModified(): Boolean = settingsPanel?.isModified() ?: false

    @Throws(ConfigurationException::class)
    override fun apply() {
        settingsPanel?.apply()
    }

    override fun reset() {
        settingsPanel?.reset()
    }

    override fun disposeUIResources() {
        settingsPanel = null
    }

    override fun getPreferredFocusedComponent(): JComponent? = settingsPanel?.preferredFocusedComponent()

    companion object {
        const val ID = "com.laowang.idea.immersive.settings"
    }
}
