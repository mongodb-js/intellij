package com.mongodb.jbplugin.fixtures.components

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.*
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.waitFor
import com.mongodb.jbplugin.fixtures.findVisible
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/** Component that represents the popup that contains the data sources
 * and actions relevant to our MongoDB plugin in a Java Editor
 *
 * @param remoteRobot
 * @param remoteComponent
 */
@DefaultXpath(by = "class", xpath = "//div[@class='DialogRootPane']")
@FixtureName("MdbJavaEditorToolbarPopup")
class MdbJavaEditorToolbarPopupFixture(
    remoteRobot: RemoteRobot,
    remoteComponent: RemoteComponent,
) : ContainerFixture(
    remoteRobot,
    remoteComponent,
) {
    val dataSources: ComboBoxFixture
        get() = find(byXpath("//div[@class='DataSourceComboBoxComponent']"))

    val databases: ComboBoxFixture
        get() = find(byXpath("//div[@class='DatabaseComboBoxComponent']"))

    val hasDatabasesComboBox: Boolean
        get() = runCatching {
            find<ComboBoxFixture>(
                byXpath("//div[@class='DatabaseComboBoxComponent']"),
                timeout = 50.milliseconds.toJavaDuration()
            )
        }.isSuccess

    fun ok() = find<JButtonFixture>(byXpath("//div[@text='OK']")).click()
    fun cancel() = find<JButtonFixture>(byXpath("//div[@text='Cancel']")).click()
}

/**
 * Forcefully returns the popup, if it is not visible, throws an exception.
 *
 * @return
 */
fun RemoteRobot.findJavaEditorToolbarPopup(): MdbJavaEditorToolbarPopupFixture = findVisible()

/**
 * Checks if the toolbar exists.
 *
 * @param timeout
 * @return
 */
fun RemoteRobot.isJavaEditorToolbarPopupHidden(timeout: Duration = 10.seconds): Boolean =
    run {
        waitFor(
            timeout.toJavaDuration(),
            100.milliseconds.toJavaDuration(),
        ) {
            return@waitFor runCatching {
                findAll<MdbJavaEditorToolbarPopupFixture>().isEmpty()
            }.getOrDefault(false)
        }

        findAll<MdbJavaEditorToolbarPopupFixture>().isEmpty()
    }
