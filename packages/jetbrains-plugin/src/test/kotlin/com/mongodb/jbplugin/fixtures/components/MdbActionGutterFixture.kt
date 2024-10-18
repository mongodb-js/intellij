/**
 * Utilities to access gutters related to the MongoDb plugin functionality.
 */

package com.mongodb.jbplugin.fixtures.components

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.GutterIcon
import com.intellij.remoterobot.stepsProcessing.step
import com.mongodb.jbplugin.fixtures.components.idea.ideaFrame
import com.mongodb.jbplugin.fixtures.eventually
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * Function that returns the run query gutter at a specific line if provided. If no line provided,
 * returns the first appearance.
 *
 * @param atLine
 * @return the specific gutter than can be clicked
 */
fun RemoteRobot.findRunQueryGutter(atLine: Int? = null) = eventually<GutterIcon> {
    step("Finding run query gutter, atLine=$atLine") {
        ideaFrame().currentTab()
            .gutter.run {
                atLine?.let {
                    // for some reason, gutter icons lines are always 2 minus the line in the editor :shrug:
                    // hide this somehow in this implementation
                    getIcons().find {
                        it.lineNumber == atLine - 2 &&
                            it.description.contains("path=/icons/ConsoleRun")
                    }
                } ?: getIcons().find { it.description.contains("path=/icons/ConsoleRun") }
            }!!
    }
}

fun RemoteRobot.openRunQueryPopup(atLine: Int? = null): MdbJavaEditorToolbarPopupFixture {
    // We always deselect the current data source because otherwise clicking on gutter icon will
    // do the action itself instead of opening the popup
    return step("Opening run query popup, atLine=$atLine") {
        findJavaEditorToolbar().selectDetachDataSource()
        return@step eventually<MdbJavaEditorToolbarPopupFixture>(10.seconds.toJavaDuration()) {
            findRunQueryGutter(atLine)!!.click()
            return@eventually findJavaEditorToolbarPopup()
        }!!
    }
}
