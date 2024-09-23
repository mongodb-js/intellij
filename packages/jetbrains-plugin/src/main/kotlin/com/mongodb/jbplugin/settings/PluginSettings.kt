/**
 * Settings state for the plugin. These classes are responsible for the persistence of the plugin
 * settings.
 */

package com.mongodb.jbplugin.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import java.io.Serializable

/**
 * The state component represents the persisting state. Don't use directly, this is only necessary
 * for the state to be persisted. Use PluginSettings instead.
 *
 * @see PluginSettings
 */
@Service
@State(
    name = "com.mongodb.jbplugin.settings.PluginSettings",
    storages = [Storage(value = "MongoDBPluginSettings.xml")],
)
class PluginSettingsStateComponent : SimplePersistentStateComponent<PluginSettings>(
    PluginSettings()
)

/**
 * The settings themselves. They are tracked, so any change on the settings properties will be eventually
 * persisted by IntelliJ. To access the settings, use the useSettings provider.
 *
 * @see useSettings
 */
class PluginSettings : BaseState(), Serializable {
    var isTelemetryEnabled by property(true)
    var hasTelemetryOptOutputNotificationBeenShown by property(false)
}

/**
 * Function that provides a reference to the current PluginSettings.
 *
 * @see PluginSettings
 *
 * @return
 */
fun useSettings(): PluginSettings =
    ApplicationManager.getApplication().getService(
        PluginSettingsStateComponent::class.java,
    ).state
