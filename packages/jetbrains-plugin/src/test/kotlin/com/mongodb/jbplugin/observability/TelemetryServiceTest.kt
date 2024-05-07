package com.mongodb.jbplugin.observability

import com.mongodb.jbplugin.mockProject
import com.mongodb.jbplugin.mockRuntimeInformationService
import com.segment.analytics.Analytics
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

internal class TelemetryServiceTest {
    @Test
    fun `sends an identify event when a PluginActivated event is sent`() {
        val mockRuntimeInfo = mockRuntimeInformationService(userId = "654321")
        val service = TelemetryService(mockProject(
            runtimeInformationService = mockRuntimeInfo
        )).apply {
            analytics = mock<Analytics>()
        }

        service.sendEvent(TelemetryEvent.PluginActivated)

        verify(service.analytics).enqueue(
            argThat {
                build().let {
                    it.userId() == "654321" &&
                            it.type().name == "identify"
                }
            }
        )
    }

    @Test
    fun `sends a new connection event as a tracking event`() {
        val mockRuntimeInfo = mockRuntimeInformationService(userId = "654321")
        val service = TelemetryService(mockProject(
            runtimeInformationService = mockRuntimeInfo
        )).apply {
            analytics = mock<Analytics>()
        }

        service.sendEvent(TelemetryEvent.NewConnection(
            isAtlas = true,
            isLocalhost = false,
            isEnterprise = true,
            isGenuine = true,
            nonGenuineServerName = null,
            serverOsFamily = null,
            version = "8.0"
        ))

        verify(service.analytics).enqueue(
            argThat {
                build().let {
                    it.userId() == "654321" &&
                            it.type().name == "track"
                }
            }
        )
    }
}
