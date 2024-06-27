/**
 * Extensions for RemoteRobot and Fixtures to get access to components
 * easier and reliably.
 */

package com.mongodb.jbplugin.fixtures

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.Fixture
import com.intellij.remoterobot.search.locators.Locator
import com.intellij.remoterobot.utils.waitFor
import java.time.Duration

/**
 * Returns a fixture by the default xpath of the fixture.
 *
 * @param timeout
 * @return
 */
inline fun <reified T : Fixture> RemoteRobot.findVisible(timeout: Duration = Duration.ofMinutes(1)) =
    run {
        waitFor(
            timeout,
            Duration.ofMillis(100),
            errorMessage = "Could not find component of class ${T::class.java.canonicalName}",
        ) {
            runCatching {
                find(T::class.java).callJs<Boolean>("true")
            }.getOrDefault(false)
        }

        find(T::class.java)
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
