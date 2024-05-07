package com.mongodb.jbplugin.observability

import com.intellij.openapi.components.Service
import com.segment.analytics.Analytics
import com.segment.analytics.messages.IdentifyMessage
import com.segment.analytics.messages.TrackMessage

/**
 * @param analytics
 */
@Service
internal class TelemetryService(private val analytics: Analytics) {
    @Suppress("unused")
    constructor() : this(Analytics.builder("").build())

    fun sendEvent(event: TelemetryEvent) {
        val message = when (event) {
            is TelemetryEvent.PluginActivated -> IdentifyMessage.builder().userId(event.userId) else ->
                TrackMessage.builder(event.name).userId(event.userId)
                    .properties(event.properties.entries.associate {
                        it.key.publicName to it.value
                    })

        }

        analytics.enqueue(message)
    }
}
