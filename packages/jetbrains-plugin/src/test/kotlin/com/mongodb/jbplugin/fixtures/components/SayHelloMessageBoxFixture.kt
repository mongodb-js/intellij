package com.mongodb.jbplugin.fixtures.components

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.*
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.mongodb.jbplugin.fixtures.stripHtml

/**
 * Represents the interactions with the hello message box.
 *
 * @param remoteRobot
 * @param remoteComponent
 */
@DefaultXpath(by = "Name", xpath = "//div[@class='MyDialog' and @title='Build Info']")
@FixtureName(name = "Say Hello Message Box")
class SayHelloMessageBoxFixture(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) : ContainerFixture(
    remoteRobot, remoteComponent
) {
    val title by lazy {
        step("get title of the say hello message box") {
            find(ComponentFixture::class.java, byXpath("//div[@visible_text='Build Info']")).callJs<String>(
                "component.getText();"
            ).stripHtml()
        }
    }
    val body by lazy {
        step("get body of the say hello message box") {
            find(
                ComponentFixture::class.java, byXpath("//div[@visible_text='4.11.0']")
            ).callJs<String>("component.getText();").stripHtml()
        }
    }

    fun ok() {
        step("clicking the confirmation button of the message box") {
            find(ActionButtonFixture::class.java, byXpath("//div[@visible_text='OK']")).click()
        }
    }
}