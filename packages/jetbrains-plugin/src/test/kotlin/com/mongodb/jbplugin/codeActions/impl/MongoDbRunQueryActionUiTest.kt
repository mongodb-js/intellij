package com.mongodb.jbplugin.codeActions.impl

import com.intellij.remoterobot.RemoteRobot
import com.mongodb.jbplugin.fixtures.*
import com.mongodb.jbplugin.fixtures.components.idea.ideaFrame
import com.mongodb.jbplugin.fixtures.components.openRunQueryPopup
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

@UiTest
@RequiresMongoDbCluster
class MongoDbRunQueryActionUiTest {
    @BeforeEach
    fun setUp(
        remoteRobot: RemoteRobot,
        url: MongoDbServerUrl,
    ) {
        remoteRobot.ideaFrame().cleanDataSources()
        remoteRobot.ideaFrame().addDataSourceWithUrl(javaClass.simpleName, url)
    }

    @AfterEach
    fun tearDown(remoteRobot: RemoteRobot) {
        remoteRobot.ideaFrame().cleanDataSources()
    }

    @Test
    @RequiresProject("basic-java-project-with-mongodb", smartMode = true)
    fun `allows clicking on the gutter of a file and canceling`(remoteRobot: RemoteRobot) {
        remoteRobot.ideaFrame().openFile(
            "/src/main/java/alt/mongodb/javadriver/JavaDriverRepository.java"
        )
        val popup = remoteRobot.openRunQueryPopup(atLine = 24)
        popup.cancel()
    }

    @Test
    @RequiresProject("basic-java-project-with-mongodb", smartMode = true)
    fun `opens the popup, connects and opens the datagrip console`(remoteRobot: RemoteRobot) {
        remoteRobot.ideaFrame().openFile(
            "/src/main/java/alt/mongodb/javadriver/JavaDriverRepository.java"
        )
        val popup = remoteRobot.openRunQueryPopup(atLine = 24)
        popup.selectDataSource(javaClass.simpleName)
        popup.ok("Run Query", timeout = 1.minutes)
        // check that we open a console
        eventually(30.seconds.toJavaDuration()) {
            val currentEditor = remoteRobot.ideaFrame().currentTab()
            assertTrue(currentEditor.editor.fileName.startsWith("console"))
        }
    }
}
