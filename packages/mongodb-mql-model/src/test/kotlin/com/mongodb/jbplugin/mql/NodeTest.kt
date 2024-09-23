package com.mongodb.jbplugin.mql

import com.mongodb.jbplugin.mql.components.*
import io.github.z4kn4fein.semver.Version
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class NodeTest {
    @Test
    fun `is able to get a component if exists`() {
        val node = Node<Unit?>(null, listOf(Named(Name.EQ)))
        val named = node.component<Named>()

        assertEquals(Name.EQ, named!!.name)
    }

    @Test
    fun `returns null if a component does not exist`() {
        val node = Node<Unit?>(null, listOf())
        val named = node.component<HasFieldReference<Unit?>>()

        assertNull(named)
    }

    @Test
    fun `is able to get all components of the same type`() {
        val node =
            Node<Unit?>(
                null,
                listOf(
                    HasFieldReference(HasFieldReference.Known(null, "field1")),
                    HasFieldReference(
                        HasFieldReference.Known(
                            null,
                            "field2",
                        ),
                    ),
                ),
            )
        val fieldReferences = node.components<HasFieldReference<Unit?>>()

        assertEquals("field1", (fieldReferences[0].reference as HasFieldReference.Known).fieldName)
        assertEquals("field2", (fieldReferences[1].reference as HasFieldReference.Known).fieldName)
    }

    @Test
    fun `returns true if a component of that type exists`() {
        val node =
            Node<Unit?>(
                null,
                listOf(HasFieldReference(HasFieldReference.Known(null, "field1"))),
            )
        val hasFieldReferences = node.hasComponent<HasFieldReference<Unit?>>()

        assertTrue(hasFieldReferences)
    }

    @Test
    fun `returns false if a component of that type does not exist`() {
        val node =
            Node<Unit?>(
                null,
                listOf(HasFieldReference(HasFieldReference.Known(null, "field1"))),
            )
        val hasNamedComponent = node.hasComponent<Named>()

        assertFalse(hasNamedComponent)
    }

    @Test
    fun `it copies the Node by correctly mapping the underlying components`() {
        val node = Node<Unit?>(
            null,
            listOf(
                HasCollectionReference(HasCollectionReference.OnlyCollection(1, "qwerty")),
                HasCollectionReference(HasCollectionReference.OnlyCollection(1, "qwerty")),
            )
        )

        val copiedNode = node.copy {
            HasCollectionReference(
                HasCollectionReference.Unknown
            )
        }

        // Does not modify the original node
        assertTrue(
            node.components<HasCollectionReference<*>>()
                .all { collection ->
                    collection.reference is HasCollectionReference.OnlyCollection
                }
        )

        // creates a copy with the modified components as per our logic
        assertTrue(
            copiedNode.components<HasCollectionReference<*>>()
                .all { collection -> collection.reference is HasCollectionReference.Unknown }
        )
    }

    @Test
    fun `it creates a copy of the query with overwritten database in the components`() {
        val node = Node<Unit?>(
            null,
            listOf(
                HasCollectionReference(HasCollectionReference.OnlyCollection(1, "qwerty")),
            )
        )

        val modifiedNode = node.queryWithOverwrittenDatabase("foo")
        val nodeReference = modifiedNode.component<HasCollectionReference<*>>()
        // Does not modify the original node
        assertTrue(
            node.component<HasCollectionReference<*>>()?.let {
                it.reference is HasCollectionReference.OnlyCollection
            }
                ?: false
        )

        assertTrue(
            nodeReference
                ?.let {
                    it.reference is HasCollectionReference.Known
                }
                ?: false
        )
        assertEquals(
            (nodeReference?.reference as HasCollectionReference.Known).namespace.database,
            "foo"
        )
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

    @Test
    fun `adds target cluster if does not exist`() {
        val targetCluster = HasTargetCluster(Version.parse("7.0.0"))
        val query = Node(Unit, emptyList()).withTargetCluster(targetCluster)

        assertEquals(targetCluster, query.component<HasTargetCluster>())
    }

    @Test
    fun `removes old target cluster and adds a new one`() {
        val oldCluster = HasTargetCluster(Version.parse("5.0.0"))
        val targetCluster = HasTargetCluster(Version.parse("7.0.0"))
        val query = Node(Unit, listOf(oldCluster)).withTargetCluster(targetCluster)

        assertEquals(targetCluster, query.component<HasTargetCluster>())
    }

    companion object {
        @JvmStatic
        fun validComponents(): Array<Array<Any>> =
            arrayOf(
                arrayOf(HasChildren<Unit?>(emptyList()), HasChildren::class.java),
                arrayOf(
                    HasCollectionReference(HasCollectionReference.Unknown),
                    HasCollectionReference::class.java
                ),
                arrayOf(
                    HasCollectionReference(
                        HasCollectionReference.Known(1, 2, Namespace("db", "coll"))
                    ),
                    HasCollectionReference::class.java,
                ),
                arrayOf(
                    HasCollectionReference(HasCollectionReference.OnlyCollection(1, "coll")),
                    HasCollectionReference::class.java,
                ),
                arrayOf(
                    HasFieldReference(HasFieldReference.Unknown),
                    HasFieldReference::class.java
                ),
                arrayOf(
                    HasFieldReference(HasFieldReference.Known(null, "abc")),
                    HasFieldReference::class.java
                ),
                arrayOf(HasFilter<Unit?>(Node(null, emptyList())), HasFilter::class.java),
                arrayOf(
                    HasValueReference(HasValueReference.Unknown),
                    HasValueReference::class.java
                ),
                arrayOf(
                    HasValueReference(HasValueReference.Constant(null, 123, BsonInt32)),
                    HasValueReference::class.java
                ),
                arrayOf(
                    HasValueReference(HasValueReference.Runtime(null, BsonInt32)),
                    HasValueReference::class.java
                ),
                arrayOf(
                    HasValueReference(HasValueReference.Unknown),
                    HasValueReference::class.java
                ),
                arrayOf(Named(Name.EQ), Named::class.java),
            )
    }
}
