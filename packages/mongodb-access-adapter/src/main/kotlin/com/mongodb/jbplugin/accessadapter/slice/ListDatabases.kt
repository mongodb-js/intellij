/**
 * A slice that represents the build information of the connected cluster.
 */

package com.mongodb.jbplugin.accessadapter.slice

import com.mongodb.jbplugin.accessadapter.MongoDbDriver
import org.bson.Document

/**
 * @property databases
 */
data class ListDatabases(
    val databases: List<Database>,
) {
    object Slice : com.mongodb.jbplugin.accessadapter.Slice<ListDatabases> {
        override suspend fun queryUsingDriver(from: MongoDbDriver): ListDatabases {
            val result =
                from.runCommand(
                    "admin",
                    Document(
                        mapOf(
                            "listDatabases" to 1,
                        ),
                    ),
                    Document::class,
                )

            val databases = result.getList("databases", Map::class.java)
            return ListDatabases(
                databases.map {
                    Database(it["name"].toString())
                },
            )
        }
    }

/**
 * @property name
 */
data class Database(
        val name: String,
    )
}
