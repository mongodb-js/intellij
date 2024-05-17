package com.mongodb.jbplugin.meta

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class BuildInformationTest {
    @Test
    fun `loads all build information from the resource file`() {
        assertNotNull(BuildInformation.pluginVersion)
        assertEquals("<none>", BuildInformation.segmentApiKey)
    }
}
