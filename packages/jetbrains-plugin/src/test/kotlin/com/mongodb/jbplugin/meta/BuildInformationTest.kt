package com.mongodb.jbplugin.meta

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BuildInformationTest {
    @Test
    fun `loads all build information from the resource file`() {
        assertEquals("4.11.0", BuildInformation.driverVersion)
        assertEquals("<none>", BuildInformation.segmentApiKey)
    }
}
