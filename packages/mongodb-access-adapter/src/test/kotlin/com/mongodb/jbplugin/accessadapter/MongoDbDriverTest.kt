package com.mongodb.jbplugin.accessadapter

import com.mongodb.jbplugin.mql.Namespace
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MongoDbDriverTest {
    @Test
    fun `parses a namespace`() {
        val namespace = "mydb.mycoll".toNs()
        assertEquals("mydb", namespace.database)
        assertEquals("mycoll", namespace.collection)
    }

    @Test
    fun `parses a namespace where collections have dots in a name`() {
        val namespace = "mydb.myco.ll".toNs()
        assertEquals("mydb", namespace.database)
        assertEquals("myco.ll", namespace.collection)
    }

    @Test
    fun `removes trailing spaces`() {
        val namespace = """ mydb.myco"ll    """.toNs()
        assertEquals("mydb", namespace.database)
        assertEquals("myco\"ll", namespace.collection)
    }

    @Test
    fun `can parse back a serialised namespace`() {
        val namespace = Namespace("mydb", "my.cool.col")
        val deserialized = namespace.toString().toNs()

        assertEquals(namespace, deserialized)
    }
}
