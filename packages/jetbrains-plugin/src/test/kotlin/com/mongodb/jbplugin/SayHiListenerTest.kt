package com.mongodb.jbplugin

import com.intellij.openapi.ui.Messages
import com.mongodb.jbplugin.fixtures.mockProject
import com.mongodb.jbplugin.observability.probe.PluginActivatedProbe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.verify

class SayHiListenerTest {
    @Test
    fun `emits a plugin activated probe`() = runBlocking {
        val pluginActivatedProbe = mock<PluginActivatedProbe>()
        val project = mockProject(pluginActivatedProbe = pluginActivatedProbe)
        val listener = SayHiListener()

        mockStatic(Messages::class.java).use {
            listener.execute(project)
        }

        verify(pluginActivatedProbe).pluginActivated()
    }
}
