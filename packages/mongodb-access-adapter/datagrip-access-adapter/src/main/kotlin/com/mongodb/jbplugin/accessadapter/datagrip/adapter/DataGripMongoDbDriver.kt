/**
 * Represents a MongoDB driver interface that uses a DataGrip
 * connection to query MongoDB.
 */

package com.mongodb.jbplugin.accessadapter.datagrip.adapter

import com.google.gson.Gson
import com.intellij.database.dataSource.DatabaseConnection
import com.intellij.database.dataSource.DatabaseConnectionManager
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.connection.ConnectionRequestor
import com.intellij.database.run.ConsoleRunConfiguration
import com.intellij.openapi.project.Project
import com.mongodb.ConnectionString
import com.mongodb.jbplugin.accessadapter.MongoDbDriver
import com.mongodb.jbplugin.accessadapter.Namespace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.bson.conversions.Bson
import org.bson.json.JsonMode
import org.bson.json.JsonWriterSettings
import org.owasp.encoder.Encode
import kotlin.reflect.KClass
import kotlin.time.Duration

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
    private val gson = Gson()
    private val jsonWriterSettings =
        JsonWriterSettings
            .builder()
            .outputMode(JsonMode.EXTENDED)
            .indent(false)
            .build()

    private fun String.encodeForJs(): String = Encode.forJavaScript(this)

    private fun Bson.toJson(): String = this.toBsonDocument().toJson(jsonWriterSettings).encodeForJs()

    override suspend fun connectionString(): ConnectionString = ConnectionString(dataSource.url!!)

    override suspend fun <T : Any> runCommand(
        database: String,
        command: Bson,
        result: KClass<T>,
        timeout: Duration,
    ): T =
        withContext(
            Dispatchers.IO,
        ) {
            runQuery(
                """
                db.getSiblingDB("${database.encodeForJs()}")
                  .runCommand(EJSON.parse("${command.toJson()}"))
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
        withContext(Dispatchers.IO) {
            runQuery(
                """db.getSiblingDB("${namespace.database.encodeForJs()}")
                 .getCollection("${namespace.collection.encodeForJs()}")
                 .findOne(EJSON.parse("${query.toJson()}"), EJSON.parse("${options.toJson()}")) 
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
    ) = withContext(Dispatchers.IO) {
        runQuery(
            """db.getSiblingDB("${namespace.database.encodeForJs()}")
                 .getCollection("${namespace.collection.encodeForJs()}")
                 .find(EJSON.parse("${query.toJson()}")).limit($limit) 
            """.trimMargin(),
            result,
            timeout,
        )
    }

    override suspend fun countAll(
        namespace: Namespace,
        query: Bson,
        timeout: Duration,
    ) = withContext(Dispatchers.IO) {
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

    suspend fun <T : Any> runQuery(
        queryString: String,
        resultClass: KClass<T>,
        timeout: Duration,
    ): List<T> =
        withContext(Dispatchers.IO) {
            val connection = getConnection()
            val remoteConnection = connection.remoteConnection
            val statement = remoteConnection.prepareStatement(queryString.trimIndent())

            withTimeout(timeout) {
                val listOfResults = mutableListOf<T>()
                val resultSet = statement.executeQuery()

                if (resultClass.java.isPrimitive || resultClass == String::class.java) {
                    while (resultSet.next()) {
                        listOfResults.add(resultSet.getObject(1) as T)
                    }
                } else {
                    while (resultSet.next()) {
                        val hashMap = resultSet.getObject(1) as Map<String, Any>
                        val result = gson.fromJson(gson.toJson(hashMap), resultClass.java)
                        listOfResults.add(result)
                    }
                }

                listOfResults
            }
        }

    private suspend fun getConnection(): DatabaseConnection {
        val connections = DatabaseConnectionManager.getInstance().activeConnections
        return connections.firstOrNull { it.connectionPoint.dataSource == dataSource }
            ?: DatabaseConnectionManager.establishConnection(
                dataSource,
                ConsoleRunConfiguration.newConfiguration(project).apply {
                    setOptionsFromDataSource(dataSource)
                },
                ConnectionRequestor.Anonymous(),
                project,
                true, // if password is not available
            )!!
    }
}
