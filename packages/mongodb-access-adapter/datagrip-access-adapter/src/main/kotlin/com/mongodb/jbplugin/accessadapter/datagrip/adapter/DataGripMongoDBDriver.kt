package com.mongodb.jbplugin.accessadapter.datagrip.adapter

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.project.Project
import com.mongodb.jbplugin.accessadapter.MongoDBDriver
import com.mongodb.jbplugin.accessadapter.Namespace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bson.Document
import kotlin.reflect.KClass
import kotlin.time.Duration

class DataGripMongoDBDriver(
    val project: Project,
    val dataSource: LocalDataSource
) : MongoDBDriver {
    override suspend fun <T : Any> runCommand(command: Document, result: KClass<T>, timeout: Duration): T = withContext(Dispatchers.IO) {
        DataSourceQuery(project, dataSource, result).runQuery(
            """db.runCommand(${command.toJson()})""",
            timeout
        )[0]
    }

    override suspend fun <T : Any> findOne(
        namespace: Namespace,
        query: Document,
        options: Document,
        result: KClass<T>,
        timeout: Duration
    ): T? = withContext(Dispatchers.IO) {
        DataSourceQuery(project, dataSource, result).runQuery(
            """db.getSiblingDB("${namespace.database}")
                 .getCollection("${namespace.collection}")
                 .findOne(${query.toJson()}, ${options.toJson()}) """.trimMargin(),
            timeout
        ).getOrNull(0)
    }

    override suspend fun <T : Any> findAll(
        namespace: Namespace,
        query: Document,
        result: KClass<T>,
        limit: Int,
        timeout: Duration
    ) = withContext(Dispatchers.IO) {
        DataSourceQuery(project, dataSource, result).runQuery(
            """db.getSiblingDB("${namespace.database}")
                 .getCollection("${namespace.collection}")
                 .find(${query.toJson()}).limit(${limit}) """.trimMargin(),
            timeout
        )
    }
}