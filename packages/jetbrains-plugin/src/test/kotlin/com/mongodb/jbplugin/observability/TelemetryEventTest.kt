package com.mongodb.jbplugin.observability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class TelemetryEventTest {
    @Test
    fun `PluginActivated is mapped correctly`() {
        val pluginActivated = TelemetryEvent.PluginActivated
        assertEquals(mapOf<TelemetryProperty, Any>(), pluginActivated.properties)
        assertEquals("PluginActivated", pluginActivated.name)
    }
}
