package com.mongodb.jbplugin.meta

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BuildInformationTest {
    @Test
    fun `loads all build information from the resource file`() {
        assertEquals("0.0.2-madeUp", BuildInformation.pluginVersion)
        assertEquals("4.0.0-madeUp", BuildInformation.driverVersion)
        assertEquals("madeUp", BuildInformation.segmentApiKey)
    }
}
