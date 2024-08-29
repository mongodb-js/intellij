package com.mongodb.jbplugin.dialects.mongosh

import com.mongodb.jbplugin.dialects.DialectFormatter
import com.mongodb.jbplugin.mql.BsonType
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasCollectionReference

object MongoshDialectFormatter : DialectFormatter {
    override fun <S> formatQuery(query: Node<S>, explain: Boolean): String {
        val runtimeParametersBuilder = mutableListOf<String>()
        val queryBuilder = mutableListOf<String>()

        val collectionReference = query.component<HasCollectionReference>()
        when (val reference = collectionReference?.reference) {
            is HasCollectionReference.Known -> {
                queryBuilder += ("""
                    db.getSiblingDb("${reference.namespace.database}").${reference.namespace.collection}
                """.trimIndent())
            }
            is HasCollectionReference.OnlyCollection -> {
                runtimeParametersBuilder += ("""
                    var databaseName = ""
                """.trimIndent())

                queryBuilder += ("""
                    db.getSiblingDb(databaseName).${reference.collection}
                """.trimIndent())
            }
            else -> {
                runtimeParametersBuilder += ("""
                    var databaseName = ""
                """.trimIndent())

                runtimeParametersBuilder += ("""
                    var collectionName = ""
                """.trimIndent())

                queryBuilder += ("""
                    db.getSiblingDb(databaseName).getCollection(collectionName)
                """.trimIndent())
            }
        }

        queryBuilder += ".find("
        // generate query here
        queryBuilder += ")"

        if (explain) {
            queryBuilder += ".explain()"
        }

        val runtimeParams = runtimeParametersBuilder.joinToString("\n")
        val queryString = queryBuilder.joinToString("")
        return runtimeParams + "\n" + queryString
    }



    override fun formatType(type: BsonType) = ""

}