/**
 * Contains all runtime information relevant for observability.
 */

package com.mongodb.jbplugin.observability

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.PermanentInstallationID
import com.intellij.openapi.components.Service
import com.intellij.openapi.util.SystemInfo

/**
 * Represents all the gathered information from the host machine.
 *
 * @property userId
 * @property osName
 * @property arch
 * @property jvmVendor
 * @property jvmVersion
 * @property buildVersion
 * @property applicationName
 */
data class RuntimeInformation(
    val userId: String,
    val osName: String,
    val arch: String,
    val jvmVendor: String,
    val jvmVersion: String,
    val buildVersion: String,
    val applicationName: String,
)

/**
 * Computes, if possible, the current runtime information. It provides a method that
 * returns a RuntimeInformation object.
 *
 * Do not use RuntimeInformation for feature toggling.
 *
 * @see RuntimeInformation
 */
@Service
class RuntimeInformationService {
    private val userId by lazy {
        getOrDefault("<userId>") { PermanentInstallationID.get() }
    }

    private val osName by lazy {
        getOrDefault("<osName>") { SystemInfo.getOsNameAndVersion() }
    }

    private val arch by lazy {
        getOrDefault("<arch>") { SystemInfo.OS_ARCH }
    }

    private val jvmVendor by lazy {
        getOrDefault("<jvmVendor>") { SystemInfo.JAVA_VENDOR }
    }

    private val jvmVersion by lazy {
        getOrDefault("<jvmVersion>") { SystemInfo.JAVA_VERSION }
    }

    private val buildVersion by lazy {
        getOrDefault("<fullVersion>") { ApplicationInfo.getInstance().fullVersion }
    }

    private val applicationName by lazy {
        getOrDefault("<fullApplicationName>") {
            ApplicationInfo.getInstance().fullApplicationName
        }
    }

    fun get(): RuntimeInformation = RuntimeInformation(
        userId,
        osName,
        arch,
        jvmVendor,
        jvmVersion,
        buildVersion,
        applicationName
    )

    private fun <T> getOrDefault(default: T, supplier: () -> T?): T {
        return try {
            supplier() ?: default
        } catch (ex: Throwable) {
            return default
        }
    }
}
