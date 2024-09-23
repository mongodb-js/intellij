package com.mongodb.jbplugin.codeActions.impl

import com.intellij.remoterobot.RemoteRobot
import com.mongodb.jbplugin.fixtures.*
import com.mongodb.jbplugin.fixtures.components.findJavaEditorToolbar
import com.mongodb.jbplugin.fixtures.components.findJavaEditorToolbarPopup
import com.mongodb.jbplugin.fixtures.components.findRunQueryGutter
import com.mongodb.jbplugin.fixtures.components.idea.ideaFrame
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration

@UiTest
@RequiresMongoDbCluster
class MongoDbRunQueryActionUiTest {
    @BeforeEach
    fun setUp(
        remoteRobot: RemoteRobot,
        url: MongoDbServerUrl,
    ) {
        remoteRobot.ideaFrame().addDataSourceWithUrl(javaClass.simpleName, url)
    }

    @AfterEach
    fun tearDown(remoteRobot: RemoteRobot) {
        remoteRobot.ideaFrame().cleanDataSources()
    }

    @Test
    @RequiresProject("basic-java-project-with-mongodb", smartMode = true)
    fun `allows clicking on the gutter of a file and canceling`(remoteRobot: RemoteRobot) {
        remoteRobot.ideaFrame().openFile("/src/main/java/alt/mongodb/javadriver/JavaDriverRepository.java")
        remoteRobot.findRunQueryGutter(atLine = 24)!!.click()
        // because we are disconnected, we should now try to connect
        val popup = remoteRobot.findJavaEditorToolbarPopup()
        popup.cancel()
    }

    @Test
    @RequiresProject("basic-java-project-with-mongodb", smartMode = true)
    fun `opens the popup, connects and opens the datagrip console`(remoteRobot: RemoteRobot) {
        remoteRobot.ideaFrame().openFile("/src/main/java/alt/mongodb/javadriver/JavaDriverRepository.java")
        remoteRobot.findJavaEditorToolbar().detachDataSource()
        remoteRobot.findRunQueryGutter(atLine = 24)!!.click()
        // because we are disconnected, we should now try to connect
        val popup = remoteRobot.findJavaEditorToolbarPopup()
        popup.dataSources.selectItem(
            javaClass.simpleName
        )
        popup.ok()
        // now we will see a notification balloon
        remoteRobot.ideaFrame().ensureNotificationIsVisible("Opening connection to ${javaClass.simpleName}")
        // wait until the balloon is gone
        remoteRobot.ideaFrame().waitUntilNotificationIsGone(
            title = "Opening connection to ${javaClass.simpleName}",
            timeout = Duration.ofMinutes(1)
        )
        // check that we open a console
        eventually {
            val currentEditor = remoteRobot.ideaFrame().currentTab()
            assertTrue(currentEditor.editor.fileName.startsWith("console"))
        }
    }

    @Test
    @RequiresProject("basic-java-project-with-mongodb", smartMode = true)
    fun `waits until the connection is successful if there is an attached datasource`(remoteRobot: RemoteRobot) {
        remoteRobot.ideaFrame().openFile("/src/main/java/alt/mongodb/javadriver/JavaDriverRepository.java")
        remoteRobot.findJavaEditorToolbar().detachDataSource()
        remoteRobot.findJavaEditorToolbar().dataSources.selectItem(javaClass.simpleName)
        remoteRobot.ideaFrame().waitUntilConnectedToMongoDb(javaClass.simpleName)
        // check that we open a console
        eventually(Duration.ofMinutes(1), recovery = remoteRobot::closeAllOpenModals) {
            remoteRobot.findRunQueryGutter(atLine = 24)!!.click()
            val currentEditor = remoteRobot.ideaFrame().currentTab()
            assertTrue(currentEditor.editor.fileName.startsWith("console"))
        }
    }
}