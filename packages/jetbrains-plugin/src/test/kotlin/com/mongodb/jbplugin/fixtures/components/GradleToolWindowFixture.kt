package com.mongodb.jbplugin.fixtures.components

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.*
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.waitFor
import com.intellij.remoterobot.utils.waitForIgnoringError
import com.mongodb.jbplugin.fixtures.infoAndProgressPanel
import java.time.Duration

@DefaultXpath(
    by = "class",
    xpath = "//div[@class='InternalDecoratorImpl' and @accessiblename='Gradle Tool Window']"
)
@FixtureName("Gradle Tool Window")
class GradleToolWindowFixture(
    remoteRobot: RemoteRobot,
    remoteComponent: RemoteComponent,
) : ContainerFixture(remoteRobot, remoteComponent) {
    val actionToolbar by lazy {
        find<ContainerFixture>(
            byXpath("//div[contains(@myvisibleactions, 'To') or contains(@myvisibleactions, 'of')]")
        )
    }

    val reloadGradleButton by lazy {
        step("Retrieving an enabled reload gradle button") {
            val button = actionToolbar.find<JButtonFixture>(byXpath("//div[@myicon='refresh.svg']"))
            waitFor(
                duration = Duration.ofMinutes(1),
                description = "Reload gradle button to be enabled",
                errorMessage = "Reload gradle button was still disabled"
            ) {
                button.isEnabled()
            }
            button
        }
    }

    val projectTree by lazy {
        find<ContainerFixture>(byXpath("//div[@class='ExternalProjectTree']"))
    }

    fun ensureGradleProjectsAreSynced() {
        step("Ensuring gradle projects are synced") {
            runCatching {
                waitForIgnoringError(
                    duration = Duration.ofMinutes(2),
                    description = "Gradle projects to show up",
                    errorMessage = "Gradle projects did not show up",
                ) {
                    !projectTree.hasText("Nothing to show")
                }
            }.isFailure

            step("Manually reload gradle projects") {
                reloadGradleButton.click()
                waitFor(
                    duration = Duration.ofMinutes(2),
                    description = "Gradle projects to show up after manual reload",
                    errorMessage = "Gradle projects to show up after manual reload",
                ) {
                    !projectTree.hasText("Nothing to show")
                }
            }

            remoteRobot.infoAndProgressPanel().waitForInProgressTasksToFinish()
        }
    }
}

fun RemoteRobot.gradleToolWindow(): GradleToolWindowFixture = find()

fun RemoteRobot.maybeGradleToolWindow(): GradleToolWindowFixture? = runCatching {
    gradleToolWindow()
}.getOrNull()

fun RemoteRobot.openGradleToolWindow(): GradleToolWindowFixture {
    return step("Open gradle tool window") {
        waitFor(
            duration = Duration.ofMinutes(1),
            description = "Gradle tool window to open",
            errorMessage = "Gradle tool window did not open"
        ) {
            maybeGradleToolWindow()?.isShowing ?: run {
                rightToolbar().gradleButton.click()
                maybeGradleToolWindow()?.isShowing == true
            }
        }

        return@step gradleToolWindow()
    }
}
