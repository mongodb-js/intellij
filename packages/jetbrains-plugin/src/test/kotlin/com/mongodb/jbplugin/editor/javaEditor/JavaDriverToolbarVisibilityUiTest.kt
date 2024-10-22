package com.mongodb.jbplugin.editor.javaEditor

import com.intellij.remoterobot.RemoteRobot
import com.mongodb.jbplugin.fixtures.*
import com.mongodb.jbplugin.fixtures.components.findJavaEditorToolbar
import com.mongodb.jbplugin.fixtures.components.idea.ideaFrame
import com.mongodb.jbplugin.fixtures.components.isJavaEditorToolbarHidden
import com.mongodb.jbplugin.fixtures.components.openDatabaseToolWindow
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

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
        remoteRobot.openDatabaseToolWindow().removeAllDataSources()
    }

    @Test
    @RequiresProject("basic-java-project-with-mongodb", smartMode = true)
    fun `shows the toolbar in a java file with references to the driver`(remoteRobot: RemoteRobot) {
        remoteRobot.ideaFrame().openFile(
            "/src/main/java/alt/mongodb/javadriver/JavaDriverRepository.java"
        )
        val toolbar = remoteRobot.findJavaEditorToolbar()
        assertTrue(toolbar.isShowing)
    }

    @Test
    @RequiresProject("basic-java-project-with-mongodb", smartMode = true)
    fun `shows the toolbar in all the java files with references to the driver`(
        remoteRobot: RemoteRobot
    ) {
        remoteRobot.ideaFrame().openFile(
            path = "/src/main/java/alt/mongodb/javadriver/JavaDriverRepository.java"
        )
        assertTrue(remoteRobot.findJavaEditorToolbar().isShowing)

        remoteRobot.ideaFrame().openFile(
            path = "/src/main/java/alt/mongodb/javadriver/JavaDriverRepositoryClone.java",
            closeOpenedFiles = false
        )
        assertTrue(remoteRobot.findJavaEditorToolbar().isShowing)

        remoteRobot.ideaFrame().openFile(
            path = "/src/main/java/alt/mongodb/javadriver/JavaDriverRepository.java",
            closeOpenedFiles = false
        )
        assertTrue(remoteRobot.findJavaEditorToolbar().isShowing)
    }

    @Test
    @RequiresProject("basic-java-project-with-mongodb", smartMode = true)
    fun `does not show the toolbar in a java file without references to the driver`(
        remoteRobot: RemoteRobot
    ) {
        remoteRobot.ideaFrame().openFile(
            "/src/main/java/alt/mongodb/javadriver/NoDriverReference.java"
        )
        assertTrue(remoteRobot.isJavaEditorToolbarHidden())
    }

    @Test
    @RequiresProject("basic-java-project-with-mongodb", smartMode = true)
    fun `does show existing data sources in the combo box`(
        remoteRobot: RemoteRobot,
        url: MongoDbServerUrl,
    ) {
        remoteRobot.ideaFrame().openFile(
            "/src/main/java/alt/mongodb/javadriver/JavaDriverRepository.java"
        )

        val toolbar = remoteRobot.findJavaEditorToolbar()
        assertTrue(toolbar.dataSources.listValues().contains(javaClass.simpleName))
    }

    @Test
    @RequiresProject("basic-java-project-with-mongodb", smartMode = true)
    fun `does not show the database select on a java driver file`(
        remoteRobot: RemoteRobot,
        url: MongoDbServerUrl,
    ) {
        remoteRobot.ideaFrame().openFile(
            "/src/main/java/alt/mongodb/javadriver/JavaDriverRepository.java"
        )

        val toolbar = remoteRobot.findJavaEditorToolbar()
        assertFalse(toolbar.hasDatabasesComboBox)
    }

    @Test
    @RequiresProject("basic-java-project-with-mongodb", smartMode = true)
    fun `does show the database select on a spring criteria file`(
        remoteRobot: RemoteRobot,
        url: MongoDbServerUrl,
    ) {
        remoteRobot.ideaFrame().openFile(
            "/src/main/java/alt/mongodb/springcriteria/SpringCriteriaRepository.java"
        )

        val toolbar = remoteRobot.findJavaEditorToolbar()
        assertTrue(toolbar.hasDatabasesComboBox)

        toolbar.selectDataSource(javaClass.simpleName)

        eventually(1.minutes.toJavaDuration()) {
            // it can take a few seconds, we will retry every few milliseconds
            // but wait at least for a minute if we can't select a database
            toolbar.selectDatabase("admin")
        }
    }

    @Test
    @RequiresProject("basic-java-project-with-mongodb", smartMode = true)
    fun `shows the toolbar when a reference to the driver is added`(
        remoteRobot: RemoteRobot,
        url: MongoDbServerUrl,
    ) {
        remoteRobot.ideaFrame().openFile(
            "/src/main/java/alt/mongodb/javadriver/NoDriverReference.java"
        )
        assertTrue(remoteRobot.isJavaEditorToolbarHidden())
        val editor = remoteRobot.ideaFrame().currentTab().editor
        val textBeforeChanges = editor.text

        editor.insertTextAtLine(1, 0, "import com.mongodb.client.MongoClient;")
        assertTrue(remoteRobot.findJavaEditorToolbar().isShowing)
        editor.replaceText("import com.mongodb.client.MongoClient;", "")
        assertTrue(remoteRobot.isJavaEditorToolbarHidden())
    }
}
