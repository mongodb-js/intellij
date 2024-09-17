package com.mongodb.jbplugin.dialects.mongosh

import com.mongodb.jbplugin.mql.BsonString
import com.mongodb.jbplugin.mql.Namespace
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.*
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class MongoshDialectFormatterTest {
    @Test
    fun `can format a query without references to a collection reference`() {
        assertGeneratedQuery("""
            var collection = ""
            var database = ""

            db.getSiblingDB(database).getCollection(collection).find({ "myField": "myVal", })
        """.trimIndent()) {
            Node(
                Unit, listOf(
                    HasChildren(
                        listOf(
                            Node(
                                Unit, listOf(
                                    HasFieldReference(HasFieldReference.Known(Unit, "myField")),
                                    HasValueReference(HasValueReference.Constant(Unit, "myVal", BsonString))
                                )
                            )
                        )
                    )
                )
            )
        }
    }

    @Test
    fun `can format a simple query`() {
        val namespace = Namespace("myDb", "myColl")

        assertGeneratedQuery(
            """
            db.getSiblingDB("myDb").getCollection("myColl").find({ "myField": "myVal", })
        """.trimIndent()
        ) {
            Node(
                Unit, listOf(
                    HasCollectionReference(HasCollectionReference.Known(Unit, Unit, namespace)),
                    HasChildren(
                        listOf(
                            Node(
                                Unit, listOf(
                                    Named(Name.EQ),
                                    HasFieldReference(HasFieldReference.Known(Unit, "myField")),
                                    HasValueReference(HasValueReference.Constant(Unit, "myVal", BsonString))
                                )
                            )
                        )
                    )
                )
            )
        }
    }

    @Test
    fun `can format a query with an explain plan`() {
        assertGeneratedQuery("""
            var collection = ""
            var database = ""

            db.getSiblingDB(database).getCollection(collection).find({ "myField": "myVal", }).explain()
        """.trimIndent(), explain = true) {
            Node(
                Unit, listOf(
                    HasChildren(
                        listOf(
                            Node(
                                Unit, listOf(
                                    HasFieldReference(HasFieldReference.Known(Unit, "myField")),
                                    HasValueReference(HasValueReference.Constant(Unit, "myVal", BsonString))
                                )
                            )
                        )
                    )
                )
            )
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["and", "or", "not"])
    fun `can format a query with subquery operators`(operator: String) {
        assertGeneratedQuery("""
            var collection = ""
            var database = ""

            db.getSiblingDB(database).getCollection(collection).find({ "${"$"}$operator": [ { "myField": "myVal"}, ]})
        """.trimIndent()) {
            Node(
                Unit, listOf(
                    Named(Name.from(operator)),
                    HasChildren(
                        listOf(
                            Node(
                                Unit, listOf(
                                    HasFieldReference(HasFieldReference.Known(Unit, "myField")),
                                    HasValueReference(HasValueReference.Constant(Unit, "myVal", BsonString))
                                )
                            )
                        )
                    )
                )
            )
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["lt", "lte", "gt", "gte"])
    fun `can format a query with range operators`(operator: String) {
        assertGeneratedQuery("""
            var collection = ""
            var database = ""

            db.getSiblingDB(database).getCollection(collection).find({ "myField": { "${"$"}$operator": "myVal"}, })
        """.trimIndent()) {
            Node(
                Unit, listOf(
                    HasChildren(
                        listOf(
                            Node(
                                Unit, listOf(
                                    Named(Name.from(operator)),
                                    HasFieldReference(HasFieldReference.Known(Unit, "myField")),
                                    HasValueReference(HasValueReference.Constant(Unit, "myVal", BsonString))
                                )
                            )
                        )
                    )
                )
            )
        }
    }

    @Test
    fun `generates an index suggestion for a query given its fields`() {
        assertGeneratedIndex(
            """
                // Potential fields to consider indexing: myField, myField2
                // Learn about creating an index: https://www.mongodb.com/docs/v7.0/core/data-model-operations/#indexes
                db.getSiblingDB("myDb").getCollection("myCollection")
                  .createIndex({ "<your_field_1>": 1, "<your_field_2>": 1 })
            """.trimIndent()
        ) {
            Node(
                Unit, listOf(
                    HasCollectionReference(HasCollectionReference.Known(Unit, Unit, Namespace("myDb", "myCollection"))),
                    HasChildren(
                        listOf(
                            Node(
                                Unit, listOf(
                                    HasFieldReference(HasFieldReference.Known(Unit, "myField")),
                                    HasValueReference(HasValueReference.Constant(Unit, "myVal", BsonString))
                                )
                            ),
                            Node(
                                Unit, listOf(
                                    HasFieldReference(HasFieldReference.Known(Unit, "myField2")),
                                    HasValueReference(HasValueReference.Constant(Unit, "myVal2", BsonString))
                                )
                            )
                        )
                    )
                )
            )
        }
    }
}

private fun assertGeneratedQuery(
@Language("js") js: String,
 explain: Boolean = false,
 script: () -> Node<Unit>
) {
    val generated = MongoshDialectFormatter.formatQuery(script(), explain)
    assertEquals(js, generated)
}

private fun assertGeneratedIndex(@Language("js") js: String, script: () -> Node<Unit>) {
    val generated = MongoshDialectFormatter.indexCommandForQuery(script())
    assertEquals(js, generated)
}