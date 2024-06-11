package com.mongodb.jbplugin.observability.probe

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.mongodb.jbplugin.observability.LogMessage
import com.mongodb.jbplugin.observability.TelemetryEvent
import com.mongodb.jbplugin.observability.TelemetryService

private val logger: Logger = logger<PluginActivatedProbe>()

/**
 * This probe is emitted when the plugin is activated (started).
 */
@Service
class PluginActivatedProbe {
    fun pluginActivated() {
        val application = ApplicationManager.getApplication()
        val telemetry = application.getService(TelemetryService::class.java)
        val logMessage = application.getService(LogMessage::class.java)

        telemetry.sendEvent(TelemetryEvent.PluginActivated)

        logger.info(
            logMessage.message("Plugin activated.")
                .build()
        )
    }
}
