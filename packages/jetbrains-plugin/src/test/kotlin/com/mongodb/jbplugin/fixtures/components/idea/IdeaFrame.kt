/**
 * Represents the main frame of IDEA. The one you see when you open a project, the whole window.
 * Reference code taken from:
 * https://github.com/JetBrains/intellij-ui-test-robot/blob/master/ui-test-example/src/test/kotlin/org/intellij/examples/simple/plugin/pages/IdeaFrame.kt
 */

package com.mongodb.jbplugin.fixtures.components.idea

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.*
import com.mongodb.jbplugin.fixtures.findVisible

/**
 * Fixture that represents the frame itself. You can add more functions here if you want to interact
 * with the frame.
 *
 * @param remoteRobot
 * @param remoteComponent
 */
@FixtureName("Idea frame")
@DefaultXpath("IdeFrameImpl type", "//div[@class='IdeFrameImpl']")
class IdeaFrame(
    remoteRobot: RemoteRobot,
    remoteComponent: RemoteComponent,
) : CommonContainerFixture(remoteRobot, remoteComponent) {
    fun openFile(path: String) {
        runJs(
            """
            importPackage(com.intellij.openapi.fileEditor)
            importPackage(com.intellij.openapi.vfs)
            importPackage(com.intellij.openapi.wm.impl)
            importClass(com.intellij.openapi.application.ApplicationManager)
            
            const path = '$path'
            const frameHelper = ProjectFrameHelper.getFrameHelper(component)
            if (frameHelper) {
                const project = frameHelper.getProject()
                const projectPath = project.getBasePath()
                const file = LocalFileSystem.getInstance().findFileByPath(projectPath + '/' + path)
                const openFileFunction = new Runnable({
                    run: function() {
                        FileEditorManager.getInstance(project).openTextEditor(
                            new OpenFileDescriptor(
                                project,
                                file
                            ), true
                        )
                    }
                })
                ApplicationManager.getApplication().invokeLater(openFileFunction)
            }
        """,
            true,
        )
    }
}

/**
 * Returns a reference to the idea frame. In the reference code this accepts a function
 * that is applied to the frame.
 *
 * @return
 */
fun RemoteRobot.ideaFrame(): IdeaFrame = findVisible()
