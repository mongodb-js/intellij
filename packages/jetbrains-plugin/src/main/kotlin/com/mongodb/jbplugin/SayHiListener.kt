package com.mongodb.jbplugin

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.ui.Messages
import com.mongodb.jbplugin.meta.BuildInformation
import com.mongodb.jbplugin.observability.probe.PluginActivatedProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Example listener, we will get rid of this.
 */
class SayHiListener : StartupActivity, DumbAware {
    override fun runActivity(project: Project) {
        val pluginActivated = project.getService(PluginActivatedProbe::class.java)

        runBlocking(Dispatchers.EDT) {
            pluginActivated.pluginActivated()
            Messages.showInfoMessage(project, "${BuildInformation.driverVersion}", "Build Info")
        }
    }
}
