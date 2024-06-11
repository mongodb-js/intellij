package com.mongodb.jbplugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.mongodb.jbplugin.observability.probe.PluginActivatedProbe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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
        }
    }
}
