/**
 * Extension for tests that depend on an Application.
 */

package com.mongodb.jbplugin.fixtures

import com.google.gson.Gson
import com.intellij.database.dataSource.*
import com.intellij.ide.ui.NotRoamableUiSettings
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
import com.mongodb.jbplugin.settings.PluginSettings
import com.mongodb.jbplugin.settings.PluginSettingsStateComponent
import org.junit.jupiter.api.extension.*
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.kotlin.any

import java.util.UUID

import kotlinx.coroutines.test.TestScope

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
private class IntegrationTestExtension :
    BeforeTestExecutionCallback,
    AfterTestExecutionCallback,
    ParameterResolver {
    private lateinit var application: Application
    private lateinit var settings: PluginSettingsStateComponent
    private lateinit var messageBus: MessageBus
    private lateinit var project: Project

    override fun beforeTestExecution(context: ExtensionContext?) {
        application = mock()
        project = mock()
        settings = PluginSettingsStateComponent()

        messageBus =
            RootBus(
                mock<MessageBusOwner>().apply {
                    `when`(this.isDisposed()).thenReturn(false)
                    `when`(this.isParentLazyListenersIgnored()).thenReturn(false)
                    `when`(this.createListener(any())).then {
                        val descriptor = it.arguments[0] as ListenerDescriptor
                        Class.forName(descriptor.listenerClassName).getConstructor().newInstance()
                    }
                },
            )

        `when`(application.getMessageBus()).thenReturn(messageBus)
        `when`(project.getMessageBus()).thenReturn(messageBus)

        application.withMockedService(settings)
        application.withMockedService(DatabaseConnectionManager(TestScope()))
        application.withMockedService(NotRoamableUiSettings())

        ApplicationManager.setApplication(application) {}
    }

    override fun afterTestExecution(context: ExtensionContext?) {
    }

    override fun supportsParameter(
        parameterContext: ParameterContext?,
        extensionContext: ExtensionContext?,
    ): Boolean =
        parameterContext?.parameter?.type?.run {
            equals(Application::class.java) ||
                equals(Project::class.java) ||
                equals(PluginSettings::class.java)
        } ?: false

    override fun resolveParameter(
        parameterContext: ParameterContext?,
        extensionContext: ExtensionContext?,
    ): Any =
        when (parameterContext?.parameter?.type) {
            Application::class.java -> application
            Project::class.java -> project
            PluginSettings::class.java -> settings.state
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
 * project.withMockedService(myInfoService)
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
    applicationName: String = "Cool IDE",
) = mock<RuntimeInformationService>().also { service ->
    `when`(service.get()).thenReturn(
        RuntimeInformation(
            userId = userId,
            osName = osName,
            arch = arch,
            jvmVendor = jvmVendor,
            jvmVersion = jvmVersion,
            buildVersion = buildVersion,
            applicationName = applicationName,
        ),
    )
}

/**
 * Generates a mock log message service.
 * You'll likely will build first a log message service and
 * then inject it into a mock project, something like this:
 *
 * ```kt
 * val myLogMessage = mockLogMessage()
 * project.withMockedService(myLogMessage)
 * ```
 *
 * @return A new mocked LogMessage
 */
internal fun mockLogMessage() =
    mock<LogMessage>().also { logMessage ->
        `when`(logMessage.message(any())).then { message ->
            LogMessageBuilder(Gson(), message.arguments[0].toString())
        }
    }

/**
 * Returns a mocked data source configured for MongoDB.
 *
 * @return
 */
internal fun mockDataSource() =
    mock<LocalDataSource>().also { dataSource ->
        val driver = mock<DatabaseDriver>()
        `when`(driver.id).thenReturn("mongo")
        `when`(dataSource.databaseDriver).thenReturn(driver)
        val testClass = Thread.currentThread().stackTrace[2].className
        `when`(dataSource.name).thenReturn(testClass + "_" + UUID.randomUUID().toString())
    }

/**
 * Returns a mocked connection for the provided dataSource.
 *
 * @param dataSource Either a mock or a real data source.
 * @return
 */
internal fun mockDatabaseConnection(dataSource: LocalDataSource) =
    mock<DatabaseConnection>().also { connection ->
        val connectionPoint = mock<DatabaseConnectionPoint>()
        `when`(connection.connectionPoint).thenReturn(connectionPoint)
        `when`(connectionPoint.dataSource).thenReturn(dataSource)
    }
