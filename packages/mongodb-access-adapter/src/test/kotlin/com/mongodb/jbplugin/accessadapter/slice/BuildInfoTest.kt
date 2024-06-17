package com.mongodb.jbplugin.accessadapter.slice

import com.mongodb.client.model.Filters
import com.mongodb.jbplugin.accessadapter.MongoDbDriver
import com.mongodb.jbplugin.accessadapter.toNs
import org.bson.Document
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.kotlin.eq

import java.net.URI

import kotlinx.coroutines.runBlocking

class BuildInfoTest {
    @Test
    fun `returns a valid build info`(): Unit =
        runBlocking {
            val command = Document(mapOf("buildInfo" to 1))
            val driver = Mockito.mock<MongoDbDriver>()
            `when`(driver.serverUri()).thenReturn(URI.create("mongodb://localhost/"))
            `when`(
                driver.countAll("admin.atlascli".toNs(), Filters.eq("managedClusterType", "atlasCliLocalDevCluster")),
            ).thenReturn(1L)
            `when`(driver.runCommand(command, BuildInfo::class)).thenReturn(
                BuildInfo(
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
                    "mongodb://localhost",
                    buildEnvironment = emptyMap(),
                ),
            )

            val data = BuildInfo.Slice.queryUsingDriver(driver)
            Assertions.assertEquals("7.8.0", data.version)
            Assertions.assertEquals("1235abc", data.gitVersion)
        }
}
