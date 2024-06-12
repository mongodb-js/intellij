package com.mongodb.jbplugin.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*

@Service
@State(
    name = "com.mongodb.jbplugin.settings.PluginSettings",
    storages = [ Storage(value = "MongoDBPluginSettings.xml") ],
)
class PluginSettings : SimplePersistentStateComponent<PluginSettingsState>(PluginSettingsState())

class PluginSettingsState : BaseState() {
    var isTelemetryEnabled by property(true)
    var hasTelemetryOptOutputNotificationBeenShown by property(false)
}

fun useSettings(): PluginSettingsState {
    return ApplicationManager.getApplication().getService(PluginSettings::class.java).state
}