package com.mongodb.jbplugin.editor.javaEditor

import com.intellij.remoterobot.RemoteRobot
import com.mongodb.jbplugin.fixtures.MongoDbServerUrl
import com.mongodb.jbplugin.fixtures.RequiresMongoDbCluster
import com.mongodb.jbplugin.fixtures.RequiresProject
import com.mongodb.jbplugin.fixtures.UiTest
import com.mongodb.jbplugin.fixtures.components.findJavaEditorToolbar
import com.mongodb.jbplugin.fixtures.components.idea.ideaFrame
import com.mongodb.jbplugin.fixtures.components.isJavaEditorToolbarHidden
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@UiTest
@RequiresMongoDbCluster
class JavaDriverToolbarVisibilityUiTest {
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
    @RequiresProject("basic-java-project-with-mongodb")
    fun `shows the toolbar in a java file with references to the driver`(remoteRobot: RemoteRobot) {
        remoteRobot.ideaFrame().openFile("/src/main/java/alt/mongodb/javadriver/JavaDriverRepository.java")
        val toolbar = remoteRobot.findJavaEditorToolbar()
        assertTrue(toolbar.isShowing)
    }

    @Test
    @RequiresProject("basic-java-project-with-mongodb")
    fun `does not show the toolbar in a java file without references to the driver`(remoteRobot: RemoteRobot) {
        remoteRobot.ideaFrame().openFile("/src/main/java/alt/mongodb/javadriver/NoDriverReference.java")
        assertTrue(remoteRobot.isJavaEditorToolbarHidden())
    }

    @Test
    @RequiresProject("basic-java-project-with-mongodb")
    fun `does show existing data sources in the combo box`(
        remoteRobot: RemoteRobot,
        url: MongoDbServerUrl,
    ) {
        remoteRobot.ideaFrame().openFile("/src/main/java/alt/mongodb/javadriver/JavaDriverRepository.java")

        val toolbar = remoteRobot.findJavaEditorToolbar()
        assertTrue(toolbar.dataSources.listValues().contains(javaClass.simpleName))
    }

    @Test
    @RequiresProject("basic-java-project-with-mongodb")
    fun `does remove all deleted data sources`(
        remoteRobot: RemoteRobot,
        url: MongoDbServerUrl,
    ) {
        remoteRobot.ideaFrame().openFile("/src/main/java/alt/mongodb/javadriver/JavaDriverRepository.java")

        val toolbar = remoteRobot.findJavaEditorToolbar()
        assertTrue(toolbar.dataSources.listValues().contains(javaClass.simpleName))

        remoteRobot.ideaFrame().cleanDataSources()
        assertFalse(toolbar.dataSources.listValues().contains(javaClass.simpleName))
    }

    @Test
    @RequiresProject("basic-java-project-with-mongodb")
    fun `shows the toolbar when a reference to the driver is added`(
        remoteRobot: RemoteRobot,
        url: MongoDbServerUrl,
    ) {
        assertTrue(remoteRobot.isJavaEditorToolbarHidden())

        remoteRobot.ideaFrame().openFile("/src/main/java/alt/mongodb/javadriver/NoDriverReference.java")
        val editor = remoteRobot.ideaFrame().currentEditor()
        val textBeforeChanges = editor.text

        editor.insertTextAtLine(1, 0, "import com.mongodb.client.MongoClient;")

        remoteRobot.findJavaEditorToolbar()
        editor.text = textBeforeChanges.replace("\n", "\\\n")
    }
}
