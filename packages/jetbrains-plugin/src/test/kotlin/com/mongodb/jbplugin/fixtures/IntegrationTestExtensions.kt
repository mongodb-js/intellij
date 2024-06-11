/**
 * Extension for tests that depend on an Application.
 */

package com.mongodb.jbplugin.fixtures

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import org.junit.jupiter.api.extension.*
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

/**
 * Annotation to be used within the test.
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

    override fun beforeTestExecution(context: ExtensionContext?) {
        application = mock()

        ApplicationManager.setApplication(application) {}
    }

    override fun afterTestExecution(context: ExtensionContext?) {
    }

    override fun supportsParameter(parameterContext: ParameterContext?, extensionContext: ExtensionContext?): Boolean =
        parameterContext?.parameter?.type?.equals(Application::class.java) ?: false

    override fun resolveParameter(parameterContext: ParameterContext?, extensionContext: ExtensionContext?): Any =
        application
}

/**
 * Convenience function in application for tests. It mocks the implementation of a single service
 * with whatever implementation is passed as a parameter. For example:
 *
 * ```kt
 * application.withMockedService(mockRuntimeInformationService())
 * ```
 *
 * @param serviceImpl
 * @return
 */
inline fun <reified T> Application.withMockedService(serviceImpl: T): Application {
    `when`(getService(T::class.java)).thenReturn(serviceImpl)
    return this
}