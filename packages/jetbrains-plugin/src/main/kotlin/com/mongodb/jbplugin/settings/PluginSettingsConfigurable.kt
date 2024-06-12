package com.mongodb.jbplugin.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class PluginSettingsConfigurable: Configurable {
    private lateinit var settingsComponent: PluginSettingsComponent

    override fun createComponent(): JComponent {
        settingsComponent = PluginSettingsComponent()
        return settingsComponent.root
    }

    override fun isModified(): Boolean {
        val savedSettings = useSettings()
        return settingsComponent.isTelemetryEnabledCheckBox.isSelected != savedSettings.isTelemetryEnabled
    }

    override fun apply() {
        val savedSettings = useSettings()
        savedSettings.isTelemetryEnabled = settingsComponent.isTelemetryEnabledCheckBox.isSelected
    }

    override fun reset() {
        val savedSettings = useSettings()
        settingsComponent.isTelemetryEnabledCheckBox.isSelected = savedSettings.isTelemetryEnabled
    }

    override fun getDisplayName() = "MongoDB"
}

class PluginSettingsComponent {
    internal val root: JPanel
    internal val isTelemetryEnabledCheckBox = JBCheckBox("Enable telemetry")

    init {
        root = FormBuilder.createFormBuilder()
            .addComponent(isTelemetryEnabledCheckBox)
            .addTooltip("Allow the collection of anonymous diagnostics and usage telemetry data to help improve the product.")
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }
}