package com.mongodb.jbplugin.mql

import com.mongodb.jbplugin.mql.components.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

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
                listOf(
                    HasFieldReference(HasFieldReference.Known("field1")),
                    HasFieldReference(
                        HasFieldReference.Known(
                            "field2",
                        ),
                    ),
                ),
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

    @MethodSource("validComponents")
    @ParameterizedTest
    fun `does support the following component`(
        component: Component,
        componentClass: Class<Component>,
    ) {
        val node =
            Node<Unit?>(
                null,
                listOf(
                    component,
                ),
            )

        assertNotNull(node.component(componentClass))
    }

    companion object {
        @JvmStatic
        fun validComponents(): Array<Array<Any>> =
            arrayOf(
                arrayOf(HasChildren<Unit?>(emptyList()), HasChildren::class.java),
                arrayOf(HasCollectionReference(HasCollectionReference.Unknown), HasCollectionReference::class.java),
                arrayOf(HasCollectionReference(HasCollectionReference.Known(Namespace("db", "coll"))),
 HasCollectionReference::class.java),
                arrayOf(HasCollectionReference(HasCollectionReference.OnlyCollection("coll")),
 HasCollectionReference::class.java),
                arrayOf(HasFieldReference(HasFieldReference.Unknown), HasFieldReference::class.java),
                arrayOf(HasFieldReference(HasFieldReference.Known("abc")), HasFieldReference::class.java),
                arrayOf(HasFilter<Unit?>(Node(null, emptyList())), HasFilter::class.java),
                arrayOf(HasValueReference(HasValueReference.Unknown), HasValueReference::class.java),
                arrayOf(HasValueReference(HasValueReference.Constant(123, "int")), HasValueReference::class.java),
                arrayOf(HasValueReference(HasValueReference.Runtime("int")), HasValueReference::class.java),
                arrayOf(HasValueReference(HasValueReference.Unknown), HasValueReference::class.java),
                arrayOf(Named("abc"), Named::class.java),
            )
    }
}
