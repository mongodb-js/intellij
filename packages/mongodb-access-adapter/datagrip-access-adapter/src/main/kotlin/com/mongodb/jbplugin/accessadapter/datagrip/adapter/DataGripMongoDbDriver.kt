/**
 * Represents a MongoDB driver interface that uses a DataGrip
 * connection to query MongoDB.
 */

package com.mongodb.jbplugin.accessadapter.datagrip.adapter

import com.google.gson.GsonBuilder
import com.intellij.database.dataSource.DatabaseConnection
import com.intellij.database.dataSource.DatabaseConnectionManager
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.connection.ConnectionRequestor
import com.intellij.database.run.ConsoleRunConfiguration
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.jbplugin.accessadapter.ExplainPlan
import com.mongodb.jbplugin.accessadapter.MongoDbDriver
import com.mongodb.jbplugin.dialects.OutputQuery
import com.mongodb.jbplugin.dialects.mongosh.MongoshDialect
import com.mongodb.jbplugin.mql.Namespace
import com.mongodb.jbplugin.mql.Node
import kotlinx.coroutines.*
import org.bson.Document
import org.bson.codecs.DecoderContext
import org.bson.codecs.configuration.CodecRegistries.fromRegistries
import org.bson.codecs.configuration.CodecRegistry
import org.bson.conversions.Bson
import org.bson.json.JsonMode
import org.bson.json.JsonWriterSettings
import org.jetbrains.annotations.VisibleForTesting
import org.owasp.encoder.Encode
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val TIMEOUT = 5

/**
 * Currently we are using mongosh through the GraalVM, and doesn't support parallelism. So
 * we are running the queries in a dedicated single thread.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private val mongosh = Dispatchers.IO.limitedParallelism(1)

/**
 * The driver itself. Shouldn't be used directly, but through the
 * DataGripBasedReadModelProvider.
 *
 * @see com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
 *
 * @param project
 * @param dataSource
 */
internal class DataGripMongoDbDriver(
    private val project: Project,
    private val dataSource: LocalDataSource,
) : MongoDbDriver {
    override val connected: Boolean
        get() =
            DatabaseConnectionManager.getInstance().activeConnections.any {
                it.connectionPoint.dataSource == dataSource
            }

    private val codecRegistry: CodecRegistry =
        fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry()
        )
    private val jsonWriterSettings =
        JsonWriterSettings
            .builder()
            .outputMode(JsonMode.EXTENDED)
            .indent(false)
            .build()

    private fun String.encodeForJs(): String = Encode.forJavaScript(this)

    private fun Bson.toJson(): String =
        this
            .toBsonDocument(Bson::class.java, codecRegistry)
            .toJson(jsonWriterSettings)
            .encodeForJs()

    override suspend fun connectionString(): ConnectionString = ConnectionString(dataSource.url!!)

    override suspend fun <S> explain(query: Node<S>): ExplainPlan = withContext(Dispatchers.IO) {
        val queryScript = ApplicationManager.getApplication().runReadAction<OutputQuery> {
            MongoshDialect.formatter.formatQuery(query, explain = true)
        }

        if (queryScript !is OutputQuery.CanBeRun) {
            return@withContext ExplainPlan.NotRun
        }

        val explainPlanBson = runQuery(
            queryScript.query,
            Document::class,
            timeout = 1.seconds
        ).firstOrNull()

        explainPlanBson ?: return@withContext ExplainPlan.NotRun

        val queryPlanner = explainPlanBson.get("queryPlanner", Document::class.java)
        val winningPlan = queryPlanner?.get("winningPlan", Document::class.java)

        winningPlan ?: return@withContext ExplainPlan.NotRun

        planByMappingStage(
            winningPlan,
            mapOf(
                "COLLSCAN" to ExplainPlan.CollectionScan,
                "IXSCAN" to ExplainPlan.IndexScan,
                "IDHACK" to ExplainPlan.IndexScan
            )
        ) ?: ExplainPlan.NotRun
    }

    override suspend fun <T : Any> runCommand(
        database: String,
        command: Bson,
        result: KClass<T>,
        timeout: Duration,
    ): T =
        withContext(
            mongosh,
        ) {
            runQuery(
                """
                EJSON.serialize(
                    db.getSiblingDB("${database.encodeForJs()}")
                      .runCommand(EJSON.parse("${command.toJson()}"))
                , { relaxed: false })
                """.trimIndent(),
                result,
                timeout,
            )[0]
        }

    override suspend fun <T : Any> findOne(
        namespace: Namespace,
        query: Bson,
        options: Bson,
        result: KClass<T>,
        timeout: Duration,
    ): T? =
        withContext(mongosh) {
            runQuery(
                """EJSON.serialize(
                      db.getSiblingDB("${namespace.database.encodeForJs()}")
                     .getCollection("${namespace.collection.encodeForJs()}")
                     .findOne(EJSON.parse("${query.toJson()}"), EJSON.parse("${options.toJson()}"))
                , { relaxed: false })
                """.trimMargin(),
                result,
                timeout,
            ).getOrNull(0)
        }

    override suspend fun <T : Any> findAll(
        namespace: Namespace,
        query: Bson,
        result: KClass<T>,
        limit: Int,
        timeout: Duration,
    ) = withContext(mongosh) {
        runQuery(
            """
                EJSON.serialize(
                    db.getSiblingDB("${namespace.database.encodeForJs()}")
                     .getCollection("${namespace.collection.encodeForJs()}")
                     .find(EJSON.parse("${query.toJson()}")).limit($limit).toArray()
                , { relaxed: false })
            """.trimMargin(),
            result,
            timeout,
        )
    }

    override suspend fun countAll(
        namespace: Namespace,
        query: Bson,
        timeout: Duration,
    ) = withContext(mongosh) {
        runQuery(
            """
            db.getSiblingDB("${namespace.database.encodeForJs()}")
                 .getCollection("${namespace.collection.encodeForJs()}")
                 .countDocuments(EJSON.parse("${query.toJson()}"))
            """.trimIndent(),
            Long::class,
            timeout,
        )[0]
    }

    @VisibleForTesting
    internal suspend fun <T : Any> runQuery(
        queryString: String,
        resultClass: KClass<T>,
        timeout: Duration,
    ): List<T> =
        withContext(mongosh) {
            val connection = getConnection()
            val remoteConnection = connection.remoteConnection
            val statement = remoteConnection.prepareStatement(queryString.trimIndent())

            withTimeout(timeout) {
                val listOfResults = mutableListOf<T>()
                val resultSet = statement.executeQuery() ?: return@withTimeout emptyList()

                if (resultClass.java == Unit::class.java) {
                    listOfResults.add(Unit as T)
                    return@withTimeout listOfResults
                }

                if (resultClass.java.isPrimitive || resultClass == String::class.java) {
                    while (resultSet.next()) {
                        listOfResults.add(resultSet.getObject(1) as T)
                    }
                } else {
                    val decoderContext = DecoderContext.builder().build()
                    val outputCodec = codecRegistry.get(resultClass.java)
                    val gson = GsonBuilder().serializeNulls().create()

                    while (resultSet.next()) {
                        val hashMap = resultSet.getObject(1) as Map<String, Any>
                        val mdbDocument = Document.parse(gson.toJson(hashMap))
                        val bsonDocument = mdbDocument.toBsonDocument(
                            resultClass.java,
                            codecRegistry
                        )

                        val result = outputCodec.decode(bsonDocument.asBsonReader(), decoderContext)
                        listOfResults.add(result)
                    }
                }

                listOfResults
            }
        }

    private suspend fun getConnection(): DatabaseConnection {
        val connections = DatabaseConnectionManager.getInstance().activeConnections
        val connectionHandler =
            DatabaseConnectionManager
                .getInstance()
                .build(project, dataSource)
                .setRequestor(ConnectionRequestor.Anonymous())
                .setAskPassword(true)
                .setRunConfiguration(
                    ConsoleRunConfiguration.newConfiguration(project).apply {
                        setOptionsFromDataSource(dataSource)
                    },
                )

        return connections.firstOrNull { it.connectionPoint.dataSource == dataSource }
            ?: connectionHandler.create()!!.get()
    }

    @VisibleForTesting
    fun forceConnectForTesting() {
        runBlocking {
            val connection = getConnection()
            withActiveConnectionList {
                it.add(connection)
            }
        }
    }

    @VisibleForTesting
    fun closeConnectionForTesting() {
        runBlocking {
            withActiveConnectionList {
                it.clear()
            }
        }
    }

    @VisibleForTesting
    private fun withActiveConnectionList(fn: (MutableSet<DatabaseConnection>) -> Unit) {
        runBlocking {
            val connectionsManager = DatabaseConnectionManager.getInstance()
            val myConnectionsField =
                connectionsManager.javaClass
                    .getDeclaredField("myConnections")
                    .apply {
                        isAccessible = true
                    }
            val myConnections = myConnectionsField.get(
                connectionsManager
            ) as MutableSet<DatabaseConnection>
            fn(myConnections)
            myConnectionsField.isAccessible = false
        }
    }

    private fun planByMappingStage(stage: Document, mapping: Map<String, ExplainPlan>): ExplainPlan? {
        val inputStage = stage.get("inputStage", Document::class.java)
            ?: return mapping.getOrDefault(stage["stage"], null)

        return mapping.getOrDefault(inputStage["stage"], null)
    }
}

/**
 * Returns true if the provided local data source is a MongoDB data source.
 *
 * @return
 */
fun LocalDataSource.isMongoDbDataSource(): Boolean =
    this.databaseDriver?.id?.startsWith("mongo") == true || this.databaseDriver == null

/**
 * Returns true if the provided local data source has at least one active connection
 * attached to it.
 *
 * @return
 */
fun LocalDataSource.isConnected(): Boolean =
    DatabaseConnectionManager
        .getInstance()
        .activeConnections
        .any { connection ->
            connection.connectionPoint.dataSource == dataSource &&
                runCatching {
                    !connection.remoteConnection.isClosed &&
                        connection.remoteConnection.isValid(TIMEOUT)
                }.getOrDefault(false)
        }
