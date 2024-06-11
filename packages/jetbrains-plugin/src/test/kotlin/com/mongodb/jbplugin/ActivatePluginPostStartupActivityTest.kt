package com.mongodb.jbplugin

import com.intellij.openapi.project.Project
import com.mongodb.jbplugin.fixtures.IntegrationTest
import com.mongodb.jbplugin.fixtures.eventually
import com.mongodb.jbplugin.fixtures.withMockedService
import com.mongodb.jbplugin.observability.probe.PluginActivatedProbe
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.verify

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

@IntegrationTest
class ActivatePluginPostStartupActivityTest {
    @Test
    fun `emits a plugin activated probe`(project: Project) = runBlocking {
        val pluginActivatedProbe = mock<PluginActivatedProbe>()
        project.withMockedService(pluginActivatedProbe)

        val listener = ActivatePluginPostStartupActivity(CoroutineScope(Dispatchers.Default))

        listener.runActivity(project)

        eventually {
            verify(pluginActivatedProbe).pluginActivated()
        }
    }
}
