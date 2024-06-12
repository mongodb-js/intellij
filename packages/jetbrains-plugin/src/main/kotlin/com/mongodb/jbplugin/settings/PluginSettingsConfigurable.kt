/**
 * These classes represent the settings modal.
 */

package com.mongodb.jbplugin.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.FormBuilder
import com.mongodb.jbplugin.i18n.SettingsMessages
import com.mongodb.jbplugin.i18n.TelemetryMessages
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * This class represents a section in the settings modal. The UI will be implemented by
 * PluginSettingsComponent.
 */
class PluginSettingsConfigurable : Configurable {
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
        val savedSettings =
            useSettings().apply {
                isTelemetryEnabled = settingsComponent.isTelemetryEnabledCheckBox.isSelected
            }
    }

    override fun reset() {
        val savedSettings = useSettings()
        settingsComponent.isTelemetryEnabledCheckBox.isSelected = savedSettings.isTelemetryEnabled
    }

    override fun getDisplayName() = SettingsMessages.message("settings.display-name")
}

/**
 * The panel that is shown in the settings section for MongoDB.
 */
private class PluginSettingsComponent {
    val root: JPanel
    val isTelemetryEnabledCheckBox = JBCheckBox(TelemetryMessages.message("settings.telemetry-collection-checkbox"))

    init {
        root =
            FormBuilder.createFormBuilder()
                .addComponent(isTelemetryEnabledCheckBox)
                .addTooltip(TelemetryMessages.message("settings.telemetry-collection-tooltip"))
                .addComponentFillVertically(JPanel(), 0)
                .panel

        root.accessibleContext.accessibleName = "MongoDB Settings"
        isTelemetryEnabledCheckBox.accessibleContext.accessibleName = "MongoDB Enable Telemetry"
    }
}
