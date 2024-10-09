package com.mongodb.jbplugin.observability

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.mongodb.jbplugin.meta.BuildInformation
import com.mongodb.jbplugin.meta.service
import com.mongodb.jbplugin.settings.pluginSetting
import com.segment.analytics.Analytics
import com.segment.analytics.messages.TrackMessage

private val logger: Logger = logger<TelemetryService>()

/**
 * This telemetry service is used to send events to Segment. Should be used within
 * probes, no directly. That is why it's marked as internal.
 */
@Service
internal class TelemetryService : AppLifecycleListener {
    internal var analytics: Analytics =
        Analytics
            .builder(BuildInformation.segmentApiKey)
            .build()

    init {
        ApplicationManager.getApplication()
            .messageBus
            .connect()
            .subscribe(
                AppLifecycleListener.TOPIC,
                this,
            )
    }

    fun sendEvent(event: TelemetryEvent) {
        val isTelemetryEnabled by pluginSetting { ::isTelemetryEnabled }

        if (!isTelemetryEnabled) {
            return
        }

        val runtimeInformationService by service<RuntimeInformationService>()
        val runtimeInfo = runtimeInformationService.get()

        val message = TrackMessage.builder(event.name)
            .anonymousId(runtimeInfo.userId)
            .properties(
                event.properties.entries.associate {
                    it.key.publicName to it.value
                } + mapOf(
                    TelemetryProperty.PLUGIN_VERSION.publicName to BuildInformation.pluginVersion
                )
            )

        analytics.enqueue(message)
    }

    override fun appWillBeClosed(isRestart: Boolean) {
        val telemetryEnabled by pluginSetting { ::isTelemetryEnabled }

        logger.info(
            useLogMessage("Shutting down Segment analytics because the IDE is closing.")
                .put("isRestart", isRestart)
                .put("telemetryEnabled", telemetryEnabled)
                .build(),
        )

        if (telemetryEnabled) {
            analytics.flush()
        }

        analytics.shutdown()
    }
}
