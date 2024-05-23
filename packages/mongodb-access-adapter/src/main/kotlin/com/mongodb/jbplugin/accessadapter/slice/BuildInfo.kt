/**
 * A slice that represents the build information of the connected cluster.
 */

package com.mongodb.jbplugin.accessadapter.slice

import com.mongodb.jbplugin.accessadapter.MongoDbDriver
import org.bson.Document

/**
 * Slice to be used when querying the MongoDbReadModelProvider.
 *
 * @see com.mongodb.jbplugin.accessadapter.slice.BuildInfo.Slice
 * @see com.mongodb.jbplugin.accessadapter.MongoDbReadModelProvider.slice
 * @property version
 * @property gitVersion
 */
data class BuildInfo(
    val version: String,
    val gitVersion: String
) {
    object Slice : com.mongodb.jbplugin.accessadapter.Slice<BuildInfo> {
        override suspend fun queryUsingDriver(from: MongoDbDriver): BuildInfo = from.runCommand(
            Document(
                mapOf(
                    "buildInfo" to 1,
                )
            ),
            BuildInfo::class
        )
    }
}