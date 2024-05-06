package com.mongodb.jbplugin.observability

import com.intellij.database.util.common.meets
import com.segment.analytics.Analytics
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

internal class TelemetryServiceTest {
    @Test
    fun `sends an identify event when a PluginActivated event is sent`() {
        val analytics = mock<Analytics>()
        val service = TelemetryService(analytics)

        service.sendEvent(TelemetryEvent.PluginActivated("myUserId"))

        verify(analytics).enqueue(
            argThat {
                build().meets {
                    it.userId() == "myUserId" &&
                            it.type().name == "identify"
                }
            }
        )
    }
}
