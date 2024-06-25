package com.mongodb.jbplugin.observability

import com.google.gson.Gson
import com.intellij.openapi.application.Application
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.mockRuntimeInformationService
import com.mongodb.jbplugin.fixtures.withMockedService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@IntegrationTest
class LogMessageTest {
    private val gson: Gson = Gson()

    @Test
    fun `should serialize a log message to json`(application: Application) {
        application.withMockedService(mockRuntimeInformationService())

        val message = LogMessage().message("My Message").build()
        val parsedMessage = gson.fromJson<Map<String, Any>>(message, Map::class.java)

        assertEquals("My Message", parsedMessage["message"])
    }

    @Test
    fun `should serialize a log message to json with additional fields`(application: Application) {
        application.withMockedService(mockRuntimeInformationService())

        val message =
            LogMessage()
                .message("My Message")
                .put("jetbrainsId", "someId")
                .build()

        val parsedMessage = gson.fromJson<Map<String, Any>>(message, Map::class.java)

        assertEquals("My Message", parsedMessage["message"])
        assertEquals("someId", parsedMessage["jetbrainsId"])
    }

    @Test
    fun `should merge fields from a data class`(application: Application) {
        application.withMockedService(mockRuntimeInformationService())
        val message =
            LogMessage()
                .message("My Message")
                .mergeTelemetryEventProperties(
                    TelemetryEvent.NewConnection(
                        isAtlas = true,
                        isLocalhost = false,
                        isEnterprise = true,
                        isGenuine = true,
                        nonGenuineServerName = null,
                        serverOsFamily = null,
                        version = null,
                        isLocalAtlas = false,
                    ),
                )
                .build()

        val parsedMessage = gson.fromJson<Map<String, Any>>(message, Map::class.java)

        assertEquals("My Message", parsedMessage["message"])
        assertEquals(true, parsedMessage[TelemetryProperty.IS_ATLAS.publicName])
        assertEquals(false, parsedMessage[TelemetryProperty.IS_LOCALHOST.publicName])
        assertEquals(true, parsedMessage[TelemetryProperty.IS_GENUINE.publicName])
        assertEquals(false, parsedMessage[TelemetryProperty.IS_LOCAL_ATLAS.publicName])
        assertEquals("", parsedMessage[TelemetryProperty.NON_GENUINE_SERVER_NAME.publicName])
        assertEquals("", parsedMessage[TelemetryProperty.SERVER_OS_FAMILY.publicName])
        assertEquals("", parsedMessage[TelemetryProperty.VERSION.publicName])
    }
}
