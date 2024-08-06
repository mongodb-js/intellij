/**
 * A slice that represents the build information of the connected cluster.
 */

package com.mongodb.jbplugin.accessadapter.slice

import com.mongodb.ConnectionString
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
    val serverUrl: ConnectionString?,
    val buildEnvironment: Map<String, String>,
) {
    val atlasHost: String?
        get() = serverUrl?.hosts?.getOrNull(0)
?.replace(Regex(""":\d+"""), "")
        .takeIf { isAtlas }
    object Slice : com.mongodb.jbplugin.accessadapter.Slice<BuildInfo> {
        override val id = javaClass.canonicalName
        private val atlasRegex = Regex(""".*\.mongodb(-dev|-qa|-stage)?\.net(:\d+)?$""")
        private val atlasStreamRegex = Regex("""^atlas-stream-.+""")
        private val isLocalhostRegex =
            Regex(
                "^(localhost" +
                    "|127.([01]?[0-9][0-9]?|2[0-4][0-9]|25[0-5])" +
                    ".([01]?[0-9][0-9]?|2[0-4][0-9]|25[0-5])" +
                    ".([01]?[0-9][0-9]?|2[0-4][0-9]|25[0-5])" +
                    "|0.0.0.0" +
                    "|\\[(?:0*:)*?:?0*1]" +
                    ")(:[0-9]+)?$",
            )
        private val digitalOceanRegex = Regex(""".*\.mongo\.ondigitalocean\.com$""")
        private val cosmosDbRegex = Regex(""".*\.cosmos\.azure\.com$""")
        private val docDbRegex = Regex(""".*docdb(-elastic)?\.amazonaws\.com$""")

        override suspend fun queryUsingDriver(from: MongoDbDriver): BuildInfo {
            val connectionString = from.connectionString()
            val isLocalHost = connectionString.hosts.all { it.matches(isLocalhostRegex) }
            val isAtlas = connectionString.hosts.all { it.matches(atlasRegex) }
            val isLocalAtlas = checkIsAtlasCliIfConnected(from)

            val isAtlasStream = connectionString.hosts.all { it.matches(atlasRegex) && it.matches(atlasStreamRegex) }
            val isDigitalOcean = connectionString.hosts.all { it.matches(digitalOceanRegex) }
            val nonGenuineVariant =
                if (connectionString.hosts.all { it.matches(cosmosDbRegex) }) {
                    "cosmosdb"
                } else if (connectionString.hosts.all { it.matches(docDbRegex) }) {
                    "documentdb"
                } else {
                    null
                }

            val buildInfo =
                runBuildInfoCommandIfConnected(from)

            return buildInfo.copy(
                isLocalhost = isLocalHost,
                isEnterprise =
                    buildInfo.gitVersion?.contains("enterprise") == true ||
                        buildInfo.modules?.contains("enterprise") == true,
                isAtlas = isAtlas,
                isLocalAtlas = isLocalAtlas,
                isAtlasStream = isAtlasStream,
                isDigitalOcean = isDigitalOcean,
                isGenuineMongoDb = nonGenuineVariant == null,
                nonGenuineVariant = nonGenuineVariant,
                serverUrl = connectionString,
            )
        }

        private suspend fun checkIsAtlasCliIfConnected(from: MongoDbDriver) =
            if (from.connected) {
                from.countAll(
                    "admin.atlascli".toNs(),
                    Filters.eq("managedClusterType", "atlasCliLocalDevCluster"),
                ) > 0
            } else {
                false
            }

        private suspend fun runBuildInfoCommandIfConnected(from: MongoDbDriver) =
            if (from.connected) {
                from.runCommand(
                    "admin",
                    Document(
                        mapOf(
                            "buildInfo" to 1,
                        ),
                    ),
                    BuildInfo::class,
                )
            } else {
                empty()
            }
    }

    companion object {
        fun empty(): BuildInfo =
            BuildInfo(
                "<unknown>",
                null,
                null,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                null,
                null,
                emptyMap(),
            )
    }
}
