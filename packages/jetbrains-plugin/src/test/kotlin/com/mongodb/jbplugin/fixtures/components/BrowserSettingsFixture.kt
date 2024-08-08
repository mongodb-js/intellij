/**
 * Fixture that represents the browser settings page. It's used for tests that depend on opening a browser.
 *
 */

package com.mongodb.jbplugin.fixtures.components

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.*
import com.intellij.remoterobot.search.locators.XpathLocator
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.keyboard
import com.mongodb.jbplugin.fixtures.findVisible
import com.mongodb.jbplugin.fixtures.openSettingsAtSection
import java.nio.file.Files
import kotlin.io.path.Path

/**
 * Component that represents the settings page.
 *
 * @param remoteRobot
 * @param remoteComponent
 */
@DefaultXpath(by = "hierarchy", xpath = "//div[@class='DialogPanel']//div[@class='JPanel']")
@FixtureName("BrowserSettingsFixture")
class BrowserSettingsFixture(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) : ContainerFixture(
    remoteRobot,
    remoteComponent,
) {
    val ok by lazy {
        remoteRobot.findAll<JButtonFixture>().find { it.text == "OK" } ?: throw NoSuchElementException()
    }
    fun useFakeBrowser() {
        val selector = remoteRobot.find<ComboBoxFixture>(byXpath(
"//div[@accessiblename='Default Browser:' and @class='ComboBox']"
))
        selector.selectItem("Custom path")

        val commandInput = remoteRobot.find<JTextFieldFixture>(XpathLocator("type",
 "//div[@class='TextFieldWithBrowseButton']"))
        commandInput.click()

        val browserCmd = Path("src", "test", "resources", "fake-browser.sh").toAbsolutePath().toString()
        commandInput.runJs("""component.setText("$browserCmd")""", true)
        remoteRobot.keyboard { enter() }
    }

    fun lastBrowserUrl(): String {
        val pathToOutput = Path("src", "test", "resources", "FAKE_BROWSER_OUTPUT").toAbsolutePath()
        return Files.readString(pathToOutput).trim()
    }

    fun useSystemBrowser() {
        val selector = remoteRobot.find<ComboBoxFixture>(byXpath(
"//div[@accessiblename='Default Browser:' and @class='ComboBox']"
))
        selector.selectItem("System default")
    }
}

/**
 * Opens the settings dialog and returns a fixture, so it can be interacted.
 *
 * @return
 */
fun RemoteRobot.openBrowserSettings(): BrowserSettingsFixture {
    openSettingsAtSection("Web Browsers and Preview")
    return findVisible()
}
