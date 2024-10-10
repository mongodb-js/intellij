/**
 * PersistentToolbarSettings contains declaration of PersistentToolbarSettings service and a helper that helps
 * in retrieving this service from Application
 */

package com.mongodb.jbplugin.editor.services.implementations

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.mongodb.jbplugin.editor.services.ToolbarSettings
import java.io.Serializable

// Setting this service as a PROJECT service ensures that our state is saved per Project
@Service(Service.Level.PROJECT)
@State(
    name = "com.mongodb.jbplugin.editor.ToolbarSettings",
    storages = [
        Storage(
            // File used to persist these settings per project
            value = "MongodbToolbarSettings.xml",
            // Setting roamingType to DEFAULT ensures that these settings are carried forward during IntelliJ updates
            // as well
            roamingType = RoamingType.DEFAULT
        )
    ]
)
internal class ToolbarSettingsStateComponent :
    SimplePersistentStateComponent<PersistentToolbarSettings>(PersistentToolbarSettings())

internal class PersistentToolbarSettings :
    BaseState(),
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
fun Project.getToolbarSettings(): ToolbarSettings = getService(
    ToolbarSettingsStateComponent::class.java
).state
