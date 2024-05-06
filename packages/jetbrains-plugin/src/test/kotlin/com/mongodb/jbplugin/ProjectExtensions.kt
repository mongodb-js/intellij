/**
 * Functions to simplify testing that depends on a project.
 */

package com.mongodb.jbplugin

import com.intellij.openapi.project.Project
import com.mongodb.jbplugin.observability.LogMessage
import com.mongodb.jbplugin.observability.TelemetryService
import com.mongodb.jbplugin.observability.probe.PluginActivatedProbe
import org.mockito.Mockito.`when`
import org.mockito.kotlin.mock

/**
 * Creates a mock project with dependencies injected.
 *
 * All parameters are optional, so you can pass a custom mock to any of them to
 * verify.
 *
 * @param telemetryService
 * @param pluginActivatedProbe
 * @param logMessage
 * @return A mock project to be used in dependency injection.
 */
internal fun mockProject(
    telemetryService: TelemetryService = mock<TelemetryService>(),
    pluginActivatedProbe: PluginActivatedProbe = mock<PluginActivatedProbe>(),
    logMessage: LogMessage = LogMessage(),
): Project {
    val project = mock<Project>()
    `when`(project.getService(TelemetryService::class.java)).thenReturn(telemetryService)
    `when`(project.getService(PluginActivatedProbe::class.java)).thenReturn(pluginActivatedProbe)
    `when`(project.getService(LogMessage::class.java)).thenReturn(logMessage)

    return project
}
