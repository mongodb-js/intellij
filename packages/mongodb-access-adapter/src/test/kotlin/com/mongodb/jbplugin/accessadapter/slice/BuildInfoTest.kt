package com.mongodb.jbplugin.accessadapter.slice

import com.mongodb.ConnectionString
import com.mongodb.client.model.Filters
import com.mongodb.jbplugin.accessadapter.MongoDbDriver
import com.mongodb.jbplugin.accessadapter.toNs
import org.bson.Document
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mockito
import org.mockito.Mockito.`when`

import kotlinx.coroutines.runBlocking

class BuildInfoTest {
    @Test
    fun `returns a valid build info`(): Unit =
        runBlocking {
            val command = Document(mapOf("buildInfo" to 1))
            val driver = Mockito.mock<MongoDbDriver>()
            `when`(driver.connectionString()).thenReturn(ConnectionString("mongodb://localhost/"))
            `when`(
                driver.countAll("admin.atlascli".toNs(), Filters.eq("managedClusterType", "atlasCliLocalDevCluster")),
            ).thenReturn(1L)
            `when`(driver.runCommand("admin", command, BuildInfo::class)).thenReturn(
                defaultBuildInfo("mongodb://localhost"),
            )

            val data = BuildInfo.Slice.queryUsingDriver(driver)
            assertEquals("7.8.0", data.version)
            assertEquals("1235abc", data.gitVersion)
        }

    @ParameterizedTest
    @CsvSource(value = [
        "URL;;isLocalhost;;isAtlas;;isAtlasStream;;isDigitalOcean;;isGenuineMongoDb;;mongodbVariant",
        "mongodb://localhost;;true;;false;;false;;false;;true;;",
        "mongodb://localhost,another-server;;false;;false;;false;;false;;true;;",
        "mongodb+srv://example-atlas-cluster.e06cc.mongodb.net;;false;;true;;false;;false;;true;;",
        "mongodb://example-atlas-cluster.e06cc.mongodb.net,another-server;;false;;false;;false;;false;;true;;",
        "mongodb+srv://atlas-stream-example-atlas-stream.e06cc.mongodb.net;;false;;true;;true;;false;;true;;",
        "mongodb://[::1];;true;;false;;false;;false;;true;;",
        "mongodb://my-cluster.mongo.ondigitalocean.com;;false;;false;;false;;true;;true;;",
        "mongodb://my-cluster.cosmos.azure.com;;false;;false;;false;;false;;false;;cosmosdb",
        "mongodb://my-cluster.docdb.amazonaws.com;;false;;false;;false;;false;;false;;documentdb",
        "mongodb://my-cluster.docdb-elastic.amazonaws.com;;false;;false;;false;;false;;false;;documentdb",
    ],
 delimiterString = ";;",
 useHeadersInDisplayName = true
)
    fun `parses different type of url connections properly`(
        url: String,
        isLocalhost: Boolean,
        isAtlas: Boolean,
        isAtlasStream: Boolean,
        isDigitalOcean: Boolean,
        isGenuineMongoDb: Boolean,
        mongodbVariant: String?
    ): Unit = runBlocking {
        val command = Document(mapOf("buildInfo" to 1))
        val driver = Mockito.mock<MongoDbDriver>()
        `when`(driver.connectionString()).thenReturn(ConnectionString(url))
        `when`(
            driver.countAll("admin.atlascli".toNs(), Filters.eq("managedClusterType", "atlasCliLocalDevCluster")),
        ).thenReturn(1L)
        `when`(driver.runCommand("admin", command, BuildInfo::class)).thenReturn(
            defaultBuildInfo(url),
        )

        val data = BuildInfo.Slice.queryUsingDriver(driver)
        assertEquals(isLocalhost, data.isLocalhost, "isLocalhost does not match")
        assertEquals(isAtlas, data.isAtlas, "isAtlas does not match")
        assertEquals(isAtlasStream, data.isAtlasStream, "isAtlasStream does not match")
        assertEquals(isDigitalOcean, data.isDigitalOcean, "isDigitalOcean does not match")
        assertEquals(isGenuineMongoDb, data.isGenuineMongoDb, "isGenuineMongoDb does not match")
        assertEquals(mongodbVariant, data.nonGenuineVariant, "mongodbVariant does not match")
    }

    private fun defaultBuildInfo(url: String) = BuildInfo(
        "7.8.0",
        "1235abc",
        emptyList(),
        false,
        false,
        false,
        false,
        false,
        false,
        false,
        false,
        null,
        url,
        buildEnvironment = emptyMap(),
    )
}
