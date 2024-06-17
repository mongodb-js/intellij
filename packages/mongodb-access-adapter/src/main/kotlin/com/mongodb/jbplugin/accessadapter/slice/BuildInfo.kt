/**
 * A slice that represents the build information of the connected cluster.
 */

package com.mongodb.jbplugin.accessadapter.slice

import com.mongodb.client.model.Filters
import com.mongodb.jbplugin.accessadapter.MongoDbDriver
import com.mongodb.jbplugin.accessadapter.toNs
import org.bson.Document

/**
 * Slice to be used when querying the MongoDbReadModelProvider.
 *
 * @see com.mongodb.jbplugin.accessadapter.slice.BuildInfo.Slice
 * @see com.mongodb.jbplugin.accessadapter.MongoDbReadModelProvider.slice
 * @property version
 * @property gitVersion
 * @property modules
 * @property isLocalhost
 * @property isDataLake
 * @property isEnterprise
 * @property isAtlas
 * @property isLocalAtlas
 * @property isAtlasStream
 * @property isDigitalOcean
 * @property isGenuineMongoDb
 * @property nonGenuineVariant
 * @property serverUrl
 * @property buildEnvironment
 */
data class BuildInfo(
    val version: String,
    val gitVersion: String?,
    val modules: List<String>?,
    val isLocalhost: Boolean,
    val isDataLake: Boolean,
    val isEnterprise: Boolean,
    val isAtlas: Boolean,
    val isLocalAtlas: Boolean,
    val isAtlasStream: Boolean,
    val isDigitalOcean: Boolean,
    val isGenuineMongoDb: Boolean,
    val nonGenuineVariant: String?,
    val serverUrl: String,
    val buildEnvironment: Map<String, String>,
) {
    object Slice : com.mongodb.jbplugin.accessadapter.Slice<BuildInfo> {
        private val atlasRegex = Regex(""".*\.mongodb(-dev|-qa|-stage)?\.net$""")
        private val atlasStreamRegex = Regex("""^atlas-stream-.+""")
        private val isLocalhostRegex =
            Regex(
                "^(localhost" +
                    "|127.([01]?[0-9][0-9]?|2[0-4][0-9]|25[0-5])" +
                    ".([01]?[0-9][0-9]?|2[0-4][0-9]|25[0-5])" +
                    ".([01]?[0-9][0-9]?|2[0-4][0-9]|25[0-5])" +
                    "|0.0.0.0" +
                    "|(?:0*:)*?:?0*1" +
                    ")$",
            )
        private val digitalOceanRegex = Regex(""".*\.mongo\.ondigitalocean\.com$""")
        private val cosmosDbRegex = Regex(""".*\.cosmos\.azure\.com$""")
        private val docDbRegex = Regex(""".*docdb(-elastic)?\.amazonaws\.com$""")

        override suspend fun queryUsingDriver(from: MongoDbDriver): BuildInfo {
            val url = from.serverUri()
            val isLocalHost = url.host.matches(isLocalhostRegex)
            val isAtlas = url.host.matches(atlasRegex)
            val isLocalAtlas =
                from.countAll(
                    "admin.atlascli".toNs(),
                    Filters.eq("managedClusterType", "atlasCliLocalDevCluster"),
                ) > 0
            val isAtlasStream = url.host.matches(atlasRegex) && url.host.matches(atlasStreamRegex)
            val isDigitalOcean = url.host.matches(digitalOceanRegex)
            val genuineVariant =
                if (url.host.matches(cosmosDbRegex)) {
                    "cosmosdb"
                } else if (url.host.matches(docDbRegex)) {
                    "documentdb"
                } else {
                    null
                }

            val buildInfo =
                from.runCommand(
                    Document(
                        mapOf(
                            "buildInfo" to 1,
                        ),
                    ),
                    BuildInfo::class,
                )

            return buildInfo.copy(
                isLocalhost = isLocalHost,
                isEnterprise =
                    buildInfo.gitVersion?.contains("enterprise") == true ||
                        buildInfo.modules?.contains("enterprise") == true,
                isAtlas = isAtlas,
                isLocalAtlas = isLocalAtlas,
                isAtlasStream = isAtlasStream,
                isDigitalOcean = isDigitalOcean,
                isGenuineMongoDb = genuineVariant == null,
                nonGenuineVariant = genuineVariant,
                serverUrl = url.toASCIIString(),
            )
        }
    }
}
