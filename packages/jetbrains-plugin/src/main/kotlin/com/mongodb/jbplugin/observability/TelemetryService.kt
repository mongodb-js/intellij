package com.mongodb.jbplugin.observability

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.mongodb.jbplugin.meta.BuildInformation
import com.mongodb.jbplugin.settings.useSettings
import com.segment.analytics.Analytics
import com.segment.analytics.messages.IdentifyMessage
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
        if (!useSettings().isTelemetryEnabled) {
            return
        }

        val runtimeInformationService =
            ApplicationManager.getApplication().getService(
                RuntimeInformationService::class.java,
            )
        val runtimeInfo = runtimeInformationService.get()

        val message =
            when (event) {
                is TelemetryEvent.PluginActivated -> IdentifyMessage.builder()
                    .anonymousId(runtimeInfo.userId)
                    .userId("")
                else ->
                    TrackMessage.builder(event.name).anonymousId(runtimeInfo.userId)
                        .properties(
                            event.properties.entries.associate {
                                it.key.publicName to it.value
                            },
                        )
            }

        analytics.enqueue(message)
    }

    override fun appWillBeClosed(isRestart: Boolean) {
        val telemetryEnabled = useSettings().isTelemetryEnabled
        val logMessage = ApplicationManager.getApplication().getService(LogMessage::class.java)
        logger.info(
            logMessage.message("Shutting down Segment analytics because the IDE is closing.")
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
