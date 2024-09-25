package com.mongodb.jbplugin.editor.javaEditor

import com.intellij.remoterobot.RemoteRobot
import com.mongodb.jbplugin.fixtures.*
import com.mongodb.jbplugin.fixtures.components.findJavaEditorToolbar
import com.mongodb.jbplugin.fixtures.components.idea.ideaFrame
import com.mongodb.jbplugin.fixtures.components.isJavaEditorToolbarHidden
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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
        remoteRobot.ideaFrame().cleanDataSources()
    }

    @Test
    @RequiresProject("basic-java-project-with-mongodb")
    fun `shows the toolbar in a java file with references to the driver`(remoteRobot: RemoteRobot) {
        remoteRobot.ideaFrame().openFile(
            "/src/main/java/alt/mongodb/javadriver/JavaDriverRepository.java"
        )
        val toolbar = remoteRobot.findJavaEditorToolbar()
        assertTrue(toolbar.isShowing)
    }

    @Test
    @RequiresProject("basic-java-project-with-mongodb")
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
    @RequiresProject("basic-java-project-with-mongodb")
    fun `does not show the toolbar in a java file without references to the driver`(
        remoteRobot: RemoteRobot
    ) {
        remoteRobot.ideaFrame().openFile(
            "/src/main/java/alt/mongodb/javadriver/NoDriverReference.java"
        )
        assertTrue(remoteRobot.isJavaEditorToolbarHidden())
    }

    @Test
    @RequiresProject("basic-java-project-with-mongodb")
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
    @RequiresProject("basic-java-project-with-mongodb")
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
    @RequiresProject("basic-java-project-with-mongodb")
    fun `does show the database select on a spring criteria file`(
        remoteRobot: RemoteRobot,
        url: MongoDbServerUrl,
    ) {
        remoteRobot.ideaFrame().openFile(
            "/src/main/java/alt/mongodb/springcriteria/SpringCriteriaRepository.java"
        )

        val toolbar = remoteRobot.findJavaEditorToolbar()
        assertTrue(toolbar.hasDatabasesComboBox)

        eventually(1.minutes.toJavaDuration()) {
            // when we select a cluster, it will connect asynchronously
            toolbar.dataSources.selectItem(javaClass.simpleName)
        }
        eventually(1.minutes.toJavaDuration()) {
            // it can take a few seconds, we will retry every few milliseconds
            // but wait at least for a minute if we can't select a database
            toolbar.databases.selectItem("admin")
        }
    }

    @Test
    @RequiresProject("basic-java-project-with-mongodb")
    fun `does remove all deleted data sources`(
        remoteRobot: RemoteRobot,
        url: MongoDbServerUrl,
    ) {
        remoteRobot.ideaFrame().openFile(
            "/src/main/java/alt/mongodb/javadriver/JavaDriverRepository.java"
        )

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

        remoteRobot.ideaFrame().openFile(
            "/src/main/java/alt/mongodb/javadriver/NoDriverReference.java"
        )
        val editor = remoteRobot.ideaFrame().currentTab().editor
        val textBeforeChanges = editor.text

        editor.insertTextAtLine(1, 0, "import com.mongodb.client.MongoClient;")

        remoteRobot.findJavaEditorToolbar()
        editor.text = textBeforeChanges.replace("\n", "\\\n")
    }
}
