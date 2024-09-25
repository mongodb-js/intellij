package com.mongodb.jbplugin.i18n

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SettingsMessagesTest {
    @Test
    fun `loads messages from the resource bundle`() {
        val messages = SettingsMessages.message("settings.display-name")
        assertEquals("MongoDB", messages)
    }
}
