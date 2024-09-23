package com.mongodb.jbplugin.mql

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NamespaceTest {
    @Test
    fun `serialises to a valid namespace string`() {
        val namespace = Namespace("mydb", "my.cool.col")
        assertEquals("mydb.my.cool.col", namespace.toString())
    }

    @Test
    fun `is not valid if both database and collections are provided`() {
        val namespace = Namespace("mydb", "mycoll")
        assertTrue(namespace.isValid)
    }

    @Test
    fun `is not valid if the database is empty`() {
        val namespace = Namespace("", "my.cool.col")
        assertFalse(namespace.isValid)
    }

    @Test
    fun `is not valid if the collection is empty`() {
        val namespace = Namespace("mydb", "")
        assertFalse(namespace.isValid)
    }
}
