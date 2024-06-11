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

        val message = LogMessage()
            .message("My Message")
            .put("jetbrainsId", "someId")
            .build()

        val parsedMessage = gson.fromJson<Map<String, Any>>(message, Map::class.java)

        assertEquals("My Message", parsedMessage["message"])
        assertEquals("someId", parsedMessage["jetbrainsId"])
    }
}
