/** Represents the settings that will be configurable by the user in the Settings popup.
 * This shouldn't be used for sensitive data.
 **/

package com.mongodb.jbplugin.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.xmlb.XmlSerializerUtil
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Represents the state of the plugin, it will be persisted across IDE restarts.
 *
 * @see useSettings
 */
@State(
    name = "com.mongodb.jbplugin.settings.MongoDBPluginSettings",
    storages = [Storage("MongoDBPluginSettings.xml")]
)
class MongoDbPluginSettings : PersistentStateComponent<MongoDbPluginSettings> {
    var atlasApiKey = ""

    override fun getState(): MongoDbPluginSettings = this

    override fun loadState(state: MongoDbPluginSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }
}

/**
 * Represents the UI component that will be shown in the Settings popup.
 *
 * @property panel
 * @property atlasApiKeyComponent
 */
data class MongoDbPluginSettingsPanel private constructor(
    val panel: JPanel,
    val atlasApiKeyComponent: JBTextField
) {
    var atlasApiKey: String
        get() = atlasApiKeyComponent.text
        set(newText) {
            atlasApiKeyComponent.text = newText
        }

    companion object {
        fun newInstance(): MongoDbPluginSettingsPanel {
            val atlasApiKeyComponent = JBTextField()
            val panel = FormBuilder.createFormBuilder()
                .addLabeledComponent(JBLabel("Atlas API Key: "), atlasApiKeyComponent, 1, false)
                .addComponent(atlasApiKeyComponent, 1)
                .addComponentFillVertically(JPanel(), 0)
                .panel

            return MongoDbPluginSettingsPanel(panel, atlasApiKeyComponent)
        }
    }
}

/**
 * This class binds the plugin state and the panel component. Should be updated
 * when new settings are added.
 *
 * @see isModified
 * @see apply
 * @see reset
 */
internal class MongoDbSettingsConfigurable : Configurable {
    private val mongodbSettingsComponent = MongoDbPluginSettingsPanel.newInstance()

    override fun createComponent(): JComponent = mongodbSettingsComponent.panel

    override fun isModified(): Boolean {
        val settings = useSettings()
        val modified: Boolean = mongodbSettingsComponent.atlasApiKey != settings.atlasApiKey
        return modified
    }

    override fun apply() {
        useSettings().apply {
            atlasApiKey = mongodbSettingsComponent.atlasApiKey
        }
    }

    override fun reset() {
        val settings = useSettings()
        mongodbSettingsComponent.atlasApiKey = settings.atlasApiKey
    }

    override fun getDisplayName(): String = "MongoDB Plugin Settings"
}

/**
 * Returns the current settings. It's thread-safe.
 *
 * @return
 */
fun useSettings(): MongoDbPluginSettings = ApplicationManager.getApplication().getService(
    MongoDbPluginSettings::class.java
)