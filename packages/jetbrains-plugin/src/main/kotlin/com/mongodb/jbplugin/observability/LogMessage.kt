/**
 * This file defines the set of classes that will be used to build a log message.
 * These classes are marked as internal because they shouldn't be used outside
 * this module.
 *
 * Ideally, you are injecting the LogMessage service into your probe, and when
 * sending an event, we would also send a relevant log message.
 *
 */

package com.mongodb.jbplugin.observability

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/**
 * @param gson
 * @param message
 */
internal class LogMessageBuilder(private val gson: Gson, message: String) {
    private val properties: MutableMap<String, Any> = mutableMapOf("message" to message)

    fun put(key: String, value: Any): LogMessageBuilder {
        properties[key] = value
        return this
    }

    fun build(): String = gson.toJson(properties)
}

/**
 * This class will be injected in probes to build log messages. Usually like:
 * ```kt
 * @Service(Service.Level.PROJECT)
 * class MyProbe(private val project: Project) {
 *  ...
 *     fun somethingProbed() {
 *        val logMessage = project.getService(LogMessage::class.java)
 *        log.info(logMessage.message("My message").put("someOtherProp", 25).build())
 *     }
 *  ...
 * }
 * ```
 *
 * @param project
 */
@Service(Service.Level.PROJECT)
internal class LogMessage(private val project: Project) {
    private val gson = GsonBuilder().generateNonExecutableJson().disableJdkUnsafe().create()

    fun message(key: String): LogMessageBuilder {
        val runtimeInformationService = project.getService(RuntimeInformationService::class.java)
        val runtimeInformation = runtimeInformationService.get()

        return LogMessageBuilder(gson, key)
            .put("userId", runtimeInformation.userId)
            .put("os", runtimeInformation.osName)
            .put("arch", runtimeInformation.arch)
            .put("jvmVendor", runtimeInformation.jvmVendor)
            .put("jvmVersion", runtimeInformation.jvmVersion)
            .put("buildVersion", runtimeInformation.buildVersion)
            .put("ide", runtimeInformation.applicationName)
    }
}
