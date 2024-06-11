package com.mongodb.jbplugin.observability.probe

import com.intellij.openapi.application.Application
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.mockLogMessage
import com.mongodb.jbplugin.fixtures.withMockedService
import com.mongodb.jbplugin.observability.TelemetryEvent
import com.mongodb.jbplugin.observability.TelemetryService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@IntegrationTest
internal class PluginActivatedProbeTest {
    @Test
    fun `should send a PluginActivated event`(application: Application) {
        val telemetryService = mock<TelemetryService>()

        application.withMockedService(telemetryService)
            .withMockedService(mockLogMessage())

        val probe = PluginActivatedProbe()

        probe.pluginActivated()
        verify(telemetryService).sendEvent(TelemetryEvent.PluginActivated)
    }
}
