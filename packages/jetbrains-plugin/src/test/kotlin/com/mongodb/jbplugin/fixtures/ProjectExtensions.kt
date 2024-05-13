/**
 * Functions to simplify testing that depends on a project.
 */

package com.mongodb.jbplugin.fixtures

import com.google.gson.Gson
import com.intellij.openapi.project.Project
import com.mongodb.jbplugin.observability.*
import com.mongodb.jbplugin.observability.LogMessage
import com.mongodb.jbplugin.observability.TelemetryService
import com.mongodb.jbplugin.observability.probe.PluginActivatedProbe
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
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
 * @param runtimeInformationService
 * @return A mock project to be used in dependency injection.
 */
internal fun mockProject(
    telemetryService: TelemetryService = mock<TelemetryService>(),
    runtimeInformationService: RuntimeInformationService = mockRuntimeInformationService(),
    pluginActivatedProbe: PluginActivatedProbe = mock<PluginActivatedProbe>(),
    logMessage: LogMessage = mockLogMessage(),
): Project {
    val project = mock<Project>()
    `when`(project.getService(TelemetryService::class.java)).thenReturn(telemetryService)
    `when`(project.getService(RuntimeInformationService::class.java)).thenReturn(runtimeInformationService)
    `when`(project.getService(PluginActivatedProbe::class.java)).thenReturn(pluginActivatedProbe)
    `when`(project.getService(LogMessage::class.java)).thenReturn(logMessage)
    return project
}

/**
 * Generates a mock runtime information service, useful for testing. If you need
 * to create your own. You'll likely will build first an information service and
 * then inject it into a mock project, something like this:
 *
 * ```kt
 * val myInfoService = mockRuntimeInformationService(userId = "hey")
 * val myProject = mockProject(runtimeInformationService = myInfoService)
 * ```
 *
 * @param userId
 * @param osName
 * @param arch
 * @param jvmVendor
 * @param jvmVersion
 * @param buildVersion
 * @param applicationName
 * @return A new mocked RuntimeInformationService
 */
internal fun mockRuntimeInformationService(
    userId: String = "123456",
    osName: String = "Winux OSX",
    arch: String = "x128",
    jvmVendor: String = "Obelisk",
    jvmVersion: String = "42",
    buildVersion: String = "2024.2",
    applicationName: String = "Cool IDE"
): RuntimeInformationService = mock<RuntimeInformationService>().also { service ->
    `when`(service.get()).thenReturn(
        RuntimeInformation(
            userId = userId,
            osName = osName,
            arch = arch,
            jvmVendor = jvmVendor,
            jvmVersion = jvmVersion,
            buildVersion = buildVersion,
            applicationName = applicationName
        )
    )
}

/**
 * Generates a mock log message service.
 * You'll likely will build first a log message service and
 * then inject it into a mock project, something like this:
 *
 * ```kt
 * val myLogMessage = mockLogMessage()
 * val myProject = mockProject(logMessage = myLogMessage)
 * ```
 *
 * @return A new mocked LogMessage
 */
internal fun mockLogMessage(): LogMessage = mock<LogMessage>().also { logMessage ->
    `when`(logMessage.message(any())).then { message ->
        LogMessageBuilder(Gson(), message.arguments[0].toString())
    }
}
