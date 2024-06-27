package com.mongodb.jbplugin.editor.javaEditor

import com.intellij.remoterobot.RemoteRobot
import com.mongodb.jbplugin.fixtures.RequiresProject
import com.mongodb.jbplugin.fixtures.UiTest
import com.mongodb.jbplugin.fixtures.components.findJavaEditorToolbar
import com.mongodb.jbplugin.fixtures.components.hasJavaEditorToolbar
import com.mongodb.jbplugin.fixtures.components.idea.ideaFrame
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@UiTest
class JavaDriverToolbarVisibilityUiTest {
    @Test
    @RequiresProject("basic-java-project-with-mongodb")
    fun `shows the toolbar in a java file with references to the driver`(remoteRobot: RemoteRobot) {
        remoteRobot.ideaFrame().openFile("/src/main/java/alt/mongodb/javadriver/JavaDriverRepositoryExample.java")
        val toolbar = remoteRobot.findJavaEditorToolbar()

        assertTrue(remoteRobot.hasJavaEditorToolbar())
        assertTrue(toolbar.isShowing)
    }

    @Test
    @RequiresProject("basic-java-project-with-mongodb")
    fun `does not show the toolbar in a java file without references to the driver`(remoteRobot: RemoteRobot) {
        remoteRobot.ideaFrame().openFile("/src/main/java/alt/mongodb/javadriver/NoDriverReference.java")
        assertFalse(remoteRobot.hasJavaEditorToolbar())
    }
}
