package com.mongodb.jbplugin

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.mongodb.jbplugin.observability.probe.PluginActivatedProbe
import com.mongodb.jbplugin.settings.PluginSettingsConfigurable
import com.mongodb.jbplugin.settings.useSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class OpenMongoDBPluginSettingsAction : AnAction("Open Settings") {
    override fun actionPerformed(e: AnActionEvent) {
        ShowSettingsUtil.getInstance().showSettingsDialog(e.project, PluginSettingsConfigurable::class.java)
    }
}
/**
 * This notifies that the plugin has been activated.
 *
 * @param cs
 */
class ActivatePluginPostStartupActivity(private val cs: CoroutineScope) : StartupActivity, DumbAware {
    override fun runActivity(project: Project) {
        cs.launch {
            val pluginActivated = ApplicationManager.getApplication().getService(PluginActivatedProbe::class.java)
            pluginActivated.pluginActivated()

            val settings = useSettings()
            if (!settings.hasTelemetryOptOutputNotificationBeenShown) {
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("com.mongodb.jbplugin.notifications.Telemetry")
                    .createNotification("MongoDB plugin telemetry", "Anonymous telemetry is enabled by default, as it helps us improve the plugin. However, you can disable it in settings and we won't ask you to enable it again.", NotificationType.INFORMATION)
                    .setImportant(true)
                    .addAction(OpenMongoDBPluginSettingsAction())
                    .notify(project)

                settings.hasTelemetryOptOutputNotificationBeenShown = true
            }
        }
    }
}
