package com.mongodb.jbplugin.fixtures

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.ContainerFixture
import com.intellij.remoterobot.fixtures.DefaultXpath
import com.intellij.remoterobot.fixtures.FixtureName
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.waitFor
import com.mongodb.jbplugin.fixtures.components.idea.IdeaFrame
import java.time.Duration

@DefaultXpath(by = "class", xpath = "//div[@class='InfoAndProgressPanelImpl']")
@FixtureName("Status bar")
class InfoAndProgressPanelFixture(
    remoteRobot: RemoteRobot,
    remoteComponent: RemoteComponent,
) : ContainerFixture(remoteRobot, remoteComponent) {
    private val inlineProgressPanel by lazy {
        find<ContainerFixture>(byXpath("//div[@class='InlineProgressPanel']"))
    }

    private fun isInlineProgressPanelEmpty(): Boolean {
        return inlineProgressPanel.callJs(
            """
            const children = component.getComponents();
            children.length === 0 || children.every(child => !child.isVisible());
            """.trimIndent(),
            runInEdt = true
        )
    }

    fun waitForInProgressTasksToFinish(timeout: Duration = Duration.ofMinutes(2)) {
        waitFor(
            duration = timeout,
            description = "In-Progress tasks to finish",
            errorMessage = "In-Progress tasks did not finish"
        ) {
            isInlineProgressPanelEmpty()
        }
    }
}

fun IdeaFrame.infoAndProgressPanel(): InfoAndProgressPanelFixture = find()
