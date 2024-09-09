package com.mongodb.jbplugin.dialects.mongosh

import com.mongodb.jbplugin.dialects.DialectFormatter
import com.mongodb.jbplugin.mql.BsonType
import com.mongodb.jbplugin.mql.IndexAnalyzer
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasTargetCluster
import io.github.z4kn4fein.semver.Version
import org.owasp.encoder.Encode

object MongoshDialectFormatter : DialectFormatter {
    override fun <S> formatQuery(query: Node<S>, explain: Boolean) = ""
    override fun <S> indexCommandForQuery(query: Node<S>): String = when (val index = IndexAnalyzer.analyze(query)) {
            is IndexAnalyzer.SuggestedIndex.NoIndex -> ""
            is IndexAnalyzer.SuggestedIndex.MongoDbIndex -> {
                val targetCluster = query.component<HasTargetCluster>()
                val version = targetCluster?.majorVersion ?: Version(7)
                val docPrefix = "https://www.mongodb.com/docs/v${version.major}.${version.minor}"

                val fieldList = index.fields.joinToString { Encode.forJavaScript(it.fieldName) }
                val (dbName, collName) = when (val collRef = index.collectionReference.reference) {
                    is HasCollectionReference.Unknown -> ("<database>" to "<collection>")
                    is HasCollectionReference.OnlyCollection -> ("<database>" to collRef.collection)
                    is HasCollectionReference.Known -> (collRef.namespace.database to collRef.namespace.collection)
                }

                val encodedDbName = Encode.forJavaScript(dbName)
                val encodedColl = Encode.forJavaScript(collName)

                """
                    // Potential fields to consider indexing: $fieldList
                    // Learn about creating an index: $docPrefix/core/data-model-operations/#indexes
                   
                    db.getSiblingDB("$encodedDbName").getCollection("$encodedColl")
                      .createIndex({ "<your_field>": 1 })
                """.trimIndent()
            }
        }

    override fun formatType(type: BsonType) = ""
}