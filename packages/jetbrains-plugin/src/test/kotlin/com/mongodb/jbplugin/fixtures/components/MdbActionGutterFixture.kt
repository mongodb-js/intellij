/**
 * Utilities to access gutters related to the MongoDb plugin functionality.
 */

package com.mongodb.jbplugin.fixtures.components

import com.intellij.remoterobot.RemoteRobot
import com.mongodb.jbplugin.fixtures.components.idea.ideaFrame

/**
 * Function that returns the run query gutter at a specific line if provided. If no line provided,
 * returns the first appearance.
 *
 * @param atLine
 * @return the specific gutter than can be clicked
 */
fun RemoteRobot.findRunQueryGutter(atLine: Int? = null) = ideaFrame().currentTab()
    .gutter.run {
        atLine?.let {
// for some reason, gutter icons lines are always 2 minus the line in the editor :shrug:
// hide this somehow in this implementation
            getIcons().find { it.lineNumber == atLine - 2 && it.description.contains("path=/icons/ConsoleRun") }
        } ?: getIcons().find { it.description.contains("path=/icons/ConsoleRun") }
    }