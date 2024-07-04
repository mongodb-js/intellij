package com.mongodb.jbplugin.mql

import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.Named
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NodeTest {
    @Test
    fun `is able to get a component if exists`() {
        val node = Node<Unit?>(null, listOf(Named("myName")))
        val named = node.component<Named>()

        assertEquals("myName", named!!.name)
    }

    @Test
    fun `returns null if a component does not exist`() {
        val node = Node<Unit?>(null, listOf(Named("myName")))
        val named = node.component<HasFieldReference>()

        assertNull(named)
    }

    @Test
    fun `is able to get all components of the same type`() {
        val node =
            Node<Unit?>(
                null,
                listOf(HasFieldReference(HasFieldReference.Known("field1")), HasFieldReference(HasFieldReference.Known(
"field2"
))),
            )
        val fieldReferences = node.components<HasFieldReference>()

        assertEquals("field1", (fieldReferences[0].reference as HasFieldReference.Known).fieldName)
        assertEquals("field2", (fieldReferences[1].reference as HasFieldReference.Known).fieldName)
    }

    @Test
    fun `returns true if a component of that type exists`() {
        val node =
            Node<Unit?>(
                null,
                listOf(HasFieldReference(HasFieldReference.Known("field1"))),
            )
        val hasFieldReferences = node.hasComponent<HasFieldReference>()

        assertTrue(hasFieldReferences)
    }

    @Test
    fun `returns false if a component of that type does not exist`() {
        val node =
            Node<Unit?>(
                null,
                listOf(HasFieldReference(HasFieldReference.Known("field1"))),
            )
        val hasNamedComponent = node.hasComponent<Named>()

        assertFalse(hasNamedComponent)
    }
}
