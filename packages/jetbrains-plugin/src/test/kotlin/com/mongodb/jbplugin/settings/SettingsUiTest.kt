package com.mongodb.jbplugin.settings

import com.intellij.remoterobot.RemoteRobot
import com.mongodb.jbplugin.fixtures.RequiresProject
import com.mongodb.jbplugin.fixtures.UiTest
import com.mongodb.jbplugin.fixtures.components.openBrowserSettings
import com.mongodb.jbplugin.fixtures.components.openSettings
import com.mongodb.jbplugin.fixtures.components.useSetting
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

@UiTest
class SettingsUiTest {
    @Test
    @RequiresProject("basic-java-project-with-mongodb")
    fun `allows toggling the telemetry`(remoteRobot: RemoteRobot) {
        val telemetryBeforeTest = remoteRobot.useSetting<Boolean>("isTelemetryEnabled")

        val settings = remoteRobot.openSettings()
        settings.enableTelemetry.click()
        settings.ok.click()

        val telemetryAfterTest = remoteRobot.useSetting<Boolean>("isTelemetryEnabled")
        assertNotEquals(telemetryBeforeTest, telemetryAfterTest)
    }

    @Test
    @RequiresProject("basic-java-project-with-mongodb")
    fun `allows opening the privacy policy in a browser`(remoteRobot: RemoteRobot) {
        remoteRobot.openBrowserSettings().run {
            useFakeBrowser()
            ok.click()
        }

        val settings = remoteRobot.openSettings()
        settings.privacyPolicyButton.click()
        settings.ok.click()

        val lastBrowserUrl =
            remoteRobot.openBrowserSettings().run {
                useSystemBrowser()
                ok.click()
                lastBrowserUrl()
            }

        assertEquals("https://www.mongodb.com/legal/privacy/privacy-policy", lastBrowserUrl)
    }
}
