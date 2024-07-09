package com.mongodb.jbplugin.fixtures.components

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.ComboBoxFixture
import com.intellij.remoterobot.fixtures.ContainerFixture
import com.intellij.remoterobot.fixtures.DefaultXpath
import com.intellij.remoterobot.fixtures.FixtureName
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.waitFor
import com.mongodb.jbplugin.fixtures.findVisible
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/** Component that represents the toolbar that contains the data sources
 * and actions relevant to our MongoDB plugin in a Java Editor
 *
 * @param remoteRobot
 * @param remoteComponent
 */
@DefaultXpath(by = "class", xpath = "//div[@class='MdbJavaEditorToolbar']")
@FixtureName("MdbJavaEditorToolbar")
class MdbJavaEditorToolbarFixture(
    remoteRobot: RemoteRobot,
    remoteComponent: RemoteComponent,
) : ContainerFixture(
        remoteRobot,
        remoteComponent,
    ) {
    val dataSources: ComboBoxFixture
        get() = find(byXpath("//div[@class='ComboBox']"))
}

/**
 * Forcefully returns the toolbar, if it is not visible, throws an exception.
 *
 * @return
 */
fun RemoteRobot.findJavaEditorToolbar(): MdbJavaEditorToolbarFixture = findVisible()

/**
 * Checks if the toolbar exists.
 *
 * @param timeout
 * @return
 */
fun RemoteRobot.isJavaEditorToolbarHidden(timeout: Duration = 10.seconds): Boolean =
    run {
        waitFor(
            timeout.toJavaDuration(),
            100.milliseconds.toJavaDuration(),
        ) {
            return@waitFor runCatching {
                findAll<MdbJavaEditorToolbarFixture>().isEmpty()
            }.getOrDefault(false)
        }

        findAll<MdbJavaEditorToolbarFixture>().isEmpty()
    }
