package com.mongodb.jbplugin.observability.probe

import com.intellij.openapi.application.PermanentInstallationID
import com.mongodb.jbplugin.mockProject
import com.mongodb.jbplugin.observability.TelemetryEvent
import com.mongodb.jbplugin.observability.TelemetryService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

internal class PluginActivatedProbeTest {
    @Test
    fun `should send a PluginActivated event`() {
        val permanentInstallationId = mockStatic(PermanentInstallationID::class.java)
        permanentInstallationId.`when`<String> { PermanentInstallationID.get() }.thenReturn("123456")

        val telemetryService = mock<TelemetryService>()
        val probe = PluginActivatedProbe(mockProject(telemetryService = telemetryService))

        probe.pluginActivated()
        verify(telemetryService).sendEvent(TelemetryEvent.PluginActivated("123456"))
    }
}
