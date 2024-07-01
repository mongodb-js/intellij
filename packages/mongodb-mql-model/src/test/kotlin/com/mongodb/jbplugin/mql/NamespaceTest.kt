package com.mongodb.jbplugin.mql

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NamespaceTest {
    @Test
    fun `serialises to a valid namespace string`() {
        val namespace = Namespace("mydb", "my.cool.col")
        assertEquals("mydb.my.cool.col", namespace.toString())
    }
}
