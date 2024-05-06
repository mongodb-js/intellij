package com.mongodb.jbplugin.observability.probe

import com.intellij.openapi.application.PermanentInstallationID
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.mongodb.jbplugin.observability.LogMessage
import com.mongodb.jbplugin.observability.TelemetryEvent
import com.mongodb.jbplugin.observability.TelemetryService

val logger: Logger = logger<PluginActivatedProbe>()

/**
 * @param telemetry The telemetry service to be used.
 * @param logMessage
 */
@Service
class PluginActivatedProbe internal constructor(
    private val telemetry: TelemetryService?,
    private val logMessage: LogMessage?
) {
    @Suppress("unused")
    constructor() : this(null, null)

    fun pluginActivated() {
        val userId = PermanentInstallationID.get()
        telemetry?.sendEvent(
            TelemetryEvent.PluginActivated(userId)
        )

        logger.info(
            logMessage?.message("Plugin activated.")
                ?.build()
        )
    }
}
