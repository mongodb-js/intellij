package com.mongodb.jbplugin.accessadapter.datagrip.adapter

import com.mongodb.client.model.Filters
import com.mongodb.jbplugin.accessadapter.MongoDbDriver
import com.mongodb.jbplugin.accessadapter.datagrip.IntegrationTest
import com.mongodb.jbplugin.accessadapter.datagrip.MongoDbVersion
import com.mongodb.jbplugin.accessadapter.toNs
import org.bson.Document
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

import kotlinx.coroutines.runBlocking

@IntegrationTest
class DataGripMongoDbDriverTest {
    @Test
    fun `can connect and run a command`(
        version: MongoDbVersion,
        driver: MongoDbDriver,
    ) = runBlocking {
        val result =
            driver.runCommand(
                "admin",
                Document(
                    mapOf(
                        "buildInfo" to 1,
                    ),
                ),
                Map::class,
            )

        assertEquals(result["version"], version.versionString)
    }

    @Test
    fun `is able to map the result to a class`(
        version: MongoDbVersion,
        driver: MongoDbDriver,
    ) = runBlocking {
        data class MyBuildInfo(
            val version: String,
        )

        val result =
            driver.runCommand(
                "admin",
                Document(
                    mapOf(
                        "buildInfo" to 1,
                    ),
                ),
                MyBuildInfo::class,
            )

        assertEquals(result.version, version.versionString)
    }

    @Test
    fun `is able to find a document and deserialize it properly`(
        version: MongoDbVersion,
        driver: MongoDbDriver,
    ) = runBlocking {
        data class ExampleDocument(
            val text: String,
        )

        driver.runCommand(
            "test",
            Document(
                mapOf(
                    "insert" to "docs",
                    "documents" to
                        listOf(
                            ExampleDocument("myExampleTest"),
                        ),
                ),
            ),
            Unit::class,
        )

        val result =
            driver.findOne(
                "test.docs".toNs(),
                Filters.eq("text", "myExampleTest"),
                Document(),
                ExampleDocument::class,
            )

        assertEquals(result?.text, "myExampleTest")
    }

    @Test
    fun `is able to find a list of documents and deserialize it properly`(
        version: MongoDbVersion,
        driver: MongoDbDriver,
    ) = runBlocking {
        data class ExampleDocument(
            val text: String,
        )

        driver.runCommand(
            "test",
            Document(
                mapOf(
                    "insert" to "docs",
                    "documents" to
                        listOf(
                            ExampleDocument("myExampleTest"),
                            ExampleDocument("myExampleTest2"),
                        ),
                ),
            ),
            Unit::class,
        )

        val result =
            driver.findAll(
                "test.docs".toNs(),
                Filters.empty(),
                ExampleDocument::class,
            )

        assertEquals(2, result.size)
        assertEquals(result[0].text, "myExampleTest")
        assertEquals(result[1].text, "myExampleTest2")
    }

    @Test
    fun `is able to count the result of a query`(
        version: MongoDbVersion,
        driver: MongoDbDriver,
    ) = runBlocking {
        data class ExampleDocument(
            val text: String,
        )

        driver.runCommand(
            "test",
            Document(
                mapOf(
                    "insert" to "docs",
                    "documents" to
                        listOf(
                            ExampleDocument("myExampleTest"),
                            ExampleDocument("myExampleTest2"),
                        ),
                ),
            ),
            Unit::class,
        )

        val result =
            driver.countAll(
                "test.docs".toNs(),
                Filters.empty(),
            )

        assertEquals(2, result)
    }
}
