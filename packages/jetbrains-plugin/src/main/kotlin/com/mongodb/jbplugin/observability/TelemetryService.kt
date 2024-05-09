package com.mongodb.jbplugin.observability

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.segment.analytics.Analytics
import com.segment.analytics.messages.IdentifyMessage
import com.segment.analytics.messages.TrackMessage

/**
 * This telemetry service is used to send events to Segment. Should be used within
 * probes, no directly. That is why it's marked as internal.
 *
 * @param project
 */
@Service(Service.Level.PROJECT)
internal class TelemetryService(private val project: Project) {
    internal var analytics: Analytics = Analytics.builder("KEY").build()

    fun sendEvent(event: TelemetryEvent) {
        val runtimeInformationService = project.getService(RuntimeInformationService::class.java)
        val runtimeInfo = runtimeInformationService.get()

        val message = when (event) {
            is TelemetryEvent.PluginActivated -> IdentifyMessage.builder().userId(runtimeInfo.userId)
            else ->
                TrackMessage.builder(event.name).userId(runtimeInfo.userId)
                    .properties(event.properties.entries.associate {
                        it.key.publicName to it.value
                    })

        }

        analytics.enqueue(message)
    }
}
