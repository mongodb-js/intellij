package com.mongodb.jbplugin.fixtures.components

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.ContainerFixture
import com.intellij.remoterobot.fixtures.DefaultXpath
import com.intellij.remoterobot.fixtures.FixtureName
import com.mongodb.jbplugin.fixtures.findVisible

/** Component that represents the toolbar that contains the data sources
 * and actions relevant to our MongoDB plugin in a Java Editor
 *
 * @param remoteRobot
 * @param remoteComponent
 */
@DefaultXpath(by = "class", xpath = "//div[@class='MdbJavaEditorToolbar']")
@FixtureName("MdbJavaEditorToolbar")
class MdbJavaEditorToolbarFixture(
    remoteRobot: RemoteRobot,
    remoteComponent: RemoteComponent,
) : ContainerFixture(
        remoteRobot,
        remoteComponent,
    )

/**
 * Forcefully returns the toolbar, if it is not visible, throws an exception.
 *
 * @return
 */
fun RemoteRobot.findJavaEditorToolbar(): MdbJavaEditorToolbarFixture = findVisible()

/**
 * Checks if the toolbar exists.
 *
 * @return
 */
fun RemoteRobot.hasJavaEditorToolbar(): Boolean = findAll(MdbJavaEditorToolbarFixture::class.java).isNotEmpty()
