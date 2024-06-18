package com.mongodb.jbplugin.accessadapter.datagrip.adapter

import com.mongodb.jbplugin.accessadapter.MongoDbDriver
import com.mongodb.jbplugin.accessadapter.datagrip.IntegrationTest
import com.mongodb.jbplugin.accessadapter.datagrip.MongoDbVersion
import org.bson.Document
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

import kotlinx.coroutines.runBlocking

@IntegrationTest
class DataGripMongoDbDriverTest {
    @Test
    fun `can connect and run a command`(version: MongoDbVersion, driver: MongoDbDriver) = runBlocking {
        val result = driver.runCommand(
            "admin",
            Document(
                mapOf(
                    "buildInfo" to 1,
                )
            ), Map::class
        )

        assertEquals(result["version"], version.versionString)
    }

    @Test
    fun `is able to map the result to a class`(version: MongoDbVersion, driver: MongoDbDriver) = runBlocking {
        data class MyBuildInfo(val version: String)

        val result = driver.runCommand(
            "admin",
            Document(
                mapOf(
                    "buildInfo" to 1,
                )
            ), MyBuildInfo::class
        )

        assertEquals(result.version, version.versionString)
    }
}