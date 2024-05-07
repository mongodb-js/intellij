package com.mongodb.jbplugin.observability.probe

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.mongodb.jbplugin.observability.LogMessage
import com.mongodb.jbplugin.observability.TelemetryEvent
import com.mongodb.jbplugin.observability.TelemetryService

private val logger: Logger = logger<PluginActivatedProbe>()

/**
 * This probe is emitted when the plugin is activated (started).
 *
 * @param project Project where the plugin is set up
 */
@Service(Service.Level.PROJECT)
class PluginActivatedProbe(private val project: Project) {
    fun pluginActivated() {
        val telemetry = project.getService(TelemetryService::class.java)
        val logMessage = project.getService(LogMessage::class.java)

        telemetry.sendEvent(TelemetryEvent.PluginActivated)

        logger.info(
            logMessage.message("Plugin activated.")
                .build()
        )
    }
}
