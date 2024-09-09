package com.mongodb.jbplugin.mql

import com.mongodb.jbplugin.mql.components.HasChildren
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasCollectionReference.Known
import com.mongodb.jbplugin.mql.components.HasFieldReference
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class IndexAnalyzerTest {
    @Test
    fun `queries without a collection reference component are not supported`() {
        val query = Node(Unit, emptyList())
        val result = IndexAnalyzer.analyze(query)

        assertEquals(IndexAnalyzer.SuggestedIndex.NoIndex, result)
    }

    @Test
    fun `returns the suggested list of fields for a mongodb query`() {
        val collectionReference = HasCollectionReference(Known(Unit, Unit, Namespace("myDb", "myColl")))
        val query = Node(
            Unit, listOf(
                collectionReference,
                HasChildren(
                    listOf(
                        Node(
                            Unit, listOf(
                                HasFieldReference(HasFieldReference.Known(Unit, "myField"))
                            )
                        )
                    )
                )
            )
        )

        val result = IndexAnalyzer.analyze(query) as IndexAnalyzer.SuggestedIndex.MongoDbIndex

        assertEquals(1, result.fields.size)
        assertEquals(collectionReference, result.collectionReference)
        assertEquals(IndexAnalyzer.SuggestedIndex.MongoDbIndexField("myField", Unit), result.fields[0])
    }

    @Test
    fun `removes repeated field references`() {
        val collectionReference = HasCollectionReference(Known(Unit, Unit, Namespace("myDb", "myColl")))
        val query = Node(
            Unit, listOf(
                collectionReference,
                HasChildren(
                    listOf(
                        Node(
                            Unit, listOf(
                                HasFieldReference(HasFieldReference.Known(Unit, "myField"))
                            )
                        ),
                        Node(
                            Unit, listOf(
                                HasFieldReference(HasFieldReference.Known(Unit, "mySecondField"))
                            )
                        ),
                        Node(
                            Unit, listOf(
                                HasFieldReference(HasFieldReference.Known(Unit, "myField"))
                            )
                        )
                    )
                )
            )
        )

        val result = IndexAnalyzer.analyze(query) as IndexAnalyzer.SuggestedIndex.MongoDbIndex

        assertEquals(2, result.fields.size)
        assertEquals(collectionReference, result.collectionReference)
        assertEquals(IndexAnalyzer.SuggestedIndex.MongoDbIndexField("myField", Unit), result.fields[0])
        assertEquals(IndexAnalyzer.SuggestedIndex.MongoDbIndexField("mySecondField", Unit), result.fields[1])
    }
}