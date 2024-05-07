package com.mongodb.jbplugin.observability.probe

import com.mongodb.jbplugin.mockProject
import com.mongodb.jbplugin.observability.TelemetryEvent
import com.mongodb.jbplugin.observability.TelemetryService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

internal class PluginActivatedProbeTest {
    @Test
    fun `should send a PluginActivated event`() {
        val telemetryService = mock<TelemetryService>()
        val probe = PluginActivatedProbe(mockProject(telemetryService = telemetryService))

        probe.pluginActivated()
        verify(telemetryService).sendEvent(TelemetryEvent.PluginActivated)
    }
}
