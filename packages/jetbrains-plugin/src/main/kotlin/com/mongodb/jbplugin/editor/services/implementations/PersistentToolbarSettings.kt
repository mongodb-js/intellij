/**
 * PersistentToolbarSettings contains declaration of PersistentToolbarSettings service and a helper that helps
 * in retrieving this service from Application
 */

package com.mongodb.jbplugin.editor.services.implementations

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.mongodb.jbplugin.editor.services.ToolbarSettings
import java.io.Serializable

@Service
@State(
    name = "com.mongodb.jbplugin.editor.ToolbarSettings",
    storages = [Storage(value = "MongodbToolbarSettings.xml")]
)
private class ToolbarSettingsStateComponent :
    SimplePersistentStateComponent<PersistentToolbarSettings>(PersistentToolbarSettings())

private class PersistentToolbarSettings : BaseState(),
    Serializable,
    ToolbarSettings {
    override var dataSourceId by string(null)
    override var database by string(ToolbarSettings.UNINITIALIZED_DATABASE)
}

/**
 * Helper method to retrieve the PersistentToolbarSettings instance from Application
 *
 * @return
 */
fun useToolbarSettings(): ToolbarSettings =
    ApplicationManager.getApplication().getService(
        ToolbarSettingsStateComponent::class.java
    ).state
