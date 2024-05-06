package com.mongodb.jbplugin.observability.probe

import com.intellij.openapi.application.PermanentInstallationID
import com.mongodb.jbplugin.observability.LogMessage
import com.mongodb.jbplugin.observability.TelemetryEvent
import com.mongodb.jbplugin.observability.TelemetryService
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

internal class PluginActivatedProbeTest {
    @Test
    fun `should send a PluginActivated event`() {
        val telemetryService = mock<TelemetryService>()
        val probe = PluginActivatedProbe(telemetryService, LogMessage())

        val userId = "1234567"
        Mockito.mockStatic(PermanentInstallationID::class.java).use { scope ->
            scope.`when`<String> { PermanentInstallationID.get() }.thenReturn(userId)

            probe.pluginActivated()
            verify(telemetryService).sendEvent(TelemetryEvent.PluginActivated(userId))
        }
    }
}
