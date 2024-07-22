package com.mongodb.jbplugin.linting

import com.mongodb.jbplugin.accessadapter.MongoDbReadModelProvider
import com.mongodb.jbplugin.accessadapter.slice.GetCollectionSchema
import com.mongodb.jbplugin.mql.*
import com.mongodb.jbplugin.mql.components.HasChildren
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasFieldReference
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.kotlin.any

class FieldCheckingLinterTest {
    @Test
    fun `warns about a referenced field not in the specified collection`() {
        val readModelProvider = mock<MongoDbReadModelProvider<Unit>>()
        val collectionNamespace = Namespace("database", "collection")

        `when`(readModelProvider.slice(any(), any<GetCollectionSchema.Slice>())).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    collectionNamespace,
                    BsonObject(
                        mapOf(
                            "myString" to BsonString,
                            "myInt" to BsonInt32,
                        ),
                    ),
                ),
            ),
        )

        val result =
            FieldCheckingLinter.lintQuery(
                Unit,
                readModelProvider,
                Node(
                    null,
                    listOf(
                        HasCollectionReference(HasCollectionReference.Known(collectionNamespace)),
                        HasChildren(
                            listOf(
                                Node(null, listOf(HasFieldReference(HasFieldReference.Known(null, "myString")))),
                                Node(null, listOf(HasFieldReference(HasFieldReference.Known(null, "myBoolean")))),
                            ),
                        ),
                    ),
                ),
            )

        assertEquals(1, result.warnings.size)
        assertInstanceOf(FieldCheckWarning.FieldDoesNotExist::class.java, result.warnings[0])
        val warning = result.warnings[0] as FieldCheckWarning.FieldDoesNotExist
        assertEquals("myBoolean", warning.field)
    }
}
