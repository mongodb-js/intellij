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
                find(T::class.java, Duration.ofMillis(100)).callJs<Boolean>("true")
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
