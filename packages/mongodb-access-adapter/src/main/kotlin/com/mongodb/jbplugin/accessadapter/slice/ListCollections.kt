/**
 * A slice that represents the build information of the connected cluster.
 */

package com.mongodb.jbplugin.accessadapter.slice

import com.mongodb.jbplugin.accessadapter.MongoDbDriver
import org.bson.Document
import kotlin.time.Duration.Companion.seconds

/**
 * @property collections
 */
data class ListCollections(
    val collections: List<Collection>,
) {
    /**
     * @property name
     * @property type
     */
    data class Collection(
        val name: String,
        val type: String,
    )

    /**
     * @param database
     */
    data class Slice(
        private val database: String,
    ) : com.mongodb.jbplugin.accessadapter.Slice<ListCollections> {
        override val id: String
            get() = "${javaClass.canonicalName}::$database"

        override suspend fun queryUsingDriver(from: MongoDbDriver): ListCollections {
            if (database.isBlank()) {
                return ListCollections(emptyList())
            }

            val result =
                from.runCommand(
                    database,
                    Document(
                        mapOf(
                            "listCollections" to 1,
                            "authorizedCollections" to true,
                        ),
                    ),
                    Document::class,
                    1.seconds
                )

            val collectionMetadata = result.get("cursor", Map::class.java)
            val collections = collectionMetadata.get("firstBatch") as List<Map<String, *>>

            return ListCollections(
                collections.map {
                    Collection(it["name"].toString(), it["type"].toString())
                },
            )
        }
    }
}
