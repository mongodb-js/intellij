package com.mongodb.jbplugin.observability

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.Application
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.mockLogMessage
import com.mongodb.jbplugin.fixtures.mockRuntimeInformationService
import com.mongodb.jbplugin.fixtures.withMockedService
import com.mongodb.jbplugin.meta.BuildInformation
import com.mongodb.jbplugin.settings.PluginSettings
import com.segment.analytics.Analytics
import com.segment.analytics.messages.TrackMessage
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

@IntegrationTest
internal class TelemetryServiceTest {
    @Test
    fun `sends an identify event when a PluginActivated event is sent`(application: Application) {
        application.withMockedService(mockRuntimeInformationService(userId = "654321"))

        val service =
            TelemetryService().apply {
                analytics = mock<Analytics>()
            }

        application.withMockedService(service)
        service.sendEvent(TelemetryEvent.PluginActivated)
        verify(service.analytics).enqueue(
            argThat {
                build().let {
                    it.anonymousId() == "654321" &&
                        it.type().name == "track" &&
                        (it as TrackMessage).properties()?.get("plugin_version") ==
                        BuildInformation.pluginVersion
                }
            },
        )
    }

    @Test
    fun `sends a new connection event as a tracking event`(application: Application) {
        application.withMockedService(mockRuntimeInformationService(userId = "654321"))

        val service =
            TelemetryService().apply {
                analytics = mock<Analytics>()
            }

        application.withMockedService(service)
        service.sendEvent(
            TelemetryEvent.NewConnection(
                isAtlas = true,
                isLocalhost = false,
                isEnterprise = true,
                isGenuine = true,
                nonGenuineServerName = null,
                serverOsFamily = null,
                isLocalAtlas = false,
                atlasHost = "example-cluster.mongodb.net",
                version = "8.0",
            ),
        )

        verify(service.analytics).enqueue(
            argThat {
                build().let {
                    it.anonymousId() == "654321" &&
                        it.type().name == "track"
                }
            },
        )
    }

    @Test
    fun `flushes and shutdowns the segment client when the ide is closing`(
        application: Application
    ) {
        application.withMockedService(mockRuntimeInformationService(userId = "654321"))
        application.withMockedService(mockLogMessage())

        val service =
            TelemetryService().apply {
                analytics = mock<Analytics>()
            }

        application.withMockedService(service)

        val publisher = application.messageBus.syncPublisher(AppLifecycleListener.TOPIC)
        publisher.appWillBeClosed(false)

        verify(service.analytics).flush()
        verify(service.analytics).shutdown()
    }

    @Test
    fun `does not send telemetry events when telemetry is disabled`(
        application: Application,
        settings: PluginSettings,
    ) {
        settings.isTelemetryEnabled = false

        val service =
            TelemetryService().apply {
                analytics = mock<Analytics>()
            }

        application.withMockedService(service)
        service.sendEvent(TelemetryEvent.PluginActivated)

        verify(service.analytics, never()).enqueue(any())
    }

    @Test
    fun `does not flush events, but shuts down, when telemetry is disabled`(
        application: Application,
        settings: PluginSettings,
    ) {
        settings.isTelemetryEnabled = false
        application.withMockedService(mockLogMessage())

        val service =
            TelemetryService().apply {
                analytics = mock<Analytics>()
            }

        application.withMockedService(service)
        val publisher = application.messageBus.syncPublisher(AppLifecycleListener.TOPIC)
        publisher.appWillBeClosed(true)

        verify(service.analytics, never()).flush()
        verify(service.analytics).shutdown()
    }
}
