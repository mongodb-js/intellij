package com.mongodb.jbplugin.observability.probe

import com.intellij.openapi.application.Application
import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialect
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.mockLogMessage
import com.mongodb.jbplugin.fixtures.withMockedService
import com.mongodb.jbplugin.observability.TelemetryEvent
import com.mongodb.jbplugin.observability.TelemetryService
import org.bouncycastle.util.test.SimpleTest.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

import kotlinx.coroutines.test.*

@IntegrationTest
class AutocompleteSuggestionAcceptedProbeTest {
    @Test
    fun `should aggregate multiple events and send them once`(application: Application) =
        runTest {
            val telemetryService = mock<TelemetryService>()

            application
                .withMockedService(telemetryService)
                .withMockedService(mockLogMessage())

            val probe = AutocompleteSuggestionAcceptedProbe(this)
            (1..2).forEach { probe.collectionCompletionAccepted(JavaDriverDialect) }
            (1..5).forEach { probe.databaseCompletionAccepted(JavaDriverDialect) }
            (1..15).forEach { probe.fieldCompletionAccepted(JavaDriverDialect) }

            probe.sendEvents()

            verify(telemetryService).sendEvent(
                TelemetryEvent.AutocompleteGroupEvent(JavaDriverDialect, "database", 5),
            )

            verify(telemetryService).sendEvent(
                TelemetryEvent.AutocompleteGroupEvent(JavaDriverDialect, "collection", 2),
            )

            verify(telemetryService).sendEvent(
                TelemetryEvent.AutocompleteGroupEvent(JavaDriverDialect, "field", 15),
            )

            probe.appWillBeClosed(false)
        }

    @Test
    fun `do not send anything to telemetry if empty`(application: Application) =
        runTest {
            val telemetryService = mock<TelemetryService>()

            application
                .withMockedService(telemetryService)
                .withMockedService(mockLogMessage())

            val probe = AutocompleteSuggestionAcceptedProbe(this)

            probe.sendEvents()

            verify(telemetryService, never()).sendEvent(any())

            probe.appWillBeClosed(false)
        }
}
