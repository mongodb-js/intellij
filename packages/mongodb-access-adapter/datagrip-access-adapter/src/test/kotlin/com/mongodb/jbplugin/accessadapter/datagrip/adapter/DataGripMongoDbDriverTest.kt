package com.mongodb.jbplugin.accessadapter.datagrip.adapter

import com.mongodb.client.model.Filters
import com.mongodb.jbplugin.accessadapter.ExplainPlan
import com.mongodb.jbplugin.accessadapter.MongoDbDriver
import com.mongodb.jbplugin.accessadapter.datagrip.IntegrationTest
import com.mongodb.jbplugin.accessadapter.datagrip.MongoDbVersion
import com.mongodb.jbplugin.accessadapter.toNs
import com.mongodb.jbplugin.mql.BsonString
import com.mongodb.jbplugin.mql.Namespace
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasFilter
import com.mongodb.jbplugin.mql.components.HasValueReference
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import org.bson.Document
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.*
import kotlin.time.Duration.Companion.seconds

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
        driver: MongoDbDriver,
    ) = runBlocking {
        driver as DataGripMongoDbDriver

        data class ExampleDocument(
            val text: String,
            val date: Date,
            val decimal: BigDecimal
        )

        val decimalValue = BigDecimal("52.3249824889273498237498")
        val dateString = "2024-08-09T12:06:00.467Z"
        val dateValue = Date.from(Instant.parse(dateString).toJavaInstant())

        driver.runQuery(
            """
            db.docs
            .insertOne(
                { text: "myExampleTest", 
                  date: ISODate('$dateString'), 
                  decimal: { ${'$'}numberDecimal: "52.3249824889273498237498" }
                }
            )
            """.trimIndent(),
            Unit::class,
            5.seconds
        )

        val result =
            driver.findOne(
                "test.docs".toNs(),
                Filters.eq("text", "myExampleTest"),
                Document(),
                ExampleDocument::class,
            )

        assertEquals(result?.text, "myExampleTest")
        assertEquals(result?.date, dateValue)
        assertEquals(result?.decimal, decimalValue)
    }

    @Test
    fun `is able to find a list of documents and deserialize it properly`(
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

    @Test
    fun `it is able to run an explain plan given a query and returns a collscan if no index available`(
        driver: MongoDbDriver,
    ) = runBlocking {
        val namespace = Namespace("myDb", "myCollection")

        val query = Node(
            Unit,
            listOf(
                HasCollectionReference(HasCollectionReference.Known(Unit, Unit, namespace)),
                HasFilter(
                    listOf(
                        Node(
                            Unit,
                            listOf(
                                HasFieldReference(HasFieldReference.FromSchema(Unit, "myField")),
                                HasValueReference(
                                    HasValueReference.Constant(Unit, "myVal", BsonString)
                                ),
                            )
                        )
                    )
                )
            )
        )

        driver.runCommand(
            namespace.database,
            Document(
                mapOf(
                    "insert" to namespace.collection,
                    "documents" to
                        listOf(
                            mapOf("a" to 1),
                        ),
                ),
            ),
            Unit::class,
        )

        val explainPlanResult = driver.explain(query)
        assertEquals(ExplainPlan.CollectionScan, explainPlanResult)
    }

    @Test
    fun `it is able to run an explain plan given a query and returns a indexscan if an index available`(
        driver: MongoDbDriver,
    ) = runBlocking {
        val namespace = Namespace("myDb", "myCollection")

        driver.runCommand(
            namespace.database,
            Document(
                mapOf(
                    "createIndexes" to namespace.collection,
                    "indexes" to arrayOf(
                        Document(
                            mapOf(
                                "key" to Document("myField", 1),
                                "name" to "myField_1"
                            )
                        )
                    )
                )
            ),
            Unit::class
        )

        val query = Node(
            Unit,
            listOf(
                HasCollectionReference(HasCollectionReference.Known(Unit, Unit, namespace)),
                HasFilter(
                    listOf(
                        Node(
                            Unit,
                            listOf(
                                HasFieldReference(HasFieldReference.FromSchema(Unit, "myField")),
                                HasValueReference(
                                    HasValueReference.Constant(Unit, "myVal", BsonString)
                                ),
                            )
                        )
                    )
                )
            )
        )

        val explainPlanResult = driver.explain(query)
        assertEquals(ExplainPlan.IndexScan, explainPlanResult)
    }
}
