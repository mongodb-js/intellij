/**
 * Extensions for RemoteRobot and Fixtures to get access to components
 * easier and reliably.
 */

package com.mongodb.jbplugin.fixtures

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.Fixture
import com.intellij.remoterobot.fixtures.JButtonFixture
import com.intellij.remoterobot.search.locators.Locator
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.waitFor
import com.mongodb.jbplugin.fixtures.components.idea.maybeIdeaFrame
import org.owasp.encoder.Encode
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

/**
 * Returns a fixture by the default xpath of the fixture.
 *
 * @param timeout
 * @return
 */
inline fun <reified T : Fixture> RemoteRobot.findVisible(
    timeout: Duration = Duration.ofMinutes(1)
): T =
    run {
        val atomicHolder = AtomicReference<T>()
        waitFor(
            timeout,
            Duration.ofMillis(100),
            errorMessage = "Could not find component of class ${T::class.java.canonicalName}",
        ) {
            runCatching {
                val ref = find(T::class.java)
                if (ref.callJs("true")) {
                    atomicHolder.set(ref)
                    true
                } else {
                    false
                }
            }.getOrDefault(false)
        }

        atomicHolder.get() ?: throw IllegalStateException(
            "Could not find component of class ${T::class.java.canonicalName}"
        )
    }

/**
 * Returns a fixture by the locator.
 *
 * @param timeout
 * @param locator
 * @return
 */
inline fun <reified T : Fixture> RemoteRobot.findVisible(
    locator: Locator,
    timeout: Duration = Duration.ofMinutes(1),
) = run {
    waitFor(
        timeout,
        Duration.ofMillis(100),
        errorMessage = "Could not find component of class ${T::class.java.canonicalName}",
    ) {
        runCatching {
            find(T::class.java, locator, Duration.ofMillis(100)).callJs<Boolean>("true")
        }.getOrDefault(false)
    }

    find(T::class.java, locator)
}

/**
 * Opens the IntelliJ settings modal at the specific section. The section is the name of the
 * group in the left sidebar. So for example, if you want to open the MongoDB section, you
 * would specify "MongoDB".
 *
 * @param section
 */
fun RemoteRobot.openSettingsAtSection(section: String) {
    this.runJs(
        """
        importClass(com.intellij.openapi.application.ApplicationManager)
        const runAction = new Runnable({
            run: function() {
                com.intellij.openapi.options.ShowSettingsUtil.getInstance().showSettingsDialog(
                    null,
                    "$section",
                )
            }
        })
        ApplicationManager.getApplication().invokeLater(runAction)
        """.trimIndent(),
    )
}

/**
 * Runs an action and waits until dispatched.
 *
 * @param actionId
 */
fun RemoteRobot.invokeAction(actionId: String) {
    val encodedActionId = Encode.forJavaScript(actionId)

    runJs(
        """
            importClass(com.intellij.openapi.application.ApplicationManager)
            
            const actionId = "$actionId";
            const actionManager = com.intellij.openapi.actionSystem.ActionManager.getInstance();
            const action = actionManager.getAction("$encodedActionId");
            
            const runAction = new Runnable({
                run: function() {
                    actionManager.tryToExecute(action, 
                        com.intellij.openapi.ui.playback.commands.ActionCommand.getInputEvent(actionId), 
                        null, 
                        null, 
                        true
                    );
                }
            })
            ApplicationManager.getApplication().invokeLater(runAction)
        """,
        true,
    )
}

/**
 * Opens the project and waits until ready.
 *
 * @param absolutePath
 */
fun RemoteRobot.openProject(absolutePath: String) {
    val encodedPath = Encode.forJavaScript(absolutePath)

    runJs(
        """
            importClass(com.intellij.openapi.application.ApplicationManager)
            importClass(com.intellij.ide.impl.OpenProjectTask)
           
            const projectManager = com.intellij.openapi.project.ex.ProjectManagerEx.getInstanceEx()
            let task 
            try { 
                task = OpenProjectTask.build()
            } catch(e) {
                task = OpenProjectTask.newProject()
            }
            const path = new java.io.File("$encodedPath").toPath()
           
            const openProjectFunction = new Runnable({
                run: function() {
                    projectManager.openProject(path, task)
                }
            })
           
            ApplicationManager.getApplication().invokeLater(openProjectFunction)
        """,
    )

    maybeTerminateButton()
    maybeIdeaFrame()?.closeAllFiles()
    maybeTerminateButton()
}

/**
 * Closes the project and waits until properly closed.
 */
fun RemoteRobot.closeProject() {
    invokeAction("CloseProject")
    maybeTerminateButton()
}

private fun RemoteRobot.maybeTerminateButton() {
    runCatching {
        val terminateButton =
            find<JButtonFixture>(
                byXpath("//div[@text='Terminate']"),
                timeout = 50.milliseconds.toJavaDuration(),
            )
        terminateButton.click()
    }.getOrDefault(Unit)
}
