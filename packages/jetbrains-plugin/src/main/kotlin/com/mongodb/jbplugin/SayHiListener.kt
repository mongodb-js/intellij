package com.mongodb.jbplugin

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.ui.Messages
import com.mongodb.jbplugin.meta.BuildInformation
import com.mongodb.jbplugin.observability.probe.PluginActivatedProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Example listener, we will get rid of this.
 */
class SayHiListener: ProjectActivity, DumbAware {
    override suspend fun execute(project: Project) {
        val pluginActivated = project.getService(PluginActivatedProbe::class.java)

        pluginActivated.pluginActivated()
        withContext(Dispatchers.EDT) {
            Messages.showInfoMessage(project, BuildInformation.driverVersion, "Build Info")
        }
    }
}
