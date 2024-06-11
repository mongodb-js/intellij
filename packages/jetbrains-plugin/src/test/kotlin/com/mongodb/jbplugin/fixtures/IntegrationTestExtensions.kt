/**
 * Extension for tests that depend on an Application.
 */

package com.mongodb.jbplugin.fixtures

import com.google.gson.Gson
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.project.Project
import com.intellij.util.messages.ListenerDescriptor
import com.intellij.util.messages.MessageBus
import com.intellij.util.messages.MessageBusOwner
import com.intellij.util.messages.impl.RootBus
import com.mongodb.jbplugin.observability.LogMessage
import com.mongodb.jbplugin.observability.LogMessageBuilder
import com.mongodb.jbplugin.observability.RuntimeInformation
import com.mongodb.jbplugin.observability.RuntimeInformationService
import org.junit.jupiter.api.extension.*
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.kotlin.any

/**
 * Annotation to be used within the test. It provides, as a parameter of a test,
 * either a mocked setup Application or Project.
 *
 * @see com.mongodb.jbplugin.observability.LogMessageTest
 */
@ExtendWith(IntegrationTestExtension::class)
annotation class IntegrationTest

/**
 * Extension class, should not be used directly.
 */
private class IntegrationTestExtension : BeforeTestExecutionCallback,
    AfterTestExecutionCallback,
    ParameterResolver {
    private lateinit var application: Application
    private lateinit var messageBus: MessageBus
    private lateinit var project: Project

    override fun beforeTestExecution(context: ExtensionContext?) {
        application = mock()
        project = mock()

        messageBus = RootBus(mock<MessageBusOwner>().apply {
            `when`(this.isDisposed()).thenReturn(false)
            `when`(this.isParentLazyListenersIgnored()).thenReturn(false)
            `when`(this.createListener(any())).then {
                val descriptor = it.arguments[0] as ListenerDescriptor
                Class.forName(descriptor.listenerClassName).getConstructor().newInstance()
            }
        })

        `when`(application.getMessageBus()).thenReturn(messageBus)
        `when`(project.getMessageBus()).thenReturn(messageBus)

        ApplicationManager.setApplication(application) {}
    }

    override fun afterTestExecution(context: ExtensionContext?) {
    }

    override fun supportsParameter(parameterContext: ParameterContext?, extensionContext: ExtensionContext?): Boolean =
        parameterContext?.parameter?.type?.run {
            equals(Application::class.java) || equals(Project::class.java)
        } ?: false

    override fun resolveParameter(parameterContext: ParameterContext?, extensionContext: ExtensionContext?): Any =
        when (parameterContext?.parameter?.type) {
            Application::class.java -> application
            Project::class.java -> project
            else -> TODO()
        }
}

/**
 * Convenience function in application or project for tests. It mocks the implementation of a single service
 * with whatever implementation is passed as a parameter. For example:
 *
 * ```kt
 * application.withMockedService(mockRuntimeInformationService())
 * project.withMockedService(mockRuntimeInformationService())
 * ```
 *
 * @param serviceImpl
 * @return itself so it can be chained
 */
inline fun <reified S : ComponentManager, reified T> S.withMockedService(serviceImpl: T): S {
    `when`(getService(T::class.java)).thenReturn(serviceImpl)
    return this
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
): RuntimeInformationService = org.mockito.kotlin.mock<RuntimeInformationService>()
.also { service ->
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
internal fun mockLogMessage(): LogMessage = org.mockito.kotlin.mock<LogMessage>()
.also { logMessage ->
    `when`(logMessage.message(any())).then { message ->
        LogMessageBuilder(Gson(), message.arguments[0].toString())
    }
}