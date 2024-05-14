package com.mongodb.jbplugin

import com.mongodb.jbplugin.fixtures.eventually
import com.mongodb.jbplugin.fixtures.mockProject
import com.mongodb.jbplugin.observability.probe.PluginActivatedProbe
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.verify

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class SayHiListenerTest {
    @Test
    fun `emits a plugin activated probe`() = runBlocking {
        val pluginActivatedProbe = mock<PluginActivatedProbe>()
        val project = mockProject(pluginActivatedProbe = pluginActivatedProbe)
        val listener = SayHiListener(CoroutineScope(Dispatchers.Default))

        listener.runActivity(project)

        eventually {
            verify(pluginActivatedProbe).pluginActivated()
        }
    }
}
