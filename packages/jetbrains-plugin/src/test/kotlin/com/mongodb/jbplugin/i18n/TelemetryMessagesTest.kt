package com.mongodb.jbplugin.i18n

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TelemetryMessagesTest {
    @Test
    fun `loads messages from the resource bundle`() {
        val messages = TelemetryMessages.message("notification.group.name")
        assertEquals("MongoDB telemetry", messages)
    }
}
