package com.mongodb.jbplugin.linting

import com.mongodb.jbplugin.accessadapter.MongoDbReadModelProvider
import com.mongodb.jbplugin.accessadapter.slice.ListCollections
import com.mongodb.jbplugin.accessadapter.slice.ListDatabases
import com.mongodb.jbplugin.mql.Namespace
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any

class NamespaceCheckingLinterTest {
    @Test
    fun `warns about a referenced database not existing`() {
        val readModelProvider = mock<MongoDbReadModelProvider<Unit>>()
        val collectionNamespace = Namespace("database", "collection")

        `when`(readModelProvider.slice(any(), any<ListDatabases.Slice>())).thenReturn(
            ListDatabases(emptyList())
        )

        val result =
            NamespaceCheckingLinter.lintQuery(
                Unit,
                readModelProvider,
                Node(
                    null,
                    listOf(
                        HasCollectionReference(
                            HasCollectionReference.Known(Unit, Unit, collectionNamespace)
                        ),
                    ),
                ),
            )

        assertEquals(1, result.warnings.size)
        assertInstanceOf(NamespaceCheckWarning.DatabaseDoesNotExist::class.java, result.warnings[0])
        val warning = result.warnings[0] as NamespaceCheckWarning.DatabaseDoesNotExist
        assertEquals("database", warning.database)
    }

    @Test
    fun `warns about a referenced collection not existing`() {
        val readModelProvider = mock<MongoDbReadModelProvider<Unit>>()
        val collectionNamespace = Namespace("database", "collection")

        `when`(readModelProvider.slice(any(), any<ListDatabases.Slice>())).thenReturn(
            ListDatabases(listOf(ListDatabases.Database("database")))
        )

        `when`(readModelProvider.slice(any(), any<ListCollections.Slice>())).thenReturn(
            ListCollections(emptyList())
        )

        val result =
            NamespaceCheckingLinter.lintQuery(
                Unit,
                readModelProvider,
                Node(
                    null,
                    listOf(
                        HasCollectionReference(
                            HasCollectionReference.Known(Unit, Unit, collectionNamespace)
                        ),
                    ),
                ),
            )

        assertEquals(1, result.warnings.size)
        assertInstanceOf(
            NamespaceCheckWarning.CollectionDoesNotExist::class.java,
            result.warnings[0]
        )
        val warning = result.warnings[0] as NamespaceCheckWarning.CollectionDoesNotExist
        assertEquals("database", warning.database)
        assertEquals("collection", warning.collection)
    }

    @Test
    fun `warns about an unknown namespace if only collection is provided`() {
        val readModelProvider = mock<MongoDbReadModelProvider<Unit>>()

        val result =
            NamespaceCheckingLinter.lintQuery(
                Unit,
                readModelProvider,
                Node(
                    null,
                    listOf(
                        HasCollectionReference(
                            HasCollectionReference.OnlyCollection(Unit, "collection")
                        ),
                    ),
                ),
            )

        assertEquals(1, result.warnings.size)
        assertInstanceOf(NamespaceCheckWarning.NoNamespaceInferred::class.java, result.warnings[0])
    }

    @Test
    fun `warns about an unknown namespace if unknown`() {
        val readModelProvider = mock<MongoDbReadModelProvider<Unit>>()

        val result =
            NamespaceCheckingLinter.lintQuery(
                Unit,
                readModelProvider,
                Node(
                    null,
                    listOf(
                        HasCollectionReference(HasCollectionReference.Unknown),
                    ),
                ),
            )

        assertEquals(1, result.warnings.size)
        assertInstanceOf(NamespaceCheckWarning.NoNamespaceInferred::class.java, result.warnings[0])
    }

    @Test
    fun `warns about an unknown namespace if not provided`() {
        val readModelProvider = mock<MongoDbReadModelProvider<Unit>>()

        val result =
            NamespaceCheckingLinter.lintQuery(
                Unit,
                readModelProvider,
                Node(
                    null,
                    emptyList(),
                ),
            )

        assertEquals(1, result.warnings.size)
        assertInstanceOf(NamespaceCheckWarning.NoNamespaceInferred::class.java, result.warnings[0])
    }
}
