package com.mongodb.jbplugin

import com.intellij.remoterobot.RemoteRobot
import com.mongodb.jbplugin.fixtures.RequiresProject
import com.mongodb.jbplugin.fixtures.UiTest
import com.mongodb.jbplugin.fixtures.components.SayHelloMessageBoxFixture
import com.mongodb.jbplugin.fixtures.findVisible
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@UiTest
class ActivatePluginPostStartupActivityUiTest {
    @Test
    @RequiresProject("basic-java-project-with-mongodb")
    fun `shows a notification message`(remoteRobot: RemoteRobot) {
        val sayHelloMessageBox = remoteRobot.findVisible<SayHelloMessageBoxFixture>()

        assertEquals("Build Info", sayHelloMessageBox.title)
        sayHelloMessageBox.ok()
    }
}
