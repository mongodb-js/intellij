/**
 * Class with fixtures to test components that depend on MongoDB. Use the @RequiresMongoDbCluster annotation
 * in your test class name to enable.
 */

package com.mongodb.jbplugin.fixtures

import com.google.gson.Gson
import com.intellij.openapi.project.Project
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.jbplugin.accessadapter.MongoDbDriver
import com.mongodb.jbplugin.accessadapter.Namespace
import com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
import org.bson.Document
import org.bson.conversions.Bson
import org.junit.jupiter.api.extension.*
import org.testcontainers.containers.DockerComposeContainer
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.lifecycle.Startable

import java.io.File
import java.net.URI

import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlinx.coroutines.withTimeout

/**
 * Test environment.
 */
enum class MongoDbTestingEnvironment {
    LOCAL,
    LOCAL_ATLAS,
;
}

/**
 * Available testing versions.
 *
 * @property value
 */
enum class MongoDbVersion(val value: String) {
    V7_0("7.0"),
    LATEST("7.0"),
;
}

/**
 * Annotation that enables the MongoDB integration.
 *
 * @property value
 * @property version
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
@ExtendWith(MongoDBEnvironmentTestExtensions::class)
annotation class RequiresMongoDbCluster(
    val value: MongoDbTestingEnvironment = MongoDbTestingEnvironment.LOCAL,
    val version: MongoDbVersion = MongoDbVersion.LATEST,
)

/**
 * Data class that contains all the information relevant to connect to the server.
 *
 * @property value
 */
data class MongoDbServerUrl(val value: String)

/**
 * Extension class, do not use directly.
 */
class MongoDbenvironmentTestExtensions :
    BeforeAllCallback,
    AfterAllCallback,
    ParameterResolver {
    private var container: Startable? = null
    private var serverUrl: MongoDbServerUrl? = null

    override fun beforeAll(context: ExtensionContext) {
        val testClass = context.requiredTestClass
        val requiresCluster = testClass.getAnnotation(RequiresMongoDbCluster::class.java) ?: return

        when (requiresCluster.value) {
            MongoDbTestingEnvironment.LOCAL -> {
                container = MongoDBContainer("mongo:${requiresCluster.version.value}")
                (container as MongoDBContainer).start()
                serverUrl = MongoDbServerUrl((container as MongoDBContainer).replicaSetUrl)
            }
            MongoDbTestingEnvironment.LOCAL_ATLAS -> {
                container =
                    DockerComposeContainer(File("src/test/resources/local-atlas.yml"))
                        .withExposedService("mongod", 27_017)
                (container as DockerComposeContainer<*>).start()
                val port = (container as DockerComposeContainer<*>).getServicePort("mongod", 27_017)
                serverUrl =
                    MongoDbServerUrl(
                        "mongodb://localhost:$port/?directConnection=true",
                    )

                val client = MongoClients.create(serverUrl!!.value)
                client.getDatabase("admin")
                    .getCollection("atlascli")
                    .insertOne(Document("managedClusterType", "atlasCliLocalDevCluster"))
                client.close()
            }
        }
    }

    override fun afterAll(context: ExtensionContext) {
        container?.stop()
    }

    override fun supportsParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext,
    ): Boolean = parameterContext.parameter.type == MongoDbServerUrl::class.java

    override fun resolveParameter(
        parameterContext: ParameterContext?,
        extensionContext: ExtensionContext?,
    ): Any = serverUrl!!
}

/**
 * Driver implementation that delegates directly to the real MongoDbDriver instead of going through the JDBC driver.
 *
 * @property uri
 * @property client
 */
internal class DirectMongoDbDriver(val uri: String, val client: MongoClient) : MongoDbDriver {
    val gson = Gson()

    override suspend fun serverUri(): URI = URI.create(uri)

    override suspend fun <T : Any> runCommand(
        command: Bson,
        result: KClass<T>,
        timeout: Duration,
    ): T = withTimeout(timeout) {
            val doc = client.getDatabase("admin").runCommand(command)
            gson.fromJson(doc.toJson(), result.java)
        }

    override suspend fun <T : Any> findOne(
        namespace: Namespace,
        query: Bson,
        options: Bson,
        result: KClass<T>,
        timeout: Duration,
    ): T? = withTimeout(timeout) {
            val doc =
                client.getDatabase(namespace.database)
                    .getCollection(namespace.collection)
                    .find(query)
                    .limit(1)
                    .first()

            doc?.toJson()?.let { gson.fromJson(it, result.java) }
        }

    override suspend fun <T : Any> findAll(
        namespace: Namespace,
        query: Bson,
        result: KClass<T>,
        limit: Int,
        timeout: Duration,
    ): List<T> = withTimeout(timeout) {
            client.getDatabase(namespace.database)
                .getCollection(namespace.collection)
                .find(query)
                .limit(limit)
                .map { gson.fromJson(it.toJson(), result.java) }
                .toList()
        }

    override suspend fun countAll(
        namespace: Namespace,
        query: Bson,
        timeout: Duration,
    ): Long = withTimeout(timeout) {
            client.getDatabase(namespace.database)
                .getCollection(namespace.collection)
                .countDocuments(query)
        }
}

/**
 * Allows to mock a MongoDB connection at the project level.
 *
 * @param url
 * @return
 */
fun Project.withMockedMongoDbconnection(url: MongoDbServerUrl): Project {
    val client = MongoClients.create(url.value)

    val readModelProvider =
        DataGripBasedReadModelProvider(
            this,
        ).apply {
    driverFactory = { _, _ -> DirectMongoDbDriver(url.value, client) }
}
    return withMockedService(readModelProvider)
}
