package com.mongodb.jbplugin.fixtures

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.ContainerFixture
import com.intellij.remoterobot.fixtures.DefaultXpath
import com.intellij.remoterobot.fixtures.FixtureName
import com.intellij.remoterobot.fixtures.JButtonFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.waitFor
import java.time.Duration

@DefaultXpath(by = "class", xpath = "//div[@class='ToolWindowHeader'][.//div[@class='BaseLabel']]")
@FixtureName("Right Tool Window Header")
class RightToolWindowHeaderFixture(
    remoteRobot: RemoteRobot,
    remoteComponent: RemoteComponent,
) : ContainerFixture(remoteRobot, remoteComponent) {
    private val headerActionsContainer by lazy {
        find<ContainerFixture>(
            byXpath("//div[@classhierarchy='javax.swing.JPanel -> javax.swing.JComponent']")
        )
    }

    val hideButton by lazy {
        step("Retrieving hide button from right tool window header") {
            headerActionsContainer.moveMouse()
            headerActionsContainer.find<JButtonFixture>(
                byXpath("//div[@tooltiptext='Hide']")
            )
        }
    }
}

fun RemoteRobot.rightToolWindowHeader(): RightToolWindowHeaderFixture = find()

fun RemoteRobot.maybeRightToolWindowHeader(): RightToolWindowHeaderFixture? = runCatching {
    rightToolWindowHeader()
}.getOrNull()

fun RemoteRobot.closeRightToolWindow() {
    step("Closing right tool window") {
        waitFor(
            duration = Duration.ofMinutes(1),
            description = "Right tool window to close",
            errorMessage = "Right tool window did not close",
        ) {
            if (maybeRightToolWindowHeader()?.isShowing == true) {
                maybeRightToolWindowHeader()?.hideButton?.click()
            }
            maybeRightToolWindowHeader()?.isShowing != true
        }
    }
}
