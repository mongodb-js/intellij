package com.mongodb.jbplugin.fixtures.components

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.ContainerFixture
import com.intellij.remoterobot.fixtures.DefaultXpath
import com.intellij.remoterobot.fixtures.JButtonFixture
import com.intellij.remoterobot.fixtures.JPopupMenuFixture
import com.intellij.remoterobot.fixtures.JTreeFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.waitFor
import java.time.Duration

@DefaultXpath(
    by = "class",
    xpath = "//div[@class='InternalDecoratorImpl' and @accessiblename='Database Tool Window']"
)
class DatabaseToolWindowFixture(
    remoteRobot: RemoteRobot,
    remoteComponent: RemoteComponent,
) : ContainerFixture(remoteRobot, remoteComponent) {
    private val databaseTree by lazy {
        find<JTreeFixture>(byXpath("//div[@class='DatabaseViewTreeComponent']"))
    }

    fun removeDataSourceAtIndex(index: Int) {
        val oldSize = databaseTree.collectRows().size
        val dataSourceAtIndex = databaseTree.collectRows()[index]
        step("Removing DataSource $dataSourceAtIndex at index $index") {
            waitFor(
                duration = Duration.ofMinutes(1),
                description = "Data source to be removed",
                errorMessage = "Data source was not removed"
            ) {
                databaseTree.rightClickRow(index)
                val popupMenu = remoteRobot.find<JPopupMenuFixture>(
                    byXpath("//div[@class='MyMenu']")
                )
                popupMenu.select("Remove Data Source… ")

                val confirmationDialog = remoteRobot.find<ContainerFixture>(
                    byXpath("//div[@class='MyDialog']")
                )

                confirmationDialog.find<JButtonFixture>(
                    byXpath("//div[@text='OK']")
                ).click()

                databaseTree.collectRows().size == oldSize - 1
            }
        }
    }

    fun removeDataSourceByName(name: String) {
        step("Removing DataSource $name") {
            val indexToRemove = databaseTree.collectRows().indexOfFirst { it.contains(name) }
            removeDataSourceAtIndex(indexToRemove)
        }
    }

    fun removeAllDataSources() {
        step("Removing all data sources") {
            while (databaseTree.collectRows().isNotEmpty()) {
                removeDataSourceAtIndex(0)
            }
        }
    }
}

fun RemoteRobot.databaseToolWindow(): DatabaseToolWindowFixture = find()

fun RemoteRobot.maybeDatabaseToolWindow(): DatabaseToolWindowFixture? = runCatching {
    databaseToolWindow()
}.getOrNull()

fun RemoteRobot.openDatabaseToolWindow(): DatabaseToolWindowFixture {
    return step("Open database tool window") {
        waitFor(
            duration = Duration.ofMinutes(1),
            description = "Database tool window to open",
            errorMessage = "Database tool window did not open"
        ) {
            maybeDatabaseToolWindow()?.isShowing ?: run {
                rightToolbar().databaseButton.click()
                maybeDatabaseToolWindow()?.isShowing == true
            }
        }

        return@step databaseToolWindow()
    }
}
