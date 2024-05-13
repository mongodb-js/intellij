package com.mongodb.jbplugin

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.util.launchChildOnUi
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.ui.Messages
import com.mongodb.jbplugin.meta.BuildInformation
import com.mongodb.jbplugin.observability.probe.PluginActivatedProbe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Example listener, we will get rid of this.
 *
 * @param cs
 */
class SayHiListener(private val cs: CoroutineScope) : StartupActivity, DumbAware {
    override fun runActivity(project: Project) {
        val pluginActivated = project.getService(PluginActivatedProbe::class.java)

        cs.launch {
            pluginActivated.pluginActivated()
            cs.launchChildOnUi {
                Messages.showInfoMessage(project, BuildInformation.driverVersion, "Build Info")
            }
        }
    }
}
