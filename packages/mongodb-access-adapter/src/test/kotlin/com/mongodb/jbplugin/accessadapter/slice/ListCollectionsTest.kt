package com.mongodb.jbplugin.accessadapter.slice

import com.mongodb.jbplugin.accessadapter.MongoDbDriver
import org.bson.Document
import org.bson.conversions.Bson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking

class ListCollectionsTest {
    @Test
    fun `returns no collections if database is not provided`() {
        runBlocking {
            val driver = mock<MongoDbDriver>()
            val result = ListCollections.Slice("").queryUsingDriver(driver)

            assertTrue(result.collections.isEmpty())
            verify(driver, never()).runCommand(eq(""), any(), eq(Document::class), eq(1.seconds))
        }
    }

    @Test
    fun `returns collections if the database is provided`() {
        runBlocking {
            val driver = mock<MongoDbDriver>()

            `when`(driver.runCommand("myDb", listCollections(), Document::class)).thenReturn(
                Document(
                    mapOf(
                        "cursor" to mapOf(
                            "firstBatch" to listOf(
                                mapOf("name" to "myCollection", "type" to "collection")
                            )
                        )
                    )
                )
            )

            val result = ListCollections.Slice("myDb").queryUsingDriver(driver)

            assertEquals(listOf(
                ListCollections.Collection("myCollection", "collection")
            ), result.collections)
        }
    }

    private fun listCollections(): Bson = Document(
            mapOf(
                "listCollections" to 1,
                "authorizedCollections" to true,
            ),
        )
}
