package com.mongodb.jbplugin.fixtures.components

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.ContainerFixture
import com.intellij.remoterobot.fixtures.DefaultXpath
import com.intellij.remoterobot.fixtures.FixtureName
import com.intellij.remoterobot.fixtures.JButtonFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.mongodb.jbplugin.fixtures.components.idea.IdeaFrame
import java.time.Duration

@DefaultXpath(
    by = "class",
    xpath = "//div[@class='ToolWindowRightToolbar' or @accessiblename='Right Stripe']"
)
@FixtureName("RightToolbar")
class RightToolbarFixture(
    remoteRobot: RemoteRobot,
    remoteComponent: RemoteComponent
) : ContainerFixture(remoteRobot, remoteComponent) {
    val aiAssistantButton by lazy {
        find<JButtonFixture>(byXpath("//div[@tooltiptext='AI Assistant' or @text='AI Assistant']"))
    }

    val gradleButton by lazy {
        find<JButtonFixture>(byXpath("//div[@tooltiptext='Gradle' or @text='Gradle']"))
    }
}

fun IdeaFrame.rightToolbar(): RightToolbarFixture = find(
    Duration.ofSeconds(30)
)
