package com.mongodb.jbplugin.observability.probe

import com.intellij.openapi.application.PermanentInstallationID
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.mongodb.jbplugin.observability.LogMessage
import com.mongodb.jbplugin.observability.TelemetryEvent
import com.mongodb.jbplugin.observability.TelemetryService

private val logger: Logger = logger<PluginActivatedProbe>()

/**
 * @param project
 */
@Service
class PluginActivatedProbe(private val project: Project) {
    fun pluginActivated() {
        val telemetry = project.getService(TelemetryService::class.java)
        val logMessage = project.getService(LogMessage::class.java)

        val userId = PermanentInstallationID.get()
        telemetry.sendEvent(
            TelemetryEvent.PluginActivated(userId)
        )

        logger.info(
            logMessage.message("Plugin activated.")
                .build()
        )
    }
}
