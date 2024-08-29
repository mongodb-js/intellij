package com.mongodb.jbplugin.accessadapter.slice

import com.mongodb.client.model.Filters
import com.mongodb.jbplugin.accessadapter.MongoDbDriver
import com.mongodb.jbplugin.mql.*
import org.bson.Document
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

import kotlinx.coroutines.runBlocking

class GetCollectionSchemaTest {
    @Test
    fun `returns an empty schema if the database is not provided`() {
        runBlocking {
            val namespace = Namespace("", "myColl")
            val driver = mock<MongoDbDriver>()
            val result = GetCollectionSchema.Slice(namespace).queryUsingDriver(driver)

            assertEquals(namespace, result.schema.namespace)
            assertEquals(
                BsonObject(
                    emptyMap(),
                ),
                result.schema.schema,
            )

            verify(driver, never()).findAll(namespace, Filters.empty(), Document::class, 50)
        }
    }

    @Test
    fun `returns an empty schema if the collection is not provided`() {
        runBlocking {
            val namespace = Namespace("myDb", "")
            val driver = mock<MongoDbDriver>()
            val result = GetCollectionSchema.Slice(namespace).queryUsingDriver(driver)

            assertEquals(namespace, result.schema.namespace)
            assertEquals(
                BsonObject(
                    emptyMap(),
                ),
                result.schema.schema,
            )

            verify(driver, never()).findAll(namespace, Filters.empty(), Document::class, 50)
        }
    }

    @Test
    fun `should build a schema based on the result of the query`() {
        runBlocking {
            val namespace = Namespace("myDb", "myColl")
            val driver = mock<MongoDbDriver>()

            `when`(driver.findAll(namespace, Filters.empty(), Document::class, 50)).thenReturn(
                listOf(
                    Document(mapOf("string" to "myString")),
                    Document(mapOf("integer" to 52, "string" to "anotherString")),
                ),
            )
            val result = GetCollectionSchema.Slice(namespace).queryUsingDriver(driver)

            assertEquals(namespace, result.schema.namespace)
            assertEquals(
                BsonObject(
                    mapOf(
                        "string" to BsonAnyOf(BsonNull, BsonString),
                        "integer" to BsonInt32,
                    ),
                ),
                result.schema.schema,
            )
        }
    }

    @Test
    fun `should be aware of different shapes of sub documents`() {
        runBlocking {
            val namespace = Namespace("myDb", "myColl")
            val driver = mock<MongoDbDriver>()

            `when`(driver.findAll(namespace, Filters.empty(), Document::class, 50)).thenReturn(
                listOf(
                    Document(mapOf("book" to Document(mapOf("author" to "Someone")))),
                    Document(mapOf("book" to Document(mapOf("author" to "Someone Else", "isbn" to "XXXXXXXX")))),
                ),
            )
            val result = GetCollectionSchema.Slice(namespace).queryUsingDriver(driver)

            assertEquals(namespace, result.schema.namespace)
            assertEquals(
                BsonObject(
                    mapOf(
                        "book" to
                                BsonObject(
                                    mapOf(
                                        "author" to BsonAnyOf(BsonString, BsonNull),
                                        "isbn" to BsonAnyOf(BsonString, BsonNull),
                                    ),
                                ),
                    ),
                ),
                result.schema.schema,
            )
        }
    }

    @Test
    fun `should be aware of arrays with different types of elements`() {
        runBlocking {
            val namespace = Namespace("myDb", "myColl")
            val driver = mock<MongoDbDriver>()

            `when`(driver.findAll(namespace, Filters.empty(), Document::class, 50)).thenReturn(
                listOf(
                    Document(mapOf("array" to arrayOf(1, 2, 3, "abc"))),
                    Document(mapOf("array" to arrayOf(1.2f, "jkl"))),
                ),
            )
            val result = GetCollectionSchema.Slice(namespace).queryUsingDriver(driver)

            assertEquals(namespace, result.schema.namespace)
            assertEquals(
                BsonObject(
                    mapOf(
                        "array" to BsonArray(BsonAnyOf(BsonNull, BsonString, BsonDouble, BsonInt32)),
                    ),
                ),
                result.schema.schema,
            )
        }
    }
}
