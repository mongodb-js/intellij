package com.mongodb.jbplugin.observability

import com.segment.analytics.Analytics
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

internal class TelemetryServiceTest {
    @Test
    fun `sends an identify event when a PluginActivated event is sent`() {
        val service = TelemetryService().apply {
            analytics = mock<Analytics>()
        }

        service.sendEvent(TelemetryEvent.PluginActivated("myUserId"))

        verify(service.analytics).enqueue(
            argThat {
                build().let {
                    it.userId() == "myUserId" &&
                            it.type().name == "identify"
                }
            }
        )
    }
}
