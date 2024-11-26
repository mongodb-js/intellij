package com.mongodb.jbplugin.linting

import com.mongodb.jbplugin.accessadapter.MongoDbReadModelProvider
import com.mongodb.jbplugin.accessadapter.slice.GetCollectionSchema
import com.mongodb.jbplugin.mql.*
import com.mongodb.jbplugin.mql.components.HasAggregation
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasFilter
import com.mongodb.jbplugin.mql.components.HasProjections
import com.mongodb.jbplugin.mql.components.HasSorts
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
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
                        HasCollectionReference(
                            HasCollectionReference.Known(null, null, collectionNamespace)
                        ),
                        HasFilter(
                            listOf(
                                Node(
                                    null,
                                    listOf(
                                        HasFieldReference(
                                            HasFieldReference.FromSchema(null, "myString")
                                        )
                                    )
                                ),
                                Node(
                                    null,
                                    listOf(
                                        HasFieldReference(
                                            HasFieldReference.FromSchema(null, "myBoolean")
                                        )
                                    )
                                ),
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

    @Test
    fun `warns about a referenced field in a nested query not in the specified collection`() {
        val readModelProvider = mock<MongoDbReadModelProvider<Unit>>()
        val collectionNamespace = Namespace("database", "collection")

        `when`(readModelProvider.slice(any(), any<GetCollectionSchema.Slice>())).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    collectionNamespace,
                    BsonObject(
                        mapOf(
                            "myString1" to BsonInt32,
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
                        HasCollectionReference(
                            HasCollectionReference.Known(null, null, collectionNamespace)
                        ),
                        HasFilter(
                            listOf(
                                Node(
                                    null,
                                    listOf(
                                        Named(Name.AND),
                                        HasFilter(
                                            listOf(
                                                Node(
                                                    null,
                                                    listOf(
                                                        HasFieldReference(
                                                            HasFieldReference.FromSchema(
                                                                null,
                                                                "myString"
                                                            )
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    )
                                ),
                            ),
                        ),
                    ),
                ),
            )

        assertEquals(1, result.warnings.size)
        assertInstanceOf(FieldCheckWarning.FieldDoesNotExist::class.java, result.warnings[0])
        val warning = result.warnings[0] as FieldCheckWarning.FieldDoesNotExist
        assertEquals("myString", warning.field)
    }

    @Test
    fun `warns about a referenced field not in the specified collection (alongside a value reference)`() {
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
                        HasCollectionReference(
                            HasCollectionReference.Known(null, null, collectionNamespace)
                        ),
                        HasFilter(
                            listOf(
                                Node(
                                    null,
                                    listOf(
                                        HasFieldReference(
                                            HasFieldReference.FromSchema(null, "myString")
                                        )
                                    )
                                ),
                                Node(
                                    null,
                                    listOf(
                                        HasFieldReference(
                                            HasFieldReference.FromSchema(null, "myBoolean")
                                        ),
                                        HasValueReference(
                                            HasValueReference.Constant(null, true, BsonBoolean)
                                        )
                                    )
                                ),
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

    @Test
    fun `warns about a value not matching the type of underlying field`() {
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
                        HasCollectionReference(
                            HasCollectionReference.Known(null, null, collectionNamespace)
                        ),
                        HasFilter(
                            listOf(
                                Node(
                                    null,
                                    listOf(
                                        HasFieldReference(
                                            HasFieldReference.FromSchema(null, "myInt")
                                        ),
                                        HasValueReference(
                                            HasValueReference.Constant(null, null, BsonNull)
                                        )
                                    )
                                ),
                            ),
                        ),
                    ),
                ),
            )

        assertEquals(1, result.warnings.size)
        assertInstanceOf(FieldCheckWarning.FieldValueTypeMismatch::class.java, result.warnings[0])
        val warning = result.warnings[0] as FieldCheckWarning.FieldValueTypeMismatch
        assertEquals("myInt", warning.field)
    }

    @Test
    fun `warns about the referenced fields in an Aggregation#match not in the specified collection`() {
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
                        HasCollectionReference(
                            HasCollectionReference.Known(null, null, collectionNamespace)
                        ),
                        HasAggregation(
                            children = listOf(
                                Node(
                                    null,
                                    listOf(
                                        Named(Name.MATCH),
                                        HasFilter(
                                            listOf(
                                                Node(
                                                    null,
                                                    listOf(
                                                        HasFieldReference(
                                                            HasFieldReference.FromSchema(
                                                                null,
                                                                "myString"
                                                            )
                                                        )
                                                    )
                                                ),
                                                Node(
                                                    null,
                                                    listOf(
                                                        HasFieldReference(
                                                            HasFieldReference.FromSchema(
                                                                null,
                                                                "myBoolean"
                                                            )
                                                        )
                                                    )
                                                ),
                                            ),
                                        ),
                                    )
                                )
                            )
                        )
                    ),
                ),
            )

        assertEquals(1, result.warnings.size)
        assertInstanceOf(FieldCheckWarning.FieldDoesNotExist::class.java, result.warnings[0])
        val warning = result.warnings[0] as FieldCheckWarning.FieldDoesNotExist
        assertEquals("myBoolean", warning.field)
    }

    @Test
    fun `warns about a value not matching the type of underlying field in Aggregation#match`() {
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
                        HasCollectionReference(
                            HasCollectionReference.Known(null, null, collectionNamespace)
                        ),
                        HasAggregation(
                            listOf(
                                Node(
                                    null,
                                    listOf(
                                        Named(Name.MATCH),
                                        HasFilter(
                                            listOf(
                                                Node(
                                                    null,
                                                    listOf(
                                                        HasFieldReference(
                                                            HasFieldReference.FromSchema(
                                                                null,
                                                                "myInt"
                                                            )
                                                        ),
                                                        HasValueReference(
                                                            HasValueReference.Constant(
                                                                null,
                                                                null,
                                                                BsonNull
                                                            )
                                                        )
                                                    )
                                                ),
                                            ),
                                        ),
                                    )
                                )
                            )
                        )
                    ),
                ),
            )

        assertEquals(1, result.warnings.size)
        assertInstanceOf(FieldCheckWarning.FieldValueTypeMismatch::class.java, result.warnings[0])
        val warning = result.warnings[0] as FieldCheckWarning.FieldValueTypeMismatch
        assertEquals("myInt", warning.field)
    }

    @Test
    fun `warns about the referenced fields in an Aggregation#project not in the specified collection`() {
        val readModelProvider = mock<MongoDbReadModelProvider<Unit>>()
        val collectionNamespace = Namespace("database", "collection")

        `when`(readModelProvider.slice(any(), any<GetCollectionSchema.Slice>())).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    collectionNamespace,
                    BsonObject(
                        mapOf(
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
                        HasCollectionReference(
                            HasCollectionReference.Known(null, null, collectionNamespace)
                        ),
                        HasAggregation(
                            children = listOf(
                                Node(
                                    null,
                                    listOf(
                                        Named(Name.PROJECT),
                                        HasProjections(
                                            listOf(
                                                Node(
                                                    null,
                                                    listOf(
                                                        Named(Name.INCLUDE),
                                                        HasFieldReference(
                                                            HasFieldReference.FromSchema(
                                                                null,
                                                                "myBoolean"
                                                            )
                                                        ),
                                                        HasValueReference(
                                                            HasValueReference.Inferred(
                                                                null,
                                                                1,
                                                                BsonInt32
                                                            )
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    ),
                ),
            )

        assertEquals(1, result.warnings.size)
        assertInstanceOf(FieldCheckWarning.FieldDoesNotExist::class.java, result.warnings[0])
        val warning = result.warnings[0] as FieldCheckWarning.FieldDoesNotExist
        assertEquals("myBoolean", warning.field)
    }

    @Test
    fun `warns about the referenced fields in an Aggregation#sort not in the specified collection`() {
        val readModelProvider = mock<MongoDbReadModelProvider<Unit>>()
        val collectionNamespace = Namespace("database", "collection")

        `when`(readModelProvider.slice(any(), any<GetCollectionSchema.Slice>())).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    collectionNamespace,
                    BsonObject(
                        mapOf(
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
                        HasCollectionReference(
                            HasCollectionReference.Known(null, null, collectionNamespace)
                        ),
                        HasAggregation(
                            children = listOf(
                                Node(
                                    null,
                                    listOf(
                                        Named(Name.SORT),
                                        HasSorts(
                                            listOf(
                                                Node(
                                                    null,
                                                    listOf(
                                                        Named(Name.ASCENDING),
                                                        HasFieldReference(
                                                            HasFieldReference.FromSchema(
                                                                null,
                                                                "myBoolean"
                                                            )
                                                        ),
                                                        HasValueReference(
                                                            HasValueReference.Inferred(
                                                                null,
                                                                1,
                                                                BsonInt32
                                                            )
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    ),
                ),
            )

        assertEquals(1, result.warnings.size)
        assertInstanceOf(FieldCheckWarning.FieldDoesNotExist::class.java, result.warnings[0])
        val warning = result.warnings[0] as FieldCheckWarning.FieldDoesNotExist
        assertEquals("myBoolean", warning.field)
    }

    @Test
    fun `should not warn about the referenced fields in an Aggregation#addFields`() {
        val readModelProvider = mock<MongoDbReadModelProvider<Unit>>()
        val collectionNamespace = Namespace("database", "collection")

        `when`(readModelProvider.slice(any(), any<GetCollectionSchema.Slice>())).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    collectionNamespace,
                    BsonObject(
                        mapOf(
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
                        HasCollectionReference(
                            HasCollectionReference.Known(null, null, collectionNamespace)
                        ),
                        HasAggregation(
                            children = listOf(
                                Node(
                                    null,
                                    listOf(
                                        Named(Name.SORT),
                                        HasSorts(
                                            listOf(
                                                Node(
                                                    null,
                                                    listOf(
                                                        Named(Name.ASCENDING),
                                                        HasFieldReference(
                                                            HasFieldReference.Computed(
                                                                null,
                                                                "myBoolean"
                                                            )
                                                        ),
                                                        HasValueReference(
                                                            HasValueReference.Constant(
                                                                null,
                                                                1,
                                                                BsonInt32
                                                            )
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    ),
                ),
            )

        assertEquals(0, result.warnings.size)
    }

    @Test
    fun `should not warn about the referenced fields in an Aggregation#unwind`() {
        val readModelProvider = mock<MongoDbReadModelProvider<Unit>>()
        val collectionNamespace = Namespace("database", "collection")

        `when`(readModelProvider.slice(any(), any<GetCollectionSchema.Slice>())).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    collectionNamespace,
                    BsonObject(
                        mapOf(
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
                        HasCollectionReference(
                            HasCollectionReference.Known(null, null, collectionNamespace)
                        ),
                        HasAggregation(
                            children = listOf(
                                Node(
                                    null,
                                    listOf(
                                        Named(Name.UNWIND),
                                        HasFieldReference(
                                            HasFieldReference.FromSchema(
                                                null,
                                                "myBoolean",
                                                "${'$'}myBoolean"
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    ),
                ),
            )

        assertEquals(1, result.warnings.size)
    }
}
