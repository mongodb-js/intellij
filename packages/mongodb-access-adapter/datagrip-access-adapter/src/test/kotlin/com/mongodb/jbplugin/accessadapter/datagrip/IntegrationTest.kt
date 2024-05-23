/**
 * Test extension that allows us to test with the IntelliJ environment
 * without spinning up the whole IDE. Also, sets up a MongoDB instance
 * that can be queried.
 */

package com.mongodb.jbplugin.accessadapter.datagrip

import com.intellij.database.dataSource.DatabaseDriverManagerImpl
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.validation.DatabaseDriverValidator.createDownloaderTask
import com.intellij.database.psi.DataSourceManager
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ui.EDT
import com.mongodb.jbplugin.accessadapter.MongoDbDriver
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.DataGripMongoDbDriver
import org.junit.jupiter.api.extension.*
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.lifecycle.Startables

import java.nio.file.Files
import java.util.*

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Represents what version of MongoDB we support in the plugin.
 */
enum class MongoDbVersion(val versionString: String) {
    LATEST("7.0.9"),
    ;
}

/**
 * Annotation to be used in the test, at the class level.
 *
 * @see com.mongodb.jbplugin.accessadapter.datagrip.adapter.DataGripMongoDbDriverTest
 */
@TestApplication
@RunInEdt(allMethods = true, writeIntent = true)
@ExtendWith(IntegrationTestExtension::class)
@Testcontainers(parallel = false)
annotation class IntegrationTest(val mongodb: MongoDbVersion = MongoDbVersion.LATEST, val sharded: Boolean = false)

/**
 * Extension implementation. Must not be used directly.
 */
internal class IntegrationTestExtension : BeforeAllCallback,
    AfterAllCallback,
    ParameterResolver {
    private val namespace = ExtensionContext.Namespace.create(IntegrationTestExtension::class.java)
    private val containerKey = "CONTAINER"
    private val projectKey = "PROJECT"
    private val driverKey = "DRIVER"
    private val versionKey = "VERSION"

    override fun beforeAll(context: ExtensionContext?) {
        val annotation = context!!.requiredTestClass.getAnnotation(IntegrationTest::class.java)
        val container = MongoDBContainer("mongo:${annotation.mongodb.versionString}-jammy")
            .let {
                if (annotation.sharded) {
                    it.withSharding()
                } else {
                    it
                }
            }

        Startables.deepStart(container).join()
        context.getStore(namespace).put(containerKey, container)
        context.getStore(namespace).put(versionKey, annotation.mongodb)

        val project = runBlocking(Dispatchers.EDT) {
            val testClassName = context.requiredTestClass.simpleName
            ProjectUtil.openOrCreateProject(testClassName, Files.createTempDirectory(testClassName))!!
        }

        context.getStore(namespace).put(projectKey, project)

        val dataSource = runBlocking {
            val dataSourceManager = DataSourceManager.byDataSource(project, LocalDataSource::class.java)!!
            val instance = DatabaseDriverManagerImpl.getInstance()
            val jdbcDriver = instance.getDriver("mongo")

            val dataSource = LocalDataSource().apply {
                name = UUID.randomUUID().toString()
                url = container.connectionString
                isConfiguredByUrl = true
                username = ""
                passwordStorage = LocalDataSource.Storage.PERSIST
                databaseDriver = jdbcDriver
            }

            dataSourceManager.addDataSource(dataSource)
            dataSource
        }

        createDownloaderTask(dataSource, null).run(EmptyProgressIndicator())

        runBlocking(Dispatchers.EDT) {
            EDT.dispatchAllInvocationEvents()
        }

        val driver = DataGripMongoDbDriver(project, dataSource)
        context.getStore(namespace).put(driverKey, driver)

        runBlocking(Dispatchers.EDT) {
            EDT.dispatchAllInvocationEvents()
        }
    }

    override fun afterAll(context: ExtensionContext?) {
        val project = context!!.getStore(namespace).get(projectKey) as Project
        val mongodb = context.getStore(namespace).get(containerKey) as MongoDBContainer

        ApplicationManager.getApplication().invokeLater({
            ProjectManager.getInstance().closeAndDispose(project)
        }, ModalityState.defaultModalityState())

        mongodb.close()
    }

    override fun supportsParameter(parameterContext: ParameterContext?, extensionContext: ExtensionContext?): Boolean =
        parameterContext?.parameter?.type == Project::class.java ||
                parameterContext?.parameter?.type == MongoDbDriver::class.java ||
                parameterContext?.parameter?.type == MongoDbVersion::class.java

    override fun resolveParameter(parameterContext: ParameterContext?, extensionContext: ExtensionContext?): Any =
        when (parameterContext?.parameter?.type) {
            Project::class.java -> extensionContext!!.getStore(namespace).get(projectKey)
            MongoDbDriver::class.java -> extensionContext!!.getStore(namespace).get(driverKey)
            MongoDbVersion::class.java -> extensionContext!!.getStore(namespace).get(versionKey)
            else -> TODO("Parameter of type ${parameterContext?.parameter?.type?.canonicalName} is not supported.")
        }
}