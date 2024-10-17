package com.mongodb.jbplugin.fixtures.components

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.*
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.waitFor
import com.mongodb.jbplugin.fixtures.eventually
import com.mongodb.jbplugin.fixtures.findVisible
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
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

    fun ok(text: String = "OK", timeout: Duration = 10.seconds) {
        val button = find<JButtonFixture>(byXpath("//div[@text='$text']"))
        waitFor(
            timeout.toJavaDuration(),
            100.milliseconds.toJavaDuration(),
        ) {
            return@waitFor button.isEnabled()
        }
        button.click()
    }

    fun cancel() = find<JButtonFixture>(byXpath("//div[@text='Cancel']")).click()

    fun selectDataSource(title: String) {
        eventually(1.minutes.toJavaDuration()) {
            dataSources.selectItemContains(title)
            if (!dataSources.selectedText().contains(title)) {
                throw Exception("Could not select data source - $title")
            }
        }
    }

    fun selectDetachDataSource() {
        eventually(1.minutes.toJavaDuration()) {
            dataSources.selectItem("Detach data source")
            if (dataSources.selectedText() != "") {
                throw Exception("Could not detach data source")
            }
        }
    }

    fun selectDatabase(title: String) {
        eventually(1.minutes.toJavaDuration()) {
            databases.selectItemContains(title)
            if (!databases.selectedText().contains(title)) {
                throw Exception("Could not select database - $title")
            }
        }
    }
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
