/**
 * These classes implement the balloon that shows the first time that the plugin is activated.
 */

package com.mongodb.jbplugin

import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.mongodb.jbplugin.i18n.TelemetryMessages
import com.mongodb.jbplugin.meta.injecting
import com.mongodb.jbplugin.observability.probe.PluginActivatedProbe
import com.mongodb.jbplugin.settings.PluginSettingsConfigurable
import com.mongodb.jbplugin.settings.pluginSetting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Class that represents the link that opens the settings page for MongoDB.
 */
class OpenMongoDbPluginSettingsAction : AnAction(
    TelemetryMessages.message("action.disable-telemetry")
) {
    override fun actionPerformed(event: AnActionEvent) {
        ShowSettingsUtil.getInstance().showSettingsDialog(
            event.project,
            PluginSettingsConfigurable::class.java
        )
    }
}

/**
 * Class that represents the link that opens the privacy policy page
 */
class OpenPrivacyPolicyPage : AnAction(TelemetryMessages.message("action.view-privacy-policy")) {
    override fun actionPerformed(event: AnActionEvent) {
        BrowserUtil.browse(TelemetryMessages.message("settings.telemetry-privacy-policy"))
    }
}

/**
 * This notifies that the plugin has been activated.
 *
 * @param cs
 */
class ActivatePluginPostStartupActivity(
    private val cs: CoroutineScope
) : StartupActivity, DumbAware {
    override fun runActivity(project: Project) {
        cs.launch {
            val pluginActivated by injecting<PluginActivatedProbe>()
            pluginActivated.pluginActivated()

            var firstTimeTelemetry by pluginSetting<Boolean> {
                ::hasTelemetryOptOutputNotificationBeenShown
            }

            if (!firstTimeTelemetry) {
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("com.mongodb.jbplugin.notifications.Telemetry")
                    .createNotification(
                        TelemetryMessages.message("notification.title"),
                        TelemetryMessages.message("notification.message"),
                        NotificationType.INFORMATION,
                    )
                    .setImportant(true)
                    .addAction(OpenPrivacyPolicyPage())
                    .addAction(OpenMongoDbPluginSettingsAction())
                    .notify(project)

                firstTimeTelemetry = true
            }
        }
    }
}
